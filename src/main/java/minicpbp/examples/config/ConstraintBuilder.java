package minicpbp.examples.config;


public interface ConstraintBuilder {
    void build(SolverContext ctx);
    String getInstruction();
}



