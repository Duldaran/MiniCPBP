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
 import minicpbp.engine.core.Constraint;
 import minicpbp.search.DFSearch;
 import minicpbp.search.SearchStatistics;
 import minicpbp.util.exception.InconsistencyException;
 
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
 
 public class Sentence_cleaned{
     public static void main(String[] args) throws IOException {
         ObjectMapper objectMapper = new ObjectMapper();
         ArrayNode arrayNode = (ArrayNode) objectMapper.readTree(new File("./src/main/java/minicpbp/examples/data/Sentence/commongen_hard_nohuman.json"));
         Iterator<JsonNode> elements = arrayNode.elements();
 
         List<Logging> logs = new ArrayList<>();
         int count=0;
         //elements.next();
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
                 String[] line = lines.get(i).split("::");
                 if(line.length>1){
                     corrected_lines[Integer.parseInt(line[0])]=line[1];
                 }
             }

             final List<String> words = Arrays.asList(corrected_lines);
 
 
             final int NUM_PB=3;
             final double w = 1;
             final int SENTENCE_MAX_NUMBER_TOKENS=30;
 
             
             HttpClient client = HttpClient.newHttpClient();
 
                 List<String> required_words_temp = new ArrayList<String>();
                 Iterator<JsonNode> required_words_JsonNode= element.get("concept_set").elements();
                 while (required_words_JsonNode.hasNext()) {
                     String word = required_words_JsonNode.next().asText();
                     required_words_temp.add(word.substring(0, word.indexOf('_')));
                 }
                 String[] REQUIRED_WORDS = required_words_temp.toArray(new String[0]);
 
 
 
                 Solver cp = Factory.makeSolver(false);
                 IntVar[] q = Factory.makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, words.size());
                 
                 for(int i=0; i<q.length; i++){
                     q[i].setName("Q"+i);
                 }
 
                 cp.setTraceBPFlag(false);
 
                 List<Integer> acceptedState = new ArrayList<>();
                 acceptedState.add(1);
                 int[][] A = new int[2][words.size()];
                 Arrays.fill(A[0], 0);
                 A[0][words.size()-1]=1;
                 Arrays.fill(A[1], -1);
                 A[1][words.size()-1]=1;
                 cp.post(Factory.regular(q, A, 0, acceptedState));

                 for(int i = 0; i<REQUIRED_WORDS.length;i++){
                     HttpRequest request = HttpRequest.newBuilder()
                     .uri(URI.create("http://localhost:5000/tokenize"))
                     .POST(HttpRequest.BodyPublishers.ofString(" "+REQUIRED_WORDS[i]))
                     .build();
                     String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
                     String[] tokens = response.substring(1,response.length()-2).split(",");
                     if(tokens.length>1){
                        List<Integer> endState = new ArrayList<>();
                        endState.add(tokens.length);
                        int[][] States = new int[tokens.length+1][words.size()];
                        for(int j=0; j<tokens.length; j++){
                            Arrays.fill(States[j], 0);
                            States[j][Integer.parseInt(tokens[j])]=j+1;
                            if(j==1){
                                States[j][Integer.parseInt(tokens[j-1])]=j;
                            }
                        }
                        Arrays.fill(States[tokens.length], tokens.length);
                        cp.post(Factory.regular(q, States, 0, endState));       
                     }
                     else{
                        List<Integer> indexs = new ArrayList<>();
                        System.out.println("REQUIRED_WORDS[i] "+REQUIRED_WORDS[i]);
                        for(String word : words){
                            if(word.toUpperCase().strip().startsWith(REQUIRED_WORDS[i].toUpperCase().strip())){
                                indexs.add(words.indexOf(word));
                                System.out.println("word "+word);
                            }
                        }
                        int[] REQUIRED_INDEX = indexs.stream().mapToInt(Integer::intValue).toArray();
                        cp.post(Factory.atleast(q, REQUIRED_INDEX, 1));
                     }
                 }

                 
         System.out.println("Using "+NUM_PB+" iterations of BP");
        System.out.println(instruction);
         String current_sentence = "# Your Results   - Sentence:";
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
 
                     int max_token = -1;
                     double max_score = 0;
 
                     for (String tuple : response.split("\\],\\[")) {
                         try {
                             String[] token_score = tuple.split(",");
                             token_score[1]=token_score[1].replaceAll("\\]","");
                             token_score[0]=token_score[0].replaceAll("\\[","");
                             int token = Integer.parseInt(token_score[0]);
                             double score = Double.parseDouble(token_score[1]);
                             
                             tokens[token] = token;
                             scores[token] = score;
 
                             if (score > max_score) {
                                 max_score = score;
                                 max_token = token;
                             }
 
                         } catch (Exception e) {
                             System.err.println(tuple);
                             System.err.println(e);
                         }
                     }
 
 
                     System.out.println("token "+i);
 
                     Constraint c = Factory.oracle(q[i], tokens, scores);

                     c.setWeight(w);
                     System.out.println("oracle's weight set to "+w);
                     cp.post(c);
                     System.out.println("GPT, before BP (max token, 'the word', its probability) "+max_token+", '"+words.get(max_token)+"', "+max_score);
                     try {
                         cp.fixPoint();
                     }
                     catch (InconsistencyException e) {
                         System.out.println("INCONSISTENCY!");
                         for(int j=0; j<q.length; j++){
                             System.out.println(q[j].getName()+q[j].toString());
                         }
                         for(String word: REQUIRED_WORDS){
                             System.out.println(word);
                         }
                     }
                     System.out.println("CP model, before BP (max token, 'the word', its probability) "+q[i].valueWithMaxMarginal()+", '"+words.get(q[i].valueWithMaxMarginal())+"', "+q[i].maxMarginal());
                     cp.vanillaBP(NUM_PB);
                     System.out.println("after BP (max token, 'the word', its probability) "+q[i].valueWithMaxMarginal()+", '"+words.get(q[i].valueWithMaxMarginal())+"', "+q[i].maxMarginal());
                     

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
                     System.out.println("sentence so far: " + current_sentence);

                 }
                 double perplexityScore = Math.exp(-logSumProbs / num_tok);
                 System.out.println("solution : " + current_sentence);
                 //System.out.println("Perplexity is of " + perplexityScore);
                 logs.add(new Logging(current_sentence, perplexityScore, REQUIRED_WORDS));
             
             objectMapper.writeValue(Paths.get(String.format("model_results_%d_%2.1f.json", NUM_PB, w)).toFile(), logs);
         }
 
         
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
 