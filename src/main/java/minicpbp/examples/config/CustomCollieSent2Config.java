package minicpbp.examples.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ibm.icu.impl.Pair;

import minicpbp.cp.Factory;

public class CustomCollieSent2Config implements ConstraintBuilder {

    private final int exactWords;
    private final Map<String, Integer> requiredWordPositions; // 1-based positions

    public CustomCollieSent2Config(int exactWords, Map<String, Integer> requiredWordPositions) {
        if (exactWords <= 0) {
            throw new IllegalArgumentException("exactWords must be > 0");
        }
        this.exactWords = exactWords;
        this.requiredWordPositions = new LinkedHashMap<>(requiredWordPositions);
        for (Map.Entry<String, Integer> entry : this.requiredWordPositions.entrySet()) {
            int pos = entry.getValue();
            if (pos < 1 || pos > exactWords) {
                throw new IllegalArgumentException("Position out of range for word '" + entry.getKey() + "': " + pos);
            }
        }
    }

    public static CustomCollieSent2Config fromArgs(List<String> args) {
        if (args.size() < 2) {
            throw new IllegalArgumentException("CustomCollieSent2Config expects: <exactWords> <word:position,word:position,...>");
        }
        int exactWords = Integer.parseInt(args.get(0));
        Map<String, Integer> positions = parseWordPositions(args.get(1));
        return new CustomCollieSent2Config(exactWords, positions);
    }

    private static Map<String, Integer> parseWordPositions(String spec) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (spec == null || spec.trim().isEmpty()) {
            return result;
        }
        String[] pairs = spec.split(",");
        for (String p : pairs) {
            String[] kv = p.trim().split(":");
            if (kv.length != 2) {
                throw new IllegalArgumentException("Invalid word-position pair: " + p + ". Expected format word:position");
            }
            String word = kv[0].trim();
            int position = Integer.parseInt(kv[1].trim());
            if (word.isEmpty()) {
                throw new IllegalArgumentException("Word cannot be empty in pair: " + p);
            }
            result.put(word, position);
        }
        return result;
    }

    @Override
    public String getInstruction() {
        if (requiredWordPositions.isEmpty()) {
            return "Generate a sentence with exactly " + exactWords + " words.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Generate a sentence with exactly ").append(exactWords).append(" words, where ");
        int c = 0;
        for (Map.Entry<String, Integer> entry : requiredWordPositions.entrySet()) {
            if (c > 0) {
                sb.append(" and ");
            }
            sb.append("word ").append(entry.getValue()).append(" is \"").append(entry.getKey()).append("\"");
            c++;
        }
        sb.append(".");
        return sb.toString();
    }

    @Override
    public void build(SolverContext ctx) {
        for (Map.Entry<String, Integer> entry : requiredWordPositions.entrySet()) {
            int idx = findUniqueWordIndex(ctx.words, entry.getKey());
            ctx.word_index[entry.getValue() - 1].assign(idx);
        }

        ctx.cp.post(Factory.atmost(ctx.word_index, ctx.pad_token, Math.max(exactWords - exactWords - 1, 0)));

        List<Integer> acceptedState = new ArrayList<>();
        int[][] a = new int[2][ctx.corpusDomains_size];
        acceptedState.add(1);
        Arrays.fill(a[0], 0);
        a[0][ctx.end_sentence] = 1;
        Arrays.fill(a[1], -1);
        a[1][ctx.pad_token] = 1;
        ctx.cp.post(Factory.regular(ctx.word_index, a, 0, acceptedState));
    }

    private int findUniqueWordIndex(List<String> words, String token) {
        String expected = " " + token;
        int found = -1;
        for (int i = 0; i < words.size(); i++) {
            if (expected.equals(words.get(i))) {
                if (found != -1) {
                    throw new RuntimeException("The word '" + token + "' appears multiple times in the corpus.");
                }
                found = i;
            }
        }
        if (found == -1) {
            throw new RuntimeException("Could not find required word in the corpus: " + token);
        }
        return found;
    }

    @Override
    public Pair<Integer, Integer> getWordCountRange() {
        return Pair.of(exactWords, exactWords);
    }

    @Override
    public String fileRef() {
        throw new UnsupportedOperationException("Unimplemented method 'fileRef'");
    }

    @Override
    public Boolean isValid() {
        throw new UnsupportedOperationException("Unimplemented method 'isValid'");
    }
}
