package minicpbp.examples.config;
import java.util.List;
import com.ibm.icu.impl.Pair;
import java.net.http.HttpClient;
import java.util.ArrayList;

public interface SentenceBuilder {
    String buildSentence(ArrayList<ScoredSentence> bases, HttpClient client, int port, double mask_percent, ArrayList<Integer> bannedIndices, int minLength, int maxLength);
    void recordSearchFailure(boolean failed);
    String buildSentenceLight(ScoredSentence base, double mask_percent, int minLength, int maxLength, List<Pair<Integer, Double>> leastToMostProbWords);
    List<Pair<Integer, Double>> buildLeastToMostProbWords(HttpClient client, int port, String sentence, int maxLength);
}
