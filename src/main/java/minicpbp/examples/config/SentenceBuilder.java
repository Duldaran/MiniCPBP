package minicpbp.examples.config;

import java.net.http.HttpClient;
import java.util.ArrayList;

public interface SentenceBuilder {
    String buildSentence(ArrayList<ScoredSentence> bases, HttpClient client, int port);
}
