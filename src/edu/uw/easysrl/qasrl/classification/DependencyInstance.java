package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableMap;
import edu.uw.easysrl.dependencies.Dependency;

import java.util.Map;

/**
 * A binary classification instance.
 * Label = 1: dependency (directed attachment) is in gold
 * Label = 0: not in gold
 * Created by luheng on 4/21/16.
 */
public class DependencyInstance {
    final boolean inGold;
    final int sentenceId, headId, argId;
    final ImmutableMap<Integer, Double> features;

    DependencyInstance(int sentenceId, int headId, int argId, boolean inGold, ImmutableMap<Integer, Double> features) {
        this.sentenceId = sentenceId;
        this.headId = headId;
        this.argId = argId;
        this.inGold = inGold;
        this.features = features;
    }
}
