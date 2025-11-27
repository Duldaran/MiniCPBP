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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.io.ObjectInputFilter.Config;
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
import minicpbp.examples.config.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ibm.icu.impl.Pair;

import fzn.test;

public class NLP_v3 {
    public static void main(String[] args) throws Exception {
        
            int port = Integer.parseInt(args[1]);
            final int NUM_ITERATIONS = Integer.parseInt(args[3]);
            final double weight = Double.parseDouble(args[0]);
            final String llm_name = args.length > 5 ? args[5] : "zephyr";
            final String configArg = args.length > 4 ? args[4] : "CollieSent1Config";


        try {

        ConstraintBuilder cb;
        switch (configArg) {
            case "CollieSent1Config":
                cb = new CollieSent1Config();
                break;
            case "CollieSent2Config":
                cb = new CollieSent2Config();
                break;  
            case "CollieSent3Config":
                cb = new CollieSent3Config();   
                break;
            case "CollieSent4Config":
                cb = new CollieSent4Config();   
                break;
            default:
                throw new IllegalArgumentException("Unknown config: " + configArg);
        }

        long startTime = System.currentTimeMillis();

        List<Logging>  logs = new ArrayList<>();

        List<String> lines = Collections.emptyList();
         try {
             lines = Files.readAllLines(Paths.get("./src/main/java/minicpbp/examples/data/MNREAD/"+llm_name+"/tokenizer_dict.txt"),StandardCharsets.UTF_8);
         }
         catch (Exception e) {
             e.printStackTrace();
         }

        int sentence_end_index = -1;
         int token_size = Integer.parseInt(lines.get(lines.size()-1).split(":")[0]);

         String[] corrected_lines = new String[token_size];
         Arrays.fill(corrected_lines, "");
         for(int i=0;i<lines.size();i++){
             String[] line = lines.get(i).split("::");
             if(line.length>1){
                corrected_lines[Integer.parseInt(line[0])]=line[1];
                if(line[1].equals(".") && sentence_end_index==-1){
                    sentence_end_index = i;
                }
             }
         }
 
        final List<String> tokens_list = Arrays.asList(corrected_lines);
        ArrayList<String> words = new ArrayList<>();
        Map<Integer, List<Integer>> corpusDomainsSet = new HashMap<>();
        Map<Integer, Integer> corpusDomainToIndex = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        int k = 0;
        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get("./src/main/java/minicpbp/examples/data/MNREAD/"+llm_name+"/corpus_tokenized_words.json")), StandardCharsets.UTF_8);
            final List<List<Integer>> parsedCorpusDomains = objectMapper.readValue(jsonContent, new TypeReference<List<List<Integer>>>() {}); 
            for (int i = 0; i < parsedCorpusDomains.size(); i++) {
                List<Integer> sublist = parsedCorpusDomains.get(i);
                String word_string = sublist.stream().map(n -> tokens_list.get(n)).collect(Collectors.joining(""));
                if (words.contains(word_string)) {
                    continue;
                }
                words.add(word_string);
                
                if (!corpusDomainsSet.containsKey(sublist.get(0)))
                    corpusDomainsSet.put(sublist.get(0), new ArrayList<>(List.of(k)));
                else
                    corpusDomainsSet.get(sublist.get(0)).add(k);

                corpusDomainToIndex.put(k, sublist.get(0));
                k++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }



        words.add(".");
        words.add("<PAD>");
        corpusDomainsSet.put(sentence_end_index, new ArrayList<>(Collections.singletonList(words.size()-2)));
        corpusDomainsSet.put(-1, new ArrayList<>(Collections.singletonList(words.size()-1)));
        corpusDomainToIndex.put(words.size()-2, sentence_end_index);
        corpusDomainToIndex.put(words.size()-1, -1);

        List<Integer> corpusDomains = new ArrayList<>();
        for (int idx = 0; idx < words.size(); idx++) {
            corpusDomains.add(idx);
        }


        System.out.println("corpusDomains size: " + corpusDomains.size());
        final int final_sentence_end = corpusDomains.get(corpusDomains.size()-2);
        final int pad_token = corpusDomains.get(corpusDomains.size()-1);



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

