package minicpbp.examples.config;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.ibm.icu.impl.Pair;

public class randomSentenceBuilder implements SentenceBuilder {
    private final String mask_string = "[MASK]";
    private LengthSelector lengthSelector = new LengthSelector();
    private int lastLength = -1;

    @Override
    public String buildSentence(ArrayList<ScoredSentence> bases,HttpClient client, int port, double mask_percent, ArrayList<Integer> bannedIndices, int minLength, int maxLength) {
        ScoredSentence base = selectWeightedRandom(bases, new Random());
        System.out.println("Random Base sentence: " + base);
        String[] words = base.getSentence().split(" ");
        Random rand = new Random();
        
        //length adjustment (one step: -1, 0, or +1)
        int currentLength = words.length;
        int lengthDelta = lengthSelector.selectLengthDelta(currentLength, minLength, maxLength);
        lastLength = currentLength + lengthDelta;
        
        if (lengthDelta != 0) {
            System.out.println("Length delta: " + lengthDelta + " (" + currentLength + " -> " + lastLength + ")");
        }
        
        // Select mask positions first
        int numMasks = Math.max(1, (int) Math.ceil(mask_percent * words.length));
        Set<Integer> maskIndices = new HashSet<>();
        while (maskIndices.size() < numMasks) {
            int idx = rand.nextInt(words.length);
            while (maskIndices.contains(idx) || (bannedIndices != null && bannedIndices.contains(idx))) {
                idx = rand.nextInt(words.length);
            }
            maskIndices.add(idx);
        }
        
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
    
    /**
     * Adjust sentence length by ±1 word near a mask position
     */
    private String[] adjustLengthNearMask(String[] words, int delta, Set<Integer> maskIndices) {
        Random rand = new Random();
        List<String> wordList = new ArrayList<>(Arrays.asList(words));
        
        // Select a random mask position
        int maskPos = new ArrayList<>(maskIndices).get(rand.nextInt(maskIndices.size()));
        
        if (delta > 0 && wordList.size() < 100) {
            // Add a new mask adjacent to the chosen mask, then shift later masks to keep their word targets.
            int insertPos = Math.min(maskPos + 1, wordList.size());
            wordList.add(insertPos, mask_string);
            Set<Integer> shiftedMaskIndices = new HashSet<>();
            for (int index : maskIndices) {
                shiftedMaskIndices.add(index >= insertPos ? index + 1 : index);
            }
            maskIndices.clear();
            maskIndices.addAll(shiftedMaskIndices);
            maskIndices.add(insertPos);
            System.out.println("Inserted mask near position " + maskPos);
        } else if (delta < 0 && wordList.size() > 3) {
            // Remove a word adjacent to the chosen mask, then shift later masks back to keep their word targets.
            int removePos = maskPos + 1;
            if (removePos >= wordList.size()) {
                removePos = Math.max(0, maskPos - 1);
            }
            if (removePos != maskPos && removePos < wordList.size()) {
                wordList.remove(removePos);
                Set<Integer> shiftedMaskIndices = new HashSet<>();
                for (int index : maskIndices) {
                    if (index > removePos) {
                        shiftedMaskIndices.add(index - 1);
                    } else if (index < removePos) {
                        shiftedMaskIndices.add(index);
                    }
                }
                maskIndices.clear();
                maskIndices.addAll(shiftedMaskIndices);
                System.out.println("Removed word near position " + maskPos);
            }
        }
        
        return wordList.toArray(new String[0]);
    }
    
    /**
     * Record search outcome for the selector.
     */
    public void recordSearchFailure(boolean failed) {
        if (lastLength > 0) {
            lengthSelector.recordOutcome(lastLength, failed);
        }
    }

    private static ScoredSentence selectWeightedRandom(ArrayList<ScoredSentence> sentences, Random random) {
        if (sentences.isEmpty()) return null;

        return sentences.get(random.nextInt(sentences.size()));
    }

    @Override
    public String buildSentenceLight(ScoredSentence base, double mask_percent, int minLength, int maxLength,
            List<Pair<Integer, Double>> leastToMostProbWords) {
        String newBase = base.getSentence();
        if (newBase.split(" ").length < minLength) {
            for(int i = 0; i < minLength - newBase.split(" ").length; i++) {
                newBase += " " + mask_string;
            }
        }
        else if (newBase.split(" ").length > maxLength) {
            String[] words = newBase.split(" ");
            newBase = String.join(" ", Arrays.copyOfRange(words, 0, maxLength));
        }
        return buildSentence(  new ArrayList<>(Arrays.asList(new ScoredSentence(newBase, base.getPerplexity()))), null, 0, mask_percent, new ArrayList<>(), minLength, maxLength);
    }

    @Override
    public List<Pair<Integer, Double>> buildLeastToMostProbWords(HttpClient client, int port, String sentence, int maxLength) {
        
        return new ArrayList<>();
    }

    
    
}
