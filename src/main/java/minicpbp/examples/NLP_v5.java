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

public class NLP_v5 {
    public static void main(String[] args) throws Exception {
        
            int port = Integer.parseInt(args[1]);
            final int NUM_ITERATIONS = Integer.parseInt(args[3]);
            final double weight = Double.parseDouble(args[0]);
            final String llm_name = args.length > 4 ? args[4] : "zephyr";
            final String configArg = args.length > 5 ? args[5] : "CollieSent1Config";

        List<Logging>  logs = new ArrayList<>();

        try {

        ConstraintBuilder cb;
        switch (configArg) {
            case "CollieSent1Config":
                cb = new CollieSent1Config();
                break;
            default:
                throw new IllegalArgumentException("Unknown config: " + configArg);
        }

        long startTime = System.currentTimeMillis();

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
        Map<List<Integer>, List<Integer>> corpusDomainsSet = new HashMap<>();
        Map<Integer, List<Integer>> corpusDomainToIndex = new HashMap<>();
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
                for(int j=0;j<=sublist.size()-1;j++){
                    List<Integer> prefix = sublist.subList(0, j+1);
                    if (!corpusDomainsSet.containsKey(prefix))
                        corpusDomainsSet.put(prefix, new ArrayList<>(List.of(k)));
                    else
                        corpusDomainsSet.get(prefix).add(k);
                }
                corpusDomainToIndex.put(k, sublist);
                k++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }



        words.add(".");
        words.add("<PAD>");
        corpusDomainsSet.put(List.of(sentence_end_index), new ArrayList<>(Collections.singletonList(words.size()-2)));
        corpusDomainsSet.put(List.of(-1), new ArrayList<>(Collections.singletonList(words.size()-1)));
        corpusDomainToIndex.put(words.size()-2, List.of(sentence_end_index));
        corpusDomainToIndex.put(words.size()-1, List.of(-1));

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

        IntVar[] line = makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, 0, 2);     
        cb.build(new SolverContext(cp, corpusDomains.size(), final_sentence_end, pad_token, charNum, lengthTokens, word_index, words, line));
        String[] tokens_used = new String[SENTENCE_MAX_NUMBER_TOKENS];
        StateManager sm = cp.getStateManager();
        String instruction = cb.getInstruction();

        System.out.println("Time after initialisation of iteration " + z + ": " + (System.currentTimeMillis()-startTime)/1000.0+"s");


        tokens_used = new String[SENTENCE_MAX_NUMBER_TOKENS];


        String selectedWord = " "+commonWords[new Random().nextInt(commonWords.length)];
        

