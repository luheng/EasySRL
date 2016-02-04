package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.*;
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
                List<String> question = questionGenerator.generateQuestion(dependency, words, parse);
                if (!generatePseudoQuestions && (question == null || question.size() == 0)) {
                    continue;
                }
                String questionStr = (question == null || question.size() == 0) ? "-NOQ-" :
                        question.stream().collect(Collectors.joining(" "));
                if (groupSameLabelDependencies) {
                    Set<Integer> answerIds = new HashSet<>();
                    dependencies.stream().forEach(dep ->
                            answerIds.addAll(AnswerGenerator.getArgumentIdsForDependency(words, parse, dep)));
                    List<Integer> answerIdList = new ArrayList<>(answerIds);
                    Collections.sort(answerIdList);
                    Query query = new Query(predicateId, category, argNum, answerIdList, rankId, questionStr);
                    unmergedQueryList.add(query);
                } else {
                    for (ResolvedDependency dep : dependencies) {
                        List<Integer> answerIds = AnswerGenerator.getArgumentIdsForDependency(words, parse, dep)
                                .stream().sorted().collect(Collectors.toList());
                        Query query = new Query(predicateId, category, argNum, answerIds, rankId, questionStr);
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

    // FIXME: make the logic better.
    private static void collapseQuery(GroupedQuery groupedQuery, List<String> words, List<Parse> parses) {
        HashMap<String, Set<Integer>> questionToParses = new HashMap<>();
        // Map the set of argument heads to parses.
        HashMap<ImmutableList<Integer>, Set<Integer>> argListToParses = new HashMap<>();
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
            questionToParses.get(query.question).add(query.parseId);
            argListToParses.get(argList).add(query.parseId);
        });

        // merge answer options
        double bestQuestionScore = -1.0;
        String bestQuestion = "";
        Query bestQuery = null;
        for (String question : questionToParses.keySet()) {
            double score = questionToParses.get(question).stream().mapToDouble(pid -> parses.get(pid).score).sum();
            if (score > bestQuestionScore) {
                bestQuestionScore = score;
                bestQuestion = question;
            }
        }
        for (Query query : groupedQuery.queries) {
            if (query.question.equals(bestQuestion)) {
                bestQuery = query;
                break;
            }
        }

        assert bestQuery != null;

        int predId = bestQuery.predicateIndex;
        int argNum = bestQuery.argumentNumber;
        Category predCategory = bestQuery.category;
        Category argCategory = predCategory.getArgument(argNum);
        argListToParses.forEach((argList, parseIds) -> {
            double argListScore = parseIds.stream().mapToDouble(id -> parses.get(id).score).sum();
            argListToScore.put(argList, argListScore);

            TObjectDoubleHashMap<String> spanToScore = new TObjectDoubleHashMap<>();
            parseIds.forEach(parseId -> {
                Parse parse = parses.get(parseId);
                String span = AnswerGenerator.getAnswerSpan(parse, words, predId, predCategory, argList, argCategory);
                spanToScore.adjustOrPutValue(span, parse.score, parse.score);
            });
            String bestSpan = "";
            double bestSpanScore = Double.MIN_VALUE;
            for (String span : spanToScore.keySet()) {
                double score = spanToScore.get(span);
                if (score > bestSpanScore) {
                    bestSpanScore = score;
                    bestSpan = span;
                }
            }

            if (!spanToArgList.containsKey(bestSpan) ||
                    (argListScore > argListToScore.get(spanToArgList.get(bestSpan)))) {
                spanToArgList.put(bestSpan, argList);
            }
            if (!spanToParses.containsKey(bestSpan)) {
                spanToParses.put(bestSpan, new HashSet<>());
            }
            spanToParses.get(bestSpan).addAll(parseIds);
        });
        groupedQuery.collapse(bestQuery.predicateIndex, bestQuery.category, bestQuery.argumentNumber, bestQuestion,
                              spanToArgList, spanToParses);
    }

}
