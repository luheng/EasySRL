package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.*;

/**
 * Query generator.
 * Created by luheng on 1/17/16.
 */
@Deprecated
public class QueryGeneratorNew {

    /**
     * @param words the sentence
     * @param parses the nbest list
     * @param questionGenerator to generate wh-question from a resolved dependency
     * @return a list of queries, filtered and sorted
     */
    public static List<GroupedQuery> generateQueries(final int sentenceId, final List<String> words,
                                                     final List<Parse> parses,
                                                     final QuestionGenerator questionGenerator) {
        List<GroupedQuery> groupedQueryList = new ArrayList<>();
        int numParses = parses.size();

        // Debugging
        // System.out.println(words.stream().collect(Collectors.joining(" ")));
        for (int i = 0; i < words.size(); i++) {
            // For lambda.
            final int predId = i;
            Table<Category, Integer, Set<Integer>> depToParses = HashBasedTable.create();
            for (int j = 0; j < numParses; j++) {
                // For lambda.
                final int parseId = j;
                parses.get(parseId).dependencies.stream()
                        .filter(d -> d.getHead() == predId)
                        .forEach(dep -> {
                            Category category = dep.getCategory();
                            int argNum = dep.getArgNumber();
                            if (!depToParses.contains(category, argNum)) {
                                depToParses.put(category, argNum, new HashSet<>());
                            }
                            depToParses.get(category, argNum).add(parseId);
                        });
            }
            // Debugging
            // System.out.println("[predicate]:\t" + words.get(i));
            for (Category category : depToParses.rowKeySet()) {
                // Debugging
                // System.out.println("\t[category]:\t" + category);
                for (int argNum : depToParses.row(category).keySet()) {
                    TObjectDoubleHashMap<String> questionScores = new TObjectDoubleHashMap<>(),
                                                 answerScores = new TObjectDoubleHashMap<>();
                    // Generate a grouped query.
                    GroupedQuery groupedQuery = new GroupedQuery(sentenceId, words, parses);
                    for (int parseId : depToParses.get(category, argNum)) {
                        final Parse parse = parses.get(parseId);
                        Optional<QuestionAnswerPair> qaPairOpt = questionGenerator.generateQuestion(predId, argNum,
                                words, parse);
                        if (!qaPairOpt.isPresent()) {
                            continue;
                        }
                        QuestionAnswerPair qa = qaPairOpt.get();
                        String question = qa.renderQuestion(), answer = qa.renderAnswer();
                        // A very crude estimation.
                        questionScores.adjustOrPutValue(question, parse.score, parse.score);
                        answerScores.adjustOrPutValue(answer, parse.score, parse.score);
                        // Legacy.
                        Query query = new Query(qa, parseId);
                        groupedQuery.addQuery(query);
                    }
                    // Debugging
                    /* for (String question : questionScores.keySet()) {
                        System.out.println("\t\t\t" + question + "\t" + questionScores.get(question) / totalScores);
                    } */
                    // Legacy.
                    Map<ImmutableList<Integer>, String> argListToSpan = new HashMap<>();
                    Map<String, ImmutableList<Integer>> spanToArgList = new HashMap<>();
                    Map<String, Set<Integer>> spanToParseIds = new HashMap<>();
                    for (Query query : groupedQuery.queries) {
                        String answer = query.answer;
                        ImmutableList<Integer> argList = ImmutableList.copyOf(query.argumentIds);
                        if (!argListToSpan.containsKey(argList) ||
                                answerScores.get(answer) > answerScores.get(argListToSpan.get(argList))) {
                            argListToSpan.put(argList, answer);
                            spanToArgList.put(answer, argList);
                        }
                    }
                    for (Query query : groupedQuery.queries) {
                        ImmutableList<Integer> argList = ImmutableList.copyOf(query.argumentIds);
                        String answer = argListToSpan.get(argList);
                        if (!spanToParseIds.containsKey(answer)) {
                            spanToParseIds.put(answer, new HashSet<>());
                        }
                        spanToParseIds.get(answer).add(query.parseId);
                    }
                    double bestQuestionScore = Double.MIN_VALUE;
                    String bestQuestion = "";
                    for (String question : questionScores.keySet()) {
                        double score = questionScores.get(question);
                        if (score > bestQuestionScore) {
                            bestQuestion = question;
                            bestQuestionScore = score;
                        }
                    }
                    assert !bestQuestion.isEmpty();
                    groupedQuery.collapse(predId, category, argNum, bestQuestion, spanToArgList, spanToParseIds);
                    for (Query query : groupedQuery.queries) {
                        if (query.question.equals(bestQuestion)) {
                            groupedQuery.questionDependencies = new HashSet<>();
                            groupedQuery.questionDependencies.addAll(query.qaPair.questionDeps);
                            break;
                        }
                    }
                    groupedQueryList.add(groupedQuery);
                }
            }
        }
        return groupedQueryList;
    }
}
