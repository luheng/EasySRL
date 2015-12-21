package edu.uw.easysrl.syntax.evaluation;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame;

import java.util.*;

/**
 * Created by luheng on 12/14/15.
 */
public class ActiveLearningEvaluation {
    /**
     * Compute oracle number against gold.
     * @param goldDeps gold dependencies
     * @param predictedDeps predicted dependencies
     * @return P/R/F1 result
     */
    public static Results evaluate(Collection<SRLDependency> goldDeps, Collection<ResolvedDependency> predictedDeps) {
        int predictedCount = 0;
        List<SRLDependency> goldDepsList = new ArrayList<>(goldDeps);
        Set<Integer> matchedGold = new HashSet<>();
        for (final ResolvedDependency predictedDep : predictedDeps) {
            //if (((predictedDep.getSemanticRole() == SRLFrame.NONE)) ||
            if (predictedDep.getOffset() == 0 /* Unrealized arguments */) {
                continue;
            }
            predictedCount++;
            for (int goldDepId = 0; goldDepId < goldDepsList.size(); goldDepId++) {
                if (matchedGold.contains(goldDepId)) {
                    continue;
                }
                SRLDependency goldDep = goldDepsList.get(goldDepId);
                if (goldDep.getArgumentPositions().size() == 0) {
                    continue;
                }
                int predictedPredicate;
                int predictedArgument;
                if (goldDep.isCoreArgument()) {
                    predictedPredicate = predictedDep.getHead();
                    predictedArgument = predictedDep.getArgumentIndex();
                } else {
                    predictedPredicate = predictedDep.getArgumentIndex();
                    predictedArgument = predictedDep.getHead();
                }
                if (goldDep.getPredicateIndex() == predictedPredicate
                      //  && (goldDep.getLabel() == predictedDep.getSemanticRole())
                        && goldDep.getArgumentPositions().contains(predictedArgument)) {
                    matchedGold.add(goldDepId);
                    break;
                }
            }
        }
        return new Results(predictedCount, matchedGold.size(), goldDeps.size() );
    }
}
