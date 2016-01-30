package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;

import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.List;

/**
 * Scoring function
 * Created by luheng on 1/28/16.
 */
public class ScoreFunction {
    TObjectDoubleHashMap<String> labeledDependencyFunction;
    TObjectDoubleHashMap<String> unlabeledDependencyFunction;

    public ScoreFunction(List<Parse> nbestParses) {
        labeledDependencyFunction = new TObjectDoubleHashMap<>();
        unlabeledDependencyFunction = new TObjectDoubleHashMap<>();
        nbestParses.forEach(parse -> parse.dependencies.forEach(dep -> {
            labeledDependencyFunction.put(getLabeledDependencyString(dep), 0.0);
            unlabeledDependencyFunction.put(getUnlabeledDependencyString(dep), 0.0);
        }));
        /*
        System.out.println(String.format(
                "Initialized scoring function with %d labeled dependencies and %d unlabeled dependencies",
                labeledDependencyFunction.size(), unlabeledDependencyFunction.size()));
        */
    }

    public void update(int predIdx, int argIdx, Category category, int argNum, double delta) {
        labeledDependencyFunction.adjustValue(getLabeledDependencyString(predIdx, argIdx, category, argNum), delta);
        unlabeledDependencyFunction.adjustValue(getUnlabeledDependencyString(predIdx, argIdx), delta);
    }

    public double getScore(Parse parse) {
        return parse.dependencies.stream().mapToDouble(dep ->
                labeledDependencyFunction.get(getLabeledDependencyString(dep)) +
                        unlabeledDependencyFunction.get(getUnlabeledDependencyString(dep))).sum();
    }

    private static String getLabeledDependencyString(final ResolvedDependency dependency) {
        return "" + dependency.getHead() + "\t" + dependency.getCategory() + "." + dependency.getArgNumber() + "\t" +
                dependency.getArgument();
    }

    private static String getUnlabeledDependencyString(final ResolvedDependency dependency) {
        return "" + dependency.getHead() + "\t" + dependency.getArgument();
    }

    private static String getLabeledDependencyString(int predIdx, int argIdx, Category category, int argNum) {
        return "" + predIdx + "\t" + category + "." + argNum + "\t" + argIdx;
    }

    private static String getUnlabeledDependencyString(int predIdx, int argIdx) {
        return "" + predIdx + "\t" + argIdx;
    }
}
