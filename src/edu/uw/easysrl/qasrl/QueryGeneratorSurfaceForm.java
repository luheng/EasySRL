package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Latest query generator.
 *
 * Two options: qkey -> question -> parse ids
 *              question -> qkey -> parse ids
 *
 *
 * Created by luheng on 2/18/16.
 */
public class QueryGeneratorSurfaceForm {

    /**
     * @param words the sentence
     * @param parses the nbest list
     */
    public static List<GroupedQuery> generateQueries(final int sentenceId, final List<String> words,
                                                     final List<Parse> parses) {
        int numParses = parses.size();
        double totalScore = parses.stream().mapToDouble(p->p.score).sum();

        // question -> pred.category.argnum -> parses
        Table<String, String, Set<Integer>> questionPool = HashBasedTable.create();
        // answer -> answer head ids -> parses
        Table<String, ImmutableList<Integer>, Set<Integer>> answerPool = HashBasedTable.create();
        // Bipartite, bi-directional question-answer relation.
        Table<String, String, Set<Integer>> questionToAnswer = HashBasedTable.create();

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
                    String questionKey = "" + predId + "." + category + "." + argNum;
                    ImmutableList<Integer> answerHeadList = ImmutableList.copyOf(argHeadToSpan.keySet().stream()
                            .sorted().collect(Collectors.toList()));
                    String answerStr = answerHeadList.stream()
                            .map(argHeadToSpan::get)
                            .collect(Collectors.joining(QuestionAnswerPair.answerDelimiter));

                    // Add stuff.
                    for (String questionStr : questionStrList) {
                        if (!questionPool.contains(questionStr, questionKey)) {
                            questionPool.put(questionStr, questionKey, new HashSet<>());
                        }
                        questionPool.get(questionStr, questionKey).add(parseId);
                        if (!answerPool.contains(answerStr, answerHeadList)) {
                            answerPool.put(answerStr, answerHeadList, new HashSet<>());
                        }
                        answerPool.get(answerStr, answerHeadList).add(parseId);
                        if (!questionToAnswer.contains(questionStr, answerStr)) {
                            questionToAnswer.put(questionStr, answerStr, new HashSet<>());
                        }
                        questionToAnswer.get(questionStr, answerStr).add(parseId);
                    }
                }
            }
        }

        List<GroupedQuery> groupedQueryList = new ArrayList<>();
        for (String question : questionToAnswer.rowKeySet()) {
            String qkey = getBestSurfaceForm(questionPool.row(question), parses);
            String[] info = qkey.split("\\.");
            int predId = Integer.parseInt(info[0]);
            Category category = Category.valueOf(info[1]);
            int argNum = Integer.parseInt(info[2]);

            Map<String, ImmutableList<Integer>> spanToArgList = new HashMap<>();
            Map<String, Set<Integer>> spanToParseIds = new HashMap<>();
            Set<String> answers = new HashSet<>();

            for (String answer : questionToAnswer.row(question).keySet()) {
                ImmutableList<Integer> heads = getBestStructure(answerPool.row(answer), parses);
                if (!answers.contains(answer)) {
                    answers.add(answer);
                    spanToParseIds.put(answer, new HashSet<>());
                    spanToArgList.put(answer, heads);
                }
                spanToParseIds.get(answer).addAll(questionToAnswer.get(question, answer));
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
            // TODO: fix this.
            GroupedQuery groupedQuery = new GroupedQuery(sentenceId, words, parses);
            groupedQuery.collapse(predId, category, argNum, question, spanToArgList, spanToParseIds);
            groupedQueryList.add(groupedQuery);
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
