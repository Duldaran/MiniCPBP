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

package minicpbp.engine.core;

import minicpbp.state.StateStack;
import minicpbp.util.Marginal;
import minicpbp.util.Procedure;
import minicpbp.util.exception.InconsistencyException;
import minicpbp.util.Belief;

import java.security.InvalidParameterException;
import java.util.*;

/**
 * Implementation of a variable
 * with a {@link SparseSetDomain}.
 */
public class IntVarImpl implements IntVar {

    private String name;
    private Solver cp;
    private Belief beliefRep;
    private IntDomain domain;
    private StateStack<Constraint> onDomain;
    private StateStack<Constraint> onBind;
    private StateStack<Constraint> onBounds;
    private StateStack<Constraint> constraints; //contains all constraints, allows us to get the failure count of all constraints applied to the variable
    private boolean isForBranching = false;
    private HashMap<Integer, Double> secondMax = new HashMap<Integer, Double>();
    private HashMap<Integer, Double> secondMin= new HashMap<Integer, Double>();

    private DomainListener domListener = new DomainListener() {
        @Override
        public void empty() {
            throw InconsistencyException.INCONSISTENCY; // Integer Vars cannot be empty
        }

        @Override
        public void bind() {
            scheduleAll(onBind);
        }

        @Override
        public void change() {
            scheduleAll(onDomain);
        }

        @Override
        public void changeMin() {
            scheduleAll(onBounds);
        }

        @Override
        public void changeMax() {
            scheduleAll(onBounds);
        }
    };

    /**
     * Creates a variable with the elements {@code {0,...,n-1}}
     * as initial domain.
     *
     * @param cp the solver in which the variable is created
     * @param n  the number of values with {@code n > 0}
     */
    public IntVarImpl(Solver cp, int n) {
        this(cp, 0, n - 1);
    }

    /**
     * Creates a variable with the elements {@code {min,...,max}}
     * as initial domain.
     *
     * @param cp  the solver in which the variable is created
     * @param min the minimum value of the domain
     * @param max the maximum value of the domain with {@code max >= min}
     */
    public IntVarImpl(Solver cp, int min, int max) {
        if (min > max) throw new InvalidParameterException("at least one setValue in the domain");
        this.cp = cp;
        beliefRep = cp.getBeliefRep();
        domain = new SparseSetDomain(cp, min, max);
        onDomain = new StateStack<>(cp.getStateManager());
        onBind = new StateStack<>(cp.getStateManager());
        onBounds = new StateStack<>(cp.getStateManager());
        constraints = new StateStack<>(cp.getStateManager());
        cp.registerVar(this);
    }

    /**
     * Creates a variable with a given set of values as initial domain.
     *
     * @param cp     the solver in which the variable is created
     * @param values the initial values in the domain, it must be nonempty
     */
    public IntVarImpl(Solver cp, Set<Integer> values) {
        this(cp, values.stream().min(Integer::compare).get(), values.stream().max(Integer::compare).get());
        if (values.isEmpty()) throw new InvalidParameterException("at least one setValue in the domain");
        for (int i = min(); i <= max(); i++) {
            if (!values.contains(i)) {
                try {
                    this.remove(i);
                } catch (InconsistencyException e) {
                }
            }
        }
    }

    @Override
    public Solver getSolver() {
        return cp;
    }

    @Override
    public boolean isBound() {
        return domain.size() == 1;
    }

    @Override
    public String toString() {
        return domain.toString();
    }

    @Override
    public Marginal toMarginal() {
        System.out.println(getName());
        Marginal margin = new Marginal();
        for (int i = domain.min(); i <= domain.max(); i++) {
            if (domain.contains((i)))
                margin.map.put(i,domain.marginal(i));
        }
        return margin;
    }

    @Override
    public void whenBind(Procedure f) {
        onBind.push(constraintClosure(f));
    }

    @Override
    public void whenBoundsChange(Procedure f) {
        onBounds.push(constraintClosure(f));
    }

