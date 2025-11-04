/*
 * mini-cp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License  v3
 * as published by the Free Software Foundation.
 *
 * mini-cp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY.
 * See the GNU Lesser General Public License  for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with mini-cp. If not, see http://www.gnu.org/licenses/lgpl-3.0.en.html
 *
 * Copyright (c)  2018. by Laurent Michel, Pierre Schaus, Pascal Van Hentenryck
 *
 * mini-cpbp, replacing classic propagation by belief propagation
 * Copyright (c)  2019. by Gilles Pesant
 */

package minicpbp.examples;

import minicpbp.cp.Factory;
import minicpbp.engine.constraints.Circuit;
import minicpbp.engine.constraints.Element1D;
import minicpbp.engine.constraints.LessOrEqual;
import minicpbp.engine.constraints.Markov;
import minicpbp.engine.core.BoolVar;
import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.LDSearch;
import minicpbp.search.Objective;
import minicpbp.util.exception.InconsistencyException;
import minicpbp.util.io.InputReader;
import minicpbp.search.SearchStatistics;
import minicpbp.state.StateManager;
import minicpbp.examples.config.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.io.BufferedReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.icu.impl.Pair;

public class NLP_MLM_v2 {
    final static String mask_string = "[MASK]";

    private static class ScoredSentence {
        private String sentence;
        private double perplexity;
        private double weight;
    
        public ScoredSentence(String sentence, double perplexity) {
            this.sentence = sentence;
            this.perplexity = perplexity;
            this.weight = 0.0;
        }
        
        public String getSentence() { return sentence; }
        public double getPerplexity() { return perplexity; }
        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }
        
