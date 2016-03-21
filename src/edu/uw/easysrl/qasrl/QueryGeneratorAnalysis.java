package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;
import gnu.trove.map.hash.TIntIntHashMap;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Just to look at the queries.
 * Created by luheng on 2/17/16.
 */
@Deprecated
public class QueryGeneratorAnalysis {
    TIntIntHashMap answerSpanConfusion;
    double avgQuestionConfusion, avgAnswerConfusion;
    int numSentences;

    public QueryGeneratorAnalysis() {
        answerSpanConfusion = new TIntIntHashMap();
        avgQuestionConfusion = .0;
        avgAnswerConfusion = .0;
        numSentences = 0;
    }

    public void printStats() {
        List<Integer> keys = new ArrayList<>();
        for (int k : answerSpanConfusion.keys()) keys.add(k);
        keys.stream().sorted().forEach(k -> System.out.println(k + "\t" + answerSpanConfusion.get(k)));
        System.out.println("question confusion:\t" + avgQuestionConfusion / numSentences);
        System.out.println("answer confusion:\t" + avgAnswerConfusion / numSentences);
    }


    /**
     * @param words the sentence
     * @param parses the nbest list
     * @param questionGenerator to generate wh-question from a resolved dependency
     */
    public void generateQueries(final int sentenceId, final List<String> words, final List<Parse> parses,
                                final QuestionGenerator questionGenerator) {
        int numParses = parses.size();
        double totalScore = parses.stream().mapToDouble(p->p.score).sum();
        // TODO: DependencyToId, AnswerSpanResolver

        // word_id -> { span_string -> {set of dependencies, list of parses}}
        List<Map<String, Set<Integer>>> headToSpanToParses = new ArrayList<>();
        List<Map<String, Set<ResolvedDependency>>> headToSpanToDeps = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            headToSpanToParses.add(new HashMap<>());
            headToSpanToDeps.add(new HashMap<>());
        }

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
            Table<Category, Integer, Set<QuestionAnswerPair>> depToQAs = HashBasedTable.create();
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
                        String question = qa.renderQuestion();
                        String answer = qa.renderAnswer();
                        final ImmutableList<Integer> heads = ImmutableList.copyOf(qa.targetDeps.stream()
                                .map(ResolvedDependency::getArgument).sorted()
                                .collect(Collectors.toList()));
                        String qkey = String.format("%d.%s.%d", predId, category, argNum);

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

