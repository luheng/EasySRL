package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

/**
 * Helpers.
 * Created by luheng on 3/19/16.
 */
public class QAPairAggregatorUtils {
    public static String answerDelimiter = " _AND_ ";

    public static String getQuestionDependenciesHashString(QuestionAnswerPair qa) {
        return qa.getQuestionDependencies().stream()
                .filter(dep -> dep.getHead() == qa.getPredicateIndex() && dep.getArgNumber() != qa.getArgumentNumber())
                .collect(toMap(ResolvedDependency::getArgNumber, ResolvedDependency::getArgumentIndex))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining("\t"));
    }

    public static double getScore(Collection<QuestionAnswerPair> qaList) {
        return qaList.stream()
                .collect(groupingBy(QuestionAnswerPair::getParseId))
                .entrySet().stream()
                .mapToDouble(e -> e.getValue().get(0).getParse().score)
                .sum();
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

    static class QuestionSurfaceFormToStructure<QA extends QuestionAnswerPair> {
        public final String question;
        public final QuestionStructure structure;
        public final Collection<QA> qaList;

        public QuestionSurfaceFormToStructure(String question, QuestionStructure structure, Collection<QA> qaList) {
            this.question = question;
            this.structure = structure;
            this.qaList = qaList;
        }
    }

    static class AnswerSurfaceFormToStructure<QA extends QuestionAnswerPair> {
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
