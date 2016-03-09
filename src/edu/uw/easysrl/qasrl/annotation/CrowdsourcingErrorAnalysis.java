package edu.uw.easysrl.qasrl.annotation;

import com.google.common.base.Strings;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.analysis.PPAttachment;
import edu.uw.easysrl.qasrl.corpora.PronounList;
import edu.uw.easysrl.qasrl.pomdp.POMDP;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 2/29/16.
 */
public class CrowdsourcingErrorAnalysis {
    private static final int nBest = 100;
    private static final int maxNumOptionsPerQuestion = 6;
    static {
        GroupedQuery.maxNumNonNAOptionsPerQuery = maxNumOptionsPerQuestion - 2;
    }

    // Query pruning parameters.
    private static QueryPruningParameters queryPruningParameters = new QueryPruningParameters(
            1,    /* top K */
            0.1,  /* min question confidence */
            0.1,  /* min answer confidence */
            0.05  /* min attachment entropy */
    );

    private static final String[] annotationFiles = {
            "./Crowdflower_data/f878213.csv",
            "./Crowdflower_data/f882410.csv"
    };

    static Accuracy answerAcc = new Accuracy();
    static int numUnmatchedQuestions = 0, numMatchedQuestions = 0, numPronounQuestions = 0;

    static final int minAgreement = 4;
    static final boolean skipPronouns = true;

    public static void main(String[] args) {
        Map<Integer, List<AlignedAnnotation>> annotations = loadData(annotationFiles);
        assert annotations != null;

        //printOneStepAnalysis(annotations);
        printSequentialAnalysis(annotations);
    }

