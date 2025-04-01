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
 * Copyright (c)  2018. by Laurent Michel, Pierre Schaus, Pascal Van Hentenryck
 *
 * mini-cpbp, replacing classic propagation by belief propagation
 * Copyright (c)  2019. by Gilles Pesant
 */

package minicpbp.examples;

import minicpbp.engine.constraints.Circuit;
import minicpbp.engine.constraints.Element1D;
import minicpbp.engine.constraints.LessOrEqual;
import minicpbp.engine.constraints.Markov;
import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.LDSearch;
import minicpbp.search.Objective;
import minicpbp.util.io.InputReader;
import minicpbp.search.SearchStatistics;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

public class MNREAD {
    public static void main(String[] args) {

        Solver cp = makeSolver();
//        int[] sizes = new int[] {1,3,2,1,3,2,2,2,1,2};
        IntVar[] sizes = makeIntVarArray(cp, 10, 1, 3 );
        for (int i=0; i<sizes.length; i++)
            sizes[i].setName("size["+i+"]");
        sizes[0].remove(2); sizes[0].remove(3);
        sizes[1].remove(1);
        sizes[3].remove(2); sizes[3].remove(3);
        sizes[5].remove(3);
        sizes[6].remove(2);
        sizes[7].remove(1); sizes[7].remove(2);
        sizes[8].remove(1);
        int nbLines = 3, lineMin = 7, lineMax = 8;
        IntVar[] line = makeIntVarArray(cp, sizes.length, nbLines);
        for (int i=0; i<line.length; i++)
            line[i].setName("line["+i+"]");
        IntVar[] lineSize = makeIntVarArray(cp, nbLines, lineMin, lineMax);
        for (int i=0; i<lineSize.length; i++)
            lineSize[i].setName("lineSize["+i+"]");
        line[0].assign(0);
        line[line.length-1].assign(nbLines-1);
        for (int i=0; i<line.length-1; i++) {
            cp.post(lessOrEqual(line[i], line[i + 1]));
            cp.post(lessOrEqual(line[i + 1],plus(line[i],1)));
        }
        cp.post(binPacking(line,sizes,lineSize));

//        cp.setTraceSearchFlag(true);
//        cp.setTraceBPFlag(true);

        IntVar[] vars = Arrays.copyOf(line, 2*line.length);
        for (int i = 0; i < sizes.length; i++) {
            vars[line.length + i] = sizes[i];
        }
        DFSearch dfs = makeDfs(cp, minEntropy(vars));

        dfs.onSolution(() -> {
            for (int i = 0; i < line.length; i++) {
                System.out.print(line[i].min()+" ");
            }
            System.out.print("\t");
            for (int i = 0; i < lineSize.length; i++) {
                System.out.print(lineSize[i].min()+" ");
            }
            System.out.println();
            for (int i = 0; i < line.length; i++) {
                System.out.print(sizes[i].min()+" ");
            }
            System.out.println();
        });

        //############# search ################
        SearchStatistics stats = dfs.solve();
        System.out.println(stats);

    }
}
