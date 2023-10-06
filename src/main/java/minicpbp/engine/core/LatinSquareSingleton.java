package minicpbp.engine.core;

import java.util.HashMap;

public final class LatinSquareSingleton {
    private static LatinSquareSingleton INSTANCE;

    private LatinSquareSingleton() {
    }

    public static LatinSquareSingleton getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new LatinSquareSingleton();
        }

        return INSTANCE;
    }



    private static Marginal[][] sols;

    public static void initializeSols(int order) {
        sols = new Marginal[order][order];
        for (int i = 0; i < sols.length; i++) {
            for (int j = 0; j < sols[i].length; j++) {
                sols[i][j]= new Marginal();
            }
        }
    }

    public static void addSol(int[][] sol) {
        for (int i = 0; i < sol.length; i++) {
            for (int j = 0; j < sol[i].length; j++) {
                int value = sol[i][j];
                sols[i][j].map.put(value, sols[i][j].map.getOrDefault(value, 0f) + 1);
            }
        }
    }

    public  static void normalizeSols(int nbSols){
        for (int i = 0; i < sols.length; i++) {
            for (int j = 0; j < sols[i].length; j++) {
                sols[i][j].normalizeMarginal(nbSols);
            }
        }
    }

    public static void printTrueMarginals(){
        System.out.println("-------------------");
        for (int i = 0; i < sols.length; i++) {
            for (int j = 0; j < sols[i].length; j++) {
                System.out.println(i+" "+j +" : "+sols[i][j].map.toString());
            }
        }
        System.out.println("-------------------");
    }

}

class Marginal {
    HashMap<Integer,Float> map = new HashMap<Integer,Float>();

    public void normalizeMarginal(int nbSols){
        map.replaceAll((key,value)-> value/nbSols);
    }
}
