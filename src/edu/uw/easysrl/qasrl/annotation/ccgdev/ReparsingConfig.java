package edu.uw.easysrl.qasrl.annotation.ccgdev;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * Created by luheng on 5/29/16.
 */
public class ReparsingConfig {
    @Option(name="-pronoun",usage="Fix pronoun")
    public boolean fixPronouns = false;

    @Option(name="-subspan",usage="Fix subspans")
    public boolean fixSubspans = false;

    @Option(name="-appositive",usage="Fix appositives")
    public boolean fixAppositves = false;

    @Option(name="-relative",usage="Fix relatives")
    public boolean fixRelatives = false;

    @Option(name="-conjunction",usage="Fix conjunctions")
    public boolean fixConjunctions = false;

    @Option(name="-disjunctive",usage="Use disjunctives")
    public boolean useSubspanDisjunctives = false;

    @Option(name="-pos_threshold",usage="")
    public int positiveConstraintMinAgreement = 3;

    @Option(name="-neg_threshold",usage="")
    public int negativeConstraintMaxAgreement = 2;

    @Option(name="-pos_penalty",usage="")
    public double positiveConstraintPenalty = 1.5;

    @Option(name="-neg_penalty",usage="")
    public double negativeConstraintPenalty = 1.5;

    @Option(name="-tag_penalty",usage="supertag penalty")
    public double supertagPenalty = 0.0;

    public ReparsingConfig() {
    }

    public ReparsingConfig(final String[] args) {
        CmdLineParser parser = new CmdLineParser(this);
        //parser.setUsageWidth(120);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            e.printStackTrace();
        }
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
