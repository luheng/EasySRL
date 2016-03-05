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
public class CoordinatedArguments {

    private static String[] connectingWords = { "and" , "or" , "," };

    // Look for coordination/appositive arguments.
    int predHead;
    Category category;
    int argNum;
    Map<String, Set<Parse>> questions;
    Table<ImmutableList<Integer>, String, Set<Parse>> answers;
    Map<ImmutableList<Integer>, String> annotations;
    Map<ImmutableList<Integer>, Double> answerHeadsToScore;
    int numQAs;

    private CoordinatedArguments() {
        questions = new HashMap<>();
        answers = HashBasedTable.create();
        annotations = new HashMap<>();
        numQAs = 0;
    }

    public static Optional<CoordinatedArguments> findCoordinatedArguments(int predHead, Category category, int argNum,
                List<String> sentence, Map<Integer, List<QuestionAnswerPairReduced>> parseToQAList,
                List<Parse> allParses) {
        CoordinatedArguments cordArg = new CoordinatedArguments();
        cordArg.predHead = predHead;
        cordArg.category = category;
        cordArg.argNum = argNum;
        for (int parseId : parseToQAList.keySet()) {
            final List<QuestionAnswerPairReduced> qaList = parseToQAList.get(parseId);
            if (qaList == null || qaList.size() == 0) {
                continue;
            }
            Map<Integer, String> argIdToSpan = new HashMap<>();
            parseToQAList.get(parseId).forEach(qa -> {
                argIdToSpan.put(qa.targetDep.getArgument(), qa.renderAnswer());
            });
            ImmutableList<Integer> argIds = ImmutableList.copyOf(argIdToSpan.keySet().stream()
                    .sorted()
                    .collect(Collectors.toList()));
            final Parse parse = allParses.get(parseId);
            String questionStr = parseToQAList.get(parseId).get(0).renderQuestion();
            String answerStr = argIds.stream().map(argIdToSpan::get)
                    .collect(Collectors.joining(QuestionAnswerPair.answerDelimiter));
            if (!cordArg.questions.containsKey(questionStr)) {
                cordArg.questions.put(questionStr, new HashSet<>());
            }
            cordArg.questions.get(questionStr).add(parse);
            if (!cordArg.answers.contains(argIds, answerStr)) {
                cordArg.answers.put(argIds, answerStr, new HashSet<>());
            }
            cordArg.answers.get(argIds, answerStr).add(parse);
            cordArg.numQAs += qaList.size();
        }

        // Compute scores for each arg head group.
        // TODO: move to a separate function.
        double totalScore = AnalysisHelper.getScore(allParses);
        cordArg.answerHeadsToScore = new HashMap<>();
        cordArg.answers.rowKeySet().stream()
                .forEach(argIds -> {
                    Set<Parse> parses = new HashSet<>();
                    cordArg.answers.row(argIds).entrySet().stream().forEach(a -> {
                        parses.addAll(a.getValue());
                    });
                    cordArg.answerHeadsToScore.put(argIds, AnalysisHelper.getScore(parses) / totalScore);
                });

        boolean hasCoordination = false;
        /**
         * Definition of pseudo-coordination: The answers have a dominant single head option.
         */
        boolean pseudoCoordination = false;
        for (ImmutableList<Integer> argIds : cordArg.answers.rowKeySet()) {
            double score = cordArg.answerHeadsToScore.get(argIds);
            if (argIds.size() == 1 && score > 0.7) {
                pseudoCoordination = true;
            }
            String annotation = "";
            if (argIds.size() > 1) {
                hasCoordination = true;
                for (int i = 1; i < argIds.size(); i++) {
                    for (int j = argIds.get(i - 1) + 1; j < argIds.get(i); j++) {
                        final String word = sentence.get(j);
                        for (String cw : connectingWords) {
                            if (word.equalsIgnoreCase(cw)) {
                                annotation += cw + " ";
                            }
                        }
                    }
                }
            }
            cordArg.annotations.put(argIds, annotation.trim());
        }
        //return hasCoordination ? Optional.of(cordArg) : Optional.empty();
        return hasCoordination && pseudoCoordination ? Optional.of(cordArg) : Optional.empty();
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
        answers.rowKeySet().stream()
                .sorted((a1, a2) -> Double.compare(-answerHeadsToScore.get(a1), -answerHeadsToScore.get(a2)))
                .forEach(argIds -> {
                    Map<String, Double> answerStringToScore = new HashMap<>();
                    answers.row(argIds).entrySet().stream().forEach(a -> {
                        answerStringToScore.put(a.getKey(), AnalysisHelper.getScore(a.getValue()) / totalScore);
                    });
                    // Print answer head score.
                    System.out.print(String.format("\t%s\t%.3f\t",
                            argIds.stream().map(id -> String.format("%s:%s", id, sentence.get(id)))
                                    .collect(Collectors.joining(",")),
                            answerHeadsToScore.get(argIds)));
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
            List<CoordinatedArguments> ambiguities = new ArrayList<>();
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
                        Optional<CoordinatedArguments> cordArg = findCoordinatedArguments(predHead, category, argNum,
                                sentence, parseToQAList, allParses);
                        if (cordArg.isPresent()) {
                            ambiguities.add(cordArg.get());
                        }
                    }
                }
            }
            if (sid < 500 && ambiguities.size() > 0) {
                // Print
                System.out.println("SID=" + sid + "\t" + sentence.stream().collect(Collectors.joining(" ")));
                for (CoordinatedArguments cordArg : ambiguities) {
                    cordArg.print(sentence, allParses);
                }
                System.out.println();
            }
            coveredQAs += ambiguities.stream().mapToInt(a -> a.numQAs).sum();
            numCases += ambiguities.size();
        }
        System.out.println("Found " + numCases + " pseudo-coordination cases.");
        System.out.println(String.format("Covered %d QAs.", coveredQAs));
    }
}
