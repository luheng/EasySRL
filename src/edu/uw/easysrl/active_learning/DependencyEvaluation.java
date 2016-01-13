package edu.uw.easysrl.active_learning;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.evaluation.Results;


import java.util.Set;

/**
 * Evaluating with labeled CCG dependency.
 * TODO: make sure the numbers are directly comparable.
 * Created by luheng on 1/5/16.
 */
public class DependencyEvaluation {

    public static Results evaluate(final Set<ResolvedDependency> predicted, final Set<ResolvedDependency> gold) {
        int numMatched = (int) predicted.stream().filter(dep -> matchesAny(dep, gold)).count();
        return new Results(predicted.size(), numMatched, gold.size());
    }

    public static boolean matchesAny(ResolvedDependency target, final Set<ResolvedDependency> dependencies) {
        for (ResolvedDependency dep : dependencies) {
            if (target.getHead() == dep.getHead() && target.getArgument() == dep.getArgument() &&
                    labelMatch(target, dep)) {
                return true;
            }
        }
        return false;
    }

    private static boolean labelMatch(ResolvedDependency dep1, ResolvedDependency dep2) {
        return dep1.getCategory().equals(dep2.getCategory()) && dep1.getArgNumber() == dep2.getArgNumber();
    }

    private static String getLabel(ResolvedDependency dependency) {
        return dependency.getCategory() + "." + dependency.getArgNumber();
    }
}
