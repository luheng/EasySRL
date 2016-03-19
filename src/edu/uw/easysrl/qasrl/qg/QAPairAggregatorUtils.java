package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.ImmutableList;

/**
 * Created by luheng on 3/19/16.
 */
public class QAPairAggregatorUtils {
    public static String answerDelimiter = " _AND_ ";


    public static class AnswerSurfaceFormTriple {
        public final String answerString;
        public final ImmutableList<Integer> argList;
        public final double score;

        public AnswerSurfaceFormTriple(String answerString, ImmutableList<Integer> argList, double score) {
            this.answerString = answerString;
            this.argList = argList;
            this.score = score;
        }
    }
}
