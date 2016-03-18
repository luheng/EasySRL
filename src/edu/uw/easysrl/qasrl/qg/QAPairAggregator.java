package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import com.google.common.collect.ImmutableList;

/**
 * A QAPairAggregator is a method for aggregating many QuestionAnswerPairs
 * into many (but fewer) QAPairSurfaceForms.
 * Examples may include aggregation by string or target dependency.
 *
 * Created by julianmichael on 3/17/16.
 */
@FunctionalInterface
public interface QAPairAggregator<T extends QAPairSurfaceForm> {
    public ImmutableList<T> aggregate(ImmutableList<IQuestionAnswerPair> qaPairs);
}