    @Override
    public void whenDomainChange(Procedure f) {
        onDomain.push(constraintClosure(f));
    }

    private Constraint constraintClosure(Procedure f) {
        Constraint c = new ConstraintClosure(cp, f);
        getSolver().post(c, false);
        return c;
    }

    @Override
    public void propagateOnDomainChange(Constraint c) {
        onDomain.push(c);
    }

    @Override
    public void propagateOnBind(Constraint c) {
        onBind.push(c);
    }

    @Override
    public void propagateOnBoundChange(Constraint c) {
        onBounds.push(c);
    }

    @Override 
    public void registerConstraint(Constraint c) {
        constraints.push(c);
    }


    protected void scheduleAll(StateStack<Constraint> constraints) {
        Iterator<Constraint> iterator = constraints.iterator();
        while (iterator.hasNext()) {
            cp.schedule(iterator.next());
        }
    }

    @Override
    public int min() {
        return domain.min();
    }

    @Override
    public int max() {
        return domain.max();
    }

    @Override
    public int size() {
        return domain.size();
    }

    @Override
    public int fillArray(int[] dest) {
        return domain.fillArray(dest);
    }

    @Override
    public boolean contains(int v) {
        return domain.contains(v);
    }

    @Override
    public void remove(int v) {
        domain.remove(v, domListener);
    }

    @Override
    public void assign(int v) {
        domain.removeAllBut(v, domListener);
    }

    @Override
    public void removeBelow(int v) {
        domain.removeBelow(v, domListener);
    }

    @Override
    public void removeAbove(int v) {
        domain.removeAbove(v, domListener);
    }

    @Override
    public void removeOutside(Set<Integer> S) {
        for (int i = min(); i <= max(); i++) {
            if (!S.contains(i)) {
                try {
                    this.remove(i);
                } catch (InconsistencyException e) {
                }
            }
        }
    }

    @Override
    public int randomValue() {
        return domain.randomValue();
    }

    @Override
    public int biasedWheelValue() {
        return domain.biasedWheelValue();
    }

    @Override
    public double marginal(int v) {
        return domain.marginal(v);
    }

    @Override
    public void setMarginal(int v, double m) {
        domain.setMarginal(v, m);
    }

    @Override
    public void initializeMarginals() {
        domain.initializeMarginals(); secondMin.clear();secondMax.clear();
    }

    @Override
    public void resetMarginals() {
        domain.resetMarginals(); secondMax.clear(); secondMin.clear();
    }

    @Override
    public double getSecondMin(int v){
        if(secondMin.get(v)==null) return -1.0;
        return secondMin.get(v);
    }

    @Override
    public double getSecondMax(int v){
        if(secondMax.get(v)==null) return -1.0;
        return secondMax.get(v);
    }

    @Override
    public void putSecondMin(int v, double b){
        secondMin.put(v, b);
    }

    @Override
    public void putSecondMax(int v, double b){
        secondMax.put(v, b);
    }

    @Override
    public void normalizeMarginals() {
        domain.normalizeMarginals();
    }

    @Override
    public double maxMarginal() {
        return domain.maxMarginal();
    }

    @Override
    public int valueWithMaxMarginal() {
        return domain.valueWithMaxMarginal();
    }

    @Override
    public double minMarginal() {
        return domain.minMarginal();
    }

    @Override
    public int valueWithMinMarginal() {
        return domain.valueWithMinMarginal();
    }

    @Override
    public double maxMarginalRegret() {
        return domain.maxMarginalRegret();
    }

    @Override
    public double entropy() {
        return domain.entropy();
    }

    @Override
    public double impact() {
        return domain.impact();
    }

    @Override
    public int deg(){
        int sum = 0;
        Iterator<Constraint> iterator = constraints.iterator();
        while (iterator.hasNext())
            if (iterator.next().isActive()) sum++;
        return sum;
    }

