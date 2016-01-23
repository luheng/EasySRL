package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.syntax.grammar.Category;
import jdk.nashorn.internal.runtime.Debug;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Grouped query ...
 * Created by luheng on 1/21/16.
 */
public class GroupedQuery {
    class AnswerOption {
        ImmutableList<Integer> argumentIds;
        String answer;
        Set<Integer> parseIds;

        AnswerOption(ImmutableList<Integer> argumentIds, String answer, Set<Integer> parseIds) {
            this.argumentIds = argumentIds;
            this.answer = answer;
            this.parseIds = parseIds;
        }
    }

    // should represent the latent role here.
    int totalNumParses;
    Set<Query> queries;
    boolean collapsed;

    // Information specified only after collapsing;
    int predicateIndex;
    Category category;
    int argumentNumber;

    String question;
    List<AnswerOption> answerOptions;

    public GroupedQuery(int numParses) {
        totalNumParses = numParses;
        queries = new HashSet<>();
        collapsed = false;
    }

    public GroupedQuery(Query query, int numParses) {
        this(numParses);
        queries.add(query);
    }

    public boolean canMerge(Query query) {
        for (Query q : queries) {
            if (canMerge(q, query)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canMerge(Query q1, Query q2) {
        if (q1.predicateIndex == q2.predicateIndex) {
            if (q1.question.equals(q2.question)) {
                return true;
            }
            if (q1.category == q2.category && q1.argumentNumber == q2.argumentNumber) {
                return true;
            }
            // Fuzzy match.
            /*
            int argNum1 = q1.argumentNumber == 1 ? 1 : q1.argumentNumber - q1.category.getNumberOfArguments();
            int argNum2 = q2.argumentNumber == 1 ? 1 : q2.argumentNumber - q2.category.getNumberOfArguments();
            if (argNum1 == argNum2 && q1.argumentIds.stream().filter(q2.argumentIds::contains).count() > 0) {
                return true;
            }
            */
        }
        return false;
    }

    public void add(Query query) {
        queries.add(query);
    }

    public void collapse() {
        HashMap<String, Set<Integer>> questionToParses = new HashMap<>();
        HashMap<ImmutableList<Integer>, Set<Integer>> answerToParses = new HashMap<>();
        HashMap<ImmutableList<Integer>, String> answerToSpan = new HashMap<>();
        queries.forEach(query -> {
            ImmutableList<Integer> argList = ImmutableList.copyOf(query.argumentIds);
            if (!questionToParses.containsKey(query.question)) {
                questionToParses.put(query.question, new HashSet<>());
            }
            if (!answerToParses.containsKey(argList)) {
                answerToParses.put(argList, new HashSet<>());
            }
            questionToParses.get(query.question).add(query.parseId);
            answerToParses.get(argList).add(query.parseId);
            answerToSpan.put(argList, query.answer);
        });

        // merge answer options
        // TODO: debug
        double bestQuestionScore = -1.0;
        String bestQuestion = "";
        Query bestQuery = null;
        for (String question : questionToParses.keySet()) {
            double score = questionToParses.get(question).size();
            if (score > bestQuestionScore) {
                bestQuestionScore = score;
                bestQuestion = question;
            }
        }
        for (Query query : queries) {
            if (query.question.equals(bestQuestion)) {
                bestQuery = query;
                break;
            }
        }
        predicateIndex = bestQuery.predicateIndex;
        category = bestQuery.category;
        argumentNumber = bestQuery.argumentNumber;
        question = bestQuestion;
        answerOptions = new ArrayList<>();
        Set<Integer> allParseIds = IntStream.range(0, totalNumParses).boxed().collect(Collectors.toSet());
        answerToParses.keySet().forEach(argList -> {
            Set<Integer> parseIds = answerToParses.get(argList);
            answerOptions.add(new AnswerOption(argList, answerToSpan.get(argList), parseIds));
            allParseIds.removeAll(parseIds);
        });
        answerOptions.add(new AnswerOption(ImmutableList.of(-1), "N/A", allParseIds));
        collapsed = true;
    }

    public void print(List<String> words, int response) {
        System.out.println(String.format("%d:%s\t%s\t%d", predicateIndex, words.get(predicateIndex),
                category, argumentNumber));
        double entropy = QueryGenerator.getAnswerEntropy(this);
        System.out.println(String.format("%.6f\t%s", entropy, question));
        for (int i = 0; i < answerOptions.size(); i++) {
            AnswerOption ao = answerOptions.get(i);
            String match = (i == response ? "*" : "");
            String argIdsStr = ao.argumentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            // FIXME: why argumentIds can be size 0????
            String argHeadsStr = (ao.argumentIds.size() == 0 || ao.argumentIds.get(0) == -1) ? "N/A" :
                    ao.argumentIds.stream().map(words::get).collect(Collectors.joining(","));
            String parseIdsStr = DebugPrinter.getShortListString(ao.parseIds);
            System.out.println(String.format("%s%d\t%s:%s(%s)\t%s", match, i, argIdsStr, argHeadsStr, ao.answer,
                    parseIdsStr));
        }
        System.out.println();
    }
}
