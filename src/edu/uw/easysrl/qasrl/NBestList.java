package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.syntax.evaluation.Results;

import java.util.List;

import static edu.uw.easysrl.util.GuavaCollectors.*;

/**
 * Holds an n-best list of parses.
 * Reranking/reparsing involves transforming one n-best list into another,
 * on the basis of annotations from crowd workers.
 *
 * Holds scores separate from the list of parses in case we want to re-score them as in reranking.
 * Also TODO convenience methods for loading n-best lists from a file or from parsing text.
 *
 * Created by julianmichael on 3/18/16.
 */
public final class NBestList {
    private final ImmutableList<Parse> parses;
    private final ImmutableList<Double> scores;
    private ImmutableList<Results> results;
    private int oracleId;

    public int getN() {
        return parses.size();
    }

    public ImmutableList<Parse> getParses() {
        return parses;
    }

    public Parse getParse(final int parseId) {
        return parses.get(parseId);
    }

    public ImmutableList<Double> getScores() {
        return scores;
    }

    public Double getScore(int parseId) {
        return scores.get(parseId);
    }

    public NBestList(ImmutableList<Parse> parses, ImmutableList<Double> scores) {
        this.parses = parses;
        this.scores = scores;
    }

    public void cacheResults(final Parse goldParse) {
        results = ImmutableList.copyOf(CcgEvaluation.evaluateNBest(parses, goldParse.dependencies));
        oracleId = 0;
        for (int k = 1; k < parses.size(); k++) {
            if (results.get(k).getF1() > results.get(oracleId).getF1()) {
                oracleId = k;
            }
        }
    }

    public int getOracleId() {
        return oracleId;
    }

    public Results getResults(int parseId) {
        return results.get(parseId);
    }

    /**
     * uses the parser-assigned scores
     */
    public NBestList(ImmutableList<Parse> parses) {
        this.parses = parses;
        this.scores = parses.stream()
            .map(p -> p.score)
            .collect(toImmutableList());
    }

    public static ImmutableList<NBestList> loadFromFile(String filename) {
        return null; // XXX TODO
    }

    public static ImmutableList<NBestList> loadByParsing(String filename /* also should pass in a parser */) {
        return null; // XXX TODO
    }
}
