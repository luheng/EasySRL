package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.syntax.grammar.Category;

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
        double probability;

        AnswerOption(ImmutableList<Integer> argumentIds, String answer, Set<Integer> parseIds) {
            this.argumentIds = argumentIds;
            this.answer = answer;
            this.parseIds = parseIds;
        }
    }

    int sentenceId, totalNumParses;
    Set<Query> queries;

    // Information specified only after collapsing;
    int predicateIndex;
    Category category;
    int argumentNumber;
    String question;
    List<AnswerOption> answerOptions;

    double answerMargin, answerEntropy;
    // TODO: move this to ActiveLearning ...
    static final double rankDiscountFactor = 0.0;
    static final boolean pruneAnswerOptions = false;

    public GroupedQuery(int sentenceId, int numParses) {
        this.sentenceId = sentenceId;
        this.totalNumParses = numParses;
        queries = new HashSet<>();
        answerOptions = null;
    }

    public GroupedQuery(int sentenceId, int numParses, Query query) {
        this(sentenceId, numParses);
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
            if (q1.question.equals(q2.question) && !q1.question.equalsIgnoreCase("-NOQ-")) {
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

    public void collapse(int predicateIndex, Category category, int argumentNumber, String question,
                         Map<ImmutableList<Integer>, Set<Integer>> answerToParses,
                         Map<ImmutableList<Integer>, String> answerToSpans) {
        this.predicateIndex = predicateIndex;
        this.category = category;
        this.argumentNumber = argumentNumber;
        this.question = question;
        this.answerOptions = new ArrayList<>();

        Set<Integer> allParseIds = IntStream.range(0, totalNumParses).boxed().collect(Collectors.toSet());
        answerToParses.keySet().forEach(argList -> {
            Set<Integer> parseIds = answerToParses.get(argList);
            answerOptions.add(new AnswerOption(argList, answerToSpans.get(argList), parseIds));
            allParseIds.removeAll(parseIds);
        });
        answerOptions.add(new AnswerOption(ImmutableList.of(-1), "N/A", allParseIds));
        // Compute p(a|q), entropy, margin, etc.
        computeProbabilities();
        if (pruneAnswerOptions) {
            answerOptions = answerOptions.stream().filter(ao -> ao.probability > 0.05).collect(Collectors.toList());
            computeProbabilities();
        }
    }

    private void computeProbabilities() {
        // Normalize.
        // Compute p(a|q). discounting by rank id.
        answerOptions.forEach(ao -> ao.probability = ao.parseIds.stream()
                        .mapToDouble(i -> Math.exp(-rankDiscountFactor * i / totalNumParses)).sum());
        double sum = answerOptions.stream().mapToDouble(ao -> ao.probability).sum();
        answerOptions.forEach(ao -> ao.probability /= sum);

        // Margin.
        List<Double> prob = answerOptions.stream().map(ao -> ao.probability).sorted().collect(Collectors.toList());
        int len = prob.size();
        answerMargin = len < 2 ? 1.0 : prob.get(len - 1) - prob.get(len - 2);

        // Entropy.
        answerEntropy = -1.0 * answerOptions.stream()
                .filter(ao -> ao.probability > 0)
                .mapToDouble(ao -> ao.probability * Math.log(ao.probability) / Math.log(2.0)).sum();
    }

    public void print(List<String> words, int response) {
        System.out.println(String.format("%d:%s\t%s\t%d", predicateIndex, words.get(predicateIndex),
                category, argumentNumber));
        System.out.println(String.format("%.6f\t%.6f\t%s", answerEntropy, answerMargin, question));
        for (int i = 0; i < answerOptions.size(); i++) {
            AnswerOption ao = answerOptions.get(i);
            String match = (i == response ? "*" : "");
            String argIdsStr = ao.argumentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            // FIXME: why argumentIds can be size 0????
            String argHeadsStr = ao.argumentIds.get(0) == -1 ? "N/A" :
                    ao.argumentIds.stream().map(words::get).collect(Collectors.joining(","));
            String parseIdsStr = DebugPrinter.getShortListString(ao.parseIds);
            System.out.println(String.format("%.3f\t%s%d\t%s:%s(%s)\t%s", ao.probability, match, i, argIdsStr,
                    argHeadsStr, ao.answer, parseIdsStr));
        }
        System.out.println();
    }
}
