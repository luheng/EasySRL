package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;


/**
 * QueryOld generator.
 * Created by luheng on 1/17/16.
 */
public class QueryGenerator {
    /**
     *
     * @param words the sentence
     * @param parses the nbest list
     * @return a list of queries, filtered and sorted
     */
    @Deprecated
    public static List<QueryOld> generateQueries(final List<String> words, final List<Parse> parses,
                                              final QuestionGenerator questionGenerator,
                                              final boolean collapseQueries, final double minAnswerEntropy) {
        Map<String, QueryOld> allQueries = new HashMap<>();
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
                    allQueries.put(questionStr, new QueryOld(question, predicateId, numParses));
                }
                QueryOld query = allQueries.get(questionStr);

                /************* Generate candidate answerOptions *************/
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

        /************* Debug: print questions, group by predicate ids ***********/
        System.out.println(words.stream().collect(Collectors.joining(" ")));
        List<Integer> allPredicates = allQueries.values().stream().map(q -> q.predicateIndex).distinct().sorted()
                .collect(Collectors.toList());
        for (int predId : allPredicates) {
            System.out.println(String.format("[pred]:\t%d\t%s", predId, words.get(predId)));
            allQueries.entrySet().stream().filter(e -> e.getValue().predicateIndex == predId).forEach(e2 -> {
                Set<String> labels = new HashSet<>();
                e2.getValue().answerToParses.entrySet().stream().filter(a -> a.getKey() >= 0).forEach(a2 -> {
                    a2.getValue().stream().sorted().forEach(r -> parses.get(r).dependencies.stream()
                                    .filter(d -> d.getHead() == predId && d.getArgument() == a2.getKey())
                                    //.forEach(d2 -> System.out.println(String.format("%d\t%s\t%d", r, d2.getCategory(), d2.getArgNumber())))
                                    .forEach(d2 -> labels.add(String.format("%s\t%d", d2.getCategory(), d2.getArgNumber())))
                    );
                });
                e2.getValue().print(words);
                System.out.println(labels.stream().collect(Collectors.joining("\t")));
                System.out.println();
            });
        }

        List<QueryOld> queryList;
        /************ Collapse queries **************/
        if (collapseQueries) {
            Map<String, GroupedQueryOld> collapsed = new HashMap<>();
            for (QueryOld query : allQueries.values()) {
                String queryKey = String.format("%d_%s", query.predicateIndex,
                        query.answerToParses.keySet().stream().map(String::valueOf).collect(Collectors.joining(",")));
                if (!collapsed.containsKey(queryKey)) {
                    collapsed.put(queryKey, new GroupedQueryOld(query.predicateIndex, query.numTotalParses,
                            query.answerToParses.keySet()));
                }
                collapsed.get(queryKey).addQuery(query);
            }
            // collapsed.values().forEach(q -> q.print(words));
            queryList = collapsed.values().stream().map(GroupedQueryOld::getQuery)
                    .filter(query -> isUseful(query) && getAnswerEntropy(query) > minAnswerEntropy)
                    .collect(Collectors.toList());
        } else {
            queryList = allQueries.values().stream()
                    .filter(query -> isUseful(query) && getAnswerEntropy(query) > minAnswerEntropy)
                    .collect(Collectors.toList());
        }
        /********** Sort questions. Not very useful now ***********/
        // TODO: sort with lambda
        Collections.sort(queryList, new Comparator<QueryOld>() {
            @Override
            public int compare(QueryOld o1, QueryOld o2) {
                if (o1.answerScores.size() < o2.answerScores.size()) {
                    return -1;
                }
                return o1.answerScores.size() == o2.answerScores.size() ? 0 : 1;
            }
        });
        return queryList;
    }

    /**
     *
     * @param words the sentence
     * @param parses the nbest list
     * @return a list of queries, filtered and sorted
     */
    public static List<GroupedQuery> generateQueries(final List<String> words, final List<Parse> parses,
                                                     final QuestionGenerator questionGenerator,
                                                     final double minAnswerEntropy) {
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
                ResolvedDependency dependency = dependencies.iterator().next();
                // FIXME: modify question generator to accept less info.
                List<String> question = questionGenerator.generateQuestion(dependency, words, parse.categories,
                        parse.dependencies);
                if (question == null || question.size() == 0) {
                    continue;
                }
                String questionStr = question.stream().collect(Collectors.joining(" "));
                Set<Integer> answerIds = new HashSet<>();
                dependencies.stream().forEach(dep -> {
                    answerIds.addAll(AnswerGenerator.getArgumentIds(words, parse, dep));

                });
                List<Integer> answerIdList = new ArrayList<>(answerIds);
                Collections.sort(answerIdList);

                List<String> answerSpans = new ArrayList<>();
                answerIdList.forEach(id -> {
                    Set<Integer> excludeIndices = new HashSet<>(answerIdList);
                    excludeIndices.add(predicateId);
                    excludeIndices.remove(id);
                    answerSpans.add(AnswerGenerator.getArgumentConstituent(words, parse.syntaxTree, id, excludeIndices));
                });
                // TODO: sort the answer spans.
                String answerStr = answerSpans.stream().collect(Collectors.joining(" and "));
                Query query = new Query(predicateId, dependency.getCategory(), argNum, answerIdList, rankId,
                                questionStr, answerStr);
                unmergedQueryList.add(query);
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
                groupedQueryList.add(new GroupedQuery(query, numParses));
            }
        }
        /********** Sort questions. Not very useful now ***********/
        groupedQueryList.forEach(GroupedQuery::collapse);
        // TODO: sort with lambda
        return groupedQueryList.stream()
                .filter(query -> getAnswerEntropy(query) > minAnswerEntropy)
                .collect(Collectors.toList());
    }

    public static boolean isUseful(final QueryOld query) {
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

    public static boolean isReasonable(final QueryOld query) {
        // Not reasonable if not enough parses propose that question.
        return query.answerToParses.values().stream().mapToInt(Collection::size).sum() > 0.1 * query.numTotalParses;
    }

    // FIXME: entropy > 1 ...
    public static double getAnswerEntropy(final QueryOld query) {
        final Collection<Set<Integer>> parseIds = query.answerToParses.values();
        int sum = parseIds.stream().mapToInt(Collection::size).sum();
        return -1.0 * parseIds.stream().mapToDouble(Collection::size)
                .filter(d -> d > 0)
                .map(p -> p / sum * Math.log(p / sum)).sum();
    }

    public static double getAnswerEntropy(final GroupedQueryOld query) {
        final Collection<Set<Integer>> parseIds = query.answerToParses.values();
        int sum = parseIds.stream().mapToInt(Collection::size).sum();
        return -1.0 * parseIds.stream().mapToDouble(Collection::size)
                .filter(d -> d > 0)
                .map(p -> p / sum * Math.log(p / sum)).sum();
    }

    public static double getAnswerEntropy(final GroupedQuery query) {
        final List<Integer> dist = query.answerOptions.stream().map(ao -> ao.parseIds.size())
                .collect(Collectors.toList());
        double sum = dist.stream().mapToDouble(d->d).sum();
        return -1.0 * dist.stream()
                .filter(d -> d > 0)
                .mapToDouble(p -> p / sum * Math.log(p / sum)).sum();
    }
}
