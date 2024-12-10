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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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


public class Sentence{
    public static void main(String[] args) {
        //int[] sizes = {100,500,1000,1500,2000,2500,3000,4000,5000};
        int[] sentence_length= {10,15,20,25};
        for (int s:sentence_length){
            List<String> lines = Collections.emptyList();
            try {
                lines = Files.readAllLines(Paths.get("./src/main/java/minicpbp/examples/data/Sentence/google-10000-english-no-swears.txt"),StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            int SENTENCE_MAX_NUMBER_WORDS=s;
            final int SENTENCE_MIN_LENGTH=40;
            final int SENTENCE_MAX_LENGTH=60;
            final int MAX_WORDS_LENGTH=5;
            final int DOMAINS_SIZE=2000;
            final String[] Punctuation = {"'",".","-","(",")","!","?",",", ":"};

            lines = lines.subList(0, 2000);
            lines.add(0,"<|endoftext|>");
            lines.add(1,"");

            final String[] REQUIRED_WORDS = {"soft", "beach", "math"};
            int[] REQUIRED_INDEX = new int[REQUIRED_WORDS.length];
            for(int i = 0; i<REQUIRED_WORDS.length;i++){
                if (lines.contains(REQUIRED_WORDS[i])==false) lines.add(REQUIRED_WORDS[i]);
                REQUIRED_INDEX[i]=(lines.indexOf(REQUIRED_WORDS[i]));
            }
            for(String punc : Punctuation){
                lines.add(punc);
            }
            final List<String> words = lines;

            int[] word_length = new int[lines.size()];
            for(int i=0;i<words.size();i++){
                if(lines.get(i).charAt(0)=='<'){word_length[i]=0;}
                else {word_length[i]=lines.get(i).length();}
            }

            Solver cp = Factory.makeSolver(false);
            IntVar[] q = Factory.makeIntVarArray(cp, SENTENCE_MAX_NUMBER_WORDS, words.size());
            IntVar[] l = Factory.makeIntVarArray(cp, SENTENCE_MAX_NUMBER_WORDS, words.size());
            IntVar total_length = Factory.sum(l);

            cp.post(Factory.allDifferent(q));
            for(int index : REQUIRED_INDEX){
                cp.post(Factory.atleast(q, index, 1));
            }
            cp.post(Factory.exactly(q, 0, 1));
            
            for(int i=0;i<s;i++){
                cp.post(Factory.element(word_length, q[i], l[i]));
            }

            for(IntVar length : l){
                cp.post(Factory.isLessOrEqual(length, MAX_WORDS_LENGTH));
            }

            //cp.post(Factory.isLessOrEqual(total_length,SENTENCE_MAX_LENGTH));
            cp.post(Factory.isLargerOrEqual(total_length,SENTENCE_MIN_LENGTH));

            try{
                HttpClient client = HttpClient.newHttpClient();

                String current_sentence = "";
                Double logSumProbs = 0.0;
                for (int i = 0; i < SENTENCE_MAX_NUMBER_WORDS; i++) {
                    // Makes the request
                    HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:5000/token"))
                    .POST(HttpRequest.BodyPublishers.ofString(current_sentence))
                    .build();
                    String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
                    System.out.println(response);

                    // Parse the response into data structures
                    HashMap<Integer, Double> tokenToScoreNLP = new HashMap<>();
                    double minScore = 2;
                    for (String line : response.split("],[")) {
                        String[] lineValues = line.split(":");
                        String tokenString = lineValues[0].split("\"")[1];
                        
                        // System.out.println(tokenString);
                        int token = Arrays.asList(words).indexOf(tokenString); ;  //g.tokenEncoder.get(tokenString);
                        double score = Double.parseDouble(lineValues[1]);

                        if (score < minScore) {
                            minScore = score;
                        }

                        tokenToScoreNLP.put(token, score);
                    }

                    HashMap<Integer, Double> tokenToScoreMap = new HashMap<>();
                    for (int t = 0; t < words.size(); t++) {
                        if (tokenToScoreNLP.containsKey(t)) {
                            tokenToScoreMap.put(t, tokenToScoreNLP.get(t));
                        } else {
                            tokenToScoreMap.put(t,minScore/2.0);
                        }
                    }


                    double scoreSum = 0;
                    for (double v : tokenToScoreMap.values()) {
                        scoreSum += v;
                    }

                    int[] tokens = new int[words.size()];
                    double[] scores = new double[words.size()];
                    for (int t = 0; t < words.size(); t++) {
                        tokens[t] = t;
                        scores[t] = tokenToScoreMap.get(t) / scoreSum;
                    }

                    // post oracle
                    cp.post(Factory.oracle(q[i], tokens, scores));
                    cp.fixPoint();
                    cp.beliefPropa();


                    q[i].assign(q[i].biasedWheelValue());//TODO : Trouver un meilleur sampling
                    int chosen = q[i].min();
                    if (tokenToScoreNLP.containsKey(chosen)) {
                        logSumProbs += Math.log(tokenToScoreNLP.get(chosen));
                    } else {
                        System.out.println("Chose a value not in the nlp model");
                        logSumProbs = -Double.MAX_VALUE;
                    }
                    current_sentence += chosen; //g.tokenDecoder.get(chosen);
                    System.out.println(current_sentence);
                }
                double perplexityScore = Math.exp(-logSumProbs / MAX_WORDS_LENGTH);
                System.out.println("solution :" + current_sentence);
                System.out.println("Perplexity is of " + perplexityScore);
            }
            catch(Exception e){
                System.out.println(e);
            }
            /** 
            DFSearch search = Factory.makeDfs(cp, maxMarginal(q));

            search.onSolution(() ->{
                    String[] sentence = new String[q.length];

                    for(int i = 0; i < q.length; i++) {
                        sentence[i] = words.get(q[i].max());
                    }
                    System.out.println("solution:" + Arrays.toString(sentence));
                    }
            );
            SearchStatistics stats = search.solve(statistics -> statistics.numberOfSolutions() == 1);

            System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
            System.out.format("Statistics: %s\n", stats);
            System.out.format("Size: %s\n", s);
            System.out.format("Time: %s\n", stats.timeElapsed());
            System.out.println("-----");
            **/
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
}
