package minicpbp.examples.config;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class randomMoleculeBuilder implements MoleculeBuilder {
    private final String mask_string = "<mask>";

    @Override
    public String buildMolecule(ArrayList<ScoredMolecule> bases, HttpClient client, int port) {
        ScoredMolecule base = selectWeightedRandom(bases, new Random(), 0.8);
        System.out.println("Base molecule: " + base);
        
        String molecule = base.getMolecule();
        char[] chars = molecule.toCharArray();
        
        Random rand = new Random();
        
        // Determine number of positions to mask (e.g., 20-30% of molecule length)
        int minMasks = Math.max(2, chars.length / 5);
        int maxMasks = Math.max(2, chars.length / 3);
        int numMasks = rand.nextInt(maxMasks - minMasks + 1) + minMasks;
        
        Set<Integer> maskIndices = new HashSet<>();
        
        // Select random positions to mask, avoiding special tokens like '<s>' or padding
        while (maskIndices.size() < numMasks) {
            int idx = rand.nextInt(chars.length);
            while (maskIndices.contains(idx)) {
                idx = rand.nextInt(chars.length);
            }
            
            maskIndices.add(idx);
        }
        
        System.out.println("Masking " + maskIndices.size() + " positions: " + maskIndices);
        
        // Build the masked molecule
        StringBuilder maskedMolecule = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            if (maskIndices.contains(i)) {
                maskedMolecule.append(mask_string);
            } else {
                maskedMolecule.append(chars[i]);
            }
        }
        
        return maskedMolecule.toString();
    }



    public static ScoredMolecule selectWeightedRandom(
            ArrayList<ScoredMolecule> molecules, 
            Random random, 
            double temperature) {
        
        if (molecules.isEmpty()) return null;

        double totalWeight = 0.0;
        
        // Calculate weights based on scores (lower score = better = higher weight)
        for (ScoredMolecule mol : molecules) {
            double weight = Math.exp(-mol.getScore() / temperature);
            mol.setWeight(weight);
            totalWeight += weight;
        }
        
        // Normalize weights
        for (ScoredMolecule mol : molecules) {
            mol.setWeight(mol.getWeight() / totalWeight);
        }
        
        // Select based on weighted probability
        double randomValue = random.nextDouble();
        double cumulativeWeight = 0.0;
        
        for (ScoredMolecule mol : molecules) {
            cumulativeWeight += mol.getWeight();
            if (randomValue <= cumulativeWeight) {
                return mol;
            }
        }
        
        // Fallback
        return molecules.get(molecules.size() - 1);
    }
}