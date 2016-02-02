package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;

import java.util.*;
import java.util.stream.Collectors;


/**
 * QueryOld generator.
 * Created by luheng on 1/17/16.
 */
public class QueryGenerator {

    /**
     * @param words the sentence
     * @param parses the nbest list
     * @return a list of queries, filtered and sorted
     */
    public static List<GroupedQuery> generateQueries(final int sentenceId, final List<String> words,
                                                     final List<Parse> parses,
                                                     final QuestionGenerator questionGenerator) {
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
                List<String> question = questionGenerator
                    .generateQuestion(dependency, words, parse);
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
                groupedQueryList.add(new GroupedQuery(sentenceId, numParses, query));
            }
        }
        groupedQueryList.forEach(GroupedQuery::collapse);
        return groupedQueryList;
    }

}
