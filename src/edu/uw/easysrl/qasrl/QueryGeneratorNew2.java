package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.annotation.InterAnnotatorAgreement;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Latest query generator.
 * Created by luheng on 2/18/16.
 */
public class QueryGeneratorNew2 {
    /**
     * @param words the sentence
     * @param parses the nbest list
     * @param questionGenerator to generate wh-question from a resolved dependency
     */
    public static List<GroupedQuery> generateQueries(final int sentenceId, final List<String> words,
                                                     final List<Parse> parses,
                                                     final QuestionGenerator questionGenerator) {
        int numParses = parses.size();
        double totalScore = parses.stream().mapToDouble(p->p.score).sum();
        // TODO: DependencyToId, AnswerSpanResolver

        // pred.category.argnum -> question -> parses
        Table<String, String, Set<Integer>> questionPool = HashBasedTable.create();
        // argHeadSet -> answer -> parses
        Table<ImmutableList<Integer>, String, Set<Integer>> answerPool = HashBasedTable.create();
        // Bipartite, bi-directional question-answer relation.
        Table<String, ImmutableList<Integer>, Set<Integer>> questionToAnswer = HashBasedTable.create();
        Table<ImmutableList<Integer>, String, Set<Integer>> answerToQuestion = HashBasedTable.create();

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
            for (Category category : depToParses.rowKeySet()) {
                for (int argNum : depToParses.row(category).keySet()) {
                    for (int parseId : depToParses.get(category, argNum)) {
                        final Parse parse = parses.get(parseId);
                        Optional<QuestionAnswerPair> qaPairOpt = questionGenerator.generateQuestion(predId, argNum,
                                words, parse);
                        if (!qaPairOpt.isPresent()) {
                            continue;
                        }
                        QuestionAnswerPair qa = qaPairOpt.get();
                        String qkey = String.format("%d.%s.%d", predId, category, argNum);
                        String question = qa.renderQuestion();
                        final ImmutableList<Integer> heads = ImmutableList.copyOf(qa.targetDeps.stream()
                                .map(ResolvedDependency::getArgument).sorted()
                                .collect(Collectors.toList()));
                        List<String> spans = new ArrayList<>();
                        for (int j = 0; j < heads.size(); j++) {
                            spans.add(getAnswerSpan(qa.answers.get(j), heads.get(j), words));
                        }
                        //String answer = spans.stream().collect(Collectors.joining(" # "));
                        String answer = qa.renderAnswer();

                        // Register to question pool.
                        if (!questionPool.contains(qkey, question)) {
                            questionPool.put(qkey, question, new HashSet<>());
                        }
                        questionPool.get(qkey, question).add(parseId);

                        // Register to answer pool.
                        if (!answerPool.contains(heads, answer)) {
                            answerPool.put(heads, answer, new HashSet<>());
                        }
                        answerPool.get(heads, answer).add(parseId);

                        if (!questionToAnswer.contains(qkey, heads)) {
                            questionToAnswer.put(qkey, heads, new HashSet<>());
                        }
                        if (!answerToQuestion.contains(heads, qkey)) {
                            answerToQuestion.put(heads, qkey, new HashSet<>());
                        }
                        questionToAnswer.get(qkey, heads).add(parseId);
                        answerToQuestion.get(heads, qkey).add(parseId);
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
            String question = getBestSurfaceForm(questionPool.row(qkey), parses);

            Map<String, ImmutableList<Integer>> spanToArgList = new HashMap<>();
            Map<String, Set<Integer>> spanToParseIds = new HashMap<>();
            Set<String> answers = new HashSet<>();

            for (ImmutableList<Integer> heads : questionToAnswer.row(qkey).keySet()) {
                String answer = getBestSurfaceForm(answerPool.row(heads), parses);
                if (!answers.contains(answer)) {
                    answers.add(answer);
                    spanToParseIds.put(answer, new HashSet<>());
                    spanToArgList.put(answer, heads);
                }
                spanToParseIds.get(answer).addAll(questionToAnswer.get(qkey, heads));
            }
            for (String answer : answers) {
                if (answer.startsWith("to ")) {
                    String answerSuffix = answer.substring(3);
                    System.err.println(answer + "\t" + answerSuffix);
                    if (answers.contains(answerSuffix)) {
                        spanToParseIds.get(answerSuffix).addAll(spanToParseIds.get(answer));
                        spanToParseIds.remove(answer);
                        spanToArgList.remove(answer);
                    }
                }
            }
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
            //System.err.println(answerStr + ", " + sentStr);
            return "";
        }
        return answerStr;
    }
}
