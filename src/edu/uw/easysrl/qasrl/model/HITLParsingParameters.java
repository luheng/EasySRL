package edu.uw.easysrl.qasrl.model;

/**
 * Created by luheng on 3/25/16.
 */
public class HITLParsingParameters {
    // Inclusive boundary.
    public int positiveConstraintMinAgreement = 5;
    public int negativeConstraintMaxAgreement = 1;
    public double supertagPenaltyWeight = 1.0;
    public double attachmentPenaltyWeight = 1.0;
    // Like, really confident.
    public double oraclePenaltyWeight = 1000.0;

    public int jeopardyQuestionMinAgreement = 4;
    public double jeopardyQuestionWeight = 1.0;

    public boolean skipJeopardyQuestions = false;
    public boolean skipPronounEvidence = true;

    public int maxTagsPerWord = 50;

    public HITLParsingParameters() {
    }

    public String toString() {
        // TODO:
        return "";
    }
}
