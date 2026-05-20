package minicpbp.examples.config;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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
            // Add: insert a mask adjacent to existing mask
            int insertPos = Math.min(maskPos + 1, wordList.size());
            wordList.add(insertPos, mask_string);
            System.out.println("Inserted mask near position " + maskPos);
        } else if (delta < 0 && wordList.size() > 3) {
            // Remove: delete word adjacent to mask (but not the mask itself)
            int removePos = maskPos + 1;
            if (removePos >= wordList.size()) {
                removePos = Math.max(0, maskPos - 1);
            }
            if (removePos != maskPos && removePos < wordList.size()) {
                wordList.remove(removePos);
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
    
}
