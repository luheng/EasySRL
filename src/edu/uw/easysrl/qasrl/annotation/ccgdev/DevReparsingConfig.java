package edu.uw.easysrl.qasrl.annotation.ccgdev;

import org.kohsuke.args4j.Option;

/**
 * Created by luheng on 5/29/16.
 */
public class DevReparsingConfig {
    @Option(name="-pronoun",usage="Fix pronoun")
    boolean fixPronouns = true;

    @Option(name="-subspan",usage="Fix subspans")
    boolean fixSubspans = true;

    @Option(name="-appositive",usage="Fix appositives")
    boolean fixAppositves = false;

    @Option(name="-relatives",usage="Fix relatives")
    boolean fixRelatives = false;

    @Option(name="-conjunctions",usage="Fix conjunctions")
    boolean fixConjunctions = false;

    @Option(name="-disjunctives",usage="Use disjunctives")
    boolean useSubspanDisjunctives = true;

    @Option(name="-pos_threshold",usage="")
    int positiveConstraintMinAgreement = 3;

    @Option(name="-neg_threshold",usage="")
    int negativeConstraintMaxAgreement = 2;

    @Option(name="-pos_penalty",usage="")
    double positiveConstraintPenalty = 2.0;

    @Option(name="-neg_penalty",usage="")
    double negativeConstraintPenalty = 2.0;

    @Option(name="-tag_penalty",usage="supertag penalty")
    double supertagPenalty = 0.0;

    public DevReparsingConfig() {

    }

    public String toString() {
        return new StringBuilder()
                .append("Fix pronouns=\t").append(fixPronouns)
                .append("\nFix subspans=\t").append(fixSubspans)
                .append("\nFix appositives=\t").append(fixAppositves)
                .append("\nFix relatives=\t").append(fixRelatives)
                .append("\nFix conjunctions=\t").append(fixConjunctions)
                .append("\nUse disjunctives=\t").append(useSubspanDisjunctives)
                .append("\nPositive threshold=\t").append(positiveConstraintMinAgreement)
                .append("\nNegative threshold=\t").append(negativeConstraintMaxAgreement)
                .append("\nPositive constraint penalty=\t").append(positiveConstraintPenalty)
                .append("\nNegative constraint penalty=\t").append(negativeConstraintPenalty)
                .append("\nSupertag constraint penalty=\t").append(supertagPenalty)
                .toString();
    }

}
