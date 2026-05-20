package minicpbp.examples.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ibm.icu.impl.Pair;

import minicpbp.cp.Factory;

public class CustomCollieSent4Config implements ConstraintBuilder {

    private final List<String> requiredWords;
    private final int minWords;
    private final int maxWords;

    public CustomCollieSent4Config(List<String> requiredWords, int minWords, int maxWords) {
        if (requiredWords == null || requiredWords.isEmpty()) {
            throw new IllegalArgumentException("requiredWords cannot be empty");
        }
        if (minWords <= 0 || maxWords < minWords) {
            throw new IllegalArgumentException("Invalid word range: minWords=" + minWords + ", maxWords=" + maxWords);
        }
        this.requiredWords = new ArrayList<>(requiredWords);
        this.minWords = minWords;
        this.maxWords = maxWords;
    }

    public static CustomCollieSent4Config fromArgs(List<String> args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("CustomCollieSent4Config expects: <word1,word2,...> [minWords] [maxWords]");
        }
        List<String> words = parseWordList(args.get(0));
        int minWords = args.size() > 1 ? Integer.parseInt(args.get(1)) : 5;
        int maxWords = args.size() > 2 ? Integer.parseInt(args.get(2)) : 20;
        return new CustomCollieSent4Config(words, minWords, maxWords);
    }

    private static List<String> parseWordList(String csv) {
        List<String> words = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) {
            return words;
        }
        for (String raw : csv.split(",")) {
            String w = raw.trim();
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        return words;
    }

    @Override
    public String getInstruction() {
        return "Generate a sentence but be sure to include these words: " + String.join(", ", requiredWords) + ".";
    }

    @Override
    public void build(SolverContext ctx) {
        List<Integer> requiredIndices = new ArrayList<>();
        for (String requiredWord : requiredWords) {
            int idx = findUniqueWordIndex(ctx.words, requiredWord);
            requiredIndices.add(idx);
            ctx.cp.post(Factory.atleast(ctx.word_index, idx, 1));
        }

        int[] idxArray = requiredIndices.stream().mapToInt(Integer::intValue).toArray();
        ctx.cp.post(Factory.atleast(ctx.word_index, idxArray, requiredIndices.size()));

        ctx.cp.post(Factory.atmost(ctx.word_index, ctx.pad_token, Math.max(maxWords - minWords - 1, 0)));

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
        return Pair.of(minWords, maxWords);
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
