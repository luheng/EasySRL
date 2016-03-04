package edu.uw.easysrl.qasrl.analysis;

import com.google.common.collect.Table;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;


import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 3/3/16.
 */
public class AnalysisHelper {

    public static double getScore(final Collection<Parse> parseList) {
        return parseList.stream().mapToDouble(parse -> parse.score).sum();
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
}
