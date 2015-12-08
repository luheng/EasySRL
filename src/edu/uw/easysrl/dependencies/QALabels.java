package edu.uw.easysrl.dependencies;

import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;

/**
 * Created by luheng on 11/11/15.
 */
public class QALabels {
    public final static SRLLabel W0 = SRLLabel.make("W0", true);
    public final static SRLLabel W1 = SRLLabel.make("W1", true);
    public final static SRLLabel W2 = SRLLabel.make("W2", true);
    public final static SRLLabel WHERE = SRLLabel.make("WHERE", false);
    public final static SRLLabel WHEN = SRLLabel.make("WHEN", false);
    public final static SRLLabel HOW = SRLLabel.make("HOW", false);
    public final static SRLLabel HOWMUCH = SRLLabel.make("HOWMUCH", false);
    public final static SRLLabel WHY = SRLLabel.make("WHY", false);

    public static final String[] qaLabelPrefixes =
            new String[] {"W0", "W1", "W2", "WHERE", "WHEN", "HOW", "HOWMUCH", "WHY"};

    public static boolean isCore(String label) {
        return label.startsWith("W0") || label.startsWith("W1") || label.startsWith("W2");
    }

    public static boolean isQALabel(SRLLabel label) {
        if (label.name.equals("NONE")) {
            return true;
        }
        for (String prefix : qaLabelPrefixes) {
            if (label.name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
