package edu.uw.easysrl.qasrl;

import com.google.common.collect.*;
import com.google.common.collect.Table.Cell;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;


/**
 * Query generator.
 * Created by luheng on 1/17/16.
 */
public class QueryGeneratorExperimental {

    /**
     * @param words the sentence
     * @param parses the nbest list
     * @param questionGenerator to generate wh-question from a resolved dependency
     * @param generatePseudoQuestions generate -NOQ- questions if set to true, for error analysis and brainstorming
     *                                about new question templates.
     * @param groupSameLabelDependencies set to true to consider multi-head dependencies (conjunctions and appositives)
     * @return a list of queries, filtered and sorted
     */
    public static List<GroupedQuery> generateQueries(final int sentenceId, final List<String> words,
                                                     final List<Parse> parses,
                                                     final QuestionGenerator questionGenerator,
                                                     boolean generatePseudoQuestions,
                                                     boolean groupSameLabelDependencies) {
        List<Query> unmergedQueryList = new ArrayList<>();
        List<GroupedQuery> groupedQueryList = new ArrayList<>();

        int numParses = parses.size();
        for (int rankId = 0; rankId < numParses; rankId++) {
            Parse parse = parses.get(rankId);

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
                int predicateId = entry.getRowKey();
                int argNum = entry.getColumnKey();
                Set<ResolvedDependency> dependencies = entry.getValue();

                ResolvedDependency anyDependency = dependencies.iterator().next();
                Category category = anyDependency.getCategory();

                ResolvedDependency dependency = dependencies.iterator().next();
                QuestionAnswerPair qaPair = questionGenerator.generateQuestion(dependency, words, parse);
                if (!generatePseudoQuestions && (qaPair == null || qaPair.questionWords.size() == 0)) {
                    continue;
                }
                String questionStr = (qaPair == null || qaPair.questionWords.size() == 0) ? "-NOQ-" :
                        qaPair.questionWords.stream().collect(Collectors.joining(" "));
                if (groupSameLabelDependencies) {
                    Set<Integer> answerIds = new HashSet<>();
                    dependencies.stream().forEach(dep ->
                            answerIds.addAll(AnswerGenerator.getArgumentIdsForDependency(words, parse, dep)));
                    List<Integer> answerIdList = new ArrayList<>(answerIds);
                    Collections.sort(answerIdList);
                    Query query = new Query(predicateId, category, argNum, answerIdList, rankId, qaPair);
                    unmergedQueryList.add(query);
                } else {
                    for (ResolvedDependency dep : dependencies) {
                        List<Integer> answerIds = AnswerGenerator.getArgumentIdsForDependency(words, parse, dep)
                                .stream().sorted().collect(Collectors.toList());
                        Query query = new Query(predicateId, category, argNum, answerIds, rankId, qaPair);
                        unmergedQueryList.add(query);
                    }
                }
            }
        }
        /************ Collapse queries **************/
        for (Query query : unmergedQueryList) {
            boolean merged = false;
            for (GroupedQuery groupedQuery : groupedQueryList) {
                if (groupedQuery.canMerge(query)) {
                    groupedQuery.add(query);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                groupedQueryList.add(new GroupedQuery(sentenceId, words, parses, query));
            }
        }
        groupedQueryList.forEach(groupedQuery -> collapseQuery(groupedQuery, words, parses));
        return groupedQueryList;
    }

    private static void collapseQuery(GroupedQuery groupedQuery, List<String> words, List<Parse> parses) {
        HashMap<String, Set<Integer>> questionToParses = new HashMap<>();
        HashMap<ImmutableList<Integer>, Set<Integer>> argSetToParses = new HashMap<>();

        groupedQuery.queries.forEach(query -> {
            ImmutableList<Integer> argList = ImmutableList.copyOf(query.argumentIds);
            if (!questionToParses.containsKey(query.question)) {
                questionToParses.put(query.question, new HashSet<>());
            }
            if (!argSetToParses.containsKey(argList)) {
                argSetToParses.put(argList, new HashSet<>());
            }
            questionToParses.get(query.question).add(query.parseId);
            argSetToParses.get(argList).add(query.parseId);
        });

        double bestQuestionScore = -1.0;
        String bestQuestion = "";
        Query representativeQuery = null;
        for (String question : questionToParses.keySet()) {
            double score = questionToParses.get(question).stream().mapToDouble(pid -> parses.get(pid).score).sum();
            if (score > bestQuestionScore) {
                bestQuestionScore = score;
                bestQuestion = question;
            }
        }
        for (Query query : groupedQuery.queries) {
            if (query.question.equals(bestQuestion)) {
                representativeQuery = query;
                break;
            }
        }
        assert representativeQuery != null;
        Map<String, Set<Integer>> spanToParses = new HashMap<>();
        Map<String, Set<Integer>> spanToArgIds = new HashMap<>();
        int predId = representativeQuery.predicateIndex;
        int argNum = representativeQuery.argumentNumber;
        argSetToParses.forEach((argSet, parseIds) -> {
            parseIds.forEach(parseId -> {
                Parse parse = parses.get(parseId);
                Category predCategory = parse.categories.get(predId);
                String answerSpan = AnswerGenerator.getAnswerSpan(parses.get(parseId), words, predId, predCategory,
                                                                  argSet, predCategory.getArgument(argNum));
                if (!spanToParses.containsKey(answerSpan)) {
                    spanToParses.put(answerSpan, new HashSet<>());
                }
                if (!spanToArgIds.containsKey(answerSpan)) {
                    spanToArgIds.put(answerSpan, new HashSet<>());
                }
                spanToParses.get(answerSpan).add(parseId);
                spanToArgIds.get(answerSpan).addAll(argSet);
            });
        });
        // Get answer spans: <span -> {arg_head, -> parse_ids}>
        /*
        Table<String, Integer, List<Integer>> answerSpansToParses = HashBasedTable.create();
        for (Query query : groupedQuery.queries) {
            int parseId = query.parseId;
            int predId = query.predicateIndex;
            Parse parse = groupedQuery.parses.get(parseId);
            Category predCategory = parse.categories.get(predId);
            Category argCategory = predCategory.getArgument(query.argumentNumber);
            for (int argId : query.argumentIds) {
                String span = AnswerGenerator.getAnswerSpan(parse, words, predId, predCategory, argId, argCategory);
                if (!answerSpansToParses.contains(span, argId)) {
                    answerSpansToParses.put(span, argId, new ArrayList<>());
                }
                answerSpansToParses.get(span, argId).add(parseId);
            }
        }
        // Debug
        System.out.println(words.stream().collect(Collectors.joining(" ")));
        System.out.println(bestQuery.question);
        for (String span : answerSpansToParses.rowKeySet()) {
            System.out.println("\t" + span);
            for (int argId : answerSpansToParses.row(span).keySet()) {
                System.out.println("\t\t" + argId + "\t" +
                        answerSpansToParses.get(span, argId).stream().sorted().map(String::valueOf)
                                .collect(Collectors.joining(",")));
            }
        }
        */
        // The old method.
        //Map<ImmutableList<Integer>, String> answerToSpans = AnswerGenerator.generateAnswerSpans(
        //        bestQuery.predicateIndex, argSetToParses, words, parses);
        //groupedQuery.collapse(bestQuery.predicateIndex, bestQuery.category, bestQuery.argumentNumber, bestQuestion,
        //        argSetToParses, answerToSpans);
        groupedQuery.collapseNew(representativeQuery.predicateIndex, representativeQuery.category,
                representativeQuery.argumentNumber, bestQuestion, spanToParses, spanToArgIds);
    }

}
