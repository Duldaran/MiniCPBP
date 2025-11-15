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

    @Override
    public String buildSentence(ArrayList<ScoredSentence> bases, HttpClient client, int port) {
        ScoredSentence base = selectWeightedRandom(bases, new Random(), 0.8);
        System.out.println("Base sentence: " + base);
        String[] words = base.getSentence().split(" ");
        Random rand = new Random();
        int numMasks = rand.nextBoolean() ? 3 : 4;
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
        int i = 0;
        while (maskIndices.size() < numMasks) {
            maskIndices.add(leastToMostProbWords.get(i).first);
            i++;
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
