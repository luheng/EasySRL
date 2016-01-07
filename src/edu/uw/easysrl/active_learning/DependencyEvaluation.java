package edu.uw.easysrl.active_learning;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.evaluation.Results;


import java.util.Set;

/**
 * Created by luheng on 1/5/16.
 */
public class DependencyEvaluation {

    public static Results evaluate(final Set<ResolvedDependency> predicted, final Set<ResolvedDependency> gold) {
        int numMatched = (int) predicted.stream().filter(dep -> matchesAnyGoldDependency(dep, gold)).count();
        return new Results(predicted.size(), numMatched, gold.size());
    }

    public static boolean matchesAnyGoldDependency(ResolvedDependency target, final Set<ResolvedDependency> gold) {
        for (ResolvedDependency goldDep : gold) {
            if (target.getHead() == goldDep.getHead() && target.getArgument() == goldDep.getArgument()) {
                return true;
            }
        }
        return false;
    }
}