                String[] commonWords = {
        "I",
        "You",
        "He",
        "She",
        "It",
        "We",
        "They",
        "The",
        "A",
        "An",
        "This",
        "That",
        "These",
        "Those",
        "There"};

        for (String word : commonWords) {
            if (!words.contains(" "+word)) {
                System.out.println("Adding common word to vocabulary: " + word);
                words.add(" "+word);
                corpusDomains.add(words.size()-1);
            }
        }


        List<Integer> listCharNum = new ArrayList<>();
        List<Integer> listLengthTokens = new ArrayList<>();
        for (int i = 0; i < corpusDomains.size(); i++) {
            int domainIndex = corpusDomains.get(i);
            String word = words.get(domainIndex);
            int charSum = 0;
            if(i==pad_token){
                listCharNum.add(0);
                listLengthTokens.add(0);
                continue;
            }
            listCharNum.add(word.length());
            for (char c : word.toCharArray()) {
                if(word.length()==0 || word.equals(".")){
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
            listLengthTokens.add(charSum);
        }

 
        final int MAX_NUMBER_SPACE = 5;
        Pair<Integer, Integer> wordCountRange = cb.getWordCountRange();
        final int MIN_NUMBER_WORD = wordCountRange.first;
        final int MAX_NUMBER_WORD = wordCountRange.second;

        final boolean PRINT_TRACE = false;
        final int NUM_PB = 3;
        final double w = weight;
        final int SENTENCE_MAX_NUMBER_TOKENS = MAX_NUMBER_WORD +1;
        final int ORACLE_TOP_K = 500;
        //final int NUM_ITERATIONS = 8;





        int[] charNum = listCharNum.stream().mapToInt(Integer::intValue).toArray();
        int[] lengthTokens = listLengthTokens.stream().mapToInt(Integer::intValue).toArray();


       
        
        double initTime = (System.currentTimeMillis() - startTime) / 1000.0;
        System.out.println("Initialization time (s): " + initTime);


        HttpClient client = HttpClient.newHttpClient();

       

        for (int z = 0; z < NUM_ITERATIONS; z += 1) {//For loop
        
        Solver cp = makeSolver();
        IntVar[] word_index = makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, 0, corpusDomains.size()-1);
         String[] tokens_used = new String[SENTENCE_MAX_NUMBER_TOKENS];
        StateManager sm = cp.getStateManager();
        sm.saveState();
        String instruction = cb.getInstruction();

        IntVar[] line = makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, 0, 2);
        cb.build(new SolverContext(cp, corpusDomains.size(), final_sentence_end, pad_token, charNum, lengthTokens, word_index, words, line));
        


        String selectedWord = " "+commonWords[new Random().nextInt(commonWords.length)];
        word_index[0].assign(corpusDomains.indexOf(words.indexOf(selectedWord)));

        String current_sentence = selectedWord;
        int num_tok=1;
        while(num_tok<SENTENCE_MAX_NUMBER_TOKENS){
            
            final int i = num_tok;
            System.out.println(num_tok);
            System.out.println(current_sentence);
            HttpRequest request = HttpRequest.newBuilder()    
                .uri(URI.create("http://localhost:" + port + "/token"))
                .POST(HttpRequest.BodyPublishers.ofString(instruction + current_sentence))
                .build();
            String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();

            int[] tokens = new int[corpusDomains.size()];
            double[] scores = new double[corpusDomains.size()];


            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(response);
            ArrayNode tupleList = (ArrayNode) jsonNode.get("prob");



            int max_token= -1;
            double max_score = 0;
            double total_score = 0;



            List<Pair<Integer, Double>> tokenScoreList = new ArrayList<>();
            for (JsonNode tuple : tupleList) {
                try {
                    int token = ( tuple.get(0)).asInt();
                    double score = ( tuple.get(1)).asDouble();
                    if (!corpusDomainsSet.containsKey(token)) continue;
                    if (score < 0) continue;
                    tokenScoreList.add(Pair.of(token, score));
                } catch (Exception e) {
                    if (PRINT_TRACE) {
                        System.err.println(tuple);
                        System.err.println(e);
                    }
                }
            }

            tokenScoreList.sort((a, b) -> Double.compare(
                b.second, a.second
            ));


            if(tokenScoreList.get(0).first==sentence_end_index && i<word_index.length-1){
                System.out.println("sentence end reached");
                try {
                    sm.saveState();
                    
                    word_index[i].assign(final_sentence_end);
                    cp.fixPoint();
                    current_sentence += words.get(corpusDomains.get(final_sentence_end));

                    List<Integer> list_sub = new ArrayList<>(corpusDomainToIndex.get(final_sentence_end));
                    tokens_used[num_tok] = tokens_list.get(list_sub.get(0));
                    System.out.println("End reached successfully");
                    sm.restoreState();
                    break;
                } catch (Exception e) {
                    sm.restoreState();
                    System.out.println("Not able to end sentence yet, continuing");
                }
            }

            int limit = Math.min(ORACLE_TOP_K, tokenScoreList.size());

            for (int l = 0; l < limit; l++) {
                int token = tokenScoreList.get(l).first;
                double score = tokenScoreList.get(l).second;
                int[] token_indexs = corpusDomainsSet.get(token).stream().mapToInt(Integer::intValue).toArray();
                for (int token_index : token_indexs) {
                    tokens[token_index] = token_index;
                    scores[token_index] = score;
                    total_score += score;
                }
                if (PRINT_TRACE) {
                    if (score > max_score) {
                        System.out.println("New max score found: " + score);
                        System.out.println("Token Strong: " + tokens_list.get(token));
                        System.out.println("Token: " + token);
                        System.out.println("Corpus indexs: " + Arrays.toString(token_indexs));
                        for(int idx: token_indexs){
                            System.out.println("Corresponding word: " + words.get(idx));
                        }
                        max_score = score;
                        max_token= token_indexs[0];
                    }
                }
            }
            for (int j=0; j<tokens.length; j++) {
                double score=scores[j];
                if (score > 0) {
                    scores[j] /= total_score;
                }
                else if(score<0){
                    throw new RuntimeException("Score is negative or zero");
                }
            }



            if(PRINT_TRACE) System.out.println("token "+i);

            Constraint c = Factory.oracle(word_index[i], tokens, scores);

            c.setWeight(w);
            if(PRINT_TRACE)  System.out.println("oracle's weight set to "+w);
            cp.post(c);
            if(PRINT_TRACE)  System.out.println("GPT, before BP (max token, 'the word', its probability) "+max_token+", '"+words.get(max_token)+"', "+max_score);
            if(PRINT_TRACE) 
            {
                double[] temp = scores.clone();
                Arrays.sort(temp);
                for(int n=1; n<=5; n++){
                    for(int m=0; m<temp.length; m++){
                        if(temp[temp.length-n]==scores[m]){
                            System.out.println("GPT, before BP (max token, 'the word', its probability) "+m+", '"+words.get(m)+"', "+scores[m]);
                        }
                    }
                }
            }

            try {
                cp.fixPoint();
            }
            catch (InconsistencyException e) {
                if (PRINT_TRACE) {
                    System.out.println("INCONSISTENCY!");
                    for(int j=0; j<word_index.length; j++){
                        System.out.println(word_index[j].getName()+word_index[j].toString());
                    }
                }
                current_sentence += " ERROR";
                break;
            }
            if(PRINT_TRACE) 
            {
                TreeMap<Double, Integer> bestTokens = new TreeMap<Double, Integer>();
                for(int j=0; j<word_index[i].size(); j++){
                    bestTokens.put(word_index[i].marginal(j), j);
                }
                for(int j=0; j<5; j++){
                    if(bestTokens.isEmpty()){
                        break;
                    }
                    double prob = bestTokens.lastKey();
                    int token = bestTokens.remove(prob);
                    System.out.println("CP model, before BP (max token, 'the word', its probability) "+token+", '"+words.get(token)+"', "+prob);
                }
            }

            if(PRINT_TRACE)  System.out.println("CP model, before BP (max token, 'the word', its probability) "+word_index[i].valueWithMaxMarginal()+", '"+words.get(word_index[i].valueWithMaxMarginal())+"', "+word_index[i].maxMarginal());
            cp.vanillaBP(NUM_PB);
            if(PRINT_TRACE)  System.out.println("after BP (max token, 'the word', its probability) "+word_index[i].valueWithMaxMarginal()+", '"+words.get(word_index[i].valueWithMaxMarginal())+"', "+word_index[i].maxMarginal());
            
            if(PRINT_TRACE) 
            {
                TreeMap<Double, Integer> bestTokens = new TreeMap<Double, Integer>();
                for(int j=0; j<word_index[i].size(); j++){
                    bestTokens.put(word_index[i].marginal(j), j);
                }
                for(int j=0; j<5; j++){
                    if(bestTokens.isEmpty()){
                        break;
                    }
                    double prob = bestTokens.lastKey();
                    int token = bestTokens.remove(prob);
                    System.out.println("after BP (max token, 'the word', its probability) "+token+", '"+words.get(token)+"', "+prob);
                }
                System.out.println("after BP (max token, 'the word', its probability) "+word_index[i].valueWithMaxMarginal()+", '"+words.get(word_index[i].valueWithMaxMarginal())+"', "+word_index[i].marginal(word_index[i].valueWithMaxMarginal()));
            }
        
        
        
            if (word_index[i].maxMarginal() == 0.0) {
                        System.out.println("No valid tokens found");
                        current_sentence += " ERROR";
                        break;
            }

            
            int chosen = word_index[i].biasedWheelValue();
    
            word_index[i].assign(chosen);

            System.out.println("chosen: "+chosen+", '"+words.get(chosen)+"', "+word_index[i].marginal(chosen));

            num_tok+=1;
            current_sentence += words.get(chosen);
            tokens_used[i] += ", " + words.get(chosen);



            if (PRINT_TRACE) {
                System.out.println("sentence so far: " + current_sentence);
                System.out.println("index chosen: " + chosen);
            }



        }
        double perplexityScore = 0;

        if (!current_sentence.trim().endsWith("ERROR") && !current_sentence.trim().endsWith(".")) {
            current_sentence = current_sentence.trim() + ".";
        }
        System.out.println("solution : " + current_sentence);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/tokenize"))
            .POST(HttpRequest.BodyPublishers.ofString(current_sentence))
            .build();
        String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
        int[] split_response = Arrays.stream(response.substring(1,response.length()-2).split(",")).mapToInt(Integer::parseInt).toArray();
        int[] tokens= Arrays.copyOfRange(split_response, 1, split_response.length);

        
        logs.add(new Logging(current_sentence, perplexityScore, tokens, tokens_used.clone()));

        }
    
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", "ok");
    result.put("port", port);
    result.put("num_iterations", NUM_ITERATIONS);
    result.put("num_pb", NUM_PB);
    result.put("weight", w);
    result.put("llm_name", llm_name);
    result.put("logs", logs);
    result.put("date", java.time.LocalDateTime.now().toString());
    String OUTPUT_DIR = args.length > 3 ? args[2] : "./outputs";
    Files.createDirectories(Paths.get(OUTPUT_DIR));
    String outputFileName = OUTPUT_DIR + "/result_"+configArg+ "_NLP_v3_" + System.currentTimeMillis()  + ".json";
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(outputFileName).toFile(), result);
    }
    catch (Exception e) {
            e.printStackTrace();
            // Write error to output file
            String OUTPUT_DIR = args.length > 3 ? args[2] : "./outputs";
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            String outputFileName = OUTPUT_DIR + "/result_" + configArg + "_NLP_v3_" + System.currentTimeMillis()  + "_error.json";
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("status", "error");
            errorResult.put("error_message", e.getMessage());
            errorResult.put("exception", e.toString());
            errorResult.put("date", java.time.LocalDateTime.now().toString());
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

        public Logging() {
        }

        public Logging(String sentence, double perplexityScore, int[] tokens, String[] tokens_used) {
            this.sentence = sentence;
            this.perplexity = perplexityScore;
            this.tokens = tokens;
            this.tokens_used = tokens_used;
        }
    }
}
    

