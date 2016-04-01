package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;

import static edu.uw.easysrl.util.GuavaCollectors.toImmutableList;
import static java.util.stream.Collectors.*;

/**
 * Helpers.
 * Created by luheng on 3/19/16.
 */
public class QAPairAggregatorUtils {
    public static String answerDelimiter = " _AND_ ";

    public static String getQuestionLabelString(final QuestionAnswerPair qa) {
        return qa.getPredicateIndex() + "\t" + qa.getPredicateCategory() + "\t" + qa.getArgumentNumber() + "\t";
    }

    public static String getQuestionDependenciesString(final QuestionAnswerPair qa) {
        return qa.getQuestionDependencies().stream()
                .filter(dep -> dep.getHead() == qa.getPredicateIndex() && dep.getArgNumber() != qa.getArgumentNumber())
                .collect(toMap(ResolvedDependency::getArgNumber, ResolvedDependency::getArgumentIndex))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining("\t"));
    }

    public static String getFullQuestionStructureString(final QuestionAnswerPair qa) {
        return getQuestionLabelString(qa) + "\t" + getQuestionDependenciesString(qa);
    }

    public static double getScore(Collection<QuestionAnswerPair> qaList) {
        return qaList.stream()
                .collect(groupingBy(QuestionAnswerPair::getParseId))
                .entrySet().stream()
                .mapToDouble(e -> e.getValue().get(0).getParse().score)
                .sum();
    }

    public static QuestionSurfaceFormToStructure getQuestionSurfaceFormToStructure(
            final List<QuestionAnswerPair> qaList) {
        return new QuestionSurfaceFormToStructure(
                getBestQuestionSurfaceForm(qaList),
                new QuestionStructure(qaList),
                qaList);
    }

    public static AnswerSurfaceFormToStructure getAnswerSurfaceFormToSingleHeadedStructure(
            final List<QuestionAnswerPair> qaList) {
        final AnswerStructure answerStructure = new AnswerStructure(
                ImmutableList.of(qaList.get(0).getArgumentIndex()),
                true /* single headed */);
        return new AnswerSurfaceFormToStructure(
                getBestAnswerSurfaceForm(qaList),
                answerStructure,
                qaList);
    }

    public static ImmutableList<AnswerSurfaceFormToStructure> getAnswerSurfaceFormToMultiHeadedStructures(
            final List<QuestionAnswerPair> qaList) {
        // Get answer indices list.
        Table<ImmutableList<Integer>, String, Double> argListToAnswerToScore = HashBasedTable.create();
        Table<ImmutableList<Integer>, QuestionAnswerPair, Boolean> argListToQAs = HashBasedTable.create();
        qaList.stream()
                .collect(groupingBy(QuestionAnswerPair::getParseId))
                .values().stream()
                .forEach(someParseQAs -> {
                    Map<Integer, String> argIdToSpan = new HashMap<>();
                    someParseQAs.forEach(qa -> argIdToSpan.put(qa.getArgumentIndex(), qa.getAnswer()));
                    ImmutableList<Integer> argList = argIdToSpan.keySet().stream()
                            .sorted()
                            .collect(toImmutableList());
                    String answerString = argList.stream()
                            .map(argIdToSpan::get)
                            .collect(Collectors.joining(answerDelimiter));
                    double score = someParseQAs.get(0).getParse().score;
                    double s0 = argListToAnswerToScore.contains(argList, answerString) ?
                            argListToAnswerToScore.get(argList, answerString) : .0;
                    argListToAnswerToScore.put(argList, answerString, score + s0);
                    someParseQAs.stream().forEach(qa -> argListToQAs.put(argList, qa, Boolean.TRUE));
                });

        return argListToAnswerToScore.rowKeySet().stream()
                .map(argList -> {
                    String bestAnswerString = argListToAnswerToScore.row(argList).entrySet().stream()
                            .max(Comparator.comparing(Map.Entry::getValue))
                            .get().getKey();
                    final Collection<QuestionAnswerPair> qaList2 = argListToQAs.row(argList).keySet();
                    return new AnswerSurfaceFormToStructure(
                            bestAnswerString,
                            new AnswerStructure(argList, false /* not single headed */),
                            qaList2);
                }).collect(GuavaCollectors.toImmutableList());
    }

    public static String getBestQuestionSurfaceForm(Collection<QuestionAnswerPair> qaList) {
        return qaList.stream()
                .collect(groupingBy(QuestionAnswerPair::getQuestion))
                .entrySet()
                .stream()
                .map(questionStringGroup -> new AbstractMap.SimpleEntry<>(
                        questionStringGroup.getKey(),
                        QAPairAggregatorUtils.getScore(questionStringGroup.getValue())))
                .max(Comparator.comparing(AbstractMap.SimpleEntry::getValue))
                .get().getKey();
    }

    public static String getBestAnswerSurfaceForm(Collection<QuestionAnswerPair> qaList) {
        return qaList.stream()
                .collect(groupingBy(QuestionAnswerPair::getAnswer))
                .entrySet()
                .stream()
                .map(answerStringGroup -> {
                    double score = answerStringGroup.getValue().stream()
                        .mapToDouble(qaPair -> qaPair.getParse().score)
                        .sum();
                    return new AbstractMap.SimpleEntry<>(answerStringGroup.getKey(), score);
                })
                .max(Comparator.comparing(AbstractMap.SimpleEntry::getValue))
                .get().getKey();
    }

    static class QuestionSurfaceFormToStructure {
        public final String question;
        public final QuestionStructure structure;
        public final Collection<QuestionAnswerPair> qaList;

         QuestionSurfaceFormToStructure(String question, QuestionStructure structure, Collection<QuestionAnswerPair> qaList) {
            this.question = question;
            this.structure = structure;
            this.qaList = qaList;
        }
    }

    static class AnswerSurfaceFormToStructure {
        public final String answer;
        public final AnswerStructure structure;
        public final Collection<QuestionAnswerPair> qaList;

        AnswerSurfaceFormToStructure(String answer, AnswerStructure structure, Collection<QuestionAnswerPair> qaList) {
            this.answer = answer;
            this.structure = structure;
            this.qaList = qaList;
        }
    }
}
