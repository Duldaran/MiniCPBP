package minicpbp.engine.core;

import java.util.ArrayList;
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

    private static ArrayList<Marginal[][]> BPsols;

    public static void initializeBP(){
        BPsols = new ArrayList();
    }

    public static void iterBP(int iter){
        BPsols.add(iter-1, new Marginal[sols.length][sols[1].length]);
    }

    public static void receiveBP(String name, String values, int iter){
        String[] square = name.replaceAll("[^0-9,]", "").split(",");
        String[] margins = values.replaceAll("[^0-9 .]", "").split(" ");
        Marginal margin = new Marginal();
        for(int i =0; i< margins.length; i=i+3){
            margin.map.put(Integer.parseInt(margins[i]), Float.parseFloat(margins[i+2]));
        }
        BPsols.get(iter-1)[Integer.parseInt(square[0])][Integer.parseInt(square[1])] = margin;
    }

    public static void printBPMarginals(){
        for (int iter = 0; iter< BPsols.size(); iter++){
            System.out.println("BP Iteration "+(iter+1));
            System.out.println("-------------------");
            for (int i = 0; i < sols.length; i++) {
                for (int j = 0; j < sols[i].length; j++) {
                    System.out.println(i+" "+j +" : "+BPsols.get(iter)[i][j].map.toString());
                }
            }
            System.out.println("-------------------");
        }
    }

}

class Marginal {
    HashMap<Integer,Float> map = new HashMap<Integer,Float>();

    public void normalizeMarginal(int nbSols){
        map.replaceAll((key,value)-> value/nbSols);
    }
}
