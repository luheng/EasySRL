package edu.uw.easysrl.qasrl;

/**
 * Created by luheng on 1/28/16.
 */
public abstract class Reranker {
    public int numQueries, numEffectiveQueries;

    public abstract void rerank(final GroupedQuery query, final Response response);
    public abstract int getRerankedBest(final int sentenceId);
    public abstract void printVotes();
}
