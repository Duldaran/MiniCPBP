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
import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.LDSearch;
import minicpbp.search.Objective;
import minicpbp.util.exception.InconsistencyException;
import minicpbp.util.io.InputReader;
import minicpbp.search.SearchStatistics;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
import java.util.Random;
import java.util.TreeMap;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class MNREAD {
    public static void main(String[] args) throws Exception {

        List<Logging> logs = new ArrayList<>();

        List<String> lines = Collections.emptyList();
         try {
             lines = Files.readAllLines(Paths.get("./src/main/java/minicpbp/examples/data/MNREAD/tokenizer_dict_llama.txt"),StandardCharsets.UTF_8);
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
        
        List<Integer> corpusDomains = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get("./src/main/java/minicpbp/examples/data/MNREAD/corpus_domain.json")), StandardCharsets.UTF_8);
            corpusDomains = objectMapper.readValue(jsonContent, new TypeReference<List<Integer>>() {}); 
            corpusDomains.remove(Integer.valueOf(220));
            corpusDomains.remove(Integer.valueOf(6));
            corpusDomains.add(13);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Map<Integer, Integer> indexToCorpusDomain = new HashMap<>();
        for (int i = 0; i < corpusDomains.size(); i++) {
            indexToCorpusDomain.put(corpusDomains.get(i), i);
        }

        List<Integer> capitalized_words= new ArrayList<>();
        for(int i=0; i<corpusDomains.size(); i++){
            if(words.get(corpusDomains.get(i)).strip().length()!=0 && Character.isUpperCase(words.get(corpusDomains.get(i)).strip().charAt(0))){
                capitalized_words.add(i);
            }
        }

        int[] start_words  = new int[corpusDomains.size()];
        for(int i=0; i<corpusDomains.size(); i++){
            if(words.get(corpusDomains.get(i)).length()!=0 && words.get(corpusDomains.get(i)).charAt(0)==' '){
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
            if(i==corpusDomains.size()-1){
                charNum[i]=0;
                lengthTokens[i]=0;
                continue;
            }
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

        final int LINE_SIZE = 15896;
        final int SPACE_SIZE =512;
        final int MIN_SPACE_SIZE =410;
        final int MAX_SPACE_SIZE =640;
        final int MAX_NUMBER_SPACE = 5;
        final int MIN_NUMBER_WORD = 9;
        final int MAX_NUMBER_WORD = 15;
        final int NUMBER_CHAR = 59;
        final boolean PRINT_TRACE = false;
        final int NUM_PB = 3;
        final double w =1.0;
        final int NUM_ITERATIONS = 10;

    for(int iter=0;iter<NUM_ITERATIONS;iter++){

        Solver cp = makeSolver();
        IntVar[] sizes = makeIntVarArray(cp, 30, Arrays.stream(lengthTokens).min().getAsInt(), Arrays.stream(lengthTokens).max().getAsInt());
        IntVar[] word_index = makeIntVarArray(cp, sizes.length, 0, corpusDomains.size()-1);
        IntVar[] has_space = makeIntVarArray(cp, sizes.length, 0, 1);
        IntVar[] num_char = makeIntVarArray(cp, sizes.length, Arrays.stream(charNum).min().getAsInt(), Arrays.stream(charNum).max().getAsInt());
        for (int i=0; i<sizes.length; i++){
            sizes[i].setName("size["+i+"]");
            word_index[i].setName("word_index["+i+"]");
            cp.post(element(lengthTokens, word_index[i], sizes[i]));
            cp.post(element(start_words, word_index[i], has_space[i]));
            cp.post(element(charNum, word_index[i], num_char[i]));
        }

        IntVar nb_words = makeIntVar(cp,MIN_NUMBER_WORD+1,MAX_NUMBER_WORD+1);
        
        cp.post(sum(has_space, nb_words));
        cp.post(sum(num_char, NUMBER_CHAR));



        int nbLines = 3;
        IntVar[] line = makeIntVarArray(cp, sizes.length, nbLines);
        for (int i=0; i<line.length; i++)
            line[i].setName("line["+i+"]");




        IntVar[] lineSize = makeIntVarArray(cp, nbLines, LINE_SIZE-MAX_NUMBER_SPACE*(MAX_SPACE_SIZE-SPACE_SIZE), LINE_SIZE+MAX_NUMBER_SPACE*(SPACE_SIZE-MIN_SPACE_SIZE));
        for (int i=0; i<lineSize.length; i++)
            lineSize[i].setName("lineSize["+i+"]");
        line[0].assign(0);
        line[line.length-1].assign(nbLines-1);
        for (int i=0; i<line.length-1; i++) {
            cp.post(lessOrEqual(line[i], line[i + 1]));
            cp.post(lessOrEqual(line[i + 1],plus(line[i],1)));
        }
        cp.post(binPacking(line,sizes,lineSize));

        List<Integer> acceptedState = new ArrayList<>();
        int[][] A = new int[3][corpusDomains.size()];
        acceptedState.add(1);
        acceptedState.add(2);
        Arrays.fill(A[0], -1);
        for(int index:capitalized_words){A[0][index]=1;}
        Arrays.fill(A[1], 1);
        A[1][corpusDomains.size()-1]=2;
        Arrays.fill(A[2], -1);
        A[2][corpusDomains.size()-1]=2;
        cp.post(Factory.regular(word_index, A, 0, acceptedState));

         /* 
        int space_index = indexToCorpusDomain.get(220);
        List<Integer> acceptedState2 = new ArrayList<>();
        int[][] B = new int[2][corpusDomains.size()];
        acceptedState2.add(0);
        Arrays.fill(B[0], 0);
        B[0][space_index]=1;
        Arrays.fill(B[1], 0);
        B[1][space_index]=-1;
        cp.post(Factory.regular(word_index, B, 0, acceptedState2));*/
        
                
        HttpClient client = HttpClient.newHttpClient();
        
        if(PRINT_TRACE)System.out.println("Using "+NUM_PB+" iterations of BP");
        String current_sentence = "";
        Double logSumProbs = 0.0;
        int num_tok=0;
        for (int i = 0; i < sizes.length; i++) {
            // Makes the request
            HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:5000/token"))
            .POST(HttpRequest.BodyPublishers.ofString(current_sentence))
            .build();
            String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();

            // Parse the response into data structures
            int[] tokens = new int[corpusDomains.size()];
            double[] scores = new double[corpusDomains.size()];

            int max_token = -1;
            double max_score = 0;
            double total_score = 0;

            for (String tuple : response.split("\\],\\[")) {
                try {
                    String[] token_score = tuple.split(",");
                    token_score[1]=token_score[1].replaceAll("\\]","");
                    token_score[0]=token_score[0].replaceAll("\\[","");
                    int token = Integer.parseInt(token_score[0]);
                    double score = Double.parseDouble(token_score[1]);
                    
                    if (!indexToCorpusDomain.containsKey(token)) {
                        continue;
                    }
                    int token_index=indexToCorpusDomain.get(token);
                    tokens[token_index] = token_index;
                    scores[token_index] = score;
                    total_score += score;

                    if (score > max_score) {
                        max_score = score;
                        max_token = token_index;
                    }

                } catch (Exception e) {
                    System.err.println(tuple);
                    System.err.println(e);
                }
            }
            for (double score : scores) {
                if (score > 0) {
                    score /= total_score;
                }
                else {
                    throw new Exception("Score is negative or zero");
                }
            }
            max_score /= total_score;


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
                for(int j=0; j<word_index.length; j++){
                    System.out.println(word_index[j].getName()+word_index[j].toString());
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
           }

           word_index[i].assign(word_index[i].biasedWheelValue());//TODO : Trouver un meilleur sampling
            int chosen = word_index[i].min();
            num_tok++;
            if (0<=chosen && chosen<scores.length) {
                logSumProbs += Math.log(scores[chosen]);
            } else {
                System.out.println("index chosen: " + chosen);
                System.out.println(scores.length);
                System.out.println("Chose a value not in the nlp model");
                logSumProbs = -Double.MAX_VALUE;
            }
            current_sentence += words.get(corpusDomains.get(chosen));
            System.out.println("sentence so far: " + current_sentence);
            System.out.println("index chosen: " + corpusDomains.get(chosen));

        }
        double perplexityScore = Math.exp(-logSumProbs / num_tok);
        System.out.println("solution : " + current_sentence);
  
        logs.add(new Logging(current_sentence, perplexityScore));
    }
    objectMapper.writeValue(Paths.get(String.format("results_MNREAD_%d_%d_%2.1f.json",NUM_ITERATIONS,NUM_PB, w)).toFile(), logs);
    }


    public static class Logging {

        public String sentence;
        public double perplexity;

        public Logging() {
        }

        public Logging(String sentence, double perplexityScore) {
            this.sentence = sentence;
            this.perplexity = perplexityScore;
        }
    }
}
    

