package edu.uw.easysrl.qasrl.query;

/**
 * Created by luheng on 3/5/16.
 */
public class QueryPruningParameters {

    public double minPromptConfidence = 0.1;
    public double minOptionConfidence = 0.05;
    public double minOptionEntropy = 0.05;

    // Not including "Bad question" or "Answer unlisted" option.
    private static final int maxNumOptionsPerQuery = 6;

    public boolean skipBinaryQueries = true;
    public boolean skipPPQuestions = false;
    public boolean skipQueriesWithPronounOptions = false;

    public QueryPruningParameters() {
    }

    public String toString() {
        // TODO
        return "";
    }

}