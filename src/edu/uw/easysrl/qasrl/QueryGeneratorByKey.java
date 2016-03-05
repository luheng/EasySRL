package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Created by luheng on 3/1/16.
 */
@Deprecated
public class QueryGeneratorByKey {

    /**
     * @param words the sentence
     * @param parses the nbest list
     */
    public static List<GroupedQuery> generateQueries(final int sentenceId, final List<String> words,
                                                     final List<Parse> parses) {
        int numParses = parses.size();
        double totalScore = parses.stream().mapToDouble(p->p.score).sum();

        // pred.category.argnum -> question -> parses
        Table<String, String, Set<Integer>> questionPool = HashBasedTable.create();
        // answer -> answer head ids -> parses
        Table<ImmutableList<Integer>, String, Set<Integer>> answerPool = HashBasedTable.create();
        // Bipartite, bi-directional question-answer relation.
        Table<String, ImmutableList<Integer>, Set<Integer>> questionToAnswer = HashBasedTable.create();

        for (int parseId = 0; parseId < numParses; parseId++) {
            final Parse parse = parses.get(parseId);
            for (int predId = 0; predId < words.size(); predId++) {
                final Category category = parses.get(parseId).categories.get(predId);
                for (int argNum = 1; argNum <= category.getNumberOfArguments(); argNum++) {
                    List<QuestionAnswerPairReduced> qaList = QuestionGenerator
                            .generateAllQAPairs(predId, argNum, words, parse);
                    if (qaList.size() == 0) {
                        continue;
                    }
                    Map<Integer, String> argHeadToSpan = new HashMap<>();
                    qaList.forEach(qa -> argHeadToSpan.put(qa.targetDep.getArgument(), qa.renderAnswer()));
                    List<String> questionStrList = qaList.stream()
                            .map(QuestionAnswerPairReduced::renderQuestion)
                            .collect(Collectors.toList());
                    String qkey = "" + predId + "." + category + "." + argNum;
                    ImmutableList<Integer> heads = ImmutableList.copyOf(argHeadToSpan.keySet().stream()
                            .sorted().collect(Collectors.toList()));
                    String answerStr = heads.stream()
                            .map(argHeadToSpan::get)
                            .collect(Collectors.joining(QuestionAnswerPair.answerDelimiter));

                    // Add stuff.
                    for (String questionStr : questionStrList) {
                        if (!questionPool.contains(qkey, questionStr)) {
                            questionPool.put(qkey, questionStr, new HashSet<>());
                        }
                        questionPool.get(qkey, questionStr).add(parseId);
                        if (!answerPool.contains(heads, answerStr)) {
                            answerPool.put(heads, answerStr, new HashSet<>());
                        }
                        answerPool.get(heads, answerStr).add(parseId);
                        if (!questionToAnswer.contains(qkey, heads)) {
                            questionToAnswer.put(qkey, heads, new HashSet<>());
                        }
                        questionToAnswer.get(qkey, heads).add(parseId);
                    }
                }
            }
        }

        List<GroupedQuery> groupedQueryList = new ArrayList<>();
        for (String qkey : questionToAnswer.rowKeySet()) {
            String[] info = qkey.split("\\.");
            int predId = Integer.parseInt(info[0]);
            Category category = Category.valueOf(info[1]);
            int argNum = Integer.parseInt(info[2]);

            Map<String, ImmutableList<Integer>> spanToArgList = new HashMap<>();
            Map<String, Set<Integer>> spanToParseIds = new HashMap<>();
            Set<String> answers = new HashSet<>();

            for (ImmutableList<Integer> heads : questionToAnswer.row(qkey).keySet()) {
                for (String span : answerPool.row(heads).keySet()) {
                    if (!spanToParseIds.containsKey(span)) {
                        spanToParseIds.put(span, new HashSet<>());
                        spanToArgList.put(span, heads);
                    }
                    spanToParseIds.get(span).addAll(questionToAnswer.get(qkey, heads));
                    answers.add(span);
                }
            }
            for (String answer : answers) {
                if (answer.startsWith("to ")) {
                    String answerSuffix = answer.substring(3);
                    // System.err.println(answer + "\t" + answerSuffix);
                    if (answers.contains(answerSuffix)) {
                        spanToParseIds.get(answerSuffix).addAll(spanToParseIds.get(answer));
                        spanToParseIds.remove(answer);
                        spanToArgList.remove(answer);
                    }
                }
            }
            for (String question : questionPool.row(qkey).keySet()) {
                System.out.println(question + "\t" + qkey);
                GroupedQuery groupedQuery = new GroupedQuery(sentenceId, words, parses);
                groupedQuery.collapse(predId, category, argNum, question, spanToArgList, spanToParseIds);
                groupedQueryList.add(groupedQuery);
            }
            System.out.println();
        }
        return groupedQueryList;
    }

    private static String getBestSurfaceForm(final Map<String, Set<Integer>> surfaceFormToParses,
                                             final List<Parse> parses) {
        String bestSF = "";
        double bestScore = Double.NEGATIVE_INFINITY;
        for (String sf : surfaceFormToParses.keySet()) {
            double score = surfaceFormToParses.get(sf).stream().mapToDouble(i -> parses.get(i).score).sum();
            if (score > bestScore) {
                bestScore = score;
                bestSF = sf;
            }
        }
        return bestSF;
    }

    private static ImmutableList<Integer> getBestStructure(final Map<ImmutableList<Integer>, Set<Integer>> headToParses,
                                                           final List<Parse> parses) {
        ImmutableList<Integer> bestHeads = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (ImmutableList<Integer> heads : headToParses.keySet()) {
            double score = headToParses.get(heads).stream().mapToDouble(i -> parses.get(i).score).sum();
            if (score > bestScore) {
                bestScore = score;
                bestHeads = heads;
            }
        }
        return bestHeads;
    }

    private static String punctuations = "...,:?!---LRB-RRB-";

    // Answer span processing:
    // 1. remove the starting/trailing with punctuation
    // 2. remove the spans that does not contain the head word
    // 3. remove the discontinuous spans
    private static String getAnswerSpan(final List<String> answerWords, final int head, final List<String> sentWords) {
        List<String> awords = new ArrayList<>(answerWords);
        String sentStr = sentWords.stream().collect(Collectors.joining(" "));
        if (punctuations.contains(awords.get(0))) {
            awords.remove(0);
        }
        if (awords.size() == 0) {
            return "";
        }
        if (punctuations.contains(awords.get(awords.size() - 1))) {
            awords.remove(awords.size() - 1);
        }
        if (awords.size() == 0) {
            return "";
        }
        String answerStr = awords.stream().collect(Collectors.joining(" "));
        if (!answerStr.toLowerCase().contains(sentWords.get(head).toLowerCase())) {
            return "";
        }
        if (!sentStr.toLowerCase().contains(answerStr.toLowerCase())) {
            //System.err.println(answerStr + "\t ... \t " + sentStr);
            return "";
        }
        return answerStr;
    }
}

