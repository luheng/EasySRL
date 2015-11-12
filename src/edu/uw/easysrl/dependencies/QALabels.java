package edu.uw.easysrl.dependencies;

import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;

/**
 * Created by luheng on 11/11/15.
 */
public class QALabels {
    public final static SRLLabel WHO = SRLLabel.make("WHO", true);
    public final static SRLLabel WHAT = SRLLabel.make("WHAT", true);
    public final static SRLLabel WHERE = SRLLabel.make("WHERE", false);
    public final static SRLLabel WHEN = SRLLabel.make("WHEN", false);
    public final static SRLLabel HOW = SRLLabel.make("HOW", false);
    public final static SRLLabel HOWMUCH = SRLLabel.make("HOW MUCH", false);
    public final static SRLLabel WHY = SRLLabel.make("WHY", false);
}
