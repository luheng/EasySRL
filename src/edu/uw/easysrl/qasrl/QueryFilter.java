package edu.uw.easysrl.qasrl;

import java.util.Collection;
import java.util.List;

/**
 * Query filter.
 * Created by luheng on 1/17/16.
 */
public class QueryFilter {
    public static boolean isUseful(final Query query, final List<Parse> parses) {
        // Not useful if everyone agree on the same thing.
        // Not useful if only the 1-best has opinions.
        int numParses = parses.size();
        for (Collection<Integer> parseIds : query.answerToParses.values()) {
            if (parseIds.size() < numParses && !(parseIds.size() == 1 && parseIds.contains(0))) {
                return true;
            }
        }
        // TODO: answer entropy
        return false;
    }

    public static boolean isReasonable(final Query query, final List<Parse> parses) {
        // Not reasonable if not enough parses propose that question.
        return query.answerToParses.values().stream().mapToInt(Collection::size).sum() > 0.1 * parses.size();
    }
}
