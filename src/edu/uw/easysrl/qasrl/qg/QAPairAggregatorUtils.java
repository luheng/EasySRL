package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;

import static edu.uw.easysrl.util.GuavaCollectors.*;
import static java.util.stream.Collectors.*;

/**
 * Helpers.
 * Created by luheng on 3/19/16.
 */
public class QAPairAggregatorUtils {
    public static String answerDelimiter = " _AND_ ";


    public static ImmutableSet<Integer> getParseIds(Collection<IQuestionAnswerPair> qaList) {
        return qaList.stream()
                .map(IQuestionAnswerPair::getParseId)
                .collect(toImmutableSet());
    }

    public static double getScore(Collection<IQuestionAnswerPair> qaList) {
        return qaList.stream()
                .collect(groupingBy(IQuestionAnswerPair::getParseId))
                .entrySet().stream()
                .mapToDouble(e -> e.getValue().get(0).getParseScore())
                .sum();
    }

}
