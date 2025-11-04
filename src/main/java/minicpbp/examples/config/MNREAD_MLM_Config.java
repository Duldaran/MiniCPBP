package minicpbp.examples.config;

import com.ibm.icu.impl.Pair;

import minicpbp.cp.Factory;

public class MNREAD_MLM_Config implements ConstraintBuilder {
    final static private int NUMBER_CHAR = 60; // Verify if you need to count the spaces at the beginning of line + No period at the end
    final static private int LINE_SIZE = 15896;
    final static private int SPACE_SIZE =512;
    final static private int MIN_SPACE_SIZE =410;
    final static private int MAX_SPACE_SIZE =640;
    final static private int MAX_NUMBER_SPACE = 5;
    final static private int MIN_NUMBER_WORD = 9;
    final static private int MAX_NUMBER_WORD = 15;

    @Override
    public String getInstruction() {
        return "Please generate a sentence with exactly 60 characters. Include whitespace into your character count.";
    }

    @Override
    public void build(SolverContext ctx) {
       

        // sizes, word_index, has_space, num_char
        minicpbp.engine.core.IntVar[] sizes = minicpbp.cp.Factory.makeIntVarArray(
                ctx.cp,
                ctx.word_index.length,
                java.util.Arrays.stream(ctx.lengthTokens).min().getAsInt(),
                java.util.Arrays.stream(ctx.lengthTokens).max().getAsInt()
        );
        minicpbp.engine.core.IntVar[] word_index = ctx.word_index;
        minicpbp.engine.core.IntVar[] num_char = minicpbp.cp.Factory.makeIntVarArray(
                ctx.cp,
                sizes.length,
                java.util.Arrays.stream(ctx.charNum).min().getAsInt(),
                java.util.Arrays.stream(ctx.charNum).max().getAsInt()
        );

        for (int i = 0; i < sizes.length; i++) {
            sizes[i].setName("size[" + i + "]");
            word_index[i].setName("word_index[" + i + "]");
            ctx.cp.post(minicpbp.cp.Factory.element(ctx.lengthTokens, word_index[i], sizes[i]));
            ctx.cp.post(minicpbp.cp.Factory.element(ctx.charNum, word_index[i], num_char[i]));
        }


        // total characters constraint
        ctx.cp.post(minicpbp.cp.Factory.sum(num_char, NUMBER_CHAR));

        // lines and packing
        int nbLines = 3;
        minicpbp.engine.core.IntVar[] line = minicpbp.cp.Factory.makeIntVarArray(ctx.cp, sizes.length, 0, nbLines - 1);
        for (int i = 0; i < line.length; i++)
            line[i].setName("line[" + i + "]");

        minicpbp.engine.core.IntVar[] lineSize = minicpbp.cp.Factory.makeIntVarArray(
                        ctx.cp,
                        nbLines,
                        LINE_SIZE + SPACE_SIZE - MAX_NUMBER_SPACE * (MAX_SPACE_SIZE - SPACE_SIZE),
                        LINE_SIZE + SPACE_SIZE + MAX_NUMBER_SPACE * (SPACE_SIZE - MIN_SPACE_SIZE)
                );
        for (int i = 0; i < lineSize.length; i++)
            lineSize[i].setName("lineSize[" + i + "]");

        line[0].assign(0);
        line[line.length - 1].assign(nbLines - 1);

        for (int i = 0; i < line.length - 1; i++) {
            ctx.cp.post(minicpbp.cp.Factory.lessOrEqual(line[i], line[i + 1]));
            ctx.cp.post(minicpbp.cp.Factory.lessOrEqual(line[i + 1], minicpbp.cp.Factory.plus(line[i], 1)));
            ctx.cp.post(minicpbp.cp.Factory.notEqual(word_index[i], word_index[i + 1]));
        }

        ctx.cp.post(minicpbp.cp.Factory.binPacking(line, sizes, lineSize));


    }

    @Override
    public Pair<Integer, Integer> getWordCountRange() {
        return Pair.of(MIN_NUMBER_WORD, MAX_NUMBER_WORD);
    }
    
}
