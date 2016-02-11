package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.*;

/**
 * Experimental reranker. Not used now.
 * Created by luheng on 1/28/16.
 */
public class RerankerDependencyFactored implements Reranker {
    // Dependency-factored scoring function for each sentence.
    final Map<Integer, List<Parse>> allParses;
    final Map<Integer, DependencyScoreFunction> scoreFunctions;
    final static double stepSize = 0.5;

    public RerankerDependencyFactored(final Map<Integer, List<Parse>> allParses) {
        this.allParses = allParses;
        this.scoreFunctions = new HashMap<>();
        allParses.forEach((sentIdx, parses) -> scoreFunctions.put(sentIdx, new DependencyScoreFunction(parses)));
    }

    public void rerank(final GroupedQuery query, final Response response) {
        final int predIdx = query.predicateIndex;
        final Category category = query.category;
        final int argNum = query.argumentNumber;
        final List<GroupedQuery.AnswerOption> options = query.answerOptions;
        for (int r : response.chosenOptions) {
            final List<Integer> chosenArgIds = options.get(r).argumentIds;
            final DependencyScoreFunction scoreFunction = scoreFunctions.get(query.sentenceId);
            chosenArgIds.forEach(argId -> scoreFunction.update(predIdx, argId, category, argNum, stepSize));
        }
        /*
        Set<Integer> allArgIds = new HashSet<>();
        query.answerOptions.forEach(ao -> allArgIds.addAll(ao.argumentIds));
        allArgIds.removeAll(chosenArgIds);
        allArgIds.forEach(argId -> scFun.update(predIdx, argId, category, argNum, -1.0));
        */
        // TODO: update effective query count
    }

    public int getRerankedBest(final int sentenceId) {
        final List<Parse> parses = allParses.get(sentenceId);
        DependencyScoreFunction scoreFunction = scoreFunctions.get(sentenceId);
        int bestK = 0;
        double bestScore = scoreFunction.getScore(parses.get(0));
        for (int k = 1; k < parses.size(); k++) {
            double score = scoreFunction.getScore(parses.get(k));
            if (score > bestScore) {
                bestScore = score;
                bestK = k;
            }
        }
        return bestK;
    }

    public void printVotes() {
      // TODO
    }
}
