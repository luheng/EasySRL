package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Comparator;

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

    public static String getBestQuestionSurfaceForm(Collection<IQuestionAnswerPair> qaList) {
        return qaList.stream()
                .collect(groupingBy(IQuestionAnswerPair::getQuestion))
                .entrySet()
                .stream()
                .map(questionStringGroup -> new AbstractMap.SimpleEntry<>(
                        questionStringGroup.getKey(),
                        QAPairAggregatorUtils.getScore(questionStringGroup.getValue())))
                .max(Comparator.comparing(AbstractMap.SimpleEntry::getValue))
                .get().getKey();
    }

    public static String getBestAnswerSurfaceForm(Collection<IQuestionAnswerPair> qaList) {
        return qaList.stream()
                .collect(groupingBy(IQuestionAnswerPair::getAnswer))
                .entrySet()
                .stream()
                .map(answerStringGroup -> {
                    double score = answerStringGroup.getValue().stream()
                            .mapToDouble(IQuestionAnswerPair::getParseScore)
                            .sum();
                    return new AbstractMap.SimpleEntry<>(answerStringGroup.getKey(), score);
                })
                .max(Comparator.comparing(AbstractMap.SimpleEntry::getValue))
                .get().getKey();
    }

    static class QuestionSurfaceFormToStructure<QA extends IQuestionAnswerPair> {
        public final String question;
        public final QuestionStructure structure;
        public final Collection<QA> qaList;

        public QuestionSurfaceFormToStructure(String question, QuestionStructure structure, Collection<QA> qaList) {
            this.question = question;
            this.structure = structure;
            this.qaList = qaList;
        }
    }

    static class AnswerSurfaceFormToStructure<QA extends IQuestionAnswerPair> {
        public final String answer;
        public final AnswerStructure structure;
        public final Collection<QA> qaList;

        public AnswerSurfaceFormToStructure(String answer, AnswerStructure structure, Collection<QA> qaList) {
            this.answer = answer;
            this.structure = structure;
            this.qaList = qaList;
        }
    }
}
