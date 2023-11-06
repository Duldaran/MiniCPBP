/*
 * mini-cp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License  v3
 * as published by the Free Software Foundation.
 *
 * mini-cp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY.
 * See the GNU Lesser General Public License  for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with mini-cp. If not, see http://www.gnu.org/licenses/lgpl-3.0.en.html
 *
 * Copyright (c)  2017. by Laurent Michel, Pierre Schaus, Pascal Van Hentenryck
 *
 * mini-cpbp, replacing classic propagation by belief propagation
 * Copyright (c)  2019. by Gilles Pesant
 */

// Example command line
//mvn exec:java -Dexec.mainClass="minicpbp.examples.LatinSquare" -Dexec.args="10 50 10 10"


package minicpbp.examples;

import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.IntVar;
import minicpbp.util.ArrayUtil;
import minicpbp.util.LatinSquareSingleton;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.SearchStatistics;
import static minicpbp.cp.Factory.*;
import static minicpbp.cp.BranchingScheme.*;

import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.Scanner;

/**
 * The Partially-Filled Latin Square problem.
 */
public class LatinSquare {

	public static void main(String[] args) {

		int order = Integer.parseInt(args[0]);
		int nbFilled = Integer.parseInt(args[1]);
		int nbOfFiles = Integer.parseInt(args[2]);
		int nbIter = Integer.parseInt(args[3]);

		Solver cp = makeSolver();
		double[] itersKL= new double[nbIter];
		LatinSquareSingleton ls = LatinSquareSingleton.getInstance();

		for(int fileNum=1; fileNum<=nbOfFiles; fileNum++ ){

			IntVar[][] x = new IntVar[order][order];

			for (int i = 0; i < order; i++) {
				for (int j = 0; j < order; j++) {
					x[i][j] = makeIntVar(cp, 0, order-1);
					x[i][j].setName("x["+i+","+j+"]");
				}
			}

			partialAssignments(x,order,nbFilled,fileNum);

			for(int i = 0; i < order; i++) {
				// constraint on row i
				cp.post(allDifferent(x[i]));
				// constraint on column i
				IntVar[] column = new IntVar[order];
				for (int j = 0; j < order; j++) {
					column[j] = x[j][i];
				}
				cp.post(allDifferent(column));
			}

			IntVar[] xFlat = new IntVar[x.length * x.length];
			for (int i = 0; i < x.length; i++) {
				System.arraycopy(x[i], 0, xFlat, i * x.length, x.length);
			}

			// enumerate all solutions in order to compute exact marginals
	//		/*

			ls.initializeSols(order);
			DFSearch dfs = makeDfs(cp, minEntropy(xFlat));


			dfs.onSolution(() -> {
						int [][] sol = new int[order][order];
						for (int i = 0; i < order; i++) {
							for (int j = 0; j < order; j++) {
								int value = x[i][j].min();
								sol[i][j]=value;
								//System.out.print(value + " ");
							}
							//System.out.println();
						}
						ls.addSol(sol);
						//System.out.println("-------------------");
				}
			);

			SearchStatistics stats = dfs.solve();
			ls.normalizeSols(stats.numberOfSolutions());
			//System.out.println(stats);

	//		 */

			// perform k iterations of message-passing and trace the resulting marginals
	//		/*
			cp.fixPoint(); // initial constraint propagation

			// set each constraint's weight according to some attention criterion
			Iterator<Constraint> iterator = cp.getConstraints().iterator();
			while (iterator.hasNext()) {
				Constraint c = iterator.next();
				c.setWeight(0.5+1.0/( 1.0 + (double) c.dynamicArity()));
			}

			cp.setTraceBPFlag(false);
			ls.initializeBP(nbIter);
			cp.vanillaBP(nbIter, ls);
	//		*/

			itersKL = ArrayUtil.addByElement(ls.calculateItersKL(false), itersKL);
			//ls.printBPMarginals();
			//ls.printTrueMarginals();

		}

		itersKL=ArrayUtil.divideByElement(itersKL, nbOfFiles);
		System.out.println("KL moyens:");
		ls.printKLinCSV(itersKL);
		System.out.println();
	}



    public static void partialAssignments(IntVar[][] vars, int order, int nbFilled, int nbFile){

		try {
	    	Scanner scanner = new Scanner(new FileReader("./src/main/java/minicpbp/examples/data/LatinSquare10x10/qwh.o"+order+".h"+nbFilled+"."+nbFile+".pls"));
 	    	scanner.next("order");
	    	scanner.nextInt(); // order
		
	    	for (int i = 0; i < order; i++) {
				for (int j = 0; j < order; j++) {
		    		int v = scanner.nextInt();
		    		if (v != -1) {
						vars[i][j].assign(v);
					}
				}
	    	}
	    	scanner.close();
		}
		catch (IOException e) {
	    	System.err.println("Error : " + e.getMessage());
	    	System.exit(2);
		}
	}
}
