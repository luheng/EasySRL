package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.analysis.PPAttachment;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;
import jdk.nashorn.internal.runtime.Debug;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;


/**
 * Query generator.
 * Created by luheng on 1/17/16.
 */
public class QueryGenerator {

    public static List<MultiQuery> getAllMultiQueries(int sentenceId, final List<String> sentence,
                                                      final List<Parse> allParses,
                                                      QueryPruningParameters pruningParams) {
        final double totalScore = AnalysisHelper.getScore(allParses);
        List<MultiQuery> multiQueryList = new ArrayList<>();

        // For jeopardy style.
        Table<String, String, List<QuestionAnswerPairReduced>> globalStringsToQAPairs = HashBasedTable.create();
        Table<String, String, Set<Parse>> globalStringsToParses = HashBasedTable.create();
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

                                    insertParseId(questionToParseIds, question, parseId);
                                    insertParseId(answerToParseIds, answer, parseId);
                                    insertParseId(globalAnswerToParseIds, answer, parseId);

                                    insertParse(qaStringsToParses, question, answer, parse);
                                    insertParse(globalStringsToParses, question, answer, parse);
                                    insertQA(qaStringsToQAPairs, question, answer, qa);
                                    insertQA(globalStringsToQAPairs, question, answer, qa);
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
                            .limit(pruningParams.questionSurfaceFormTopK)
                            .collect(Collectors.toList());
                    List<String> answers = new ArrayList<>(answerToScore.keySet());
                    for (int i = 0; i < filteredQuestions.size(); i++) {
                        String question = filteredQuestions.get(i);
                        if (questionToScore.get(question) < pruningParams.minQuestionConfidence) {
                            continue;
                        }
                        // Avoid NullPointer exception in MultiQuery..
                        double entropy = .0;
                        for (String answer : answers) {
                            if (!qaStringsToParses.contains(question, answer)) {
                                qaStringsToParses.put(question, answer, new HashSet<>());
                                qaStringsToQAPairs.put(question, answer, new ArrayList<>());
                            } else {
                                qaStringsToParses.get(question, answer).forEach(parse ->
                                        insertParse(globalStringsToParses, question, answer, parse));
                                qaStringsToQAPairs.get(question, answer).forEach(qa ->
                                        insertQA(globalStringsToQAPairs, question, answer, qa));
                                double score = AnalysisHelper.getScore(qaStringsToParses.get(question, answer)) / totalScore;
                                if (score > 0) {
                                    entropy -= score * Math.log(score);
                                }
                            }
                        }
                        System.out.println(entropy);
                        if (entropy > pruningParams.minAnswerEntropy) {
                            multiQueryList.add(new MultiQuery.Forward(sentenceId, question, answerToScore.keySet(),
                                    qaStringsToQAPairs, qaStringsToParses));
                        }
                    }
                }
            }
        }
        // Create jeopardy-style questions.
        populateScores(globalAnswerToScore, globalAnswerToParseIds, allParses);
        // Find confident answer spans.
        List<String> sortedAnswers = globalAnswerToScore.entrySet().stream()
                .sorted((a1, a2) -> Double.compare(-a1.getValue(), -a2.getValue()))
                .map(Entry::getKey)
                .collect(Collectors.toList());
        for (String answer : sortedAnswers) {
            double score = globalAnswerToScore.get(answer);
            if (score < pruningParams.minQuestionConfidence) {
                continue;
            }
            Set<String> questions = globalStringsToParses.column(answer).entrySet().stream()
                    .filter(e -> AnalysisHelper.getScore(e.getValue()) > pruningParams.minAnswerConfidence * totalScore)
                    .map(Entry::getKey)
                    .collect(Collectors.toSet());
            if (questions.size() > 1) {
                multiQueryList.add(new MultiQuery.Backward(sentenceId, answer, questions, globalStringsToQAPairs,
                        globalStringsToParses));
            }
        }
        return multiQueryList;
    }

    public static List<GroupedQuery> getAllGroupedQueries(int sentenceId, final List<String> sentence,
                                                          final List<Parse> allParses,
                                                          QueryPruningParameters pruningParams) {
        List<GroupedQuery> queryList = new ArrayList<>();
        for (int predHead = 0; predHead < sentence.size(); predHead++) {
            Map<Category, Set<Integer>> taggings = new HashMap<>();
            /***** Analysis. ****/
            /*
            Map<String, Set<Integer>> labelToArgIds = new HashMap<>();
            Map<String, Set<String>> labelToQuestions = new HashMap<>();
            Map<String, Set<Integer>> labelToParseIds = new HashMap<>();
            boolean predicateHasQuestion = false;
            boolean predicateIsPP = false;
            boolean predicateHasSkippedQuestion = false;
            */

            for (int parseId = 0; parseId < allParses.size(); parseId ++) {
                Parse parse = allParses.get(parseId);
                Category category = parse.categories.get(predHead);
                if (!taggings.containsKey(category)) {
                    taggings.put(category, new HashSet<>());
                }
                taggings.get(category).add(parseId);
            }
            /************** For analysis. *******/
            /*
            for (Category category : taggings.keySet()) {
                for (int argNum = 1; argNum <= category.getNumberOfArguments(); argNum++) {
                    String label = category + "." + argNum;
                    labelToArgIds.put(label, new HashSet<>());
                    labelToQuestions.put(label, new HashSet<>());
                    labelToParseIds.put(label, new HashSet<>());
                }
            }
            */
            /***********************************/
            for (Category category : taggings.keySet()) {
                for (int argNum = 1; argNum <= category.getNumberOfArguments(); argNum++) {
                    Map<String, Set<Integer>> questionToParseIds = new HashMap<>();
                    Map<String, Double> questionToScore = new HashMap<>();
                    Table<ImmutableList<Integer>, String, Set<Integer>> answerToParseIds = HashBasedTable.create();
                    Table<ImmutableList<Integer>, String, Double> answerToScore = HashBasedTable.create();

                    for (int parseId : taggings.get(category)) {
                        List<QuestionAnswerPairReduced> qaList = QuestionGenerator.generateAllQAPairs(predHead, argNum,
                                sentence, allParses.get(parseId));

                        /************** For analysis. *******/
                        /*
                        if (qaList.size() > 0) {
                            predicateHasQuestion = true;
                        } else {
                            predicateHasSkippedQuestion = true;
                        }
                        if (category == PPAttachment.nounAdjunct || category == PPAttachment.verbAdjunct) {
                            predicateIsPP = true;
                        }
                        String label = category + "." + argNum;
                        for (ResolvedDependency dep : allParses.get(parseId).dependencies) {
                            if (dep.getHead() == predHead && dep.getCategory() == category &&
                                    dep.getArgNumber() == argNum) {
                                labelToArgIds.get(label).add(dep.getArgument());
                            }
                        }
                        qaList.forEach(qa -> labelToQuestions.get(label).add(qa.renderQuestion()));
                        labelToParseIds.get(label).add(parseId);
                        */
                        /***********************************/

                        // Get concatenated answer.
                        Map<Integer, String> argIdToSpan = new HashMap<>();
                        qaList.forEach(qa -> argIdToSpan.put(qa.targetDep.getArgument(), qa.renderAnswer()));
                        ImmutableList<Integer> argIds = ImmutableList.copyOf(argIdToSpan.keySet().stream()
                                .sorted()
                                .collect(Collectors.toList()));
                        if (argIds.size() == 0) {
                            qaList.forEach(qa -> System.err.println(qa.renderQuestion() + "\t" + qa.renderAnswer()));
                            continue;
                        }
                        String answer = argIds.stream()
                                .map(argIdToSpan::get)
                                .collect(Collectors.joining(QuestionAnswerPair.answerDelimiter));
                        //answerToArgIds.put(answer, argIds);
                        insertParseId(answerToParseIds, argIds, answer, parseId);
                        // Get questions surface forms.
                        qaList.forEach(qa -> insertParseId(questionToParseIds, qa.renderQuestion(), parseId));
                    }
                    // Compute question scores.
                    populateScores(answerToScore, answerToParseIds, allParses);
                    populateScores(questionToScore, questionToParseIds, allParses);
                    // Get most probable answer.
                    Map<String, Set<Integer>> answerStringToParseIds = new HashMap<>();
                    Map<String, ImmutableList<Integer>> answerStringToArgIds = new HashMap<>();
                    Map<String, Double> answerStringToScore = new HashMap<>();
                    double answerScoreSum = .0,
                           answerEntropy = .0;
                    for (ImmutableList<Integer> argList : answerToScore.rowKeySet()) {
                        String answer = getBestSurfaceForm(answerToScore.row(argList));
                        double score = answerToScore.row(argList).values().stream().mapToDouble(s -> s).sum();
                        if (score > pruningParams.minAnswerConfidence) {
                            answerScoreSum += score;
                            answerStringToScore.put(answer, score);
                            answerStringToArgIds.put(answer, argList);

                            if (!answerStringToArgIds.containsKey(answer)) {
                                answerStringToArgIds.put(answer, argList);
                            } else {
                                // FIXME: this is a hack. Ideally we want a disjunctive set of arg ids.
                                Set<Integer> extendedArgList = new HashSet<>(answerStringToArgIds.get(answer));
                                extendedArgList.addAll(argList);
                                answerStringToArgIds.put(answer, ImmutableList.copyOf(extendedArgList.stream().sorted()
                                        .collect(Collectors.toList())));
                            }

                            answerToParseIds.row(argList).values()
                                    .forEach(parseIds -> parseIds
                                            .forEach(pid -> insertParseId(answerStringToParseIds, answer, pid)));
                        }
                    }
                    for (String answer : answerStringToScore.keySet()) {
                        double p = answerStringToScore.get(answer) / answerScoreSum;
                        answerEntropy -= (p > 0 ? p * Math.log(p) : 0);
                    }
                    // Filter unambiguous attachments.
                    if (answerStringToScore.size() < 2 || answerEntropy < pruningParams.minAnswerEntropy) {
                        continue;
                    }
                    // Get question surface forms.
                    List<String> filteredQuestions = questionToScore.entrySet().stream()
                            .sorted((q1, q2) -> Double.compare(-q1.getValue(), -q2.getValue()))
                            .filter(q -> q.getValue() > pruningParams.minQuestionConfidence)
                            .limit(pruningParams.questionSurfaceFormTopK)
                            .map(Entry::getKey)
                            .collect(Collectors.toList());
                    for (String question : filteredQuestions) {
                        GroupedQuery query = new GroupedQuery(sentenceId, sentence, allParses);
                        query.collapse(predHead, category, argNum, question, answerStringToArgIds,
                                       answerStringToParseIds);
                        queryList.add(query);
                    }
                    /******** analysis ***********/
                    /*
                    System.out.println("SID=" + sentenceId + "\t" + sentence.stream().collect(Collectors.joining(" ")));
                    filteredQuestions.forEach(System.out::println);
                    for (ImmutableList<Integer> argList : answerToScore.rowKeySet()) {
                        System.out.println("\t" + DebugPrinter.getShortListString(argList));
                        for (String answer : answerToScore.row(argList).keySet()) {
                            System.out.println("\t\t" + answer + "\t" + answerToScore.get(argList, answer));
                        }
                    }
                    // Actual answers.
                    for (String answer : answerStringToScore.keySet()) {
                        System.out.println("\t" + answer + "\t" +
                                DebugPrinter.getShortListString(answerStringToArgIds.get(answer)) + "\t" +
                                answerStringToScore.get(answer));
                    }
                    System.out.println();
                    */
                }
            }
            /********** For analysis. *******/
            /*
            if (predicateHasQuestion && predicateHasSkippedQuestion && !predicateIsPP) {
                double totalScore = AnalysisHelper.getScore(allParses);
                System.out.println("SID=" + sentenceId + "\t" + sentence.stream().collect(Collectors.joining(" ")));
                for (String label : labelToArgIds.keySet()) {
                    double score = AnalysisHelper.getScore(labelToParseIds.get(label), allParses) / totalScore;
                    if (labelToQuestions.get(label).isEmpty()) {
                        System.out.println(predHead + ":" + sentence.get(predHead) + "\t" + label + "\t" + score);
                        for (int argId : labelToArgIds.get(label)) {
                            System.out.println("\t" + argId + ":" + sentence.get(argId));
                        }
                    } else {
                        System.out.println(predHead + ":" + sentence.get(predHead) + "\t" + label + "\t" + score);
                        for (String question : labelToQuestions.get(label)) {
                            System.out.println(question);
                        }
                        for (int argId : labelToArgIds.get(label)) {
                            System.out.println("\t" + argId + ":" + sentence.get(argId));
                        }
                    }
                }
                System.out.println();
            }
            */
        }
        return queryList;
    }

    public static List<GroupedQuery> getAllGroupedQueriesCheckbox(int sentenceId, final List<String> sentence,
                                                                  final List<Parse> allParses,
                                                                  QueryPruningParameters pruningParams) {
        List<GroupedQuery> queryList = new ArrayList<>();
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
                    Map<String, Set<Integer>> questionToParseIds = new HashMap<>();
                    Map<String, Double> questionToScore = new HashMap<>();
                    Table<ImmutableList<Integer>, String, Set<Integer>> answerToParseIds = HashBasedTable.create();
                    Table<ImmutableList<Integer>, String, Double> answerToScore = HashBasedTable.create();
                    for (int parseId : taggings.get(category)) {
                        List<QuestionAnswerPairReduced> qaList = QuestionGenerator.generateAllQAPairs(predHead, argNum,
                                sentence, allParses.get(parseId));
                        Map<Integer, String> argIdToSpan = new HashMap<>();
                        qaList.forEach(qa -> argIdToSpan.put(qa.targetDep.getArgument(), qa.renderAnswer()));
                        List<Integer> argIds = argIdToSpan.keySet().stream().sorted().collect(Collectors.toList());
                        if (argIds.size() == 0) {
                            continue;
                        }
                        argIdToSpan.entrySet().forEach(e -> {
                            insertParseId(answerToParseIds, ImmutableList.of(e.getKey()), e.getValue(), parseId);
                            qaList.forEach(qa -> insertParseId(questionToParseIds, qa.renderQuestion(), parseId));
                        });
                    }
                    // Compute question scores.
                    populateScores(answerToScore, answerToParseIds, allParses);
                    populateScores(questionToScore, questionToParseIds, allParses);
                    // Get most probable answer.
                    Map<String, Set<Integer>> answerStringToParseIds = new HashMap<>();
                    Map<String, ImmutableList<Integer>> answerStringToArgIds = new HashMap<>();
                    Map<String, Double> answerStringToScore = new HashMap<>();
                    double answerScoreSum = .0,
                           answerEntropy = .0;
                    for (ImmutableList<Integer> argList : answerToScore.rowKeySet()) {
                        String answer = getBestSurfaceForm(answerToScore.row(argList));
                        double score = answerToScore.row(argList).values().stream().mapToDouble(s -> s).sum();
                        answerScoreSum += score;
                        answerStringToScore.put(answer, score);
                        //if (!answerStringToArgIds.containsKey(answer)) {
                        answerStringToArgIds.put(answer, argList);
                        /*} else {
                            // FIXME: this is a hack. Ideally we want a disjunctive set of arg ids.
                            Set<Integer> extendedArgList = new HashSet<>(answerStringToArgIds.get(answer));
                            extendedArgList.addAll(argList);
                            answerStringToArgIds.put(answer, ImmutableList.copyOf(extendedArgList.stream().sorted()
                                    .collect(Collectors.toList())));
                        }*/
                        answerToParseIds.row(argList).values()
                                .forEach(parseIds -> parseIds
                                        .forEach(pid -> insertParseId(answerStringToParseIds, answer, pid)));
                    }
                    for (String answer : answerStringToScore.keySet()) {
                        double p = answerStringToScore.get(answer) / answerScoreSum;
                        answerEntropy -= (p > 0 ? p * Math.log(p) : 0);
                    }
                    // Filter unambiguous attachments.
                    if (answerStringToScore.size() < 2 || answerEntropy < pruningParams.minAnswerEntropy) {
                        continue;
                    }
                    // Get question surface forms.
                    List<String> filteredQuestions = questionToScore.entrySet().stream()
                            .sorted((q1, q2) -> Double.compare(-q1.getValue(), -q2.getValue()))
                            .filter(q -> q.getValue() > pruningParams.minQuestionConfidence)
                            .limit(pruningParams.questionSurfaceFormTopK)
                            .map(Entry::getKey)
                            .collect(Collectors.toList());
                    for (String question : filteredQuestions) {
                        GroupedQuery query = GroupedQuery.makeQuery(sentenceId, sentence, allParses, predHead,
                                category, argNum, question, answerStringToArgIds, answerStringToParseIds,
                                true /* allow multiple */, false /* jeopardy style */);
                        queryList.add(query);
                    }
                }
            }
        }
        return queryList;
    }

    public static String getBestSurfaceForm(Map<String, Double> qaToScore) {
        Entry<String, Double> best = qaToScore.entrySet().stream()
                .max((e1, e2) -> Double.compare(e1.getValue(), e2.getValue()))
                //.min((e1, e2) -> Integer.compare(e1.getKey().length(), e2.getKey().length()))
                .get();
        return best.getKey();
    }

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

    private static void insertParseId(Table<ImmutableList<Integer>, String, Set<Integer>> qaTable,
                                      ImmutableList<Integer> o, String s, int parseId) {
        if (!qaTable.contains(o, s)) {
            qaTable.put(o, s, new HashSet<>());
        }
        qaTable.get(o, s).add(parseId);
    }

    private static void populateScores(Map<String, Double> qaScores, Map<String, Set<Integer>> qaTable,
                                       List<Parse> allParses) {
        double totalScore = AnalysisHelper.getScore(allParses);
        qaTable.entrySet().forEach(e -> qaScores.put(e.getKey(),
                AnalysisHelper.getScore(e.getValue(), allParses) / totalScore));
    }

    private static void populateScores(Table<ImmutableList<Integer>, String, Double> qaScores,
                                       Table<ImmutableList<Integer>, String, Set<Integer>> qaTable,
                                       List<Parse> allParses) {
        double totalScore = AnalysisHelper.getScore(allParses);
        qaTable.rowKeySet().forEach(row -> {
            qaTable.row(row).entrySet().forEach(col ->
                    qaScores.put(row, col.getKey(), AnalysisHelper.getScore(col.getValue(), allParses) / totalScore));
        });
    }

    /******* Old stuff ********/

    /**
     * @param words the sentence
     * @param parses the nbest list
     * @param questionGenerator to generate wh-question from a resolved dependency
     * @param generatePseudoQuestions generate -NOQ- questions if set to true, for error analysis and brainstorming
     *                                about new question templates.
     * @return a list of queries, filtered and sorted
     */
    @Deprecated
    public static List<GroupedQuery> generateQueries(final int sentenceId, final List<String> words,
                                                     final List<Parse> parses,
                                                     final QuestionGenerator questionGenerator,
                                                     boolean generatePseudoQuestions) {
        List<Query> unmergedQueries = new ArrayList<>();
        List<GroupedQuery> groupedQueryList = new ArrayList<>();

        int numParses = parses.size();
        for (int parseId = 0; parseId < numParses; parseId++) {
            Parse parse = parses.get(parseId);
            /**** group dependency by predicate id *******/
            Table<Integer, Integer, Set<ResolvedDependency>> groupedDependencies = HashBasedTable.create();
            parse.dependencies.forEach(dependency -> {
                int predicateId = dependency.getHead();
                int argNum = dependency.getArgNumber();
                if (!groupedDependencies.contains(predicateId, argNum)) {
                    groupedDependencies.put(predicateId, argNum, new HashSet<>());
                }
                groupedDependencies.get(predicateId, argNum).add(dependency);
            });
            /**** generate queries for each predicate-argNum pair *******/
            for (Cell<Integer, Integer, Set<ResolvedDependency>> entry : groupedDependencies.cellSet()) {
                Set<ResolvedDependency> dependencies = entry.getValue();
                ResolvedDependency anyDependency = dependencies.iterator().next();
                Optional<QuestionAnswerPair> qaPairOpt = questionGenerator.generateQuestion(anyDependency, words, parse);
                if (!generatePseudoQuestions && !qaPairOpt.isPresent()) {
                    continue;
                }
                unmergedQueries.add(new Query(qaPairOpt.get(), parseId));
            }
        }
        /************ Collapse queries **************/
        for (Query query : unmergedQueries) {
            boolean merged = false;
            for (GroupedQuery groupedQuery : groupedQueryList) {
                if (groupedQuery.canMerge(query)) {
                    groupedQuery.addQuery(query);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                groupedQueryList.add(new GroupedQuery(sentenceId, words, parses, query));
            }
        }
        /*
        double[] parseDist = new double[parses.size()];
        double norm = parses.stream().mapToDouble(p -> p.score).sum();
        for (int i = 0; i < parses.size(); i++) {
            parseDist[i] = parses.get(i).score / norm;
        }
        */
        groupedQueryList.forEach(groupedQuery -> collapseQuery(groupedQuery, parses));
        return groupedQueryList;
    }

    @Deprecated
    private static void collapseQuery(GroupedQuery groupedQuery, List<Parse> parses) {
        HashMap<String, Set<Integer>> questionToParses = new HashMap<>();
        Table<ImmutableList<Integer>, String, Set<Integer>> argListToSpanToParses = HashBasedTable.create();
        Map<ImmutableList<Integer>, Set<Integer>> argListToParses = new HashMap<>();

        // Map the set of argument heads (argList) to its score (sum of parse scores)
        Map<ImmutableList<Integer>, Double> argListToScore = new HashMap<>();
        // Map a surface string of an answer to its most probable arg list.
        Map<String, ImmutableList<Integer>> spanToArgList = new HashMap<>();
        // Map a surface string of an answer to a set of parse ids.
        Map<String, Set<Integer>> spanToParses = new HashMap<>();

        Map<String, Query> spanToQuery = new HashMap<>();

        groupedQuery.queries.forEach(query -> {
            ImmutableList<Integer> argList = ImmutableList.copyOf(query.argumentIds);
            if (!questionToParses.containsKey(query.question)) {
                questionToParses.put(query.question, new HashSet<>());
            }
            if (!argListToParses.containsKey(argList)) {
                argListToParses.put(argList, new HashSet<>());
            }
            if (!argListToSpanToParses.contains(argList, query.answer)) {
                argListToSpanToParses.put(argList, query.answer, new HashSet<>());
            }
            argListToSpanToParses.get(argList, query.answer).add(query.parseId);
            questionToParses.get(query.question).add(query.parseId);
            argListToParses.get(argList).add(query.parseId);
        });

        // Get most representative question.
        double bestQuestionScore = -1.0;
        String bestQuestion = "";
        Query bestQuery = null;
        for (Entry<String, Set<Integer>> entry : questionToParses.entrySet()) {
            final String question = entry.getKey();
            final Set<Integer> parseIds = entry.getValue();
            double questionScore = parseIds.stream().mapToDouble(pid -> parses.get(pid).score).sum();
            if (questionScore > bestQuestionScore) {
                bestQuestionScore = questionScore;
                bestQuestion = question;
            }
        }
        for (Query query : groupedQuery.queries) {
            if (query.question.equals(bestQuestion)) {
                bestQuery = query;
                break;
            }
        }

        // Get best set of answer strings.
        argListToSpanToParses.rowKeySet().forEach(argList -> {
            double argListScore = 0.0;
            double bestSpanScore = Double.MIN_VALUE;
            String bestSpan = "";
            for (Entry<String, Set<Integer>> entry : argListToSpanToParses.row(argList).entrySet()) {
                final String span = entry.getKey();
                final Set<Integer> parseIds = entry.getValue();
                double spanScore = parseIds.stream().mapToDouble(id -> parses.get(id).score).sum();
                argListScore += spanScore;
                if (spanScore > bestSpanScore) {
                    bestSpanScore = spanScore;
                    bestSpan = span;
                }
            }
            argListToScore.put(argList, argListScore);
            if (!spanToArgList.containsKey(argList)) {
                spanToArgList.put(bestSpan, argList);
            } else {
                ImmutableList<Integer> otherArgList = spanToArgList.get(bestSpan);
                if (argListScore > argListToScore.get(otherArgList)) {
                    spanToArgList.put(bestSpan, argList);
                }
            }
            if (!spanToParses.containsKey(bestSpan)) {
                spanToParses.put(bestSpan, new HashSet<>());
            }
            for (Set<Integer> parseIds : argListToSpanToParses.row(argList).values()) {
                spanToParses.get(bestSpan).addAll(parseIds);
            }
        });

        assert bestQuery != null;
        groupedQuery.collapse(bestQuery.predicateIndex, bestQuery.category, bestQuery.argumentNumber, bestQuestion,
                spanToArgList, spanToParses);
        //groupedQuery.questionDependencies = bestQuery.qaPair.questionDeps;
    }

    @Deprecated
    private static void collapseQuery2(GroupedQuery groupedQuery, List<Parse> parses, final double[] parseDist) {
        HashMap<String, Set<Integer>> questionToParses = new HashMap<>();
        Table<Integer, String, Set<Integer>> argHeadToSpanToParses = HashBasedTable.create();
        Map<Integer, Set<Integer>> argHeadToParses = new HashMap<>();
        Map<Integer, String> argHeadToSpan = new HashMap<>();

        // Map the set of argument heads (argList) to its score (sum of parse scores)
        Map<Integer, Double> argHeadToScore = new HashMap<>();
        // Map a surface string of an answer to its most probable arg list.
        Map<String, ImmutableList<Integer>> spanToArgList = new HashMap<>();
        // Map a surface string of an answer to a set of parse ids.
        Map<String, Set<Integer>> spanToParses = new HashMap<>();

        groupedQuery.queries.forEach(query -> {
            final String question = query.question;
            if (!questionToParses.containsKey(question)) {
                questionToParses.put(question, new HashSet<>());
            }
            questionToParses.get(question).add(query.parseId);
            for (int i = 0; i < query.argumentIds.size(); i++) {
                int argHead = query.argumentIds.get(i);
                String span = TextGenerationHelper.renderString(query.qaPair.answers.get(i));
                if (!argHeadToParses.containsKey(argHead)) {
                    argHeadToParses.put(argHead, new HashSet<>());
                }
                if (!argHeadToSpanToParses.contains(argHead, span)) {
                    argHeadToSpanToParses.put(argHead, span, new HashSet<>());
                }
                argHeadToParses.get(argHead).add(query.parseId);
                argHeadToSpanToParses.get(argHead, span).add(query.parseId);
            }
        });

        // Get most representative question.
        double bestQuestionScore = -1.0;
        String bestQuestion = "";
        Query bestQuery = null;
        for (Entry<String, Set<Integer>> entry : questionToParses.entrySet()) {
            final String question = entry.getKey();
            final Set<Integer> parseIds = entry.getValue();
            double questionScore = parseIds.stream().mapToDouble(pid -> parses.get(pid).score).sum();
            if (questionScore > bestQuestionScore) {
                bestQuestionScore = questionScore;
                bestQuestion = question;
            }
        }
        for (Query query : groupedQuery.queries) {
            if (query.question.equals(bestQuestion)) {
                bestQuery = query;
                break;
            }
        }

        // Get more representative answer span for each argHead.
        argHeadToSpanToParses.rowKeySet().forEach(argHead -> {
            double bestSpanScore = Double.MIN_VALUE;
            String bestSpan = "";
            for (Entry<String, Set<Integer>> entry : argHeadToSpanToParses.row(argHead).entrySet()) {
                final String span = entry.getKey();
                final Set<Integer> parseIds = entry.getValue();
                double spanScore = parseIds.stream().mapToDouble(id -> parseDist[id]).sum();
                if (spanScore > bestSpanScore) {
                    bestSpanScore = spanScore;
                    bestSpan = span;
                }
            }
            argHeadToSpan.put(argHead, bestSpan);
        });

        // Get span to argList and span to parses
        groupedQuery.queries.forEach(query -> {
            ImmutableList<Integer> argList = ImmutableList.copyOf(query.argumentIds);
            String span = argList.stream().map(argHeadToSpan::get).collect(Collectors.joining(" AND "));
            if (!spanToArgList.containsKey(span)) {
                spanToArgList.put(span, argList);
            }
            if (!spanToParses.containsKey(span)) {
                spanToParses.put(span, new HashSet<>());
            }
            spanToParses.get(span).add(query.parseId);
        });

        assert bestQuery != null;
        groupedQuery.collapse(bestQuery.predicateIndex, bestQuery.category, bestQuery.argumentNumber, bestQuestion,
                spanToArgList, spanToParses);
    }

}
