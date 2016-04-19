package edu.uw.easysrl.qasrl.analysis;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.dependencies.ResolvedDependency;

/**
 * Created by luheng on 4/19/16.
 */
public class ProfiledDependency {
    public final ResolvedDependency dependency;
    public double score;

    public boolean isCore = false;
    public boolean isPPAttachment = false;
    public boolean isVerbAdjunct = false;
    public boolean isVerbArgument = false;
    public boolean isNounAdjunct = false;
    public boolean isPPArgument = false;

    public boolean inGold = false;
    public boolean unlabeledInGold = false;
    public boolean undirectedInGold = false;
    public boolean coveredByQuery = false;

    public ProfiledDependency(final ResolvedDependency dependency, final double score) {
        this.dependency = dependency;
        this.score = score;
    }

    public String toString(final ImmutableList<String> sentence) {
        return String.format("%.3f\t%d:%s\t%s.%d\t%d:%s", score,
                dependency.getHead(), sentence.get(dependency.getHead()),
                dependency.getCategory(), dependency.getArgNumber(),
                dependency.getArgument(), sentence.get(dependency.getArgument()));
    }

}
