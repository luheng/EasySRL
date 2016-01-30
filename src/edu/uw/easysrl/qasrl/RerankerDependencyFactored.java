package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;

/**
 * Created by luheng on 1/28/16.
 */
public class RerankerDependencyFactored extends Reranker {
    // Dependency-factored scoring function for each sentence.
    final Map<Integer, List<Parse>> allParses;
    final Map<Integer, ScoreFunction> scoreFunctions;

    public RerankerDependencyFactored(final Map<Integer, List<Parse>> allParses) {
        this.allParses = allParses;
        this.scoreFunctions = new HashMap<>();
        allParses.forEach((sentIdx, parses) -> scoreFunctions.put(sentIdx, new ScoreFunction(parses)));
        numQueries = 0;
        numEffectiveQueries = 0;
    }

    public void rerank(final GroupedQuery query, final Response response) {
        final int predIdx = query.predicateIndex;
        final Category category = query.category;
        final int argNum = query.argumentNumber;
        final List<GroupedQuery.AnswerOption> options = query.answerOptions;
        for (int r : response.chosenOptions) {
            final List<Integer> chosenArgIds = options.get(r).argumentIds;
            final ScoreFunction scoreFunction = scoreFunctions.get(query.sentenceId);
            chosenArgIds.forEach(argId -> scoreFunction.update(predIdx, argId, category, argNum, 1.0));
        }
        /*
        Set<Integer> allArgIds = new HashSet<>();
        query.answerOptions.forEach(ao -> allArgIds.addAll(ao.argumentIds));
        allArgIds.removeAll(chosenArgIds);
        allArgIds.forEach(argId -> scFun.update(predIdx, argId, category, argNum, -1.0));
        */
        numQueries ++;
        // TODO: update effective query count
    }

    public int getRerankedBest(final int sentenceId) {
        final List<Parse> parses = allParses.get(sentenceId);
        ScoreFunction scoreFunction = scoreFunctions.get(sentenceId);
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
