package minicpbp.examples.config;

import java.util.Random;

/**
 * Length selector that performs pure random exploration.
 */
public class LengthSelector {
    private final Random random = new Random();
    private double removeProbability = 0.10;
    private double addProbability = 0.25;

    /**
     * Configure probabilities for remove (-1) and add (+1).
     * Stay (0) is computed automatically as 1 - removeProbability - addProbability.
     */
    public void setActionProbabilities(double removeProbability, double addProbability) {
        validateProbability(removeProbability, "removeProbability");
        validateProbability(addProbability, "addProbability");
        if (removeProbability + addProbability > 1.0) {
            throw new IllegalArgumentException("removeProbability + addProbability must be <= 1");
        }

        this.removeProbability = removeProbability;
        this.addProbability = addProbability;
    }

    public double getRemoveProbability() {
        return removeProbability;
    }

    public double getAddProbability() {
        return addProbability;
    }

    public double getStayProbability() {
        return 1.0 - removeProbability - addProbability;
    }

    private static void validateProbability(double probability, String name) {
        if (Double.isNaN(probability) || Double.isInfinite(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException(name + " must be a finite value in [0, 1]");
        }
    }
    
    /**
     * Record a search outcome for a given sentence length
     * @param length The sentence length attempted
     * @param failed Whether the search failed
     */
    public void recordOutcome(int length, boolean failed) {
        // Intentionally no-op: pure random selector has no learning state.
    }
    
    /**
     * Select a length modification: -1, 0, or +1 based on configured probabilities.
     * @param currentLength Current sentence length
     * @param minLength Minimum allowed length
     * @param maxLength Maximum allowed length
     * @return -1 to shrink, 0 to keep same, +1 to grow
     */
    public int selectLengthDelta(int currentLength, int minLength, int maxLength) {
        boolean canRemove = currentLength > minLength;
        boolean canAdd = currentLength < maxLength;

        double effectiveRemove = canRemove ? removeProbability : 0.0;
        double effectiveAdd = canAdd ? addProbability : 0.0;
        double thresholdRemove = effectiveRemove;
        double thresholdAdd = thresholdRemove + effectiveAdd;

        double r = random.nextDouble();
        if (r < thresholdRemove) {
            return -1;
        }
        if (r < thresholdAdd) {
            return 1;
        }

        return 0;
    }
    
    /**
     * Get statistics for debugging
     */
    public String getStats() {
        return String.format(
                "Random Length Selector probabilities: remove(-1)=%.3f, stay(0)=%.3f, add(+1)=%.3f",
                removeProbability, getStayProbability(), addProbability);
    }
}
