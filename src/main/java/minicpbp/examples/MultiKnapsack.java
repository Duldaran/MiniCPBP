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
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.SearchStatistics;
import static minicpbp.cp.Factory.*;
import static minicpbp.cp.BranchingScheme.*;

import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

public class MultiKnapsack {

    private static int nbConstraints;
    private static int nbVariables;
    private static int[][] coefficients;
    private static int[] rhs;
   
    public static void main(String[] args) {
    
        Solver cp = makeSolver();
		IntVar[] x = makeMultiKnapsack(cp,args[0]);

		// enumerate all solutions in order to compute exact marginals
//		/*
		DFSearch dfs = makeDfs(cp, minEntropy(x));

		dfs.onSolution(() -> {
			for (int i = 0; i < nbVariables; i++) {
				System.out.print(x[i].min() + " ");
			}
			System.out.println("\n-------------------");
		}
		);

		SearchStatistics stats = dfs.solve();
		System.out.println(stats);
//		 */

		// perform k iterations of message-passing and trace the resulting marginals
//		/*
		cp.fixPoint(); // initial constraint propagation
		cp.setTraceBPFlag(true);
		int k = 5;
		cp.vanillaBP(k);
//		*/
    }
    
    public static IntVar[] makeMultiKnapsack(Solver cp, String inFile){
	try {
	    Scanner scanner = new Scanner(new FileReader("./src/main/java/minicpbp/examples/data/MultiKnapsack/"+inFile));
	    
	    nbVariables = scanner.nextInt();
	    nbConstraints = scanner.nextInt();

		IntVar[] x = new IntVar[nbVariables];

		for (int j = 0; j < nbVariables; j++) {
			x[j] = makeIntVar(cp, 0, 1);
			x[j].setName("x["+j+"]");
		}

		coefficients = new int[nbConstraints+1][nbVariables];
	    rhs = new int[nbConstraints+1];

		// read optimal value
		rhs[nbConstraints] = scanner.nextInt();

		// read the objective function coefficients and find min
		int min = Integer.MAX_VALUE;
		for (int j = 0; j < nbVariables; j++) {
			coefficients[nbConstraints][j] = scanner.nextInt();
			if (coefficients[nbConstraints][j] < min) {
				min = coefficients[nbConstraints][j];
			}
		}

		// post the objective function constraint
		IntVar obj = makeIntVar(cp,rhs[nbConstraints]-2*min, rhs[nbConstraints]);
		cp.post(sum(coefficients[nbConstraints],x,obj));

		// Read the rhs
		for (int i = 0; i < nbConstraints; i++) {
			rhs[i] = scanner.nextInt();
		}

		// Read the coefficients
	    for (int i = 0; i < nbConstraints; i++) {
			for (int j = 0; j < nbVariables; j++) {
				coefficients[i][j] = scanner.nextInt();
			}
			// post the constraint
			IntVar ub = makeIntVar(cp,0,rhs[i]);
			cp.post(sum(coefficients[i],x,ub));
	    }
	    scanner.close();
	    return x;
	}
	catch (IOException e) {
	    System.err.println("Error : " + e.getMessage()) ;
	    System.exit(2) ;
	    return new IntVar[0];
	}
    }
}
