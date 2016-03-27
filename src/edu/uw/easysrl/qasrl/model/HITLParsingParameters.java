package edu.uw.easysrl.qasrl.model;

/**
 * Created by luheng on 3/25/16.
 */
public class HITLParsingParameters {
    int minAgreement = 2;
    double supertagPenaltyWeight = 1.0;
    double attachmentPenaltyWeight = 1.0;

    int ppQuestionMinAgreement = 4;
    double ppQuestionWeight = 1.0;

    boolean skipPrepositionalQuestions = true;
    boolean skipPronounEvidence = true;

    int maxTagsPerWord = 50;

    public HITLParsingParameters() {
    }
}
