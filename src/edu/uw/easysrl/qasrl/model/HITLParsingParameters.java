package edu.uw.easysrl.qasrl.model;

/**
 * Created by luheng on 3/25/16.
 */
// TODO: remove this ...
public class HITLParsingParameters {
    public int minAgreement = 2;
    public double supertagPenaltyWeight = 1.0;
    public double attachmentPenaltyWeight = 1.0;

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
