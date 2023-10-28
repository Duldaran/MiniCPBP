package minicpbp.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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

    public static void initializeBP(int nbIters){
        BPsols = new ArrayList();
        for(int i =0; i<nbIters; i++){
            BPsols.add(new Marginal[sols.length][sols[1].length]);
        }
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

    public static float[] calculateItersKL(Boolean print){
        float[] itersKL = new float[BPsols.size()];
        for(int i =0; i< itersKL.length; i++){
            itersKL[i]=calculateIterKL(i);
        }
        if(print) printKL(itersKL);
        return itersKL;
    }

    private static void printKL(float[] itersKL){
        System.out.println("--------------------");
        System.out.println("KL divergence :");
        for (int i =0;i < itersKL.length;i++){
            System.out.println(" Iter "+(i+1)+" : "+itersKL[i]);
        }
    }

    private static float calculateIterKL(int iter){
        Marginal[][] bpSol = BPsols.get(iter);
        float iterKL=0f;
        for(int i=0; i< bpSol.length;i++){
            for (int j=0;j<bpSol[0].length;j++){
                iterKL+=calculateKL(sols[i][j], bpSol[i][j]);
            }
        }
        return iterKL;
    }

    private static float calculateKL(Marginal trueMarginal, Marginal bpMarginal){
        float KLdivergence = 0f;
        for (Map.Entry<Integer, Float> entry : trueMarginal.map.entrySet()) {
            Integer k = entry.getKey();
            Float v = entry.getValue();
            float BPvalue = bpMarginal.map.get(k);
            KLdivergence += v * Math.log(v / BPvalue);
        }
        return KLdivergence;
    }

    public static void printKLinCSV(float[] itersKL){
        for (int i =0;i < itersKL.length;i++){
            System.out.print(itersKL[i]+";");
        }
    }
}

class Marginal {
    HashMap<Integer,Float> map = new HashMap<Integer,Float>();

    public void normalizeMarginal(int nbSols){
        map.replaceAll((key,value)-> value/nbSols);
    }
}
