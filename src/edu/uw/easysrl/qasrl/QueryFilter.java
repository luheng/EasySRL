package edu.uw.easysrl.qasrl;

import java.util.Collection;
import java.util.Set;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * Query filter.
 * Created by luheng on 1/17/16.
 */
public class QueryFilter {
    public static boolean isUseful(final Query query) {
        // Not useful if everyone agree on the same thing.
        // Not useful if only the 1-best has opinions.
        int numParses = query.numTotalParses;
        for (Collection<Integer> parseIds : query.answerToParses.values()) {
            if (parseIds.size() > 0 && parseIds.size() < numParses &&
                    !(parseIds.size() == 1 && parseIds.contains(0 /* 1-best parse */))) {
                return true;
            }
        }
        // TODO: answer entropy
        return false;
    }

    public static boolean isReasonable(final Query query) {
        // Not reasonable if not enough parses propose that question.
        return query.answerToParses.values().stream().mapToInt(Collection::size).sum() > 0.1 * query.numTotalParses;
    }

    // FIXME: entropy > 1 ...
    public static double getAnswerEntropy(final Query query) {
        final Collection<Set<Integer>> parseIds = query.answerToParses.values();
        int sum = parseIds.stream().mapToInt(Collection::size).sum();
        return -1.0 * parseIds.stream().mapToDouble(Collection::size)
                .filter(d -> d > 0)
                .map(p -> p / sum * Math.log(p / sum)).sum();
    }
}
