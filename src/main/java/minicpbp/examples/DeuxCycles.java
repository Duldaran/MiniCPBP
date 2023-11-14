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

//mvn exec:java -Dexec.mainClass="minicpbp.examples.DeuxCycles" -Dexec.args="10 10 10"

package minicpbp.examples;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.SearchStatistics;
import minicpbp.util.ArrayUtil;
import minicpbp.util.ExamplesMarginalsSingleton;

import static minicpbp.cp.Factory.*;
import static minicpbp.cp.BranchingScheme.*;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class DeuxCycles {

    private static int n;

    public static void main(String[] args) {

		// lengths expressed as the number of variables involved
		int cycle1 = Integer.parseInt(args[0]);
		int cycle2 = Integer.parseInt(args[1]);

		int nbIter = Integer.parseInt(args[2]);

		ExamplesMarginalsSingleton em = ExamplesMarginalsSingleton.getInstance();
		ArrayList<double[]> itersKL= new ArrayList<double[]>();

		for(int cycle1Length=2; cycle1Length<=cycle1; cycle1Length++ ){
			for(int cycle2Length=2; cycle2Length<=cycle2; cycle2Length++ ){

				Solver cp = makeSolver();
				IntVar[] x = new IntVar[cycle1Length+cycle2Length-1]; // one shared variable

				for (int j = 1; j < cycle1Length; j++) {
					x[j] = makeIntVar(cp, 1, 3);
					x[j].setName("x1["+j+"]");
				}
				for (int j = 0; j < cycle2Length-1; j++) {
					x[cycle1Length+j] = makeIntVar(cp, 1, 3);
					x[cycle1Length+j].setName("x2["+(j+1)+"]");
				}
				x[0] = makeIntVar(cp, 1, 3);
				x[0].setName("xShared");


				IntVar[] cycle1Vars = new IntVar[cycle1Length];
				IntVar[] cycle2Vars = new IntVar[cycle2Length];
				System.arraycopy(x,0,cycle1Vars,1,cycle1Length-1);
				cycle1Vars[0] = x[0];
				System.arraycopy(x,cycle1Length,cycle2Vars,1,cycle2Length-1);
				cycle2Vars[0] = x[0];

				int[][] negTable1 = new int[][]{{1,2},{3,1}};
				for (int i = 0; i < cycle1Length; i++) {
					cp.post(negTable(new IntVar[]{cycle1Vars[i],cycle1Vars[(i+1)%cycle1Length]},negTable1));
				}
				int[][] negTable2 = new int[][]{{1,3}};
				for (int i = 0; i < cycle2Length; i++) {
					cp.post(negTable(new IntVar[]{cycle2Vars[i],cycle2Vars[(i+1)%cycle2Length]},negTable2));
				}

				// enumerate all solutions in order to compute exact marginals
		//		/*
				DFSearch dfs = makeDfs(cp, minEntropy(x));
				em.initializeSols(cycle1Length+cycle2Length-1);

				dfs.onSolution(() -> {
					int [] sol = new int[x.length];
					for (int i = 0; i < x.length; i++) {
						System.out.print(x[i].min() + " ");
						sol[i]=x[i].min();
					}
					em.addSol(sol);
					System.out.println("\n-------------------");
				}
				);

				SearchStatistics stats = dfs.solve();
				em.normalizeSols(stats.numberOfSolutions());
				System.out.println(stats);
		//		 */

				// perform k iterations of message-passing and trace the resulting marginals
		//		/*
				cp.fixPoint(); // initial constraint propagation
				cp.setTraceBPFlag(false);
				em.initializeBP(nbIter);
				cp.vanillaBP(nbIter, em, cycle1Length);
		//		*/
				System.out.println("1 : "+cycle1Length+" , 2 : "+cycle2Length);

				//em.printBPMarginals();
				//em.printTrueMarginals();
				itersKL.add(em.calculateItersKL(false));
			}

		}
		System.out.println("KL moyens:");
		em.printKLinCSV(itersKL);
		System.out.println();
    }
}
