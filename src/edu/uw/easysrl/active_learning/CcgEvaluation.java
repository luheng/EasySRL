package edu.uw.easysrl.active_learning;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Evaluating with labeled CCG dependency.
 * TODO: make sure the numbers are directly comparable.
 * Created by luheng on 1/5/16.
 */
public class CcgEvaluation {

    public static Results evaluate(final Set<ResolvedDependency> predicted, final Set<ResolvedDependency> gold) {
        int numMatched = (int) predicted.stream().filter(dep -> matchesAny(dep, gold)).count();
        return new Results(predicted.size(), numMatched, gold.size());
    }

    public static Accuracy evaluateTags(final List<Category> predicted, final List<Category> gold) {
        Accuracy accuracy = new Accuracy();
        IntStream.range(0, predicted.size()).forEach(i -> accuracy.add(predicted.get(i) == gold.get(i)));
        return accuracy;
    }

    public static boolean matchesAny(final ResolvedDependency target, final Set<ResolvedDependency> dependencies) {
        for (ResolvedDependency dep : dependencies) {
            if (target.getHead() == dep.getHead() && target.getArgument() == dep.getArgument() &&
                    labelMatch(target, dep)) {
                return true;
            }
        }
        return false;
    }

    private static boolean labelMatch(final ResolvedDependency dep1, final ResolvedDependency dep2) {
        return dep1.getCategory().equals(dep2.getCategory()) && dep1.getArgNumber() == dep2.getArgNumber();
    }

    private static String getLabel(final ResolvedDependency dependency) {
        return dependency.getCategory() + "." + dependency.getArgNumber();
    }
}
