package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.syntax.grammar.Category;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;


/**
 * Query generator.
 * Created by luheng on 1/17/16.
 */
public class QueryGenerator {

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
                Optional<QuestionAnswerPair> qaPairOpt = questionGenerator.generateQuestion(dependency, words, parse);
                if (!generatePseudoQuestions && !qaPairOpt.isPresent()) {
                    continue;
                }
                String questionStr = qaPairOpt
                    .map(QuestionAnswerPair::renderQuestion)
                    .orElse("-NOQ-");
                QuestionAnswerPair qaPair = qaPairOpt.get();
                if (groupSameLabelDependencies) {
                    Set<Integer> answerIds = new HashSet<>();
                    dependencies.stream().forEach(dep ->
                            answerIds.addAll(TextGenerationHelper.getArgumentIdsForDependency(words, parse, dep)));
                    List<Integer> answerIdList = new ArrayList<>(answerIds);
                    Collections.sort(answerIdList);
                    Query query = new Query(predicateId, category, argNum, answerIdList, rankId, qaPairOpt.get());
                    unmergedQueryList.add(query);
                } else {
                    for (ResolvedDependency dep : dependencies) {
                        List<Integer> answerIds = TextGenerationHelper.getArgumentIdsForDependency(words, parse, dep)
                                .stream().sorted().collect(Collectors.toList());
                        Query query = new Query(predicateId, category, argNum, answerIds, rankId, qaPairOpt.get());
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
        groupedQueryList.forEach(groupedQuery -> collapseQuery(groupedQuery, parses));
        return groupedQueryList;
    }

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
    }

}
