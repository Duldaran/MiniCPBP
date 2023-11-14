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

import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.SearchStatistics;
import minicpbp.util.ExamplesMarginalsSingleton;
import minicpbp.util.LatinSquareSingleton;

import static minicpbp.cp.Factory.*;
import static minicpbp.cp.BranchingScheme.*;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * A rostering-inspired problem: workload constraints on rows and shift coverage on columns.
 */
public class KPRostering {

	public static void main(String[] args) {

		LatinSquareSingleton ls = LatinSquareSingleton.getInstance();
		int nbEmpl = Integer.parseInt(args[0]);
		int nbDays = Integer.parseInt(args[1]);
		int nbFile = Integer.parseInt(args[2]);
		int nbIter= Integer.parseInt(args[3]);

		ArrayList<double[]> itersKL= new ArrayList<double[]>();

		Solver cp = makeSolver();

        IntVar[][] x = new IntVar[nbEmpl][nbDays];
		for (int i = 0; i < nbEmpl; i++) {
			for (int j = 0; j < nbDays; j++) {
				x[i][j] = makeIntVar(cp, new HashSet<>(Arrays.asList(0,2,3,5)));
				x[i][j].setName("x["+i+","+j+"]");
			}
		}

		int[][] A = new int[nbEmpl][nbDays];
		int[] b = new int[nbEmpl];

		readInstance(x,A,b,nbEmpl,nbDays,nbFile);

		for(int i = 0; i < nbEmpl; i++) {
			// constraint on row i
			cp.post(sum(A[i], x[i], b[i]));
		}
		for(int j = 0; j < nbDays; j++) {
			// constraint on column j
			IntVar[] column = new IntVar[nbEmpl];
			for (int i = 0; i < nbEmpl; i++) {
				column[i] = x[i][j];
			}
			cp.post(allDifferent(column));
		}

        IntVar[] xFlat = new IntVar[nbEmpl * nbDays];
        for (int i = 0; i < nbEmpl; i++) {
            System.arraycopy(x[i], 0, xFlat, i * nbDays, nbDays);
        }

		// enumerate all solutions in order to compute exact marginals

		DFSearch dfs = makeDfs(cp, minEntropy(xFlat));
		ls.initializeSols(nbEmpl,nbDays);

        dfs.onSolution(() -> {
					int [][] sol = new int[nbEmpl][nbDays];
                    for (int i = 0; i < nbEmpl; i++) {
						for (int j = 0; j < nbDays; j++) {
							System.out.print(x[i][j].min() + " ");
							sol[i][j]=x[i][j].min();
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

		// set each constraint's weight according to some attention criterion
		/*Iterator<Constraint> iterator = cp.getConstraints().iterator();
		while (iterator.hasNext()) {
			Constraint c = iterator.next();
			c.setWeight(0.95+1.0/( 1.0 + (double) c.dynamicArity()));
		}*/

		cp.setTraceBPFlag(false);
		ls.initializeBP(nbIter);
		cp.vanillaBP(nbIter, ls, 0);
		//		*/

		//em.printBPMarginals();
		//em.printTrueMarginals();
		itersKL.add(ls.calculateItersKL(false));


		System.out.println("KL moyens:");
		ls.printKLinCSV(itersKL);
		System.out.println();

}

    public static void readInstance(IntVar[][] vars, int[][] A, int[] b, int nbEmpl, int nbDays, int nbFile){

		try {
	    	Scanner scanner = new Scanner(new FileReader("./src/main/java/minicpbp/examples/data/KPRostering/roster-"+nbEmpl+"-"+nbDays+"-"+nbFile+".in"));

			int nbForbiddenShifts = scanner.nextInt();
			for (int i = 0; i < nbForbiddenShifts; i++) {
				vars[scanner.nextInt()][scanner.nextInt()].remove(scanner.nextInt());
			}

	    	for (int i = 0; i < nbEmpl; i++) {
				for (int j = 0; j < nbDays; j++) {
		    		A[i][j] = scanner.nextInt();
				}
				b[i] = scanner.nextInt();
	    	}
	    	scanner.close();
		}
		catch (IOException e) {
	    	System.err.println("Error : " + e.getMessage());
	    	System.exit(2);
		}
	}
}
