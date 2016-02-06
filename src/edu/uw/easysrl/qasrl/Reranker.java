package edu.uw.easysrl.qasrl;

/**
 * Reranker interface.
 * Created by luheng on 1/28/16.
 */
public interface Reranker {
    void rerank(final GroupedQuery query, final Response response);
    int getRerankedBest(final int sentenceId);
    void printVotes();
}