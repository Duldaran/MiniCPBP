package minicpbp.examples.config;

import minicpbp.cp.Factory;
import minicpbp.engine.core.*;

public class CollieSent1Config implements ConstraintBuilder {

    @Override
    public String getInstruction() {
        return "Please generate a sentence with exactly 82 characters. Include whitespace into your character count.";
    }

    @Override
    public void build(SolverContext ctx) {
        final int NUMBER_CHAR = 82-1;//Verify if you need to count the spaces at the beginning of line + No period at the end
        // create num_char array using the solver from the provided context and the word_index length
        IntVar[] num_char = Factory.makeIntVarArray(ctx.cp, ctx.word_index.length,
                java.util.Arrays.stream(ctx.charNum).min().getAsInt(),
                java.util.Arrays.stream(ctx.charNum).max().getAsInt());

        for (int j = 0; j < ctx.word_index.length; j++) {
            ctx.word_index[j].setName("word_index[" + j + "]");
            ctx.cp.post(Factory.element(ctx.charNum, ctx.word_index[j], num_char[j]));
        }

        // use the sum of the provided lengthTokens as the target total number of chars (adjust if you have a dedicated IntVar)
        ctx.cp.post(Factory.sum(num_char, NUMBER_CHAR));

        java.util.List<Integer> acceptedState = new java.util.ArrayList<>();
        int[][] A = new int[2][ctx.corpusDomains_size];
        acceptedState.add(1);
        acceptedState.add(0);
        java.util.Arrays.fill(A[0], 0);
        A[0][ctx.end_sentence] = 1;
        java.util.Arrays.fill(A[1], -1);
        A[1][ctx.pad_token] = 1;
        ctx.cp.post(Factory.regular(ctx.word_index, A, 0, acceptedState));
    }
    
}
