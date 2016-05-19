package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableMap;
import edu.uw.easysrl.dependencies.Dependency;
import edu.uw.easysrl.qasrl.query.QueryType;

import java.util.Map;

/**
 * A binary classification instance.
 * Label = 1: dependency (directed attachment) is in gold
 * Label = 0: not in gold
 * Created by luheng on 4/21/16.
 */
public class DependencyInstance {
    final boolean inGold, inOneBest;
    final int sentenceId, queryId, headId, argId;
    final ImmutableMap<Integer, Double> features;
    final DependencyInstanceType instanceType;
    final QueryType queryType;

    DependencyInstance(int sentenceId, int queryId, int headId, int argId, QueryType queryType,
                       DependencyInstanceType instanceType, boolean inGold, boolean inOneBest,
                       ImmutableMap<Integer, Double> features) {
        this.sentenceId = sentenceId;
        this.queryId = queryId;
        this.queryType = queryType;
        this.instanceType = instanceType;
        this.headId = headId;
        this.argId = argId;
        this.inGold = inGold;
        this.inOneBest = inOneBest;
        this.features = features;
    }
}
