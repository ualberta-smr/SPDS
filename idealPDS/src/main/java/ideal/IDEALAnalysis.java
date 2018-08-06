/*******************************************************************************
 * Copyright (c) 2018 Fraunhofer IEM, Paderborn, Germany.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *  
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Johannes Spaeth - initial API and implementation
 *******************************************************************************/
package ideal;

import boomerang.ForwardQuery;
import boomerang.Query;
import boomerang.WeightedForwardQuery;
import boomerang.callgraph.ObservableICFG;
import boomerang.callgraph.ObservableStaticICFG;
import boomerang.results.ForwardBoomerangResults;
import boomerang.seedfactory.SeedFactory;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import typestate.TransitionFunction;
import wpds.impl.Weight;

import java.util.*;

public class IDEALAnalysis<W extends Weight> {

	private static final Logger logger = LogManager.getLogger();

	public static boolean PRINT_OPTIONS = false;

	protected final IDEALAnalysisDefinition<W> analysisDefinition;
	private final SeedFactory<W> seedFactory;
	private int seedCount;
	private Map<WeightedForwardQuery<W>, Stopwatch> analysisTime = new HashMap<>();
	private Set<WeightedForwardQuery<W>> timedoutSeeds = new HashSet<>();
	private ObservableICFG<Unit,SootMethod> dynamicICFG;


	public IDEALAnalysis(final IDEALAnalysisDefinition<W> analysisDefinition) {
		this.analysisDefinition = analysisDefinition;
		this.seedFactory = new SeedFactory<W>(){
            ObservableICFG<Unit, SootMethod> staticICFG = new ObservableStaticICFG(new JimpleBasedInterproceduralCFG());

			@Override
			public ObservableICFG<Unit, SootMethod> icfg() {
				return staticICFG;
			};
			@Override
			protected Collection<WeightedForwardQuery<W>> generate(SootMethod method, Stmt stmt,
					Collection<SootMethod> calledMethods) {
				return analysisDefinition.generate(method, stmt, calledMethods);
			}
		};
	}

	public void run() {
		printOptions();

		Collection<Query> initialSeeds = seedFactory.computeSeeds();

		if (initialSeeds.isEmpty())
			logger.info("No seeds found!");
		else
			logger.info("Analysing " + initialSeeds.size() + " seeds!");
		for (Query s : initialSeeds) {
			if(!(s instanceof WeightedForwardQuery))
				continue;
			WeightedForwardQuery<W> seed = (WeightedForwardQuery<W>) s;
			seedCount++;
			logger.info("Analyzing "+ seed);
			Stopwatch watch = Stopwatch.createStarted();
			analysisTime.put(seed, watch);
			ForwardBoomerangResults<W> res;
			try {
				res = run(seed);
			} catch(IDEALSeedTimeout e){
				res = (ForwardBoomerangResults<W>) e.getLastResults();
				timedoutSeeds.add(seed);
			}
			watch.stop();
			logger.info("Analyzed (finished,timedout): \t (" + (seedCount -timedoutSeeds.size())+ "," + timedoutSeeds.size() + ") of "+ initialSeeds.size() + " seeds! ");
			analysisDefinition.getResultHandler().report(seed,res);
		}
	}

	public ForwardBoomerangResults<W> run(ForwardQuery seed) {
		IDEALSeedSolver<W> idealAnalysis = new IDEALSeedSolver<>(analysisDefinition, seed, seedFactory);
		return idealAnalysis.run();
	}
	private void printOptions() {
		if(PRINT_OPTIONS) {
			System.out.println(analysisDefinition);
		}
	}

	public Collection<Query> computeSeeds() {
		return seedFactory.computeSeeds();
	}

	public Stopwatch getAnalysisTime(WeightedForwardQuery<TransitionFunction> key) {
		return analysisTime.get(key);
	}

	public boolean isTimedout(WeightedForwardQuery<TransitionFunction> key) {
		return timedoutSeeds.contains(key);
	}


}
