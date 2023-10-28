package minicpbp.util;

import minicpbp.state.CopyMap;

import java.util.HashMap;
import java.util.Map;

public class Marginal {
    public HashMap<Integer, Double> map = new HashMap<>();

    public void normalizeMarginal(int nbSols) {
        map.replaceAll((key, value) -> value / nbSols);
    }

    public void printMarginal() {
        for (Map.Entry<Integer, Double> entry : map.entrySet()) {
            Integer k = entry.getKey();
            Double v = entry.getValue();
            System.out.print(k + " : " + v + ", ");
        }
    }
}
