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
    public class AnswerOption {
        protected ImmutableList<Integer> argumentIds;
        protected String answer;
        Set<Integer> parseIds;
        double probability;

        AnswerOption(Set<Integer> parseIds) {
            this.parseIds = parseIds;
        }

        AnswerOption(ImmutableList<Integer> argumentIds, String answer, Set<Integer> parseIds) {
            this.argumentIds = argumentIds;
            this.answer = answer;
            this.parseIds = parseIds;
        }

        public String getAnswer() { return answer; }

        public ImmutableList<Integer> getArgumentIds() { return argumentIds; }

        public boolean isNAOption() {
            return BadQuestionOption.class.isInstance(this) || NoAnswerOption.class.isInstance(this);
        }
    }

    public class BadQuestionOption extends AnswerOption {
        BadQuestionOption(Set<Integer> parseIds) {
            super(parseIds);
        }

        public ImmutableList<Integer> getArgumentIds() { return null; }
        public String getAnswer() { return "Question is not understandable."; }
        public boolean isNAOption() {
            return true;
        }
    }

    public class NoAnswerOption extends AnswerOption {
        NoAnswerOption(Set<Integer> parseIds) {
            super(parseIds);
        }
        public ImmutableList<Integer> getArgumentIds() { return null; }
        public String getAnswer() { return "Answer is not listed."; }
        public boolean isNAOption() {
            return true;
        }
    }


    int sentenceId, totalNumParses, queryId;
    final List<String> sentence;
    final List<Parse> parses;
    Set<Query> queries;

    // Information specified only after collapsing;
    int predicateIndex;
    Category category;
    int argumentNumber;
    String question;
    List<AnswerOption> answerOptions;

    double answerMargin, answerEntropy, normalziedAnswerEntropy;
    // TODO: move this to ActiveLearning ...
    static final double rankDiscountFactor = 0.0;
    static final boolean estimateWithParseScores = true;

    public GroupedQuery(int sentenceId, final List<String> sentence, final List<Parse> parses) {
        this.sentenceId = sentenceId;
        this.sentence = sentence;
        this.parses = parses;
        this.totalNumParses = parses.size();
        queries = new HashSet<>();
        answerOptions = null;
    }

    public GroupedQuery(int sentenceId, final List<String> sentence, List<Parse> parses, Query query) {
        this(sentenceId, sentence, parses);
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
                         final Map<ImmutableList<Integer>, Set<Integer>> answerToParses,
                         final Map<ImmutableList<Integer>, String> answerToSpans) {
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
        answerOptions.add(new BadQuestionOption(allParseIds));
        //answerOptions.add(new AnswerOption(ImmutableList.of(-1), "N/A", allParseIds));
    }

    // Experimental query collapse function.
    public void collapseNew(int predicateIndex, Category category, int argumentNumber, String question,
                            final Map<String, Set<Integer>> spanToParses,
                            final Map<String, Set<Integer>> spanToArgIds) {
        this.predicateIndex = predicateIndex;
        this.category = category;
        this.argumentNumber = argumentNumber;
        this.question = question;
        this.answerOptions = new ArrayList<>();

        Set<Integer> allParseIds = IntStream.range(0, totalNumParses).boxed().collect(Collectors.toSet());
        double scoreSum = parses.stream().mapToDouble(p->p.score).sum();

        Map<String, Double> answerSpanToScore = new HashMap<>();
        spanToParses.forEach((span, parseIds) -> {
            double score = parseIds.stream().mapToDouble(id -> parses.get(id).score).sum() / scoreSum;
            answerSpanToScore.put(span, score);
        });
        List<String> sortedSpans = answerSpanToScore.keySet().stream()
                .sorted((a1, a2) -> Double.compare(-answerSpanToScore.get(a1), -answerSpanToScore.get(a2)))
                .collect(Collectors.toList());
        Set<Integer> unlistedParses = new HashSet<>();
        double accumulatedScore = 0.0;
        for (int i = 0; i < sortedSpans.size(); i++) {
            String span = sortedSpans.get(i);
            Set<Integer> parseIds = spanToParses.get(span);
            if (accumulatedScore > 0.8) {
                unlistedParses.addAll(parseIds);
            } else {
                ImmutableList<Integer> argList = ImmutableList.copyOf(spanToArgIds.get(span).stream().sorted()
                        .collect(Collectors.toList()));
                answerOptions.add(new AnswerOption(argList, span, spanToParses.get(span)));
            }
            allParseIds.removeAll(parseIds);
            accumulatedScore += answerSpanToScore.get(span);
        }
        answerOptions.add(new BadQuestionOption(allParseIds));
        answerOptions.add(new NoAnswerOption(unlistedParses));
    }

    public void setQueryId(int id) {
        this.queryId = id;
    }

    public int getQueryId() {
        return queryId;
    }

    public List<String> getSentence() {
        return sentence;
    }

    public int getPredicateIndex() { return predicateIndex; }

    public String getQuestion() { return question; }

    public List<AnswerOption> getAnswerOptions() { return answerOptions; }

    public double getAnswerEntropy() {
        return answerEntropy;
    }

    public double getNormalziedAnswerEntropy() {
        return normalziedAnswerEntropy;
    }

    public double getAnswerMargin() {
        return answerMargin;
    }

    public void computeProbabilities(double[] parseDist) {
        // Compute p(a|q).
        if (estimateWithParseScores) {
            answerOptions.forEach(ao -> ao.probability = ao.parseIds.stream()
                    .mapToDouble(i -> parseDist[i]).sum());
        } else {
            answerOptions.forEach(ao -> ao.probability = ao.parseIds.stream()
                    .mapToDouble(i -> Math.exp(-rankDiscountFactor * i / totalNumParses)).sum());
        }
        double sum = answerOptions.stream().mapToDouble(ao -> ao.probability).sum();
        answerOptions.forEach(ao -> ao.probability /= sum);

        // Margin.
        List<Double> prob = answerOptions.stream().map(ao -> ao.probability).sorted().collect(Collectors.toList());
        int len = prob.size();
        answerMargin = len < 2 ? 1.0 : prob.get(len - 1) - prob.get(len - 2);

        // Entropy divided by log(number of chosenOptions) to stay in range [0,1].
        double K = Math.log(answerOptions.size());
        answerEntropy = -1.0 * answerOptions.stream()
                .filter(ao -> ao.probability > 0)
                .mapToDouble(ao -> ao.probability * Math.log(ao.probability)).sum();
        normalziedAnswerEntropy = answerEntropy / Math.log(answerOptions.size());
    }

    public void print(final List<String> words, Response response) {
        System.out.println(String.format("%d:%s\t%s\t%d", predicateIndex, words.get(predicateIndex), category,
                argumentNumber));
        System.out.println(String.format("%.2f\t%.2f\t%s", answerEntropy, answerMargin, question));
        for (int i = 0; i < answerOptions.size(); i++) {
            AnswerOption ao = answerOptions.get(i);
            String match = (response.chosenOptions.contains(i) ? "*" : "");
            String argIdsStr = ao.isNAOption() ? "_" :
                    ao.argumentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            String argHeadsStr = ao.isNAOption() ? "N/A" :
                    ao.argumentIds.stream().map(words::get).collect(Collectors.joining(","));
            String parseIdsStr = DebugPrinter.getShortListString(ao.parseIds);
            System.out.println(String.format("%.2f\t%s%d\t%s:%s\t%s\t%s", ao.probability, match, i, argIdsStr,
                    argHeadsStr, ao.answer, parseIdsStr));
        }
        System.out.println();
    }

    public void printWithGoldDependency(final List<String> words, int response, Parse goldParse) {
        System.out.print(String.format("\t%d:%s\t%s.%d",
                predicateIndex,
                words.get(predicateIndex),
                category,
                argumentNumber));
        AnswerGenerator.getArgumentIds(words, goldParse, predicateIndex, category, argumentNumber).stream().sorted()
                .forEach(argId -> System.out.print(String.format("\t%d:%s", argId, words.get(argId))));
        System.out.println();
        System.out.println(String.format("%.2f\t \t%s", answerEntropy, question));
        for (int i = 0; i < answerOptions.size(); i++) {
            AnswerOption ao = answerOptions.get(i);
            String match = (i == response ? "*" : "");
            String argIdsStr = ao.argumentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            String argHeadsStr = ao.argumentIds.get(0) == -1 ? "N/A" :
                    ao.argumentIds.stream().map(words::get).collect(Collectors.joining(","));
            String parseIdsStr = DebugPrinter.getShortListString(ao.parseIds);
            System.out.println(String.format("%.2f\t%s[%d]\t%s\t%s:%s\t%s",
                    ao.probability,
                    match, i,
                    ao.answer,
                    argIdsStr,
                    argHeadsStr,
                    parseIdsStr));
        }
        System.out.println();
    }

    public List<AnswerOption> getTopAnswerOptions(final int numOptions) {
        return answerOptions.stream().sorted((ao1, ao2) -> Double.compare(-ao1.probability, -ao2.probability))
                .limit(numOptions).collect(Collectors.toList());
    }

}
