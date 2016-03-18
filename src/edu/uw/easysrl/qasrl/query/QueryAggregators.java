package edu.uw.easysrl.qasrl.query;

import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import com.google.common.collect.ImmutableList;
import static edu.uw.easysrl.util.GuavaCollectors.*;
import static java.util.stream.Collectors.*;

/**
 * Convenience class to hold our QueryAggregator instances,
 * which are often polymorphic (so they appear as polymorphic static methods).
 *
 * This class encodes LOGIC, NOT DATA.
 *
 * Created by julianmichael on 3/17/16.
 */
public class QueryAggregators {

    public static <QA extends QAPairSurfaceForm> QueryAggregator<QA, BasicQuery<QA>> maximalForwardAggregator() {
        return qaPairs -> qaPairs
            .stream()
            .collect(groupingBy(QA::getQuestion))
            .entrySet()
            .stream()
            .map(e -> {
                    assert e.getValue().size() > 0
                        : "grouped list should always be nonempty";
                    int sentenceId = e.getValue().get(0).getSentenceId();
                    String question = e.getKey();
                    ImmutableList<String> answers = e.getValue()
                        .stream()
                        .map(QA::getAnswer)
                        .distinct()
                        .collect(toImmutableList());
                    return new BasicQuery<QA>(sentenceId, question, answers);
                })
            .collect(toImmutableList());
    }

    private QueryAggregators() {
        throw new AssertionError("no instances");
    }
}