    private static Map<Integer, List<AlignedAnnotation>> loadData(String[] fileNames) {
        Map<Integer, List<AlignedAnnotation>> sentenceToAnnotations;
        List<AlignedAnnotation> annotationList = new ArrayList<>();
        try {
            for (String fileName : fileNames) {
                annotationList.addAll(CrowdFlowerDataReader.readAggregatedAnnotationFromFile(fileName));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        sentenceToAnnotations = new HashMap<>();
        annotationList.forEach(annotation -> {
            int sentId = annotation.sentenceId;
            if (!sentenceToAnnotations.containsKey(sentId)) {
                sentenceToAnnotations.put(sentId, new ArrayList<>());
            }
            sentenceToAnnotations.get(sentId).add(annotation);
        });
        return sentenceToAnnotations;
    }

    private static void printOneStepAnalysis(Map<Integer, List<AlignedAnnotation>> annotations) {
        List<Integer> sentenceIds = annotations.keySet().stream().sorted().collect(Collectors.toList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));

        // Learn a observation model.
        POMDP learner = new POMDP(nBest, 1000, 0.0);
        ResponseSimulator goldSimulator = new ResponseSimulatorGold(learner.goldParses, new QuestionGenerator());
        int numWorsenedSentences = 0,
            numUnchangedSentences = 0,
            numImprovedSentences = 0;

        for (int sentenceId : sentenceIds) {
            learner.initializeForSentence(sentenceId, annotations.get(sentenceId));
            final List<GroupedQuery> queries = learner.getQueryPool();
            for (GroupedQuery query : queries) {
                AlignedAnnotation annotation = getAlignedAnnotation(query, annotations.get(sentenceId));
                if (annotation == null) {
                    continue;
                }
                if (QualityControl.categoriesToFilter.contains(query.getCategory())) {
                    continue;
                }
                int[] optionDist = QualityControl.getUserResponses(query, annotation);
                Set<Integer> unmatched = getUnmatchedAnnotationOptions(query, annotation);
                int goldOption  = goldSimulator.answerQuestion(query).chosenOptions.get(0);
                Results baselineF1 = learner.getOneBestF1(sentenceId);
                Results oracleF1 = learner.getOracleF1(sentenceId);
                Results[] rerankedF1 = new Results[optionDist.length];
                int oracleParseId = learner.getOracleParseId(sentenceId);
                int oracleOption = -1;
                int oneBestOption = -1;
                for (int j = 0; j < optionDist.length; j++) {
                    learner.resetBeliefModel();
                    query.computeProbabilities(learner.beliefModel.belief);
                    learner.receiveObservationForQuery(query, new Response(j));
                    rerankedF1[j] = learner.getRerankedF1(sentenceId);
                    if (query.getAnswerOptions().get(j).getParseIds().contains(oracleParseId)) {
                        oracleOption = j;
                    }
                    if (query.getAnswerOptions().get(j).getParseIds().contains(0)) {
                        oneBestOption = j;
                    }
                }
                // Print.
                String sentenceStr = query.getSentence().stream().collect(Collectors.joining(" "));
                int predId = query.getPredicateIndex();
                String result =  "SID=" + sentenceId + "\t" + sentenceStr + "\n" + "PRED=" + predId+ "\t \t"
                        + query.getQuestion() + "\t" + query.getQuestionKey() + "\t"
                        + String.format("Baseline:: %.3f%%\tOracle:: %.3f%%\tOracle ParseId::%d\n",
                                100.0 * baselineF1.getF1(), 100.0 * oracleF1.getF1(), oracleParseId);
                for (int j = 0; j < optionDist.length; j++) {
                    String match = "";
                    for (int k = 0; k < optionDist[j]; k++) {
                        match += "*";
                    }
                    if (j == goldOption) {
                        match += "G";
                    }
                    if (j == oracleOption) {
                        match += "O";
                    }
                    if (j == oneBestOption) {
                        match += "B";
                    }
                    String improvement = " ";
                    if (rerankedF1[j].getF1() < baselineF1.getF1() - 1e-8) {
                        improvement = "[-]";
                        numWorsenedSentences ++;
                    } else if (rerankedF1[j].getF1() > baselineF1.getF1() + 1e-8) {
                        improvement = "[+]";
                        numImprovedSentences ++;
                    } else {
                        numUnchangedSentences ++;
                    }
                    GroupedQuery.AnswerOption option = query.getAnswerOptions().get(j);
                    result += String.format("%-8s\t%.3f\t%-40s\t%.3f%%\t%s\n", match, option.getProbability(),
                            option.getAnswer(), 100.0 * rerankedF1[j].getF1(), improvement);
                }
                int cnt = optionDist.length;
                for (int j : unmatched) {
                    String match = "";
                    for (int k = 0; k < annotation.answerDist[j]; k++) {
                        match += "*";
                    }
                    result += String.format("%-8s\t---\t%-40s\n", match, annotation.answerOptions.get(j));
                }
                System.out.println(result);
            }
        }
    }

    static class DebugBlock {
        double F1;
        String block;
        DebugBlock(double F1, String block) {
            this.F1 = F1;
            this.block = block;
        }
    }

    private static void printSequentialAnalysis(Map<Integer, List<AlignedAnnotation>> annotations) {
        List<Integer> sentenceIds = annotations.keySet().stream().sorted().collect(Collectors.toList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));

        Results avgBaseline = new Results(),
                avgRerank = new Results();
        int numWorsenedSentences = 0,
            numUnchangedSentences = 0,
            numImprovedSentences = 0;

        // Learn a observation model.
        POMDP learner = new POMDP(nBest, 1000, 0.0);
        learner.setQueryPruningParameters(queryPruningParameters);
        ResponseSimulator goldSimulator = new ResponseSimulatorGold(learner.goldParses, new QuestionGenerator());
        List<DebugBlock> debugging = new ArrayList<>();
        for (int sentenceId : sentenceIds) {
            learner.initializeForSentence(sentenceId, annotations.get(sentenceId));
            Results oracleF1 = learner.getOracleF1(sentenceId);
            Results baselineF1 = learner.getOneBestF1(sentenceId);
            Results currentF1 = learner.getRerankedF1(sentenceId);
            final List<GroupedQuery> queries = learner.getQueryPool();
            int queryCount = 0;
            String sentenceDebuggingString = "";
            for (GroupedQuery query : queries) {
                AlignedAnnotation annotation = getAlignedAnnotation(query, annotations.get(sentenceId));
                if (annotation == null) {
                    continue;
                }
                if (queryCount == 0) {
                    /* for (int i = 0; i < learner.beliefModel.belief.length; i++) {
                        System.out.print(learner.beliefModel.belief[i] * 100 + "\t");
                    }
                    System.out.println("\n"); */
                    queryCount ++;
                }

                int[] optionDist = QualityControl.getUserResponses(query, annotation);
                int agreement = QualityControl.getAgreementNumber(query, annotation);
                Set<Integer> unmatched = getUnmatchedAnnotationOptions(query, annotation);
                int goldOption = goldSimulator.answerQuestion(query).chosenOptions.get(0);

                Category category = query.getCategory();
                Results[] rerankedF1 = new Results[optionDist.length];
                int oracleParseId = learner.getOracleParseId(sentenceId);
                int oracleOption = -1;
                int oneBestOption = -1;
                int userOption = -1;
                for (int j = 0; j < optionDist.length; j++) {
                    if (query.getAnswerOptions().get(j).getParseIds().contains(oracleParseId)) {
                        oracleOption = j;
                    }
                    if (query.getAnswerOptions().get(j).getParseIds().contains(0)) {
                        oneBestOption = j;
                    }
                    if (userOption < 0 || optionDist[j] > optionDist[userOption]) {
                        userOption = j;
                    }
                }
                if (userOption < 0) {
                    userOption = oneBestOption;
                }

                // Update probability distribution.
                query.computeProbabilities(learner.beliefModel.belief);
                /*if (query.getAnswerOptions().get(userOption).getProbability() < 0.1) {
                    continue;
                }*/
                if (agreement < minAgreement || QualityControl.categoriesToFilter.contains(category)) {
                    continue;
                }
                if (skipPronouns && QualityControl.queryContainsPronoun(query)) {
                    // TODO: Still, see what people choose.
                    // System.err.println(query.getDebuggingInfo(new Response(userOption)));
                    numPronounQuestions ++;
                    continue;
                }

                learner.receiveObservationForQueryNoNA(query, new Response(userOption));
                rerankedF1[userOption] = learner.getRerankedF1(sentenceId);

                // Incorporate all answers.
                /*
                for (int j = 0; j < optionDist.length; j++) {
                    for (int k = 0; k < optionDist[j]; k++) {
                        learner.receiveObservationForQueryNoNA(query, new Response(j));
                    }
                    rerankedF1[j] = learner.getRerankedF1(sentenceId);
                }*/

                // Print.
                String sentenceStr = query.getSentence().stream().collect(Collectors.joining(" "));
                int predId = query.getPredicateIndex();
                int rerankParseId = learner.getRerankParseId(sentenceId);
                String result =  "SID=" + sentenceId + "\t" + sentenceStr + "\n" + "PRED=" + predId+ "\t \t"
                        + query.getQuestion() + "\t" + query.getQuestionKey() + "\t"
                        + String.format("Baseline:: %.3f%%\tOracle:: %.3f%%\tOracle ParseId::%d\tRerank ParseId::%d\n",
                        100.0 * baselineF1.getF1(), 100.0 * oracleF1.getF1(), oracleParseId, rerankParseId);
                for (int j = 0; j < optionDist.length; j++) {
                    String match = "";
                    for (int k = 0; k < optionDist[j]; k++) {
                        match += "*";
                    }
                    if (j == goldOption) {
                        match += "G";
                    }
                    if (j == oracleOption) {
                        match += "O";
                    }
                    if (j == oneBestOption) {
                        match += "B";
                    }
                    if (j == userOption) {
                        match += "U";
                    }
                    String improvement = " ";
                    if (rerankedF1[j] != null) {
                        if (rerankedF1[j].getF1() < currentF1.getF1() - 1e-8) {
                            improvement = "[-]";
                            numWorsenedSentences ++;
                        } else if (rerankedF1[j].getF1() > currentF1.getF1() + 1e-8) {
                            improvement = "[+]";
                            numImprovedSentences ++;
                        } else {
                            numUnchangedSentences ++;
                        }
                    }
                    GroupedQuery.AnswerOption option = query.getAnswerOptions().get(j);
                    if (j == userOption) {
                        Results debuggingF1 = CcgEvaluation.evaluate(
                                learner.allParses.get(sentenceId).get(rerankParseId).dependencies,
                                learner.goldParses.get(sentenceId).dependencies);
                        assert Math.abs(debuggingF1.getF1() - rerankedF1[j].getF1()) < 1e-6;
                        result += String.format("%-8s\t%.3f\t%-40s\t%.3f%%\t%s\t%s\n", match, option.getProbability(),
                                option.getAnswer(), 100.0 * rerankedF1[j].getF1(), improvement,
                                DebugPrinter.getShortListString(option.getParseIds()));
                        currentF1 = rerankedF1[j];
                    } else {
                        result += String.format("%-8s\t%.3f\t%-40s\t-\t-\t%s\n", match, option.getProbability(),
                                option.getAnswer(), DebugPrinter.getShortListString(option.getParseIds()));
                    }
                }
                int cnt = optionDist.length;
                for (int j : unmatched) {
                    String match = "";
                    for (int k = 0; k < annotation.answerDist[j]; k++) {
                        match += "*";
                    }
                    result += String.format("%-8s\t---\t%-40s\n", match, annotation.answerOptions.get(j));
                }
                sentenceDebuggingString += result + "\n";
                //System.out.println(result);
                /*
                for (int i = 0; i < learner.beliefModel.belief.length; i++) {
                    System.out.print(learner.beliefModel.belief[i] * 100 + "\t");
                }
                System.out.println("\n");
                */
            }
            if (queryCount > 0) {
                sentenceDebuggingString += String.format("Final F1: %.3f%% over %.3f%% baseline.\t", 100.0 * currentF1.getF1(),
                        100.0 * baselineF1.getF1()) + (currentF1.getF1() > baselineF1.getF1() ? "Improved." :
                        (currentF1.getF1() < baselineF1.getF1() ? "Worsened." : "Unchanged")) + "\n";
                avgBaseline.add(baselineF1);
                avgRerank.add(currentF1);
                debugging.add(new DebugBlock(currentF1.getF1() - baselineF1.getF1(), sentenceDebuggingString));
            }
        }
        System.out.println("Baseline:\n" + avgBaseline + "\nRerank:\n" + avgRerank);
        System.out.println("Num improved:\t" + numImprovedSentences + "\nNum worsened:\t" + numWorsenedSentences +
                "\nNum unchanged:\t" + numUnchangedSentences);
        System.out.println("Number of pronoun questions:\t" + numPronounQuestions);
        debugging.stream().sorted((b1, b2) ->
            Double.compare(b1.F1, b2.F1)
        ).forEach(b -> System.out.println(b.block));
    }

    private static AlignedAnnotation getAlignedAnnotation(GroupedQuery query, List<AlignedAnnotation> annotations) {
        String qkey =query.getPredicateIndex() + "\t" + query.getQuestion();
        for (AlignedAnnotation annotation : annotations) {
            String qkey2 = annotation.predicateId + "\t" + annotation.question;
            if (qkey.equals(qkey2)) {
                return annotation;
            }
        }
        return null;
    }

    private static Set<Integer> getUnmatchedAnnotationOptions(GroupedQuery query, AlignedAnnotation annotation) {
        Set<Integer> unmatched = new HashSet<>();
        for (int i = 0; i < annotation.answerStrings.size(); i++) {
            boolean matched = false;
            for (int j = 0; j < query.getAnswerOptions().size(); j++) {
                if (query.getAnswerOptions().get(j).getAnswer().equals(annotation.answerStrings.get(i))) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                unmatched.add(i);
            }
        }
        return unmatched;
    }
}
