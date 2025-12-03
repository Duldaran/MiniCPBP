package minicpbp.examples.config;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class randomSentenceBuilder implements SentenceBuilder {
    private final String mask_string = "[MASK]";

    @Override
    public String buildSentence(ArrayList<ScoredSentence> bases,HttpClient client, int port, double mask_percent, ArrayList<Integer> bannedIndices) {
        ScoredSentence base = selectWeightedRandom(bases, new Random(), 0.8);
        System.out.println("Base sentence: " + base);
        String[] words = base.getSentence().split(" ");
        Random rand = new Random();
        int numMasks = (int) Math.ceil(mask_percent * words.length);
        Set<Integer> maskIndices = new HashSet<>();
        while (maskIndices.size() < numMasks) {
            int idx = rand.nextInt(words.length);
            while (maskIndices.contains(idx) || (bannedIndices != null && bannedIndices.contains(idx))) {
                idx = rand.nextInt(words.length);
            }
            maskIndices.add(idx);
        }
        System.out.println("Masking indices: " + maskIndices);
        for (int idx : maskIndices) {
            words[idx] = mask_string;
        }
        return String.join(" ", words);
    }

    private static ScoredSentence selectWeightedRandom(ArrayList<ScoredSentence> sentences, Random random, double temperature) {
        if (sentences.isEmpty()) return null;

        double totalWeight = 0.0;
        
        for (ScoredSentence sent : sentences) {
            double weight = Math.exp(-sent.getPerplexity() / temperature);
            sent.setWeight(weight);
            totalWeight += weight;
        }
        
        for (ScoredSentence sent : sentences) {
            sent.setWeight(sent.getWeight() / totalWeight);
        }
        
        double randomValue = random.nextDouble(); // 0.0 to 1.0
        double cumulativeWeight = 0.0;
        
        for (ScoredSentence sent : sentences) {
            cumulativeWeight += sent.getWeight();
            if (randomValue <= cumulativeWeight) {
                return sent;
            }
        }
        
        // Fallback (shouldn't reach here due to normalization)
        return sentences.get(sentences.size() - 1);
    }
    
}
