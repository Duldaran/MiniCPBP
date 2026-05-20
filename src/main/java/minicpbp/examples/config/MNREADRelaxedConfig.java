package minicpbp.examples.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ibm.icu.impl.Pair;

import minicpbp.cp.Factory;
import minicpbp.engine.core.IntVar;

public class MNREADRelaxedConfig implements ConstraintBuilder {
    final static private int NUMBER_CHAR = 60;
    final static private int LINE_SIZE = 15896;
    final static private int SPACE_SIZE = 512;
    final static private int MIN_SPACE_SIZE = 410;
    final static private int MAX_SPACE_SIZE = 640;
    final static private int MAX_NUMBER_SPACE = 5;
    final static private int MIN_NUMBER_WORD = 9;
    final static private int MAX_NUMBER_WORD = 15;

    private final double lineSizeMargin;

    public MNREADRelaxedConfig(double lineSizeMargin) {
        if (lineSizeMargin < 0.0) {
            throw new IllegalArgumentException("lineSizeMargin must be >= 0.0");
        }
        this.lineSizeMargin = lineSizeMargin;
    }

    public static MNREADRelaxedConfig fromArgs(List<String> args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("MNREADRelaxedConfig expects: <lineSizeMarginRatio> (example: 0.05 for 5%)");
        }
        return new MNREADRelaxedConfig(Double.parseDouble(args.get(0)));
    }

    @Override
    public String getInstruction() {
        return "Please generate a sentence with exactly 60 characters. Include whitespace into your character count.";
    }

    @Override
    public void build(SolverContext ctx) {
        IntVar[] sizes = Factory.makeIntVarArray(
                ctx.cp,
                ctx.word_index.length,
                Arrays.stream(ctx.lengthTokens).min().getAsInt(),
                Arrays.stream(ctx.lengthTokens).max().getAsInt()
        );
        IntVar[] wordIndex = ctx.word_index;
        IntVar[] numChar = Factory.makeIntVarArray(
                ctx.cp,
                sizes.length,
                Arrays.stream(ctx.charNum).min().getAsInt(),
                Arrays.stream(ctx.charNum).max().getAsInt()
        );

        for (int i = 0; i < sizes.length; i++) {
            sizes[i].setName("size[" + i + "]");
            wordIndex[i].setName("word_index[" + i + "]");
            ctx.cp.post(Factory.element(ctx.lengthTokens, wordIndex[i], sizes[i]));
            ctx.cp.post(Factory.element(ctx.charNum, wordIndex[i], numChar[i]));
        }

        ctx.cp.post(Factory.atmost(wordIndex, ctx.pad_token, MAX_NUMBER_WORD - MIN_NUMBER_WORD));
        ctx.cp.post(Factory.sum(numChar, NUMBER_CHAR));

        int nbLines = 3;
        IntVar[] line = Factory.makeIntVarArray(ctx.cp, sizes.length, 0, nbLines - 1);
        for (int i = 0; i < line.length; i++) {
            line[i].setName("line[" + i + "]");
        }

        int baseMin = LINE_SIZE + SPACE_SIZE - MAX_NUMBER_SPACE * (MAX_SPACE_SIZE - SPACE_SIZE);
        int baseMax = LINE_SIZE + SPACE_SIZE + MAX_NUMBER_SPACE * (SPACE_SIZE - MIN_SPACE_SIZE);
        int minMargin = (int) Math.round(baseMin * lineSizeMargin);
        int maxMargin = (int) Math.round(baseMax * lineSizeMargin);
        IntVar[] lineSize = Factory.makeIntVarArray(
            ctx.cp,
            nbLines,
            baseMin - minMargin,
            baseMax + maxMargin
        );
        for (int i = 0; i < lineSize.length; i++) {
            lineSize[i].setName("lineSize[" + i + "]");
        }

        line[0].assign(0);
        line[line.length - 1].assign(nbLines - 1);

        for (int i = 0; i < line.length - 1; i++) {
            ctx.cp.post(Factory.lessOrEqual(line[i], line[i + 1]));
            ctx.cp.post(Factory.lessOrEqual(line[i + 1], Factory.plus(line[i], 1)));
            ctx.cp.post(Factory.notEqual(wordIndex[i], wordIndex[i + 1]));
        }

        ctx.cp.post(Factory.binPacking(line, sizes, lineSize));
        ctx.cp.post(Factory.atmost(ctx.word_index, ctx.pad_token, MAX_NUMBER_WORD - MIN_NUMBER_WORD - 1));

        List<Integer> acceptedState = new ArrayList<>();
        int[][] a = new int[2][ctx.corpusDomains_size];
        acceptedState.add(1);
        Arrays.fill(a[0], 0);
        a[0][ctx.end_sentence] = 1;
        Arrays.fill(a[1], -1);
        a[1][ctx.pad_token] = 1;
        ctx.cp.post(Factory.regular(wordIndex, a, 0, acceptedState));
    }

    @Override
    public Pair<Integer, Integer> getWordCountRange() {
        return Pair.of(MIN_NUMBER_WORD, MAX_NUMBER_WORD);
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
