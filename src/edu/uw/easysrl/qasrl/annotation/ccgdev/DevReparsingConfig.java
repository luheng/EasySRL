package edu.uw.easysrl.qasrl.annotation.ccgdev;

/**
 * Created by luheng on 5/29/16.
 */
public class DevReparsingConfig {
    boolean fixPronouns = false;
    boolean fixSubspans = false;
    boolean fixAppositves = false;
    boolean fixRelatives = false;
    boolean fixConjunctions = false;
    boolean useSubspanDisjunctives = false;
    boolean useOldConstraints = false;
    int positiveConstraintMinAgreement = 3;
    int negativeConstraintMaxAgreement = 2;
    double positiveConstraintPenalty = 1.0;
    double negativeConstraintPenalty = 1.0;
    double supertagPenalty = 1.0;

    public DevReparsingConfig() {

    }
}
