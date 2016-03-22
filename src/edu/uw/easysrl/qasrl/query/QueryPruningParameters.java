package edu.uw.easysrl.qasrl.query;

/**
 * Created by luheng on 3/5/16.
 */
public class QueryPruningParameters {

    public int questionSurfaceFormTopK = 1;
    public double minQuestionConfidence = 0.1;
    public double minAnswerConfidence = 0.05;
    public double minAnswerEntropy = 0.05;

    private static final int maxNumOptionsPerQuestion = 8;

    public boolean skipBinaryQueries = true;
    public boolean skipPPQuestions = true;
    public boolean skipQueriesWithPronounOptions = false;

    public QueryPruningParameters() {
    }
}