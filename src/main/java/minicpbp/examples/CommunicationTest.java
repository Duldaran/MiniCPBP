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
 
 
 public class CommunicationTest{
    public static void main(String[] args) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:5000/token"))
        .POST(HttpRequest.BodyPublishers.ofString("Hello World"))
        .build();
        String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
        
        HashMap<String, Double> tokenToScoreNLP = new HashMap<>();
        for (String tuple : response.split("\\],\\[")) {
            try {
                tuple=tuple.replaceAll("]","");
                String token = tuple.substring(tuple.indexOf('"')+1,tuple.lastIndexOf(',')-1);
                double score = Double.parseDouble(tuple.substring(tuple.lastIndexOf(',')+1));
                
                if (tokenToScoreNLP.containsKey(token)) {
                    score += tokenToScoreNLP.get(token);
                }
                tokenToScoreNLP.put(token, score);

            } catch (Exception e) {
                System.err.println(tuple);
                System.err.println(e);
            }
        }
        System.out.println(tokenToScoreNLP.size());
    }
 }
 