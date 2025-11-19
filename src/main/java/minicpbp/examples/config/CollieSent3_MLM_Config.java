package minicpbp.examples.config;

import com.ibm.icu.impl.Pair;

import minicpbp.cp.Factory;
import minicpbp.engine.core.*;

public class CollieSent3_MLM_Config implements ConstraintBuilder {

    final static private int MAX_WORDS = 30;
    final static private int MIN_WORDS = 20;

    @Override
    public String getInstruction() {
        return "Generate a sentence with at least 20 words, and each word less than six characters.";
    }

    

    @Override
    public void build(SolverContext ctx) {
        // create num_char array using the solver from the provided context and the word_index length
        IntVar[] num_char = Factory.makeIntVarArray(ctx.cp, ctx.word_index.length,
                java.util.Arrays.stream(ctx.charNum).min().getAsInt(),
                6);

        for (int j = 0; j < ctx.word_index.length; j++) {
            ctx.word_index[j].setName("word_index[" + j + "]");
            ctx.cp.post(Factory.element(ctx.charNum, ctx.word_index[j], num_char[j]));
        }

    }



    @Override
    public Pair<Integer, Integer> getWordCountRange() {
        return Pair.of(MIN_WORDS, MAX_WORDS);
    }



    @Override
    public String fileRef() {
        return "src\\main\\java\\minicpbp\\examples\\config\\files_references\\result_CollieSent3Config_v3_1761223994666_sentences.txt";
    }



    @Override
    public Boolean isValid() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isValid'");
    }
    
}
