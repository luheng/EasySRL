package edu.uw.easysrl.qasrl.query;

import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import com.google.common.collect.ImmutableList;

import java.util.List;

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
public class QueryGenerators {

    public static String kBadQuestionOptionString = "Bad question.";
    public static String kUnlistedAnswerOptionString = "Answer is not listed.";

    public static <QA extends QAPairSurfaceForm> QueryGenerator<QA, Query<QA>> maximalForwardAggregator() {
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
                ImmutableList<QA> surfaceForms = ImmutableList.copyOf(e.getValue());
                return new BasicQuery<>(sentenceId, question, answers, surfaceForms, true /* allow multiple */);
            })
            .collect(toImmutableList());
    }

    public static <QA extends QAStructureSurfaceForm> QueryGenerator<QA, Query<QA>> checkboxQueryAggregator() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(QA::getQuestion))
                .entrySet()
                .stream()
                .map(e -> {
                    final List<QA> qaList = e.getValue();
                    assert qaList.size() > 0 : "grouped list should always be nonempty";
                    final int sentenceId = e.getValue().get(0).getSentenceId();
                    final String question = e.getKey();
                    List<String> options = qaList
                            .stream()
                            .map(QA::getAnswer)
                            .distinct()
                            .collect(toList());
                    options.add(kBadQuestionOptionString);
                    return new BasicQuery<>(sentenceId,
                                            question,
                                            ImmutableList.copyOf(options),
                                            ImmutableList.copyOf(qaList),
                                            true /* allow multiple */);
                }).collect(toImmutableList());
    }

    public static <QA extends QAStructureSurfaceForm> QueryGenerator<QA, BasicQuery<QA>> radioButtonQueryAggregator() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(QA::getQuestion))
                .entrySet()
                .stream()
                .map(e -> {
                    final List<QA> qaList = e.getValue();
                    assert qaList.size() > 0 : "grouped list should always be nonempty";
                    final int sentenceId = e.getValue().get(0).getSentenceId();
                    final String question = e.getKey();
                    List<String> options = qaList
                            .stream()
                            .map(QA::getAnswer)
                            .distinct()
                            .collect(toList());
                    options.add(kUnlistedAnswerOptionString);
                    options.add(kBadQuestionOptionString);
                    return new BasicQuery<>(sentenceId,
                            question,
                            ImmutableList.copyOf(options),
                            ImmutableList.copyOf(qaList),
                            false /* allow multiple */);
                }).collect(toImmutableList());
    }

    private QueryGenerators() {
        throw new AssertionError("no instances");
    }
}
