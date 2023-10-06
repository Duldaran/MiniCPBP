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

package minicpbp.examples;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.LatinSquareSingleton;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.SearchStatistics;
import static minicpbp.cp.Factory.*;
import static minicpbp.cp.BranchingScheme.*;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Arrays;

/**
 * The Partially-Filled Latin Square problem.
 */
public class LatinSquare {

	public static void main(String[] args) {

		int order = Integer.parseInt(args[0]);
		int nbFilled = Integer.parseInt(args[1]);
		int nbFile = Integer.parseInt(args[2]);

		Solver cp = makeSolver();

        IntVar[][] x = new IntVar[order][order];

        for (int i = 0; i < order; i++) {
            for (int j = 0; j < order; j++) {
                x[i][j] = makeIntVar(cp, 0, order-1);
				x[i][j].setName("x["+i+","+j+"]");
            }
        }

		partialAssignments(x,order,nbFilled,nbFile);

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
		LatinSquareSingleton ls = LatinSquareSingleton.getInstance();
		ls.initializeSols(order);
		DFSearch dfs = makeDfs(cp, minEntropy(xFlat));


        dfs.onSolution(() -> {
					LatinSquareSingleton ls1 = LatinSquareSingleton.getInstance();
        			int [][] sol = new int[order][order];
                    for (int i = 0; i < order; i++) {
						for (int j = 0; j < order; j++) {
							int value = x[i][j].min();
							sol[i][j]=value;
							System.out.print(value + " ");
						}
						System.out.println();
                    }
					ls.addSol(sol);
					System.out.println("-------------------");
			}
        );

   		SearchStatistics stats = dfs.solve();
   		ls.normalizeSols(stats.numberOfSolutions());
        System.out.println(stats);

//		 */

		// perform k iterations of message-passing and trace the resulting marginals
//		/*
		cp.fixPoint(); // initial constraint propagation
		cp.setTraceBPFlag(true); 
		int k = 3;
		cp.vanillaBP(k);
//		*/

		LatinSquareSingleton.printTrueMarginals();
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
