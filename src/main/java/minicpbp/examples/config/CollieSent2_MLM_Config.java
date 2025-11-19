package minicpbp.examples.config;

import static minicpbp.cp.Factory.equal;

import com.ibm.icu.impl.Pair;

import minicpbp.cp.Factory;
import minicpbp.engine.core.*;

public class CollieSent2_MLM_Config implements ConstraintBuilder {

    final static private int MAX_WORDS = 10;
    final static private int MIN_WORDS = 10;

    @Override
    public String getInstruction() {
        return "Generate a sentence with 10 words, where word 3 is “soft” and word 7 is “beach” and word 10 is “water”.";
    }

    

    @Override
    public void build(SolverContext ctx) {
        boolean isBeach = false, isSoft = false, isWater = false;
        int count = 0;
        while ((!isBeach || !isSoft || !isWater) && count < ctx.words.size()) {
            String word = ctx.words.get(count);
            switch (word) {
                case " beach":
                    if (isBeach) throw new RuntimeException("The word 'beach' appears multiple times in the corpus.");
                    isBeach = true;
                    ctx.word_index[6].assign(count);
                    break;
                case " soft":
                    if (isSoft) throw new RuntimeException("The word 'soft' appears multiple times in the corpus.");
                    isSoft = true;
                    ctx.word_index[2].assign(count);
                    break;
                case " water":
                    if (isWater) throw new RuntimeException("The word 'water' appears multiple times in the corpus.");
                    isWater = true;
                    ctx.word_index[9].assign(count);
                    break;
                default:
                    break;
            }
            count++;
        }
        if(!isBeach || !isSoft || !isWater) {
            throw new RuntimeException("Could not find all required words in the corpus.");
        }

    }



    @Override
    public Pair<Integer, Integer> getWordCountRange() {
        return Pair.of(MIN_WORDS, MAX_WORDS);
    }



    @Override
    public String fileRef() {
        return "src\\main\\java\\minicpbp\\examples\\config\\files_references\\result_CollieSent2Config_v3_1761221684360_sentences.txt";
    }



    @Override
    public Boolean isValid() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isValid'");
    }
    
}
