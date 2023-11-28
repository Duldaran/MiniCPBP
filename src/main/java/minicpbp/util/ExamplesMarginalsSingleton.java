package minicpbp.util;

import minicpbp.examples.DeuxCycles;

import java.util.ArrayList;
import java.util.Map;

public class ExamplesMarginalsSingleton {
    private static ExamplesMarginalsSingleton INSTANCE;

    protected ExamplesMarginalsSingleton() {
    }

    public static ExamplesMarginalsSingleton getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new ExamplesMarginalsSingleton();
        }

        return INSTANCE;
    }


    private static Marginal[] sols;

    public static void initializeSols(int nbVariables) {
        sols = new Marginal[nbVariables];
        for (int i = 0; i < sols.length; i++) {
            sols[i]= new Marginal();
        }
    }


    public static void addSol(int[] sol) {
        for (int i = 0; i < sol.length; i++) {
            sols[i].map.put(sol[i], sols[i].map.getOrDefault(sol[i], 0.0) + 1);
        }
    }

    public  static void normalizeSols(int nbSols){
        for (int i = 0; i < sols.length; i++) {
            sols[i].normalizeMarginal(nbSols);
        }
    }

    public static void printTrueMarginals(){
        System.out.println("-------------------");
        for (int i = 0; i < sols.length; i++) {
            System.out.println(i+" : "+sols[i].map.toString());
        }
        System.out.println("-------------------");
    }

    private static ArrayList<Marginal[]> BPsols;

    public static void initializeBP(int nbIters){
        BPsols = new ArrayList();
        for(int i =0; i<nbIters; i++){
            BPsols.add(new Marginal[sols.length]);
        }
    }


    public static void receiveBP(String name, Marginal margin , int iter){
        if(name == null) return;
        String index = name.replaceAll("[^0-9,]", "");
        if(Integer.parseInt(index)>= sols.length) {System.out.print(index);return;}
        BPsols.get(iter-1)[Integer.parseInt(index)] = normalizeBP(margin);
    }

    public static void receiveBP(String name, Marginal margin , int iter, int cycle1length){
        if(name == null) return;
        String index;
        if(cycle1length!=0){index = getDeuxCyclesIndex(name, cycle1length);}
        else {index = name.replaceAll("[^0-9,]", "");}
        if(Integer.parseInt(index)>= sols.length) {System.out.print(index);return;}
        BPsols.get(iter-1)[Integer.parseInt(index)] = normalizeBP(margin);
    }

    private static Marginal normalizeBP(Marginal margin){
        Double normalizingConstant = 0.0;
        for (Double value : margin.map.values()){
            normalizingConstant+=value;
        }
        final double finalNormalizingConstraint = normalizingConstant;
        margin.map.replaceAll((k,v) -> v/finalNormalizingConstraint);
        return margin;
    }

    public static String getDeuxCyclesIndex(String name, int cycle1length){
        if(name.toUpperCase().contains("SHARED")) return "0";
        String index = name.replaceAll("[^0-9,]", "");
        if(index.charAt(0)=='1') return index.replaceFirst("1", "");
        return String.valueOf(Integer.parseInt(index.replaceFirst("2", ""))+cycle1length-1);
    }


    public static void printBPMarginals(){
        for (int iter = 0; iter< BPsols.size(); iter++){
            System.out.println("BP Iteration "+(iter+1));
            System.out.println("-------------------");
            for (int i = 0; i < sols.length; i++) {
                System.out.println(i+" : "+BPsols.get(iter)[i].map.toString());
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
        Marginal[]bpSol = BPsols.get(iter);
        double iterKL=0.0;
        for(int i=0; i< bpSol.length;i++){
            iterKL+=calculateKL(sols[i], bpSol[i]);
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
