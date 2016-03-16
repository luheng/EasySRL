package edu.uw.easysrl.qasrl;

import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper class for error analysis.
 * Created by luheng on 1/31/16.
 */
public class AnalysisHelper {

    public static double getScore(final Collection<Parse> parseList) {
        return parseList.stream().mapToDouble(parse -> parse.score).sum();
    }

    public static double getScore(final Collection<Integer> parseIds, final List<Parse> allParses) {
        return parseIds.stream()
                .filter(id -> id >= 0 && id < allParses.size())
                .mapToDouble(id -> allParses.get(id).score).sum();
    }

    public static void addQA(int argHead, QuestionAnswerPairReduced qa, Parse parse, Map<String, Set<Parse>> questions,
                             Table<Integer, String, Set<Parse>> answers) {
        // Add question.
        String questionStr = qa.renderQuestion();
        String answerStr = qa.renderAnswer();
        if (!questions.containsKey(questionStr)) {
            questions.put(questionStr, new HashSet<>());
        }
        questions.get(questionStr).add(parse);
        // Add answer.
        if (!answers.contains(argHead, answerStr)) {
            answers.put(argHead, answerStr, new HashSet<>());
        }
        answers.get(argHead, answerStr).add(parse);
    }

    public static void printCollapsed(Map<String, Set<Parse>> questions, Table<Integer, String, Set<Parse>> answers,
                                      List<String> sentence, List<Parse> allParses) {
        double totalScore = AnalysisHelper.getScore(allParses);
        Map<String, Double> questionStringToScore = new HashMap<>();
        questions.entrySet().stream().forEach(q ->
                questionStringToScore.put(q.getKey(), AnalysisHelper.getScore(q.getValue()) / totalScore));
        // Sort question strings by score.
        questionStringToScore.entrySet().stream()
                .sorted((q1, q2) -> Double.compare(-q1.getValue(), -q2.getValue()))
                .forEach(q -> System.out.println(String.format("\t%s\t%.3f", q.getKey(), q.getValue())));
        // Print answers.
        answers.rowKeySet().stream().sorted().forEach(aid -> {
            Set<Parse> parses = new HashSet<>();
            Map<String, Double> answerStringToScore = new HashMap<>();
            answers.row(aid).entrySet().stream().forEach(a -> {
                parses.addAll(a.getValue());
                answerStringToScore.put(a.getKey(), AnalysisHelper.getScore(a.getValue()) / totalScore);
            });
            // Print answer head score.
            System.out.print(String.format("\t%d:%s\t%.3f\t", aid, sentence.get(aid),
                    AnalysisHelper.getScore(parses) / totalScore));
            // Sort answer strings by score.
            System.out.println(answerStringToScore.entrySet().stream()
                    .sorted((a1, a2) -> Double.compare(-a1.getValue(), -a2.getValue()))
                    .map(a -> String.format("%s (%.3f)", a.getKey(), a.getValue()))
                    .collect(Collectors.joining("\t")));
        });
    }

    public static Results evaluateQuestionCoveredDependencies(final Parse pred, final Parse gold,
                                                              final List<String> words,
                                                              final QuestionGenerator questionGenerator) {
        Set<ResolvedDependency> predDeps = pred.dependencies.stream()
                .filter(dep -> isCoveredByQuestion(dep, words, pred, questionGenerator))
                .collect(Collectors.toSet());
        Set<ResolvedDependency> goldDeps = gold.dependencies.stream()
                .filter(dep -> isCoveredByQuestion(dep, words, gold, questionGenerator))
                .collect(Collectors.toSet());
        return CcgEvaluation.evaluate(predDeps, goldDeps);
    }

    public static boolean isCoveredByQuestion(final ResolvedDependency dependency,
                                              final List<String> words,
                                              final Parse parse,
                                              final QuestionGenerator questionGenerator) {
        Optional<QuestionAnswerPair> questionOpt = questionGenerator.generateQuestion(dependency, words, parse);
        return questionOpt.isPresent();
    }

}
