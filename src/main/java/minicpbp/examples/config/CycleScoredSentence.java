package minicpbp.examples.config;

public static class CycleScoredSentence {
    String sentence;
    double score;
    long time;
    int cycle;

    public CycleScoredSentence(String sentence, double score, long time, int cycle) {
        this.sentence = sentence;
        this.score = score;
        this.time = time;
        this.cycle = cycle;
    }
}
    
