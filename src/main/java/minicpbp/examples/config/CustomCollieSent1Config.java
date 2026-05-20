package minicpbp.examples.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ibm.icu.impl.Pair;

import minicpbp.cp.Factory;
import minicpbp.engine.core.IntVar;

public class CustomCollieSent1Config implements ConstraintBuilder {

    private final int targetCharacters;
    private final int minWords;
    private final int maxWords;

    public CustomCollieSent1Config(int targetCharacters, int minWords, int maxWords) {
        if (targetCharacters <= 0) {
            throw new IllegalArgumentException("targetCharacters must be > 0");
        }
        if (minWords <= 0 || maxWords < minWords) {
            throw new IllegalArgumentException("Invalid word range: minWords=" + minWords + ", maxWords=" + maxWords);
        }
        this.targetCharacters = targetCharacters;
        this.minWords = minWords;
        this.maxWords = maxWords;
    }

    public static CustomCollieSent1Config fromArgs(List<String> args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("CustomCollieSent1Config expects at least 1 parameter: <targetCharacters> [minWords] [maxWords]");
        }
        int targetChars = Integer.parseInt(args.get(0));
        int minWords = args.size() > 1 ? Integer.parseInt(args.get(1)) : 5;
        int maxWords = args.size() > 2 ? Integer.parseInt(args.get(2)) : 20;
        return new CustomCollieSent1Config(targetChars, minWords, maxWords);
    }

    @Override
    public String getInstruction() {
        return "Please generate a sentence with exactly " + targetCharacters + " characters. Include whitespace into your character count.";
    }

    @Override
    public void build(SolverContext ctx) {
        final int numberChar = targetCharacters;
        IntVar[] numChar = Factory.makeIntVarArray(
                ctx.cp,
                ctx.word_index.length,
                Arrays.stream(ctx.charNum).min().getAsInt(),
                Arrays.stream(ctx.charNum).max().getAsInt()
        );

        for (int j = 0; j < ctx.word_index.length; j++) {
            ctx.word_index[j].setName("word_index[" + j + "]");
            ctx.cp.post(Factory.element(ctx.charNum, ctx.word_index[j], numChar[j]));
        }

        ctx.cp.post(Factory.sum(numChar, numberChar));
        ctx.cp.post(Factory.atmost(ctx.word_index, ctx.pad_token, Math.max(maxWords - minWords - 1, 0)));

        List<Integer> acceptedState = new ArrayList<>();
        int[][] a = new int[2][ctx.corpusDomains_size];
        acceptedState.add(1);
        acceptedState.add(0);
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
