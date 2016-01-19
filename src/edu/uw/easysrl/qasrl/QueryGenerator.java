package edu.uw.easysrl.qasrl;

import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;


/**
 * Query generator.
 * Created by luheng on 1/17/16.
 */
public class QueryGenerator {

    /**
     *
     * @param words the sentence
     * @param parses the nbest list
     * @return a list of queries, filtered and sorted
     */
    public static List<Query> generateQueries(final List<String> words, final List<Parse> parses,
                                              final QuestionGenerator questionGenerator,
                                              final boolean collapseQueries, final double minAnswerEntropy) {
        Map<String, Query> allQueries = new HashMap<>();
        int numParses = parses.size();
        for (int rankId = 0; rankId < numParses; rankId++) {
            Parse parse = parses.get(rankId);
            for (ResolvedDependency targetDependency : parse.dependencies) {
                int predicateId = targetDependency.getHead();
                int argumentId = targetDependency.getArgument();
                List<String> question = questionGenerator.generateQuestion(targetDependency, words, parse.categories,
                        parse.dependencies);
                if (question == null || question.size() == 0) {
                    continue;
                }
                String questionStr = StringUtils.join(question) + "\t" + predicateId;
                if (!allQueries.containsKey(questionStr)) {
                    allQueries.put(questionStr, new Query(question, predicateId, numParses));
                }
                Query query = allQueries.get(questionStr);
                /************* Generate candidate answers *************/
                Category answerCategory = targetDependency.getCategory().getArgument(targetDependency.getArgNumber());
                if (answerCategory.equals(Category.PP) || words.get(argumentId).equals("to")) {
                    List<Integer> children = parse.dependencies.stream().filter(dep -> dep.getHead() == argumentId)
                            .map(ResolvedDependency::getArgument)
                            .collect(Collectors.toList());
                    for (int c : children) {
                        query.addAnswer(c, rankId, 1.0 /* answer score */);
                    }
                } else {
                    query.addAnswer(argumentId, rankId, 1.0 /* answer score */);
                }
                // TODO: need to distinguish between multi-args and argument ambiguity from different parses.
            }
        }
        List<Query> queryList;
        /************ Collapse queries **************/
        if (collapseQueries) {
            Map<String, GroupedQuery> collapsed = new HashMap<>();
            for (Query query : allQueries.values()) {
                String queryKey = String.format("%d_%s", query.predicateIndex,
                        query.answerToParses.keySet().stream().map(String::valueOf).collect(Collectors.joining(",")));
                if (!collapsed.containsKey(queryKey)) {
                    collapsed.put(queryKey, new GroupedQuery(query.predicateIndex, query.numTotalParses,
                            query.answerToParses.keySet()));
                }
                collapsed.get(queryKey).addQuery(query);
            }
            // collapsed.values().forEach(q -> q.print(words));
            queryList = collapsed.values().stream().map(GroupedQuery::getQuery)
                    .filter(query -> isUseful(query) && getAnswerEntropy(query) > minAnswerEntropy)
                    .collect(Collectors.toList());
        } else {
            queryList = allQueries.values().stream()
                    .filter(query -> isUseful(query) && getAnswerEntropy(query) > minAnswerEntropy)
                    .collect(Collectors.toList());
        }
        // TODO: sort with lambda
        Collections.sort(queryList, new Comparator<Query>() {
            @Override
            public int compare(Query o1, Query o2) {
                if (o1.answerScores.size() < o2.answerScores.size()) {
                    return -1;
                }
                return o1.answerScores.size() == o2.answerScores.size() ? 0 : 1;
            }
        });
        return queryList;
    }

    public static boolean isUseful(final Query query) {
        // Not useful if everyone agree on the same thing.
        // Not useful if only the 1-best has opinions.
        int numParses = query.numTotalParses;
        for (Collection<Integer> parseIds : query.answerToParses.values()) {
            if (parseIds.size() > 0 && parseIds.size() < numParses &&
                    !(parseIds.size() == 1 && parseIds.contains(0 /* 1-best parse */))) {
                return true;
            }
        }
        // TODO: answer entropy
        return false;
    }

    public static boolean isReasonable(final Query query) {
        // Not reasonable if not enough parses propose that question.
        return query.answerToParses.values().stream().mapToInt(Collection::size).sum() > 0.1 * query.numTotalParses;
    }

    // FIXME: entropy > 1 ...
    public static double getAnswerEntropy(final Query query) {
        final Collection<Set<Integer>> parseIds = query.answerToParses.values();
        int sum = parseIds.stream().mapToInt(Collection::size).sum();
        return -1.0 * parseIds.stream().mapToDouble(Collection::size)
                .filter(d -> d > 0)
                .map(p -> p / sum * Math.log(p / sum)).sum();
    }

    public static double getAnswerEntropy(final GroupedQuery query) {
        final Collection<Set<Integer>> parseIds = query.answerToParses.values();
        int sum = parseIds.stream().mapToInt(Collection::size).sum();
        return -1.0 * parseIds.stream().mapToDouble(Collection::size)
                .filter(d -> d > 0)
                .map(p -> p / sum * Math.log(p / sum)).sum();
    }
}
