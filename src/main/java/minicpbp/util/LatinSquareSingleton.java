package minicpbp.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class LatinSquareSingleton extends ExamplesMarginalsSingleton {
    private static LatinSquareSingleton INSTANCE;

    private LatinSquareSingleton() {
        super();
    }

    public static LatinSquareSingleton getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new LatinSquareSingleton();
        }

        return INSTANCE;
    }



    private static Marginal[][] sols;


    public static void initializeSols(int heigth, int width) {
        sols = new Marginal[heigth][width];
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
                sols[i][j].map.put(value, sols[i][j].map.getOrDefault(value, 0.0) + 1);
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

    public static void receiveBP(String name, Marginal margin , int iter){
        if(name == null) return;
        String[] square = name.replaceAll("[^0-9,]", "").split(",");
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

    public static double[] calculateItersKL(Boolean print){
        double[] itersKL = new double[BPsols.size()];
        for(int i =0; i< itersKL.length; i++){
            itersKL[i]=calculateIterKL(i);
        }
        if(print) printKL(itersKL);
        return itersKL;
    }

    private static void printKL(double[] itersKL){
        System.out.println("--------------------");
        System.out.println("KL divergence :");
        for (int i =0;i < itersKL.length;i++){
            System.out.println(" Iter "+(i+1)+" : "+itersKL[i]);
        }
    }

    private static double calculateIterKL(int iter){
        Marginal[][] bpSol = BPsols.get(iter);
        double iterKL=0.0;
        for(int i=0; i< bpSol.length;i++){
            for (int j=0;j<bpSol[0].length;j++){
                iterKL+=calculateKL(sols[i][j], bpSol[i][j]);
            }
        }
        return iterKL/ bpSol.length;
    }

    private static double calculateKL(Marginal trueMarginal, Marginal bpMarginal){
        float KLdivergence = 0f;
        for (Map.Entry<Integer, Double> entry : trueMarginal.map.entrySet()) {
            Integer k = entry.getKey();
            Double v = entry.getValue();
            Double BPvalue = bpMarginal.map.getOrDefault(k, 0.0);
            KLdivergence += v * Math.log(v / BPvalue);
        }
        return KLdivergence;
    }

    public static void printKLinCSV(ArrayList<double[]> itersKL){
        for (int i =0;i < itersKL.size();i++){
            for (int j =0;j < itersKL.get(i).length;j++){
                System.out.print(itersKL.get(i)[j]+";");
            }
            System.out.println();
        }
    }
}

