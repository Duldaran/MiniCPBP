package minicpbp.examples.config;

import java.net.URI;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ibm.icu.impl.Pair;

public class perplexitySentenceBuilder implements SentenceBuilder {
    private final String mask_string = "[MASK]";
    private ACOLengthSelector acoSelector = new ACOLengthSelector();
    private int lastLength = -1;

    @Override
    public String buildSentence(ArrayList<ScoredSentence> bases, HttpClient client, int port, double mask_percent, ArrayList<Integer> bannedIndices, int minLength, int maxLength) {
        ScoredSentence base = selectWeightedRandom(bases, new Random());
        System.out.println("Perplexity Base sentence: " + base);
        String[] words = base.getSentence().split(" ");
        Random rand = new Random();
        
        // ACO-based length adjustment (one step: -1, 0, or +1)
        int currentLength = words.length;
        int lengthDelta = acoSelector.selectLengthDelta(currentLength, minLength, maxLength);
        lastLength = currentLength + lengthDelta;
        
        if (lengthDelta != 0) {
            System.out.println("ACO length delta: " + lengthDelta + " (" + currentLength + " -> " + lastLength + ")");
        }
        
        int numMasks = Math.max(1, (int) Math.ceil(mask_percent * words.length));
        List<Pair<Integer, Double>> leastToMostProbWords = new ArrayList<>();
        try {
            HttpRequest reqPpl = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mlm_perplexity"))
                .POST(HttpRequest.BodyPublishers.ofString(base.getSentence()))
                .build();
            String respPpl = client.sendAsync(reqPpl, BodyHandlers.ofString())
                .thenApply(HttpResponse::body).join();

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootPpl = objectMapper.readTree(respPpl);
            ArrayNode wordProbsNode = (ArrayNode) rootPpl.get("word_probs");
            if (wordProbsNode != null) {
                for (int i = 0; i < wordProbsNode.size(); i++) {
                    JsonNode wn = wordProbsNode.get(i);
                    int w = wn.get("word_id").asInt();
                    double p = wn.get("prob").asDouble();
                    leastToMostProbWords.add(Pair.of(w, p));
                }
                leastToMostProbWords.sort((a, b) -> Double.compare(a.second, b.second));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        Set<Integer> maskIndices = new HashSet<>();
        maskIndices.addAll(selectIndexByProbability(leastToMostProbWords, rand, numMasks, bannedIndices));
        
        // Adjust length near a mask position
        if (lengthDelta != 0 && !maskIndices.isEmpty()) {
            words = adjustLengthNearMask(words, lengthDelta, maskIndices);
        }

        System.out.println("Masking indices: " + maskIndices);
        for (int idx : maskIndices) {
            if (idx < words.length) {
                words[idx] = mask_string;
            }
        }
        return String.join(" ", words);
    }

    private static ScoredSentence selectWeightedRandom(ArrayList<ScoredSentence> sentences, Random random) {
        if (sentences.isEmpty()) return null;

        return sentences.get(random.nextInt(sentences.size()));
    }

    private static Set<Integer> selectIndexByProbability(List<Pair<Integer, Double>> probList, Random random, int numMask, ArrayList<Integer> bannedIndices) {
        if (probList.isEmpty()) return new HashSet<>();
        
        double totalProb = 0.0;
        for (Pair<Integer, Double> pair : probList) {
            if (bannedIndices != null && bannedIndices.contains(pair.first)) {
                continue;
            }
            totalProb += (1-pair.second); 
        }
        
        Set<Integer> selectedIndices = new HashSet<>();
        
        while (selectedIndices.size() < numMask && selectedIndices.size() < probList.size()) {
            double randomValue = random.nextDouble() * totalProb;
            double cumulativeProb = 0.0;
            
            for (int i = 0; i < probList.size(); i++) {
                final int wordIndex = probList.get(i).first;
                if (selectedIndices.contains(wordIndex) || (bannedIndices != null && bannedIndices.contains(wordIndex))) continue;
                
                cumulativeProb += 1.0 - probList.get(i).second;
                if (randomValue <= cumulativeProb) {
                    selectedIndices.add(wordIndex);
                    totalProb -= probList.get(i).second;
                    break;
                }
            }
        }
        
        return selectedIndices;
    }
    
    /**
     * Adjust sentence length by ±1 word near a mask position
     */
    private String[] adjustLengthNearMask(String[] words, int delta, Set<Integer> maskIndices) {
        Random rand = new Random();
        List<String> wordList = new ArrayList<>(Arrays.asList(words));
        
        // Select a random mask position
        int maskPos = new ArrayList<>(maskIndices).get(rand.nextInt(maskIndices.size()));
        
        if (delta > 0 && wordList.size() < 100) {
            // Add: insert a mask adjacent to existing mask
            int insertPos = Math.min(maskPos + 1, wordList.size());
            wordList.add(insertPos, mask_string);
            System.out.println("ACO: Inserted mask near position " + maskPos);
        } else if (delta < 0 && wordList.size() > 3) {
            // Remove: delete word adjacent to mask (but not the mask itself)
            int removePos = maskPos + 1;
            if (removePos >= wordList.size()) {
                removePos = Math.max(0, maskPos - 1);
            }
            if (removePos != maskPos && removePos < wordList.size()) {
                wordList.remove(removePos);
                System.out.println("ACO: Removed word near position " + maskPos);
            }
        }
        
        return wordList.toArray(new String[0]);
    }
    
    /**
     * Record search outcome to update ACO pheromones
     */
    public void recordSearchFailure(boolean failed) {
        if (lastLength > 0) {
            acoSelector.recordOutcome(lastLength, failed);
        }
    }
}
