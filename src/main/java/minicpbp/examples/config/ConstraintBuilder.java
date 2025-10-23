package minicpbp.examples.config;

import com.ibm.icu.impl.Pair;

public interface ConstraintBuilder {
    void build(SolverContext ctx);
    String getInstruction();
    Pair<Integer, Integer> getWordCountRange();
}



