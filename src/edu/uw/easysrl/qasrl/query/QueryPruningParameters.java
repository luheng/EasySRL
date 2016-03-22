package edu.uw.easysrl.qasrl.query;

/**
 * Created by luheng on 3/5/16.
 */
public class QueryPruningParameters {

    public int questionSurfaceFormTopK = 3;
    public double minQuestionConfidence = 0.1;
    public double minAnswerConfidence = 0.01;
    public double minAnswerEntropy = 0.1;

    public boolean filterBinary = true;

    public QueryPruningParameters() {
    }

    public QueryPruningParameters(int questionSurfaceFormTopK, double minQuestionConfidence, double minAnswerConfidence,
                                  double minAnswerEntropy) {
        this.questionSurfaceFormTopK = questionSurfaceFormTopK;
        this.minQuestionConfidence = minQuestionConfidence;
        this.minAnswerEntropy = minAnswerEntropy;
        this.minAnswerConfidence = minAnswerConfidence;
    }
}