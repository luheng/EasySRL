package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Grouped query ...
 * Created by luheng on 1/21/16.
 */
@Deprecated
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

        public double getProbability() { return probability; }

        public ImmutableList<Integer> getArgumentIds() { return argumentIds; }

        public Set<Integer> getParseIds() { return parseIds; }

        public boolean isNAOption() {
            return BadQuestionOption.class.isInstance(this) || NoAnswerOption.class.isInstance(this);
        }
    }

    public class BadQuestionOption extends AnswerOption {
        BadQuestionOption(Set<Integer> parseIds) {
            super(parseIds);
        }

        public ImmutableList<Integer> getArgumentIds() { return null; }
        public String getAnswer() { return "Question is not valid."; }
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

    // The probability mass of non-NA options.
    public double questionConfidence, attachmentUncertainty;
    public double answerMargin, answerEntropy, normalizedAnswerEntropy;

    public static double rankDiscountFactor = 0.0;
    public static boolean estimateWithParseScores = true;
    // Not counting "question invalid" and "answer not listed" options.
    public static int maxNumNonNAOptionsPerQuery = 8;

    // Other dependencies
    Set<ResolvedDependency> questionDependencies;
    List<Set<ResolvedDependency>> answerDependencies;

    public GroupedQuery(int sentenceId, final List<String> sentence, final List<Parse> parses) {
        this.sentenceId = sentenceId;
        this.sentence = sentence;
        this.parses = parses;
        this.totalNumParses = parses.size();
        queries = new HashSet<>();
        questionDependencies = new HashSet<>();
        answerDependencies = new ArrayList<>();
    }

    public GroupedQuery(int sentenceId, final List<String> sentence, final List<Parse> parses, Query query) {
        this(sentenceId, sentence, parses);
        queries.add(query);
    }

    public boolean canMerge(final Query queryToMerge) {
        for (Query query : queries) {
            if (canMerge(queryToMerge, query)) {
                return true;
            }
        }
        return false;
    }

    public void addQuery(final Query query) {
        this.queries.add(query);
    }

    private static boolean canMerge(Query q1, Query q2) {
        if (q1.predicateIndex == q2.predicateIndex) {
            // Doing exact match.
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

    public void collapse(int predicateIndex, Category category, int argumentNumber, String question,
                         final Map<String, ImmutableList<Integer>> spanToArgList,
                         final Map<String, Set<Integer>> spanToParses) {
        this.predicateIndex = predicateIndex;
        this.category = category;
        this.argumentNumber = argumentNumber;
        this.question = question;
        this.answerOptions = new ArrayList<>();
        Set<Integer> invalidQuestionParseIds = IntStream.range(0, totalNumParses).boxed().collect(Collectors.toSet());
        Set<Integer> unlistedAnswerParseIds = new HashSet<>();
        List<Double> optionScores = spanToParses.values().stream()
                .map(parseIds -> parseIds.stream().mapToDouble(id -> parses.get(id).score).sum())
                .sorted()
                .collect(Collectors.toList());
        double minOptionScore = (optionScores.size() <= maxNumNonNAOptionsPerQuery) ? -1e-6 :
                optionScores.get(optionScores.size() - maxNumNonNAOptionsPerQuery) - 1e-6;

        spanToParses.forEach((span, parseIds) -> {
            ImmutableList<Integer> argList = spanToArgList.get(span);
            AnswerOption newOption = new AnswerOption(argList, span, parseIds);
            double optionScore = parseIds.stream().mapToDouble(id -> parses.get(id).score).sum();
            if (optionScore > minOptionScore) {
                answerOptions.add(newOption);
            } else {
                unlistedAnswerParseIds.addAll(parseIds);
            }
            invalidQuestionParseIds.removeAll(parseIds);
        });
        answerOptions.add(new NoAnswerOption(unlistedAnswerParseIds));
        answerOptions.add(new BadQuestionOption(invalidQuestionParseIds));
    }

    public void setQueryId(int id) {
        this.queryId = id;
    }

    public int getQueryId() {
        return queryId;
    }

    public int getSentenceId() {
        return sentenceId;
    }

    public List<String> getSentence() {
        return sentence;
    }

    public int getPredicateIndex() { return predicateIndex; }

    public Category getCategory() { return category; }

    public String getQuestionKey() {
        return predicateIndex + "." + category + "." + argumentNumber;
    }

    public String getQuestion() { return question; }

    public List<AnswerOption> getAnswerOptions() { return answerOptions; }

    // TODO: clean this up.
    public void computeProbabilities(double[] parseDist) {
        // Compute p(a|q).
        answerOptions.forEach(ao -> ao.probability = ao.parseIds.stream().mapToDouble(i -> parseDist[i]).sum());
        double sum = answerOptions.stream().mapToDouble(ao -> ao.probability).sum();
        answerOptions.forEach(ao -> ao.probability /= sum);

        // Margin.
        List<Double> prob = answerOptions.stream().map(ao -> ao.probability).sorted().collect(Collectors.toList());
        int len = prob.size();
        answerMargin = len < 2 ? 1.0 : prob.get(len - 1) - prob.get(len - 2);

        // Entropy divided by log(number of chosenOptions) to stay in range [0,1].
        answerEntropy = -1.0 * answerOptions.stream()
                .filter(ao -> ao.probability > 0)
                .mapToDouble(ao -> ao.probability * Math.log(ao.probability)).sum();
        normalizedAnswerEntropy = answerEntropy / Math.log(answerOptions.size());

        // Question confidence and attachment ambiguity.
        questionConfidence = .0;
        attachmentUncertainty = .0;
        for (AnswerOption option : answerOptions) {
            if (!GroupedQuery.BadQuestionOption.class.isInstance(option)) {
                questionConfidence += option.probability;
            }
        }
        for (AnswerOption option : answerOptions) {
            if (!GroupedQuery.BadQuestionOption.class.isInstance(option)) {
                if (option.probability > 0 && questionConfidence > 0) {
                    double p = option.probability / questionConfidence;
                    attachmentUncertainty -= p * Math.log(p);
                }
            }
        }
        //attachmentUncertainty /= Math.log(prob.size());
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
            System.out.println(String.format("%.2f\t%s%d\t(%s:%s)\t\t\t%s\t%s", ao.probability, match, i, argIdsStr,
                    argHeadsStr, ao.getAnswer(), parseIdsStr));
        }
        System.out.println();
    }

    public String getDebuggingInfo(final Response response) {
        String result = String.format("SID=%d\t%s\n", sentenceId, sentence.stream().collect(Collectors.joining(" ")));
        result += String.format("%d:%s\t%s.%d\n", predicateIndex, sentence.get(predicateIndex), category, argumentNumber);
        //result += String.format("QID=%d\tconf=%.2f\tunc=%.2f\tent=%.2f\tmarg=%.2f\t%s\n", queryId, questionConfidence,
        //        attachmentUncertainty, answerEntropy, answerMargin, question);
        result += String.format("QID=%d\tconf=%.2f\t%s\n", queryId, questionConfidence, question);
        for (int i = 0; i < answerOptions.size(); i++) {
            AnswerOption ao = answerOptions.get(i);
            String match = (response.chosenOptions.contains(i) ? "G" : " ");
            String argIdsStr = ao.isNAOption() ? "_" :
                    ao.argumentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            String argHeadsStr = ao.isNAOption() ? "N/A" :
                    ao.argumentIds.stream().map(sentence::get).collect(Collectors.joining(","));
            String parseIdsStr = DebugPrinter.getShortListString(ao.parseIds);
            result += String.format("%d\t%s\tprob=%.2f\t%s\t(%s:%s)\t%s\n", i, match, ao.probability, ao.getAnswer(),
                    argIdsStr, argHeadsStr, parseIdsStr);
        }
        return result + "\n";
    }

    public String getDebuggingInfo(final Response response, final Response goldResponse) {
        String result = String.format("SID=%d\t%s\n", sentenceId, sentence.stream().collect(Collectors.joining(" ")));
        result += String.format("%d:%s\t%s.%d\n", predicateIndex, sentence.get(predicateIndex), category, argumentNumber);
        result += String.format("QID=%d\tent=%.2f\tmarg=%.2f\t%s\n", queryId, answerEntropy, answerMargin, question);
        for (int i = 0; i < answerOptions.size(); i++) {
            AnswerOption ao = answerOptions.get(i);
            String match = (response.chosenOptions.contains(i) ? "*" : " ")
                            + (goldResponse.chosenOptions.contains(i) ? "G" : " ");
            String argIdsStr = ao.isNAOption() ? "_" :
                    ao.argumentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            String argHeadsStr = ao.isNAOption() ? "N/A" :
                    ao.argumentIds.stream().map(sentence::get).collect(Collectors.joining(","));
            String parseIdsStr = DebugPrinter.getShortListString(ao.parseIds);
            result += String.format("%d\t%s\tprob=%.2f\t%s\t(%s:%s)\t%s\n", i, match, ao.probability, ao.getAnswer(),
                    argIdsStr, argHeadsStr, parseIdsStr);
        }
        if (response.debugInfo.length() > 0) {
            result += "Comment:\t" + response.debugInfo;
        }
        return result;
    }

    public void printWithGoldDependency(final List<String> words, int response, Parse goldParse) {
        System.out.print(String.format("\t%d:%s\t%s.%d",
                predicateIndex,
                words.get(predicateIndex),
                category,
                argumentNumber));
        TextGenerationHelper.getArgumentIds(words, goldParse, predicateIndex, category, argumentNumber).stream().sorted()
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
                    ao.getAnswer(),
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