                        // Register individual answer spans
                        List<String> spans = new ArrayList<>();
                        for (int j = 0; j < heads.size(); j++) {
                            spans.add(getAnswerSpan(qa.answers.get(j), heads.get(j), words));
                        }
                        assert spans.size() == heads.size() && heads.size() == qa.answerDeps.size();
                        for (int j = 0; j < spans.size(); j++) {
                            String span = spans.get(j);
                            int head = heads.get(j);
                            if (span.length() > 0) {
                                if (!headToSpanToDeps.get(head).containsKey(span)) {
                                    headToSpanToDeps.get(head).put(span, new HashSet<>());
                                    headToSpanToParses.get(head).put(span, new HashSet<>());
                                }
                                headToSpanToDeps.get(head).get(span).addAll(qa.answerDeps.get(j));
                                headToSpanToParses.get(head).get(span).add(parseId);
                            }
                        }
                    }
                    /*
                     // Debugging
                    if (questionScores.size() > 0) {
                        if (firstQuestionOfPredicate) {
                            System.out.println(String.format("[predicate]:\t%s", words.get(i)));
                            firstQuestionOfPredicate = false;
                        }
                        System.out.println(String.format("\t\t%s.%d", category, argNum));
                        // Print questions.
                        for (String question : questionScores.keySet()) {
                            System.out.println(String.format("\t\t\t%s\t%.3f", question,
                                    questionScores.get(question) / totalScore));
                        }
                        // Print answers.
                        TObjectDoubleHashMap<String> answers = new TObjectDoubleHashMap<>();
                        for (QuestionAnswerPair qa : depToQAs.get(category, argNum)) {
                                String astr = String.format("%s\t%s",
                                        qa.targetDeps.stream().map(ResolvedDependency::getArgument).sorted()
                                                .map(String::valueOf).collect(Collectors.joining(",")),
                                        qa.renderAnswer());
                                answers.adjustOrPutValue(astr, 1, 1);
                            }
                            for (String astr : answers.keySet()) {
                                System.out.println("\t\t\t\t" + astr + "\t" + answers.get(astr));
                            }
                        }
                     */
                }
            }
        }
        System.out.println("\n" + sentenceId + "\t" + words.stream().collect(Collectors.joining(" ")));

        // Output all question-answer relations.
        /*
        for (String qkey : qaPool.rowKeySet()) {
            System.out.println(qkey);
            for (String question : questionPool.row(qkey).keySet()) {
                double score = questionPool.get(qkey, question).stream().mapToDouble(id -> parses.get(id).score).sum();
                System.out.println("\t" + question + "\t" + score / totalScore);
            }
            for (ImmutableList<Integer> heads : qaPool.row(qkey).keySet()) {
                String headsStr = heads.stream().map(String::valueOf).collect(Collectors.joining(","));
                System.out.println("\t" + headsStr);
                for (String answer : answerPool.row(heads).keySet()) {
                    double score = answerPool.get(heads, answer).stream().mapToDouble(id -> parses.get(id).score).sum();
                    System.out.println("\t\t" + answer + "\t" + score / totalScore);
                }
            }
        }
        */
       // System.out.println(questionPool.rowKeySet().size() + "\t" + questionPool.cellSet().size());
       // System.out.println(answerPool.rowKeySet().size() + "\t" + answerPool.cellSet().size());
       // System.out.println(questionToAnswer.cellSet().size());
        for (ImmutableList<Integer> heads : answerToQuestion.rowKeySet()) {
            if (answerToQuestion.row(heads).keySet().size() < 2) {
                continue;
            }
            String headsStr = heads.stream().map(String::valueOf).collect(Collectors.joining(","));
            System.out.println(headsStr);
            String answer = getBestSurfaceForm(answerPool.row(heads), parses);
            for (String qkey : answerToQuestion.row(heads).keySet()) {
                String question = getBestSurfaceForm(questionPool.row(qkey), parses);
                double qaScore = answerToQuestion.get(heads, qkey).stream().mapToDouble(i -> parses.get(i).score).sum();
                System.out.println(String.format("\t%s\t\t%s\t\t%s\t\t%.3f", qkey, question, answer, qaScore / totalScore));
            }
        }

        if (questionPool.rowKeySet().size() > 0) {
            avgQuestionConfusion += 1.0 * questionPool.cellSet().size() / questionPool.rowKeySet().size();
        }
        if (answerPool.rowKeySet().size() > 0) {
            avgAnswerConfusion += 1.0 * answerPool.cellSet().size() / answerPool.rowKeySet().size();
        }
        numSentences ++;

        // Output all registered answer spans.
        /*
        System.out.println("[global answers]");
        boolean outputSentence = true;
        for (int i = 0; i < words.size(); i++) {
            int numSpans = headToSpanToDeps.get(i).size();
            answerSpanConfusion.adjustOrPutValue(numSpans, 1, 1);
            if (numSpans < 1) {
                continue;
            }
            if (outputSentence) {
                //System.out.println("\n" + sentenceId + "\t" + words.stream().collect(Collectors.joining(" ")));
                //System.out.println("[global answers]");
                outputSentence = false;
            }
            System.out.println(i + "\t" + words.get(i));
            List<String> sortedSpans = headToSpanToDeps.get(i).keySet().stream()
                    .sorted((s1, s2) -> Integer.compare(s1.length(), s2.length()))
                    .collect(Collectors.toList());
            for (String span : sortedSpans) {
                Set<ResolvedDependency> deps = headToSpanToDeps.get(i).get(span);
                Set<Integer> parseIds = headToSpanToParses.get(i).get(span);
                double score = parseIds.stream().mapToDouble(pid -> parses.get(pid).score).sum() / totalScore;
                System.out.println(String.format("\t%s\t%.3f\t%s", span, score,
                        DebugPrinter.getShortListString(parseIds)));
               // deps.forEach(dep -> System.out.println("\t\t\t" + dep.toString(words)));
            }
        }
        */
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