        @Override
        public String toString() {
            return String.format("%s (Perplexity: %.2f, Weight: %.4f)", 
                            sentence, perplexity, weight);
        }
    }

    private static ScoredSentence selectWeightedRandom(ArrayList<ScoredSentence> sentences, Random random, double temperature) {
        if (sentences.isEmpty()) return null;

        double totalWeight = 0.0;
        
        for (ScoredSentence sent : sentences) {
            double weight = Math.exp(-sent.getPerplexity() / temperature);
            sent.setWeight(weight);
            totalWeight += weight;
        }
        
        for (ScoredSentence sent : sentences) {
            sent.setWeight(sent.getWeight() / totalWeight);
        }
        
        double randomValue = random.nextDouble(); // 0.0 to 1.0
        double cumulativeWeight = 0.0;
        
        for (ScoredSentence sent : sentences) {
            cumulativeWeight += sent.getWeight();
            if (randomValue <= cumulativeWeight) {
                return sent;
            }
        }
        
        // Fallback (shouldn't reach here due to normalization)
        return sentences.get(sentences.size() - 1);
    }
        
    

    private static String sentenceBuilder(ArrayList<ScoredSentence> bases) {
        ScoredSentence base = selectWeightedRandom(bases, new Random(), 0.8);
        System.out.println("Base sentence: " + base);
        String[] words = base.getSentence().split(" ");
        Random rand = new Random();
        int numMasks = rand.nextBoolean() ? 3 : 4;
        Set<Integer> maskIndices = new HashSet<>();
        while (maskIndices.size() < numMasks) {
            int idx = rand.nextInt(words.length + 1);
            if(idx==words.length) idx--;//Double prob for last word
            maskIndices.add(idx);
        }
        System.out.println("Masking indices: " + maskIndices);
        for (int idx : maskIndices) {
            words[idx] = mask_string;
        }
        return String.join(" ", words);
    }
   public static void main(String[] args) throws Exception {
    
        System.out.println("3 novembre");
        int port = Integer.parseInt(args[1]);
        final int NUM_ITERATIONS = Integer.parseInt(args[3]);
        final double weight = Double.parseDouble(args[0]);
        final int seed = Integer.parseInt(args[4]);

        long startTime = System.currentTimeMillis();
        try {
        HttpClient client = HttpClient.newHttpClient();
        ArrayList<ScoredSentence> base_sentence = new ArrayList<>();

        ObjectMapper objectMapper = new ObjectMapper();
        String initial_sentence = "And he had no idea what to do with the fact that she was in";
        try {
            BufferedReader br = Files.newBufferedReader(Paths.get("IJCAI2023_EN_BENCH_SORTED"), StandardCharsets.UTF_8);
            String line;
            int lineNumber = 0;
            while ((line = br.readLine()) != null) {
                System.out.println("Reading line " + lineNumber);
                if (lineNumber == seed) {
                    int lastComma = line.lastIndexOf(',');
                    initial_sentence = (lastComma >= 0) ? line.substring(0, lastComma).trim() : line.trim();
                    break;
                }
                lineNumber++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            initial_sentence = "And he had no idea what to do with the fact that she was in";
            System.err.println("Could not read base sentence file, using default.");
        }

        HttpRequest request_init = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/perplexity"))
            .POST(HttpRequest.BodyPublishers.ofString(initial_sentence))
            .build();
        String response_init = client.sendAsync(request_init, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
        JsonNode jsonNode_init = objectMapper.readTree(response_init);
        double ppl_init = jsonNode_init.get("perplexity").asDouble();
        base_sentence.add(new ScoredSentence(initial_sentence, ppl_init));




        final String llm_name="modernBert";//

        List<Logging>  logs = new ArrayList<>();

        List<String> lines = Collections.emptyList();
         try {
             lines = Files.readAllLines(Paths.get("./src/main/java/minicpbp/examples/data/MNREAD/"+llm_name+"/tokenizer_dict.txt"),StandardCharsets.UTF_8);
         }
         catch (Exception e) {
             e.printStackTrace();
         }

        int token_size = Integer.parseInt(lines.get(lines.size()-1).split(":")[0]);

         String[] corrected_lines = new String[token_size];
         Arrays.fill(corrected_lines, "");
         for(int i=0;i<lines.size();i++){
             String[] line = lines.get(i).split("::");
             if(line.length>1){
                 corrected_lines[Integer.parseInt(line[0])]=line[1];
             }
         }
 
        final List<String> tokens_list = Arrays.asList(corrected_lines);
        ArrayList<String> words = new ArrayList<>();
        Map<Integer, List<Integer>> corpusDomainsSet = new HashMap<>();
        Map<Integer, Integer> corpusDomainToIndex = new HashMap<>();
        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get("./src/main/java/minicpbp/examples/data/MNREAD/"+llm_name+"/corpus_tokenized_words.json")), StandardCharsets.UTF_8);
            final List<List<Integer>> parsedCorpusDomains = objectMapper.readValue(jsonContent, new TypeReference<List<List<Integer>>>() {}); 
            int j = 0;
            for (int i = 0; i < parsedCorpusDomains.size(); i++) {
                List<Integer> sublist = parsedCorpusDomains.get(i);
                if(sublist.size() != 1) continue;
                String word_string = sublist.stream().map(n -> tokens_list.get(n)).collect(Collectors.joining(""));//.strip();
                if (words.contains(word_string)) {
                    continue;
                }
                words.add(word_string.replace("##", ""));
                if (!corpusDomainsSet.containsKey(sublist.get(0)))
                    corpusDomainsSet.put(sublist.get(0), new ArrayList<>(List.of(j)));
                else
                    corpusDomainsSet.get(sublist.get(0)).add(j);
                corpusDomainToIndex.put(j, sublist.get(0));
                j++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (String w : base_sentence.get(0).getSentence().split(" ")) {
            if (!words.contains(w)) {
                words.add(w);
            }
        }

        List<Integer> corpusDomains = new ArrayList<>();
        for (int idx = 0; idx < words.size(); idx++) {
            corpusDomains.add(idx);
        }


        System.out.println("corpusDomains size: " + corpusDomains.size());


        int[] start_words  = new int[corpusDomains.size()];
        for(int i=0; i<corpusDomains.size(); i++){
            if(words.get(corpusDomains.get(i)).strip().length()!=0 && words.get(corpusDomains.get(i)).charAt(0)==' '){
                start_words[i]=1;
            }
        }


        String charToIntFilePath = "./src/main/java/minicpbp/examples/data/MNREAD/TimesCost_modified.json";
        Map<String, Integer> charToIntMap = new HashMap<>();
        try {
            String charToIntJson = new String(Files.readAllBytes(Paths.get(charToIntFilePath)), StandardCharsets.UTF_8);
            charToIntMap = objectMapper.readValue(charToIntJson, new TypeReference<Map<String, Integer>>() {});
        } catch (Exception e) {
            e.printStackTrace();
        }

        int[] lengthTokens = new int[corpusDomains.size()];
        int[] charNum = new int[corpusDomains.size()];
        for (int i = 0; i < corpusDomains.size(); i++) {
            int domainIndex = corpusDomains.get(i);
            String word = words.get(domainIndex);
            int charSum = 0;
            charNum[i]=word.length();
            for (char c : word.toCharArray()) {
                if(word.length()==0){
                    break;
                }
                String charStr = String.valueOf(c);
                charSum += charToIntMap.getOrDefault(charStr, 1000000);
                if (charToIntMap.getOrDefault(charStr, 1000000) == 1000000) {
                    System.err.println("Character not found in mapping: " + charStr);
                    System.err.println("Word: " + word);
                    System.err.println("Index: " + domainIndex);
                    throw new Exception("Character not found");
                }
            }
            lengthTokens[i]=charSum;
        }

        ConstraintBuilder cb = new MNREAD_MLM_Config();

        final int SENTENCE_MAX_NUMBER_TOKENS = base_sentence.get(0).getSentence().split(" ").length;
        final int ORACLE_TOP_K = 10;
        final boolean PRINT_TRACE = false;
        final int NUM_PB = 3;
        final double w = weight;
        final int failureLimit = 10;

        String[] tokens_used = new String[SENTENCE_MAX_NUMBER_TOKENS];

        System.out.println("Building model...");
        
        Solver cp = makeSolver();

        IntVar[] word_index = makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, 0, corpusDomains.size()-1);
        cb.build(new SolverContext(cp, corpusDomains.size(), -1, -1, charNum, lengthTokens, word_index, words));

        Random rand = new Random();



        DFSearch dfs = makeDfs(cp, firstFailMaxMarginalValue(word_index));
        int l=-1;

        String[] current_sentence= new String[1];
        String[] original_sentence= new String[1];

        dfs.onSolution(() -> {
            double perplexityScore = -1;
            // build sentence from assigned word_index values
            String solution="";
            for (int i = 0; i < word_index.length; i++) {
                int assigned = word_index[i].min(); // value assigned at solution
                solution += words.get(assigned);             
            }
            current_sentence[0] = solution.trim();
            if (!current_sentence[0].isEmpty() && Character.isLowerCase(current_sentence[0].charAt(0))) {
                current_sentence[0] = Character.toUpperCase(current_sentence[0].charAt(0)) + current_sentence[0].substring(1);
            }{
                current_sentence[0] = Character.toUpperCase(current_sentence[0].charAt(0)) + current_sentence[0].substring(1);
            }
            if (base_sentence.contains(current_sentence[0])) {
                System.out.println("Duplicate sentence, skipping: " + current_sentence[0]);
                return;
            }
            current_sentence[0] = current_sentence[0].endsWith("ERROR") ? current_sentence[0] : current_sentence[0] + ".";
            //System.out.println("solution : " + current_sentence[0]);

            HttpRequest request2 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mlm_tokenize"))
                .POST(HttpRequest.BodyPublishers.ofString(current_sentence[0]))
                .build();
            String response2 = client.sendAsync(request2, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
            JsonNode jsonNode2 = null;
            try {
                jsonNode2 = objectMapper.readTree(response2);
            } catch (JsonMappingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (JsonProcessingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            ArrayNode tokensArray = (ArrayNode) jsonNode2.get("token_ids");
            int[] tokens = new int[tokensArray.size()];
            for (int j = 0; j < tokensArray.size(); j++) {
                tokens[j] = tokensArray.get(j).asInt();
            }



            if(!current_sentence[0].contains("ERROR")){
                if (current_sentence[0].endsWith(".")) {
                    current_sentence[0] = current_sentence[0].substring(0, current_sentence[0].length() - 1);
                }
                HttpRequest request3 = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/perplexity"))
                    .POST(HttpRequest.BodyPublishers.ofString(current_sentence[0]))
                    .build();
                String response3 = client.sendAsync(request3, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
                JsonNode jsonNode3 = null;
                try {
                    jsonNode3 = objectMapper.readTree(response3);
                } catch (JsonMappingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (JsonProcessingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                double ppl = jsonNode3.get("perplexity").asDouble();
                ScoredSentence currentSentence = new ScoredSentence(current_sentence[0], ppl);
                Logging new_log = new Logging(current_sentence[0], original_sentence[0], ppl, tokens, new String[tokens_used.length]);
                if (!base_sentence.contains(currentSentence)) {
                    logs.add(new_log);
                    base_sentence.add(currentSentence);
                }
            }
            else {
                Logging new_log = new Logging(current_sentence[0], original_sentence[0], perplexityScore, tokens, new String[tokens_used.length]);
                logs.add(new_log);
            }
        });

        while (l < NUM_ITERATIONS-1) {
            l++;

            dfs.solveSubjectTo(statistics -> statistics.numberOfSolutions() >= failureLimit, () -> {
                        current_sentence[0] = sentenceBuilder(base_sentence);
                        original_sentence[0] = current_sentence[0];

                        System.out.println("Current sentence: " + current_sentence[0]);

                        String[] sentenceWords = current_sentence[0].split(" ");
                        List<Integer> masked_indexs = new ArrayList<>();
                        for (int idx = 0; idx < sentenceWords.length; idx++) {
                            if (!sentenceWords[idx].equals(mask_string)) {
                                try {
                                    word_index[idx].assign(words.indexOf(" " + sentenceWords[idx]));
                                } catch (Exception e) {
                                    System.out.println(e);
                                    System.err.println("Error assigning index " + idx + " to word " + sentenceWords[idx]);
                                    System.err.println(words.contains(" " + sentenceWords[idx]));
                                }
                            } else {
                                masked_indexs.add(idx);
                            }
                        }
                        
                        HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/mlm"))
                            .POST(HttpRequest.BodyPublishers.ofString("<s>"+current_sentence[0]+"."))
                            .build();
                        String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();

                        System.out.println("Response: Received");

                        JsonNode jsonNode = null;
                        try {
                            jsonNode = objectMapper.readTree(response);
                        } catch (JsonMappingException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (JsonProcessingException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        ObjectNode  maskedTokens = (ObjectNode) jsonNode;
                        System.out.println("Masked tokens found: " + maskedTokens.size());

                        for (Iterator<String> it = maskedTokens.fieldNames(); it.hasNext(); ) {
                            String fieldName = it.next();
                            JsonNode tok = maskedTokens.get(fieldName);
                            System.out.println("Masked token: " + tok.get("mask_word_position").asInt());

                            int z = tok.get("mask_word_position").asInt();
                            ArrayNode probsNode = (ArrayNode) tok.get("probs");
                            ArrayNode tokensNode = (ArrayNode) tok.get("tokens");
                            List<Pair<Integer, Double>> tokenScoreList = new ArrayList<>();
                            for (int idx = 0; idx < probsNode.size(); idx++) {
                                try {
                                double prob = probsNode.get(idx).asDouble();
                                int token = tokensNode.get(idx).asInt();
                                if (!corpusDomainsSet.containsKey(token)) continue;
                                if (prob < 0) continue;

                                Pair<Integer, Double> tuple = Pair.of(token, prob);
                                tokenScoreList.add(tuple);
                                } catch (Exception e) {
                                    if (PRINT_TRACE) {
                                        System.err.println(idx);
                                        System.err.println(e);
                                    }
                                }
                            }

                            int[] tokens = new int[corpusDomains.size()];
                            double[] scores = new double[corpusDomains.size()];

                            double total_score = 0;
            

                            tokenScoreList.sort((a, b) -> Double.compare(
                                b.second, a.second
                            ));


                            int limit = Math.min(ORACLE_TOP_K, tokenScoreList.size());
                            for (int k = 0; k < limit; k++) {
                                int token = tokenScoreList.get(k).first;
                                double score = tokenScoreList.get(k).second;
                                int[] token_indexes = corpusDomainsSet.get(token).stream().mapToInt(Integer::intValue).toArray();
                                for (int token_index : token_indexes) {
                                    tokens[token_index] = token_index;
                                    scores[token_index] = score;
                                    total_score += score;
                                }
                            }
                            for (int j=0; j<tokens.length; j++) {
                                double score=scores[j];
                                if (score > 0) {
                                    score /= total_score;
                                }
                            }

                            Constraint c = Factory.oracle(word_index[z], tokens, scores);

                            c.setWeight(w);
                            cp.post(c);
                        }                                                       
                    }
            );

            

           
            

        }
  
    
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", "ok");
    result.put("port", port);
    result.put("num_iterations", NUM_ITERATIONS);
    result.put("num_pb", NUM_PB);
    result.put("weight", w);
    result.put("llm_name", llm_name);
    result.put("base_sentence", base_sentence.get(0));
    result.put("logs", logs);
    result.put("date", java.time.LocalDateTime.now().toString());  
    result.put("time", (System.currentTimeMillis() - startTime) / 1000.0);
    String OUTPUT_DIR = args.length > 3 ? args[2] : "./outputs";
    Files.createDirectories(Paths.get(OUTPUT_DIR));
    String outputFileName = OUTPUT_DIR + "/result_NLP_MLM_v2_" + System.currentTimeMillis()  + ".json";
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(outputFileName).toFile(), result);
    }
    catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error: " + e);
            // Write error to output file
            String OUTPUT_DIR = args.length > 3 ? args[2] : "./outputs";
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            String outputFileName = OUTPUT_DIR + "/result_NLP_MLM_v2_" + System.currentTimeMillis()  + "_error.json";
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("status", "error");
            errorResult.put("error_message", e.getMessage());
            errorResult.put("exception", e.toString());
            ObjectMapper errorMapper = new ObjectMapper();
            try {
                errorMapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(outputFileName).toFile(), errorResult);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            System.exit(1);
    }
    }


    public static class Logging {

        public String sentence;
        public int[] tokens;
        public double perplexity;
        public String[] tokens_used;
        public String original_sentence;

        public Logging() {
        }

        public Logging(String sentence, String original_sentence, double perplexityScore, int[] tokens, String[] tokens_used) {
            this.sentence = sentence;
            this.original_sentence = original_sentence;
            this.perplexity = perplexityScore;
            this.tokens = tokens;
            this.tokens_used = tokens_used;
        }
    }
}
    

