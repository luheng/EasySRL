package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.sun.org.apache.xpath.internal.operations.Mult;
import edu.stanford.nlp.util.ArraySet;
import edu.uw.easysrl.qasrl.AnalysisHelper;
import edu.uw.easysrl.qasrl.MultiQuery;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Created by luheng on 3/4/16.
 */
public class AnnotationHelper {

    private static void insertQA(Table<String, String, List<QuestionAnswerPairReduced>> qaTable, String s1,
                                 String s2, QuestionAnswerPairReduced qa) {
        if (!qaTable.contains(s1, s2)) {
            qaTable.put(s1, s2, new ArrayList<>());
        }
        qaTable.get(s1, s2).add(qa);
    }

    private static void insertParse(Table<String, String, Set<Parse>> qaTable, String s1, String s2, Parse parse) {
        if (!qaTable.contains(s1, s2)) {
            qaTable.put(s1, s2, new HashSet<>());
        }
        qaTable.get(s1, s2).add(parse);
    }

    private static void insertParseId(Map<String, Set<Integer>> qaTable, String s, int parseId) {
        if (!qaTable.containsKey(s)) {
            qaTable.put(s, new HashSet<>());
        }
        qaTable.get(s).add(parseId);
    }

    public static void populateScores(Map<String, Double> qaScores, Map<String, Set<Integer>> qaTable,
                                      List<Parse> allParses) {
        double totalScore = AnalysisHelper.getScore(allParses);
        qaTable.entrySet().forEach(e -> qaScores.put(e.getKey(),
                AnalysisHelper.getScore(e.getValue(), allParses) / totalScore));
    }

    public static List<MultiQuery> getAllQueries(int sentenceId, final List<String> sentence,
                                                 final List<Parse> allParses, int topK, double minQuestionScore) {
        List<MultiQuery> multiQueryList = new ArrayList<>();

        // For jeopardy style.
        Table<String, String, List<QuestionAnswerPairReduced>> answerToQuestionToQaPairs = HashBasedTable.create();
        Table<String, String, Set<Parse>> answerToQuestionToParses = HashBasedTable.create();
        Map<String, Set<Integer>> globalAnswerToParseIds = new HashMap<>();
        Map<String, Double> globalAnswerToScore = new HashMap<>();

        // Create forward-style questions.
        for (int predHead = 0; predHead < sentence.size(); predHead++) {
            Map<Category, Set<Integer>> taggings = new HashMap<>();
            for (int parseId = 0; parseId < allParses.size(); parseId ++) {
                Parse parse = allParses.get(parseId);
                Category category = parse.categories.get(predHead);
                if (!taggings.containsKey(category)) {
                    taggings.put(category, new HashSet<>());
                }
                taggings.get(category).add(parseId);
            }
            for (Category category : taggings.keySet()) {
                for (int argNum = 1; argNum <= category.getNumberOfArguments(); argNum++) {
                    Table<String, String, List<QuestionAnswerPairReduced>> qaStringsToQAPairs = HashBasedTable.create();
                    Table<String, String, Set<Parse>> qaStringsToParses = HashBasedTable.create();
                    Map<String, Set<Integer>> questionToParseIds = new HashMap<>();
                    Map<String, Set<Integer>> answerToParseIds = new HashMap<>();
                    Map<String, Double> questionToScore = new HashMap<>();
                    Map<String, Double> answerToScore = new HashMap<>();
                    for (int parseId : taggings.get(category)) {
                        QuestionGenerator.generateAllQAPairs(predHead, argNum, sentence, allParses.get(parseId))
                                .forEach(qa -> {
                                    String question = qa.renderQuestion();
                                    String answer = qa.renderAnswer();
                                    final Parse parse = allParses.get(parseId);

                                    questionToParseIds.get(question).add(parseId);
                                    answerToParseIds.get(answer).add(parseId);

                                    insertParseId(questionToParseIds, question, parseId);
                                    insertParseId(answerToParseIds, answer, parseId);
                                    insertParseId(globalAnswerToParseIds, answer, parseId);

                                    insertParse(qaStringsToParses, question, answer, parse);
                                    insertParse(answerToQuestionToParses, answer, question, parse);
                                    insertQA(qaStringsToQAPairs, question, answer, qa);
                                    insertQA(answerToQuestionToQaPairs, answer, question, qa);
                                });
                    }
                    // Filter binary queries.
                    if (answerToParseIds.size() < 2) {
                        continue;
                    }
                    // Compute question scores.
                    populateScores(questionToScore, questionToParseIds, allParses);
                    populateScores(answerToScore, answerToParseIds, allParses);
                    // Prune questions based on surface form score.
                    List<String> filteredQuestions = questionToScore.entrySet().stream()
                            .sorted((q1, q2) -> Double.compare(-q1.getValue(), -q2.getValue()))
                            .map(Entry::getKey)
                            .limit(topK)
                            .collect(Collectors.toList());
                    List<String> answers = new ArrayList<>(answerToScore.keySet());
                    for (int i = 0; i < filteredQuestions.size(); i++) {
                        String question = filteredQuestions.get(i);
                        if (i > 0 && questionToScore.get(question) < minQuestionScore) {
                            continue;
                        }
                        // Avoid NullPointer exception in MultiQuery..
                        answers.forEach(answer -> {
                            if (!qaStringsToParses.contains(question, answer)) {
                                qaStringsToParses.put(question, answer, new HashSet<>());
                                qaStringsToQAPairs.put(question, answer, new ArrayList<>());
                            }
                        });
                        multiQueryList.add(new MultiQuery.Forward(sentenceId, question, answerToScore.keySet(),
                                    qaStringsToQAPairs, qaStringsToParses));
                    }
                }
            }
        }
        // Create jeopardy-style questions.
        populateScores(globalAnswerToScore, globalAnswerToParseIds, allParses);
        
        return multiQueryList;
    }
}
