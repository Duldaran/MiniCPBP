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
import java.util.TreeMap;
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
         ArrayNode arrayNode = (ArrayNode) objectMapper.readTree(new File("./src/main/java/minicpbp/examples/data/Sentence/commongen.json"));
         Iterator<JsonNode> elements = arrayNode.elements();
 
         List<String> lines = Collections.emptyList();
         try {
             lines = Files.readAllLines(Paths.get("./src/main/java/minicpbp/examples/data/Sentence/tokenizer_dict_phi.txt"),StandardCharsets.UTF_8);//Change with the llm
         }
         catch (Exception e) {
             e.printStackTrace();
         }

         int token_size = Integer.parseInt(lines.get(lines.size()-1).split(":")[0])+1;

         String[] corrected_lines = new String[token_size];
         Arrays.fill(corrected_lines, "");
         for(int i=0;i<lines.size();i++){
             String[] line = lines.get(i).split("::");
             if(line.length>1){
                 corrected_lines[Integer.parseInt(line[0])]=line[1];
             }
         }

        final List<String> words = Arrays.asList(corrected_lines);

        List<Integer> capitalized_words= new ArrayList<>();
        int sentence_end=-1;
        for(int i=0; i<words.size(); i++){
            if(words.get(i).equals(".")){
                sentence_end=i;
            }
            if(words.get(i).strip().length()!=0 && Character.isUpperCase(words.get(i).strip().charAt(0))){
                capitalized_words.add(i);
            }
        }


         List<Logging> logs = new ArrayList<>();
         int count=0;
         elements.next();

        final int MAX_COUNT = 40;
        final boolean PRINT_TRACE = false;

         while (elements.hasNext() && count<MAX_COUNT) {
             count++;
             JsonNode element = elements.next();
             String instruction = element.get("instruction").asText();
             instruction = instruction.replace("\n", " ");
             instruction = instruction.replace("\"", "");
 
             
 
 
             final int NUM_PB=3;
             final double w = 1;
             final int SENTENCE_MAX_NUMBER_TOKENS=30;
             final int END_TOKEN=3;
 
             
             HttpClient client = HttpClient.newHttpClient();
 
                 List<String> required_words_temp = new ArrayList<String>();
                 Iterator<JsonNode> required_words_JsonNode= element.get("concept_set").elements();
                 while (required_words_JsonNode.hasNext()) {
                     String word = required_words_JsonNode.next().asText();
                     required_words_temp.add(word.split("_")[0]);
                 }
                 String[] REQUIRED_WORDS = required_words_temp.toArray(new String[0]);
 
 
 
                 Solver cp = Factory.makeSolver(false);
                 IntVar[] q = Factory.makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, words.size());
                 
                 for(int i=0; i<q.length; i++){
                     q[i].setName("Q"+i);
                 }
 
                 cp.setTraceBPFlag(false);
                 
                 // Regular sentence constraint

                 List<Integer> acceptedState = new ArrayList<>();
                 int[][] A = new int[4][words.size()];
                 acceptedState.add(A.length-1);
                 Arrays.fill(A[0], -1);
                 for(int index:capitalized_words){
                    A[0][index]=1;
                }

                 Arrays.fill(A[1], 1);
                 A[1][END_TOKEN]=-1;
                 A[1][sentence_end]=2;
                 Arrays.fill(A[2], -1);
                 A[2][END_TOKEN]=3;
                 for(int index:capitalized_words){
                     A[2][index]=1;
                 }
                 Arrays.fill(A[3], -1);
                 A[3][END_TOKEN]=3;
                 cp.post(Factory.regular(q, A, 0, acceptedState));


                 for(int i = 0; i<REQUIRED_WORDS.length;i++){
                     HttpRequest request = HttpRequest.newBuilder()
                     .uri(URI.create("http://localhost:5000/tokenize"))
                     .POST(HttpRequest.BodyPublishers.ofString(" "+REQUIRED_WORDS[i]))
                     .build();
                     String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
                     int[] split_response = Arrays.stream(response.substring(1,response.length()-2).split(",")).mapToInt(Integer::parseInt).toArray();
                     int id = split_response[0];
                     int[] tokens= Arrays.copyOfRange(split_response, 1, split_response.length);
                     if(id==-1){
                        List<Integer> endState = new ArrayList<>();
                        endState.add(tokens.length);
                        int[][] States = new int[tokens.length+1][words.size()];
                        for(int j=0; j<tokens.length; j++){
                            Arrays.fill(States[j], 0);
                            States[j][tokens[j]]=j+1;
                            if(j==1){
                                States[j][tokens[j-1]]=j;
                            }
                        }
                        Arrays.fill(States[tokens.length], tokens.length);
                        cp.post(Factory.regular(q, States, 0, endState));       
                     }
                     else if(id==-2){
                        if(PRINT_TRACE)System.out.println("REQUIRED_WORDS[i] "+REQUIRED_WORDS[i]);
                        cp.post(Factory.atleast(q, tokens, 1));
                     }
                 }

                 
        if(PRINT_TRACE)System.out.println("Using "+NUM_PB+" iterations of BP");
        if(PRINT_TRACE)System.out.println(instruction);
         String current_sentence = "\\n" + //
                          "\\n" + //
                          "# Response:";//Remove when changing LLM
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
                             
                             if(token>=words.size()){
                                 continue;
                             }

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
 
 
                    if(PRINT_TRACE) System.out.println("token "+i);
 
                     Constraint c = Factory.oracle(q[i], tokens, scores);

                     c.setWeight(w);
                     if(PRINT_TRACE)  System.out.println("oracle's weight set to "+w);
                     cp.post(c);
                     if(PRINT_TRACE)  System.out.println("GPT, before BP (max token, 'the word', its probability) "+max_token+", '"+words.get(max_token)+"', "+max_score);
                     if(PRINT_TRACE) 
                     {
                        double[] temp = scores.clone();
                        Arrays.sort(temp);
                        for(int n=1; n<=5; n++){
                            for(int k=0; k<temp.length; k++){
                                if(temp[temp.length-n]==scores[k]){
                                    System.out.println("GPT, before BP (max token, 'the word', its probability) "+k+", '"+words.get(k)+"', "+scores[k]);
                                }
                            }
                        }
                    }

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
                         current_sentence += " ERROR";
                         break;
                     }
                     if(PRINT_TRACE) 
                     {
                        TreeMap<Double, Integer> bestTokens = new TreeMap<Double, Integer>();
                        for(int j=0; j<q[i].size(); j++){
                            bestTokens.put(q[i].marginal(j), j);
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

                    if(PRINT_TRACE)  System.out.println("CP model, before BP (max token, 'the word', its probability) "+q[i].valueWithMaxMarginal()+", '"+words.get(q[i].valueWithMaxMarginal())+"', "+q[i].maxMarginal());
                     cp.vanillaBP(NUM_PB);
                     if(PRINT_TRACE)  System.out.println("after BP (max token, 'the word', its probability) "+q[i].valueWithMaxMarginal()+", '"+words.get(q[i].valueWithMaxMarginal())+"', "+q[i].maxMarginal());
                     
                     if(PRINT_TRACE) 
                     {
                        TreeMap<Double, Integer> bestTokens = new TreeMap<Double, Integer>();
                        for(int j=0; j<q[i].size(); j++){
                            bestTokens.put(q[i].marginal(j), j);
                        }
                        for(int j=0; j<5; j++){
                            if(bestTokens.isEmpty()){
                                break;
                            }
                            double prob = bestTokens.lastKey();
                            int token = bestTokens.remove(prob);
                            System.out.println("after BP (max token, 'the word', its probability) "+token+", '"+words.get(token)+"', "+prob);
                        }
                    }

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
                     if(PRINT_TRACE) System.out.println("sentence so far: " + current_sentence);

                 }
                 double perplexityScore = Math.exp(-logSumProbs / num_tok);
                 System.out.println("solution : " + current_sentence);
                 //System.out.println("Perplexity is of " + perplexityScore);
                 logs.add(new Logging(current_sentence, perplexityScore, REQUIRED_WORDS));
             
             objectMapper.writeValue(Paths.get(String.format("model_results_%d_%d_%2.1f.json", MAX_COUNT,NUM_PB, w)).toFile(), logs);
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
 