    public int wDeg(){
        int sum = 0;
        Iterator<Constraint> iterator = constraints.iterator();
        while (iterator.hasNext()) {
            //in dom/wdeg all constraint's weigth are initialized to 1, that's why there is a +1 for each constraint
            sum += iterator.next().getFailureCount() + 1;
        }
        return sum;
    }

    @Override
    public int valueWithMinImpact() {
        return domain.valueWithMinImpact();
    }

    @Override
    public int valueWithMaxImpact() {
        return domain.valueWithMaxImpact();
    }

    @Override
    public void registerImpact(int value, double impact) {
        domain.registerImpact(value, impact);
    }

    @Override
    public double sendMessage(int v, double b) {
        assert b <= beliefRep.one() && b >= beliefRep.zero() : "b = " + b;
        assert domain.marginal(v) <= beliefRep.one() && domain.marginal(v) >= beliefRep.zero() : "domain.marginal(v) = " + domain.marginal(v);
        assert size() > 0 : "domain size is null";
        if(beliefRep.isZero(b)) return beliefRep.divide(beliefRep.one(), size());
        //return (beliefRep.divide(domain.marginal(v), b));                               //Agg:Produit, 1 dans BP iteration
       // System.out.println(getName()+", "+(domain.marginal(v) == b && secondMax.get(v)!=null? secondMax.get(v) : domain.marginal(v)));
        //return ((domain.marginal(v) == b && secondMax.get(v)!=null)? secondMax.get(v) : domain.marginal(v));     //Agg:Max, 0 dans BP iteration
        return (domain.marginal(v) == b && secondMin.get(v)!=null? secondMin.get(v) : domain.marginal(v));     //Agg:Min, 1 dans BP iteration
        //System.out.println(getName()+" Marginal : "+ domain.marginal(v)+"b : "+b);
        //if(b==beliefRep.one() && domain.marginal(v)==beliefRep.one()) {return domain.marginal(v);}
        //return (beliefRep.subtract(domain.marginal(v), b));                           //Agg:Somme (Moy arithmétique), 0 dans BP iteration
        //return (beliefRep.divide(domain.marginal(v),Math.pow(b, 1.0 / deg())));       //Agg:Moy géométrique, 1 dans BP iteration
    }

    @Override
    public void receiveMessage(int v, double b) {
        assert b <= beliefRep.one() && b >= beliefRep.zero() : "b = " + b;
        assert domain.marginal(v) <= beliefRep.one() && domain.marginal(v) >= beliefRep.zero() : "domain.marginal(v) = " + domain.marginal(v);
        //domain.setMarginal(v, beliefRep.multiply(domain.marginal(v), b));                           //Agg:Produit, 1 dans BP iteration et dans LocalBelief initial
        /*if(secondMax.get(v)==null || b > secondMax.get(v)){                                                                //Agg:Max, 0 dans BP iteration et dans LocalBelief initial
            secondMax.put(v, beliefRep.min(domain.marginal(v), b));
            domain.setMarginal(v, beliefRep.max(domain.marginal(v), b));
            //System.out.println( getName()+", B : " +b+ ", Best : "+domain.marginal(v)+ ", Second : "+secondMax.get(v));
        }*/
        if(secondMin.get(v)==null || b < secondMin.get(v)){                             //Agg:Min, 1 dans BP iteration et dans LocalBelief initial
            secondMin.put(v, beliefRep.max(domain.marginal(v), b));
            domain.setMarginal(v, beliefRep.min(domain.marginal(v), b));
        }
        //domain.setMarginal(v, beliefRep.add(domain.marginal(v), b));                              //Agg:Somme (Moy arithmétique), 0 dans BP iteration et dans LocalBelief initial
        //domain.setMarginal(v, beliefRep.multiply(domain.marginal(v), Math.pow(b, 1.0 / deg())));  //Agg:Moy géométrique, 1 dans BP iteration et dans LocalBelief initial
    }

    @Override
    public void setForBranching(boolean b) {
        this.isForBranching = b;
    }

    @Override
    public boolean isForBranching() {
        return this.isForBranching;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }
}
