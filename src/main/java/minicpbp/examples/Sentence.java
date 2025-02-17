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
 */

package minicpbp.examples;

import minicpbp.cp.Factory;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.SearchStatistics;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse.BodyHandlers;

import static minicpbp.cp.BranchingScheme.*;

import java.io.FileWriter;
import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.File;
import java.io.IOException;

public class Sentence{
    public static void main(String[] args) throws IOException {
        //int[] sizes = {100,500,1000,1500,2000,2500,3000,4000,5000};
        //int[] sentence_length= {15};
        ObjectMapper objectMapper = new ObjectMapper();
        //ArrayNode arrayNode = (ArrayNode) objectMapper.readTree(new File("./src/main/java/minicpbp/examples/data/Sentence/unmatched_instructions.json"));
        ArrayNode arrayNode = (ArrayNode) objectMapper.readTree(new File("./src/main/java/minicpbp/examples/data/Sentence/commongen_hard_nohuman.json"));
        Iterator<JsonNode> elements = arrayNode.elements();

        List<Logging> logs = new ArrayList<>();
        int count=0;
        while (elements.hasNext() && count<40) {
            count++;
            JsonNode element = elements.next();
            String instruction = element.get("instruction").asText();
            instruction = instruction.replace("\n", " ");
            instruction = instruction.replace("\"", "");

            List<String> lines = Collections.emptyList();
            try {
                lines = Files.readAllLines(Paths.get("./src/main/java/minicpbp/examples/data/Sentence/tokenizer_dict.txt"),StandardCharsets.UTF_8);
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            int token_size = Integer.parseInt(lines.get(lines.size()-1).split(":")[0]);

            String[] corrected_lines = new String[token_size];
            Arrays.fill(corrected_lines, "");
            for(int i=0;i<lines.size();i++){
                String[] line = lines.get(i).split(":");
                if(line.length>1){
                    corrected_lines[Integer.parseInt(line[0])]=line[1];
                }
            }


            final int NUM_PB=3;
            final int SENTENCE_MAX_NUMBER_TOKENS=30;
            final int SENTENCE_MIN_LENGTH=20;
            final int SENTENCE_MAX_LENGTH=200;
            final int MAX_WORDS_LENGTH=20;
            //final int DOMAINS_SIZE=lines.size();

            //lines = lines.subList(0, DOMAINS_SIZE);
            //lines.add(0,"<|endoftext|>");
            
            HttpClient client = HttpClient.newHttpClient();

                List<String> required_words_temp = new ArrayList<String>();
                Iterator<JsonNode> required_words_JsonNode= element.get("concept_set").elements();
                while (required_words_JsonNode.hasNext()) {
                    String word = required_words_JsonNode.next().asText();
                    required_words_temp.add(word.substring(0, word.indexOf('_')));
                }
                String[] REQUIRED_WORDS = required_words_temp.toArray(new String[0]);


                List<Integer> REQUIRED_INDEX = new ArrayList<Integer>();
                for(int i = 0; i<REQUIRED_WORDS.length;i++){
                    HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:5000/tokenize"))
                    .POST(HttpRequest.BodyPublishers.ofString(REQUIRED_WORDS[i]))
                    .build();
                    String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
                    for(String index : response.substring(1,response.length()-2).split(",")){
                        REQUIRED_INDEX.add(Integer.parseInt(index));
                    }
                }
                final int[] REQUIRED_INDEXES = REQUIRED_INDEX.stream().mapToInt(Integer::intValue).toArray();
                
                final List<String> words = Arrays.asList(corrected_lines);


                /*int[] word_length = new int[words.size()];
                int max_length=0;
                for(int i=0;i<words.size();i++){
                    if(words.get(i).length()==0 || words.get(i).charAt(0)=='<'){word_length[i]=0;}
                    else {word_length[i]=words.get(i).strip().length();}
                    if(word_length[i]>max_length){max_length=word_length[i];}
                }*/

                Solver cp = Factory.makeSolver(false);
                IntVar[] q = Factory.makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, words.size());
                //IntVar[] l = Factory.makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, words.size());
                //IntVar total_length = Factory.makeIntVar(cp, SENTENCE_MAX_NUMBER_TOKENS*max_length);
                
                for(int i=0; i<q.length; i++){
                    q[i].setName("Q"+i);
                    //l[i].setName("L"+i);
                }

                //cp.post(Factory.sum(l, total_length));

                cp.setTraceBPFlag(false);

                List<Integer> acceptedState = new ArrayList<>();
                acceptedState.add(1);
                int[][] A = new int[2][words.size()];
                Arrays.fill(A[0], 0);
                A[0][words.size()-1]=1;
                Arrays.fill(A[1], -1);
                A[1][words.size()-1]=1;
                cp.post(Factory.regular(q, A, 0, acceptedState));

                //Check for required word and end of sentence
                for(int index : REQUIRED_INDEXES){
                    cp.post(Factory.atleast(q, index, 1));
                }
                cp.post(Factory.atleast(q, REQUIRED_INDEXES, REQUIRED_INDEXES.length));
                cp.post(Factory.atleast(q, words.size()-1, 1));
                //cp.post(Factory.atleast(q, 220, 6));
                
                /*//Links words and their length
                for(int i=0;i<SENTENCE_MAX_NUMBER_TOKENS;i++){
                    cp.post(Factory.element(word_length, q[i], l[i]));
                }

                //Check the max length for each words
                for(IntVar length : l){
                    cp.post(Factory.isLessOrEqual(length, MAX_WORDS_LENGTH));
                }

                //Check for min or max sentence length
                cp.post(Factory.isLessOrEqual(total_length,SENTENCE_MAX_LENGTH));
                cp.post(Factory.isLargerOrEqual(total_length,SENTENCE_MIN_LENGTH));
                */
                
                String current_sentence = "";
                Double logSumProbs = 0.0;
                int num_tok=0;
                for (int i = 0; i < SENTENCE_MAX_NUMBER_TOKENS; i++) {
                    // Makes the request
                    HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:5000/token"))
                    .POST(HttpRequest.BodyPublishers.ofString(instruction+current_sentence))
                    .build();
                    String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();

                    // Parse the response into data structures
                    int[] tokens = new int[words.size()];
                    double[] scores = new double[words.size()];

                    for (String tuple : response.split("\\],\\[")) {
                        try {
                            String[] token_score = tuple.split(",");
                            token_score[1]=token_score[1].replaceAll("\\]","");
                            token_score[0]=token_score[0].replaceAll("\\[","");
                            int token = Integer.parseInt(token_score[0]);
                            double score = Double.parseDouble(token_score[1]);
                            
                            tokens[token] = token;
                            scores[token] = score;

                        } catch (Exception e) {
                            System.err.println(tuple);
                            System.err.println(e);
                        }
                    }

                    

                    // post oracle
                    cp.post(Factory.oracle(q[i], tokens, scores));
                    cp.fixPoint();
                    cp.vanillaBP(NUM_PB);


                    q[i].assign(q[i].valueWithMaxMarginal());//TODO : Trouver un meilleur sampling
                    int chosen = q[i].valueWithMaxMarginal();
                    num_tok++;
                    if (0<chosen && chosen<scores.length) {
                        logSumProbs += Math.log(scores[chosen]);
                    } else {
                        System.out.println("Chose a value not in the nlp model");
                        logSumProbs = -Double.MAX_VALUE;
                    }
                    current_sentence += words.get(chosen);
                    //System.out.println(current_sentence);
                    if (chosen == 13) {
                        for (int j = i + 1; j < SENTENCE_MAX_NUMBER_TOKENS; j++) {
                            q[j].assign(words.size() - 1);
                            cp.fixPoint();
                        }
                        break;
                    }
                }
                double perplexityScore = Math.exp(-logSumProbs / num_tok);
                //System.out.println("solution : " + current_sentence);
                //System.out.println("Perplexity is of " + perplexityScore);
                logs.add(new Logging(current_sentence, perplexityScore, REQUIRED_WORDS));
            
            objectMapper.writeValue(Paths.get(String.format("model_results_%d.json", NUM_PB)).toFile(), logs);
        }

        
    }
    private static CompletableFuture<String> testRequest() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:5000/token"))
            .POST(HttpRequest.BodyPublishers.ofString("Hello World"))
            .build();
        return client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body);
    }

    public static class Logging {

        public String sentence;
        public double perplexity;
        public String[] required_words;
    
        public Logging() {
        }
    
        public Logging(String sentence, double perplexityScore, String[] required_words) {
            this.sentence = sentence;
            this.perplexity = perplexityScore;
            this.required_words = required_words;
        }
    }
}
