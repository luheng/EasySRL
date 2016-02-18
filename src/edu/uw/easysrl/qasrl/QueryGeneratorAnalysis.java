package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Just to look at the queries.
 * Created by luheng on 2/17/16.
 */
public class QueryGeneratorAnalysis {

    TIntIntHashMap answerSpanConfusion;

    public QueryGeneratorAnalysis() {
        answerSpanConfusion = new TIntIntHashMap();
    }

    public void printStats() {
        List<Integer> keys = new ArrayList<>();
        for (int k : answerSpanConfusion.keys()) keys.add(k);
        keys.stream().sorted().forEach(k -> System.out.println(k + "\t" + answerSpanConfusion.get(k)));
    }


    /**
     * @param words the sentence
     * @param parses the nbest list
     * @param questionGenerator to generate wh-question from a resolved dependency
     */
    public void generateQueries(final int sentenceId, final List<String> words, final List<Parse> parses,
                                final QuestionGenerator questionGenerator) {
        List<GroupedQuery> groupedQueryList = new ArrayList<>();
        int numParses = parses.size();
        System.out.println("\n" + words.stream().collect(Collectors.joining(" ")));
        double totalScore = parses.stream().mapToDouble(p->p.score).sum();
        // TODO: DependencyToId, AnswerSpanResolver


        // word_id -> { span_string -> {set of dependencies, list of parses}}
        List<Map<String, Set<Integer>>> headToSpanToParses = new ArrayList<>();
        List<Map<String, Set<ResolvedDependency>>> headToSpanToDeps = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            headToSpanToParses.add(new HashMap<>());
            headToSpanToDeps.add(new HashMap<>());
        }

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
            boolean firstQuestionOfPredicate = true;
            for (Category category : depToParses.rowKeySet()) {
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

                        Query query = new Query(qa, parseId);
                        groupedQuery.addQuery(query);
                        if (!depToQAs.contains(category, argNum)) {
                            depToQAs.put(category, argNum, new HashSet<>());
                        }
                        depToQAs.get(category, argNum).add(qa);

                        // Register answers
                        final List<Integer> heads = qa.targetDeps.stream()
                                .map(ResolvedDependency::getArgument).sorted()
                                .collect(Collectors.toList());
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
                    // Debugging
                    /*
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
                            //if (qa.renderQuestion().equals(question)) {
                                String astr = String.format("%s\t%s",
                                        qa.targetDeps.stream().map(ResolvedDependency::getArgument).sorted()
                                                .map(String::valueOf).collect(Collectors.joining(",")),
                                        qa.renderAnswer());
                                answers.adjustOrPutValue(astr, 1, 1);
                            //}
                        }
                        for (String astr : answers.keySet()) {
                            System.out.println("\t\t\t\t" + astr + "\t" + answers.get(astr));
                        }
                    }
                    */
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
        // Output all registered answer spans.
        System.out.println("[global answers]");
        for (int i = 0; i < words.size(); i++) {
            int numSpans = headToSpanToDeps.get(i).size();
            if (numSpans < 1) {
                continue;
            }
            System.out.println(i + "\t" + words.get(i));
            for (String span : headToSpanToDeps.get(i).keySet()) {
                Set<ResolvedDependency> deps = headToSpanToDeps.get(i).get(span);
                Set<Integer> parseIds = headToSpanToParses.get(i).get(span);
                double score = parseIds.stream().mapToDouble(pid -> parses.get(pid).score).sum() / totalScore;
                System.out.print("\t" + span + "\t" + score);
                System.out.println("\t\t" + DebugPrinter.getShortListString(parseIds));
               // deps.forEach(dep -> System.out.println("\t\t\t" + dep.toString(words)));
            }
            answerSpanConfusion.adjustOrPutValue(numSpans, 1, 1);
        }
    }

    private static String punctuations = ".,:?!--";

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