        String current_sentence = selectedWord;
        int num_tok=1;
        while(num_tok<SENTENCE_MAX_NUMBER_TOKENS){
            
            int i = num_tok;
            System.out.println(num_tok);
            System.out.println(current_sentence);
            HttpRequest request = HttpRequest.newBuilder()    
                .uri(URI.create("http://localhost:" + port + "/token"))
                .POST(HttpRequest.BodyPublishers.ofString(instruction + current_sentence))
                .build();
            String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();

            int[] tokensNew = new int[corpusDomains.size()];
            double[] scoresNew = new double[corpusDomains.size()];

            int[] tokensContinue = new int[corpusDomains.size()];
            double[] scoresContinue = new double[corpusDomains.size()];


            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(response);
            ArrayNode tupleList = (ArrayNode) jsonNode.get("prob");

            List<Integer> last_word = mapper.convertValue(jsonNode.get("tokenized_last_word"), new TypeReference<List<Integer>>() {});
            System.out.println("Last word tokenized: " + last_word);
            

            int max_token_new = -1;
            double max_score_new = 0;
            double total_score_new = 0;

            int max_token_continue = -1;
            double max_score_continue = 0;
            double total_score_continue = 0;



            List<Pair<Integer, Double>> tokenScoreListNew = new ArrayList<>();
            List<Pair<List<Integer>, Double>> tokenScoreListContinue = new ArrayList<>();
            for (JsonNode tuple : tupleList) {
                try {
                    int token = ( tuple.get(0)).asInt();
                    double score = ( tuple.get(1)).asDouble();
                    List<Integer> combinedList = new ArrayList<>(last_word);
                    combinedList.add(token);
                    if (!corpusDomainsSet.containsKey(List.of(token)) && !corpusDomainsSet.containsKey(combinedList)) continue;
                    if (score < 0) continue;
                    if(corpusDomainsSet.containsKey(combinedList)){
                        tokenScoreListContinue.add(Pair.of(combinedList, score));
                    }
                    else{
                        tokenScoreListNew.add(Pair.of(token, score));
                    }
                } catch (Exception e) {
                    if (PRINT_TRACE) {
                        System.err.println(tuple);
                        System.err.println(e);
                    }
                }
            }

            tokenScoreListNew.sort((a, b) -> Double.compare(
                b.second, a.second
            ));
            tokenScoreListContinue.sort((a, b) -> Double.compare(
                b.second, a.second
            ));
            for (int t = 0; t < Math.min(5, tokenScoreListNew.size()); t++) {
                Pair<Integer, Double> pair = tokenScoreListNew.get(t);
                System.out.println("Top " + (t + 1) + " new: token=" + tokens_list.get(pair.first) + ", index=" + pair.first + ", score=" + pair.second);
            }

            for (int t = 0; t < Math.min(5, tokenScoreListContinue.size()); t++) {
                Pair<List<Integer>, Double> pair = tokenScoreListContinue.get(t);
                String tokenStr = pair.first.stream().map(idx -> tokens_list.get(idx)).collect(Collectors.joining(""));
                System.out.println("Top " + (t + 1) + " continue: token=" + tokenStr + ", score=" + pair.second);
            }

            if(tokenScoreListNew.get(0).first==sentence_end_index && i<word_index.length-1){
                System.out.println("sentence end reached");
                final boolean[] canEnd = {false};
                final String testSentence = current_sentence;
                sm.withNewState(() -> {
                    try {
                        String[] split_word = testSentence.trim().split(" ");
                        assert split_word.length==i;
                        for (int j = 0; j < i; j++) {
                            word_index[j].assign(words.indexOf(" " + split_word[j]));
                        }
                        
                        word_index[i].assign(final_sentence_end);
                        cp.fixPoint();

                        canEnd[0] = true;
                    } catch (Exception e) {
                        System.out.println("Not able to end sentence yet, continuing");
                        canEnd[0] = false;
                    }
                });
                if (canEnd[0]) {
                    current_sentence += words.get(corpusDomains.get(final_sentence_end));
                    List<Integer> list_sub = new ArrayList<>(corpusDomainToIndex.get(final_sentence_end));
                    list_sub.removeAll(last_word);
                    tokens_used[num_tok] = tokens_list.get(list_sub.get(0));
                    System.out.println("Final sentence: " + current_sentence);
                    break;
                }
                
            }

            int limitNew = Math.min(ORACLE_TOP_K, tokenScoreListNew.size());
            int limitContinue = Math.min(ORACLE_TOP_K, tokenScoreListContinue.size());

            for (int l = 0; l < limitNew; l++) {
                int token = tokenScoreListNew.get(l).first;
                double score = tokenScoreListNew.get(l).second;
                int[] token_indexs = corpusDomainsSet.get(List.of(token)).stream().mapToInt(Integer::intValue).toArray();
                for (int token_index : token_indexs) {
                    tokensNew[token_index] = token_index;
                    scoresNew[token_index] = score;
                    total_score_new += score;
                }
                if (PRINT_TRACE) {
                    if (score > max_score_new) {
                        System.out.println("New max score found: " + score);
                        System.out.println("Token Strong: " + tokens_list.get(token));
                        System.out.println("Token: " + token);
                        System.out.println("Corpus indexs: " + Arrays.toString(token_indexs));
                        for(int idx: token_indexs){
                            System.out.println("Corresponding word: " + words.get(idx));
                        }
                        max_score_new = score;
                        max_token_new = token_indexs[0];
                    }
                }
            }
            for (int j=0; j<tokensNew.length; j++) {
                double score=scoresNew[j];
                if (score > 0) {
                    scoresNew[j] /= total_score_new;
                }
                else if(score<0){
                    throw new RuntimeException("Score is negative or zero");
                }
            }
            System.out.println("total_score_new: "+total_score_new);
            System.out.println("max_score_new before normalization: "+max_score_new);
            max_score_new /= total_score_new;

            for (int l = 0; l < limitContinue; l++) {
                List<Integer> tokens = tokenScoreListContinue.get(l).first;
                double score = tokenScoreListContinue.get(l).second;
                int[] token_indexs = corpusDomainsSet.get(tokens).stream().mapToInt(Integer::intValue).toArray();
                for (int token_index : token_indexs) {
                    tokensContinue[token_index] = token_index;
                    scoresContinue[token_index] = score;
                    total_score_continue += score;
                }
                if (PRINT_TRACE) {
                    if (score > max_score_continue) {
                        max_score_continue = score;
                        max_token_continue = token_indexs[0];
                    }
                }
            }

            System.out.println("Total_score_continue: "+total_score_continue);
            for (int j=0; j<tokensContinue.length; j++) {
                double score=scoresContinue[j];
                if (score > 0) {
                    scoresContinue[j] /= total_score_continue;
                }
                else if(score<0){
                    throw new RuntimeException("Score is negative or zero");
                }
            }
            max_score_continue /= total_score_continue;

            Map<Integer, Double> marginalsMap = new HashMap<>();
            final double total_score = total_score_new + total_score_continue;
            final double final_score_continue = total_score_continue;   
            System.out.println("total_score: "+total_score);

            if(total_score_continue>0){
                final String testSentence = current_sentence;
                try {
                    
                sm.withNewState(() -> {
                    // assign the words in the current sentence except the last one
                    String[] split_word = testSentence.trim().split(" ");
                    for (int j = 0; j < split_word.length-1; j++) {
                        try {
                            word_index[j].assign(words.indexOf(" " + split_word[j]));
                        } catch (InconsistencyException e) {
                            System.out.println("Inconsistency detected with continue tokens, state restored");
                            throw e;
                        }
                    }

                    // create and post oracle constraint with continue tokens and scores
                    assert split_word.length-1==i-1;
                    Constraint c = Factory.oracle(word_index[i-1], tokensContinue, scoresContinue);
                    c.setWeight(w);
                    cp.post(c);

                    cp.fixPoint();
                    cp.vanillaBP(NUM_PB);
                    
                    double ratio_continue = final_score_continue / total_score;
                    Map<Integer, List<Double>> tempMap = new HashMap<>();
                    while(word_index[i-1].maxMarginal()!=0.0) {
                        List<Integer> word_indexes = new ArrayList<>(corpusDomainToIndex.get(word_index[i-1].valueWithMaxMarginal()));
                        int index;
                        word_indexes.removeAll(last_word);
                        if(word_indexes.isEmpty()) index=last_word.get(last_word.size()-1);//TODO: verify if this is ok
                        else index = word_indexes.get(0);
                        if(!tempMap.containsKey(index)){
                            tempMap.put(index, new ArrayList<>());
                        }
                        tempMap.get(index).add(word_index[i-1].maxMarginal()*ratio_continue);
                        word_index[i-1].remove(word_index[i-1].valueWithMaxMarginal());
                    }
                    for(Entry<Integer, List<Double>> entry: tempMap.entrySet()){//TODO : Consider if max would be more interesting than average
                        double marginalAverage = 0.0;
                        for(double val: entry.getValue()){
                            marginalAverage += val;
                        }
                        marginalAverage /= entry.getValue().size();
                        marginalsMap.put(entry.getKey(), marginalAverage);
                    }
                });}
                catch (Exception e) {
                    System.out.println("Inconsistency detected with continue tokens, state restored");
                }
            }

            System.out.println("Processing new tokens");
            String last_word_string = last_word.stream()
                                             .map(idx -> tokens_list.get(idx))
                                             .collect(Collectors.joining(""));
            System.out.println("Last word string: '" + last_word_string + "'");
            System.out.println("Words contains last word: " + words.contains(last_word_string));

            if(words.contains(last_word_string)) {
                final double final_score_new = total_score_new;
                final String testSentence = current_sentence;
                try {
                sm.withNewState(() -> {
                    String[] split_word = testSentence.trim().split(" ");
                    for (int j = 0; j < split_word.length; j++) {
                        try {
                            word_index[j].assign(words.indexOf(" " + split_word[j]));
                        } catch (InconsistencyException e) {
                            System.out.println("Sentence so far: "+Arrays.toString(split_word));
                            System.out.println("Inconsistency caused by : " + split_word[j]+", index: "+j);
                            System.out.println("Words contains word : " + words.indexOf(" "+split_word[j]));
                            System.out.println("Inconsistency detected with new tokens, state restored");
                            for(int jj=0; jj<word_index.length; jj++){
                                System.out.println(word_index[jj].getName()+word_index[jj].toString());
                            }
                            throw e;
                        }
                    }

                    if(PRINT_TRACE) System.out.println("token "+i);

                    Constraint c = Factory.oracle(word_index[i], tokensNew, scoresNew);
                    c.setWeight(w);
                    if(PRINT_TRACE)  System.out.println("oracle's weight set to "+w);
                    cp.post(c);
                    if(PRINT_TRACE)  System.out.println("GPT, before BP (max token, 'the word', its probability) "+max_token_new+", '"+words.get(max_token_new)+"', "+final_score_new);
                    if(PRINT_TRACE) {
                        double[] temp = scoresNew.clone();
                        Arrays.sort(temp);
                        for(int n=1; n<=5; n++){
                            for(int m=0; m<temp.length; m++){
                                if(temp[temp.length-n]==scoresNew[m]){
                                    System.out.println("GPT, before BP (max token, 'the word', its probability) "+m+", '"+words.get(m)+"', "+scoresNew[m]);
                                }
                            }
                        }
                    }

                    try {
                        cp.fixPoint();
                    } catch (InconsistencyException e) {
                        if (PRINT_TRACE) {
                            System.out.println("INCONSISTENCY!");
                            for(int j=0; j<word_index.length; j++){
                                System.out.println(word_index[j].getName()+word_index[j].toString());
                            }
                        }
                        
                        throw e;
                    }
                    
                    if(PRINT_TRACE) {
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
                    
                    if(PRINT_TRACE) {
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
                        throw new InconsistencyException();
                    }

                    double ratio = final_score_new / total_score;
                    Map<Integer, List<Double>> tempMap = new HashMap<>();
                    while(word_index[i].maxMarginal() != 0.0) {
                        double marginal = word_index[i].maxMarginal() * ratio;
                        List<Integer> word_indexes = new ArrayList<>(corpusDomainToIndex.get(word_index[i].valueWithMaxMarginal()));
                        int index;
                        word_indexes.removeAll(last_word);
                        if(word_indexes.isEmpty()) index=last_word.get(last_word.size()-1);
                        else index = word_indexes.get(0);
                        if(!tempMap.containsKey(index)){
                            tempMap.put(index, new ArrayList<>());
                        }
                        tempMap.get(index).add(marginal);
                        word_index[i].remove(word_index[i].valueWithMaxMarginal());
                    }

                    for(Entry<Integer, List<Double>> entry: tempMap.entrySet()){
                        double marginalAverage = 0.0;
                        for(double val: entry.getValue()){
                            marginalAverage += val;
                        }
                        marginalAverage /= entry.getValue().size();
                        marginalsMap.put(entry.getKey(), marginalAverage);
                    }
                });
                }
                catch (InconsistencyException e) {
                    if(final_score_continue>0)
                        System.out.println("Inconsistency detected with new tokens, state restored");
                    else{
                        e.printStackTrace();
                        current_sentence += " ERROR";
                        break;
                    }
                }
            }
            int chosen = -1;
    
            Random random = new Random();
            double randomValue = random.nextDouble();
            double cumulativeProbability = 0.0;
            
            double sum = marginalsMap.values().stream().mapToDouble(Double::doubleValue).sum();
            System.out.println("Sum of marginals before normalization: "+sum);
            if(sum==0.0){
                System.out.println("All marginals are zero, inconsistency detected");
                current_sentence += " ERROR";
                break;
            }
            for (Map.Entry<Integer, Double> entry : marginalsMap.entrySet()) {
                entry.setValue(entry.getValue() / sum);
            }
            for (Map.Entry<Integer, Double> entry : marginalsMap.entrySet()) {
                cumulativeProbability += entry.getValue();
                if (randomValue <= cumulativeProbability) {
                    chosen = entry.getKey();
                    if(chosen==-1) continue;
                    break;
                }
            }
            if (chosen == -1) {
                throw new Exception("No token chosen, inconsistency detected");
            }
            System.out.println("chosen: "+chosen+", '"+tokens_list.get(chosen)+"', "+marginalsMap.get(chosen));


            current_sentence += tokens_list.get(chosen);
            tokens_used[num_tok] += ", " + tokens_list.get(chosen);
            


            if (PRINT_TRACE) {
                System.out.println("sentence so far: " + current_sentence);
                System.out.println("index chosen: " + chosen);
            }

            if(tokens_list.get(chosen).startsWith(" ")){
                num_tok+=1;
            }


        }
        double perplexityScore = 0;

        if (!current_sentence.trim().endsWith("ERROR") && !current_sentence.trim().endsWith(".")) {
            current_sentence = current_sentence.trim() + ".";
        }
        System.out.println("solution : " + current_sentence);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/tokenize"))
            .POST(HttpRequest.BodyPublishers.ofString("0" + current_sentence))
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
    String outputFileName = OUTPUT_DIR + "/result_"+configArg+ "_NLP_v5_" + System.currentTimeMillis()  + ".json";
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(outputFileName).toFile(), result);
    }
    catch (Exception e) {
            e.printStackTrace();
            // Write error to output file
            String OUTPUT_DIR = args.length > 3 ? args[2] : "./outputs";
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            String outputFileName = OUTPUT_DIR + "/result_" + configArg + "_NLP_v5_" + System.currentTimeMillis()  + "_error.json";
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("status", "error");
            errorResult.put("error_message", e.getMessage());
            errorResult.put("exception", e.toString());
            errorResult.put("date", java.time.LocalDateTime.now().toString());
            errorResult.put("port", port);
            errorResult.put("num_iterations", NUM_ITERATIONS);
            errorResult.put("weight", weight);
            errorResult.put("llm_name", llm_name);
            errorResult.put("logs", logs);
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
    

