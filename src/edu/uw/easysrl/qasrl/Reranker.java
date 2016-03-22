package edu.uw.easysrl.qasrl;

/**
 * Reranker interface.
 * Created by luheng on 1/28/16.
 */
@Deprecated
public interface Reranker {
    void rerank(final GroupedQuery query, final Response response);
    int getRerankedBest(final int sentenceId);
    double[] getParseScores(final int sentenceId);
    void printVotes();
}
