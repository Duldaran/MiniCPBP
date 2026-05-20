package minicpbp.examples.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ibm.icu.impl.Pair;

import minicpbp.cp.Factory;
import minicpbp.engine.core.IntVar;

public class CustomCollieSent3Config implements ConstraintBuilder {

    private final int minWords;
    private final int maxWords;
    private final int maxWordCharLength;

    public CustomCollieSent3Config(int minWords, int maxWordCharLength, int maxWords) {
        if (minWords <= 0) {
            throw new IllegalArgumentException("minWords must be > 0");
        }
        if (maxWords < minWords) {
            throw new IllegalArgumentException("maxWords must be >= minWords");
        }
        if (maxWordCharLength < 0) {
            throw new IllegalArgumentException("maxWordCharLength must be >= 0");
        }
        this.minWords = minWords;
        this.maxWords = maxWords;
        this.maxWordCharLength = maxWordCharLength;
    }

    public static CustomCollieSent3Config fromArgs(List<String> args) {
        if (args.size() < 2) {
            throw new IllegalArgumentException("CustomCollieSent3Config expects: <minWords> <maxWordCharLength> [maxWords]");
        }
        int minWords = Integer.parseInt(args.get(0));
        int maxWordCharLength = Integer.parseInt(args.get(1));
        int maxWords = args.size() > 2 ? Integer.parseInt(args.get(2)) : minWords + 10;
        return new CustomCollieSent3Config(minWords, maxWordCharLength, maxWords);
    }

    @Override
    public String getInstruction() {
        return "Generate a sentence with at least " + minWords + " words, and each word at most "
                + maxWordCharLength + " characters long.";
    }

    @Override
    public void build(SolverContext ctx) {
        IntVar[] num_char = Factory.makeIntVarArray(ctx.cp, ctx.word_index.length,
                java.util.Arrays.stream(ctx.charNum).min().getAsInt(),
                maxWordCharLength);


        for (int j = 0; j < ctx.word_index.length; j++) {
            ctx.word_index[j].setName("word_index[" + j + "]");
            ctx.cp.post(Factory.element(ctx.charNum, ctx.word_index[j], num_char[j]));
        }

        // use the sum of the provided lengthTokens as the target total number of chars (adjust if you have a dedicated IntVar)

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
