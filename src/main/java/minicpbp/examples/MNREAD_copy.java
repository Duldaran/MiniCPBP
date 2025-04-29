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
import java.util.HashSet;
import java.util.LinkedHashMap;
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

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class MNREAD_copy {
    public static void main(String[] args) throws Exception {


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
            corpusDomains.remove(Integer.valueOf(6));
            corpusDomains.add(220);
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

        List<List<Integer>> corpusWords = new ArrayList<>();
        ObjectMapper objectMapperWords = new ObjectMapper();
        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get("./src/main/java/minicpbp/examples/data/MNREAD/corpus_tokenized_words.json")), StandardCharsets.UTF_8);
            corpusWords = objectMapperWords.readValue(jsonContent, new TypeReference<List<List<Integer>>>() {}); 
            corpusWords = corpusWords.subList(0, 10);//Testing
            corpusWords.add(List.of(220));
        } catch (Exception e) {
            e.printStackTrace();
        }


        Map<Integer, Map<Integer, Integer>> transitions = new HashMap<>();
        int stateCounter = 1;

        for (List<Integer> seq : corpusWords) {
            int currentState = 0;
            if (seq.contains(6)) {
                continue;
            }

            for (int i = 0; i < seq.size(); i++) {
                int input = indexToCorpusDomain.get(seq.get(i));

                Map<Integer, Integer> currentTrans = transitions.computeIfAbsent(currentState, k -> new HashMap<>());
                Integer nextState = currentTrans.get(input);
                if (i == seq.size() - 1) {
                    nextState = 0;
                    currentTrans.put(input, nextState);
                }
                if (nextState == null) {
                    nextState = stateCounter++;
                    currentTrans.put(input, nextState);
                }

                currentState = nextState;
            }
        }

        int numStates = stateCounter;
        int[][] table = new int[numStates][corpusDomains.size()];
        for (int[] row : table) Arrays.fill(row, -1);

        for (Entry<Integer, Map<Integer, Integer>> fromEntry : transitions.entrySet()) {
            int from = fromEntry.getKey();
            for (Entry<Integer, Integer> inputEntry : fromEntry.getValue().entrySet()) {
                int input = inputEntry.getKey();
                int to = inputEntry.getValue();
                table[from][input] = to;
            }
        }
        System.out.println("Transition Table:");
        System.out.print("State/Input\t");
        System.out.println();
        for (int i = 0; i < table.length; i++) {
            System.out.print("State " + i + "\t");
            for (int j = 0; j < table[i].length; j++) {
                if (table[i][j] != -1) {
                    System.out.print("Column " + corpusDomains.get(j) + ": " + table[i][j] + "\t");
                }
            }
            System.out.println();
        }

        final int LINE_SIZE = 15896;
        final int SPACE_SIZE =512;
        final int MIN_SPACE_SIZE =410;
        final int MAX_SPACE_SIZE =640;
        final int MAX_NUMBER_SPACE = 5;
        final int MIN_NUMBER_WORD = 9;
        final int MAX_NUMBER_WORD = 15;
        final int NUMBER_CHAR = 59;//Verify if you need to count the spaces at the beginning of lines
        final boolean PRINT_TRACE = false;
        final int NUM_PB = 3;
        final double w =2.0;
        final int NUM_ITERATIONS = 4;
        final int NUM_TOKENS =5;


        Solver cp = makeSolver();
        IntVar[] sizes = makeIntVarArray(cp, NUM_TOKENS, Arrays.stream(lengthTokens).min().getAsInt(), Arrays.stream(lengthTokens).max().getAsInt());
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

        IntVar nb_words = makeIntVar(cp,MIN_NUMBER_WORD,MAX_NUMBER_WORD);
        
        //cp.post(sum(has_space, nb_words));
        //cp.post(sum(num_char, NUMBER_CHAR));



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
        //cp.post(binPacking(line,sizes,lineSize));

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

        //Words regular
        cp.post(Factory.regular(word_index, table, 0,List.of(0) ));
        
        
        DFSearch search = Factory.makeDfs(cp, maxMarginal(word_index));
        final List<Integer> finalCorpusDomains = corpusDomains; // Create a final copy
        search.onSolution(() ->{
                String[] sentence = new String[word_index.length];
                for(int i = 0; i < word_index.length; i++) {
                    sentence[i] = words.get(finalCorpusDomains.get(word_index[i].max()));
                }
                int[] tokens = new int[word_index.length];
                for(int i = 0; i < word_index.length; i++) {
                    tokens[i] = finalCorpusDomains.get(word_index[i].max());
                }
                //System.out.println("solution:" + Arrays.toString(sentence));
                //System.out.println("tokens:" + Arrays.toString(tokens));
                }
        );
        SearchStatistics stats = search.solve();
        System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
        System.out.format("Statistics: %s\n", stats);
        System.out.format("Time: %s\n", stats.timeElapsed());
        System.out.println("-----");
    }
}
        

