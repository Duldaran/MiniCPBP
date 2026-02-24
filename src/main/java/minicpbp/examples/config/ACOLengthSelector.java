package minicpbp.examples.config;

import java.util.*;

/**
 * ACO-based length selector that learns from search failures.
 * Maintains pheromone trails for sentence lengths and adjusts based on failure rates.
 */
public class ACOLengthSelector {
    private static final double ALPHA = 1.0;              // Pheromone importance
    private static final double RHO = 0.15;               // Evaporation rate
    private static final double INITIAL_PHEROMONE = 1.0;
    private static final double MIN_PHEROMONE = 0.1;
    private static final double MAX_PHEROMONE = 5.0;
    private static final double EXPLORATION_RATE = 0.2;   // 20% random exploration
    
    // Track pheromones for each sentence length
    private Map<Integer, Double> lengthPheromones = new HashMap<>();
    
    // Track failures for each length
    private Map<Integer, Integer> lengthFailures = new HashMap<>();
    private Map<Integer, Integer> lengthAttempts = new HashMap<>();
    
    private Random random = new Random();
    
    /**
     * Record a search outcome for a given sentence length
     * @param length The sentence length attempted
     * @param failed Whether the search failed
     */
    public void recordOutcome(int length, boolean failed) {
        lengthAttempts.put(length, lengthAttempts.getOrDefault(length, 0) + 1);
        
        if (failed) {
            lengthFailures.put(length, lengthFailures.getOrDefault(length, 0) + 1);
            // Reduce pheromone on failure
            double currentPheromone = lengthPheromones.getOrDefault(length, INITIAL_PHEROMONE);
            lengthPheromones.put(length, Math.max(MIN_PHEROMONE, currentPheromone * 0.8));
        } else {
            // Increase pheromone on success
            double currentPheromone = lengthPheromones.getOrDefault(length, INITIAL_PHEROMONE);
            lengthPheromones.put(length, Math.min(MAX_PHEROMONE, currentPheromone + 0.5));
        }
    }
    
    /**
     * Select a length modification: -1, 0, or +1 based on pheromone trails
     * @param currentLength Current sentence length
     * @param minLength Minimum allowed length
     * @param maxLength Maximum allowed length
     * @return -1 to shrink, 0 to keep same, +1 to grow
     */
    public int selectLengthDelta(int currentLength, int minLength, int maxLength) {
        // Evaporate pheromones
        evaporatePheromones();
        
        // Determine valid deltas
        List<Integer> validDeltas = new ArrayList<>();
        if (currentLength > minLength) validDeltas.add(-1);
        validDeltas.add(0);
        if (currentLength < maxLength) validDeltas.add(1);
        
        // Exploration: random choice
        if (random.nextDouble() < EXPLORATION_RATE || lengthPheromones.isEmpty()) {
            return validDeltas.get(random.nextInt(validDeltas.size()));
        }
        
        // Exploitation: pheromone-guided selection
        List<Double> probs = new ArrayList<>();
        double totalProb = 0.0;
        
        for (int delta : validDeltas) {
            int targetLength = currentLength + delta;
            double pheromone = lengthPheromones.getOrDefault(targetLength, INITIAL_PHEROMONE);
            
            // Adjust probability based on failure rate
            int attempts = lengthAttempts.getOrDefault(targetLength, 1);
            int failures = lengthFailures.getOrDefault(targetLength, 0);
            double successRate = 1.0 - ((double) failures / attempts);
            
            // Combine pheromone with success rate
            double prob = Math.pow(pheromone, ALPHA) * (0.3 + 0.7 * successRate);
            
            probs.add(prob);
            totalProb += prob;
        }
        
        // Roulette wheel selection
        double r = random.nextDouble() * totalProb;
        double cumProb = 0.0;
        for (int i = 0; i < validDeltas.size(); i++) {
            cumProb += probs.get(i);
            if (r <= cumProb) {
                return validDeltas.get(i);
            }
        }
        
        return 0; // Default: no change
    }
    
    /**
     * Evaporate pheromones over time
     */
    private void evaporatePheromones() {
        for (Integer length : new HashSet<>(lengthPheromones.keySet())) {
            double newValue = lengthPheromones.get(length) * (1.0 - RHO);
            if (newValue < MIN_PHEROMONE) {
                lengthPheromones.put(length, MIN_PHEROMONE);
            } else {
                lengthPheromones.put(length, newValue);
            }
        }
    }
    
    /**
     * Get statistics for debugging
     */
    public String getStats() {
        StringBuilder sb = new StringBuilder("ACO Length Stats:\n");
        for (int length : lengthPheromones.keySet()) {
            int attempts = lengthAttempts.getOrDefault(length, 0);
            int failures = lengthFailures.getOrDefault(length, 0);
            double pheromone = lengthPheromones.get(length);
            sb.append(String.format("  Length %d: pheromone=%.2f, attempts=%d, failures=%d\n", 
                                   length, pheromone, attempts, failures));
        }
        return sb.toString();
    }
}
