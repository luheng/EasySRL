package edu.uw.easysrl.qasrl.qg.util;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import java.util.Optional;

public final class Argument {
    private Optional<ResolvedDependency> depOpt;
    private Predication pred;

    public Optional<ResolvedDependency> getDependency() { return depOpt; }
    public Predication getPredication() { return pred; }

    public Argument(Optional<ResolvedDependency> depOpt, Predication pred) {
        this.depOpt = depOpt;
        this.pred = pred;
    }
}
