package edu.uw.easysrl.qasrl.analysis;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.pomdp.POMDP;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 3/2/16.
 */
public class OverlappingArguments {
    // Look for coordination/appositive arguments.
    int predHead;
    Category category;
    int argNum;
    Map<String, Set<Parse>> questions;
    Table<Integer, String, Set<Parse>> answers;
    int numQAs;

    private OverlappingArguments() {
        questions = new HashMap<>();
        answers = HashBasedTable.create();
        numQAs = 0;
    }

    public static Optional<OverlappingArguments> findOverlappingArguments(int predHead, Category category, int argNum,
                List<String> sentence, Map<Integer, List<QuestionAnswerPairReduced>> parseToQAList,
                List<Parse> allParses) {
        OverlappingArguments overlapArg = new OverlappingArguments();
        overlapArg.predHead = predHead;
        overlapArg.category = category;
        overlapArg.argNum = argNum;
        for (int parseId : parseToQAList.keySet()) {
            final List<QuestionAnswerPairReduced> qaList = parseToQAList.get(parseId);
            if (qaList == null || qaList.size() == 0) {
                continue;
            }
            final Parse parse = allParses.get(parseId);
            String questionStr = parseToQAList.get(parseId).get(0).renderQuestion();
            for (QuestionAnswerPairReduced qa : qaList) {
                int argId = qa.targetDep.getArgument();
                String answerStr = qa.renderAnswer();
                if (!overlapArg.questions.containsKey(questionStr)) {
                    overlapArg.questions.put(questionStr, new HashSet<>());
                }
                overlapArg.questions.get(questionStr).add(parse);
                if (!overlapArg.answers.contains(argId, answerStr)) {
                    overlapArg.answers.put(argId, answerStr, new HashSet<>());
                }
                overlapArg.answers.get(argId, answerStr).add(parse);
                overlapArg.numQAs += qaList.size();
            }
        }
        List<Integer> argIds = overlapArg.answers.rowKeySet().stream().sorted().collect(Collectors.toList());
        for (int i = 0; i < argIds.size(); i++) {
            for (int j = 0; j < argIds.size(); j++) {
                if (i == j) {
                    continue;
                }
                Collection<String> spans1 = overlapArg.answers.row(argIds.get(i)).keySet();
                Collection<String> spans2 = overlapArg.answers.row(argIds.get(j)).keySet();
                for (String s1 : spans1) {
                    for (String s2 : spans2) {
                        if (s1.contains(s2) || s2.contains(s1)) {
                            return Optional.of(overlapArg);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public void print(List<String> sentence, List<Parse> allParses) {
        System.out.println(String.format("%d:%s\t%s\t%d", predHead, sentence.get(predHead), category, argNum));

        double totalScore = AnalysisHelper.getScore(allParses);
        Map<String, Double> questionStringToScore = new HashMap<>();
        questions.entrySet().stream().forEach(q ->
                questionStringToScore.put(q.getKey(), AnalysisHelper.getScore(q.getValue()) / totalScore));
        // Sort question strings by score.
        questionStringToScore.entrySet().stream()
                .sorted((q1, q2) -> Double.compare(-q1.getValue(), -q2.getValue()))
                .forEach(q -> System.out.println(String.format("\t%s\t%.3f", q.getKey(), q.getValue())));
        // Print answers.
        Map<Integer, Double> answerHeadsToScore = new HashMap<>();
        answers.rowKeySet().stream()
                .forEach(argId -> {
                    Set<Parse> parses = new HashSet<>();
                    answers.row(argId).entrySet().stream().forEach(a -> {
                        parses.addAll(a.getValue());
                    });
                    answerHeadsToScore.put(argId, AnalysisHelper.getScore(parses) / totalScore);
                });
        answers.rowKeySet().stream()
                .sorted((a1, a2) -> Double.compare(-answerHeadsToScore.get(a1), -answerHeadsToScore.get(a2)))
                .forEach(argId -> {
                    Map<String, Double> answerStringToScore = new HashMap<>();
                    answers.row(argId).entrySet().stream().forEach(a -> {
                        answerStringToScore.put(a.getKey(), AnalysisHelper.getScore(a.getValue()) / totalScore);
                    });
                    // Print answer head score.
                    System.out.print(String.format("\t%d:%s\t%.3f\t", argId, sentence.get(argId),
                            answerHeadsToScore.get(argId)));
                    // Sort answer strings by score.
                    System.out.println(answerStringToScore.entrySet().stream()
                            .sorted((a1, a2) -> Double.compare(-a1.getValue(), -a2.getValue()))
                            .map(a -> String.format("%s (%.3f)", a.getKey(), a.getValue()))
                            .collect(Collectors.joining("\t")));
                });
    }

    public static void main(String[] args) {
        POMDP learner = new POMDP(100 /* nbest */, 10000 /* horizon */, 0.0 /* money penalty */);
        int numCases = 0;
        int totalQAs = 0, coveredQAs = 0;

        for (int sid : learner.allParses.keySet()) {
            final List<String> sentence = learner.getSentenceById(sid);
            final List<Parse> allParses = learner.allParses.get(sid);
            if (allParses == null) {
                continue;
            }
            List<OverlappingArguments> ambiguities = new ArrayList<>();
            for (int predHead = 0; predHead < sentence.size(); predHead++) {
                Map<Category, Set<Integer>> taggings = new HashMap<>();
                for (int parseId = 0; parseId < allParses.size(); parseId ++) {
                    Parse parse = allParses.get(parseId);
                    Category category = parse.categories.get(predHead);
                    if (category == PPAttachment.nounAdjunct || category == PPAttachment.verbAdjunct) {
                        continue;
                    }
                    if (!taggings.containsKey(category)) {
                        taggings.put(category, new HashSet<>());
                    }
                    taggings.get(category).add(parseId);
                }
                for (Category category : taggings.keySet()) {
                    for (int argNum = 1; argNum <= category.getNumberOfArguments(); argNum++) {
                        Map<Integer, List<QuestionAnswerPairReduced>> parseToQAList = new HashMap<>();
                        for (int parseId : taggings.get(category)) {
                            parseToQAList.put(parseId, QuestionGenerator.generateAllQAPairs(predHead, argNum, sentence,
                                    allParses.get(parseId)));
                        }
                        Optional<OverlappingArguments> argOpt = findOverlappingArguments(predHead, category, argNum,
                                sentence, parseToQAList, allParses);
                        if (argOpt.isPresent()) {
                            ambiguities.add(argOpt.get());
                        }
                    }
                }
            }
            if (sid < 500 && ambiguities.size() > 0) {
                // Print
                System.out.println("SID=" + sid + "\t" + sentence.stream().collect(Collectors.joining(" ")));
                for (OverlappingArguments overlapArg : ambiguities) {
                    overlapArg.print(sentence, allParses);
                }
                System.out.println();
            }
            coveredQAs += ambiguities.stream().mapToInt(a -> a.numQAs).sum();
            numCases += ambiguities.size();
        }
        System.out.println("Found " + numCases + " coordination cases.");
        System.out.println(String.format("Covered %d QAs.", coveredQAs));
    }
}
