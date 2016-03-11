package edu.uw.easysrl.qasrl.pomdp;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.CrowdFlowerDataReader;
import edu.uw.easysrl.qasrl.annotation.QualityControl;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class EvidenceDrivenExperiment {
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

    static final int minAgreement = 2;
    static final double supertagPenaltyWeight = 5.0;
    static final double attachmentPenaltyWeight = 1.0;
    static final boolean skipPronouns = false;

    static BaseCcgParser.ConstrainedCcgParser reparser;

    public static void main(String[] args) {
        Map<Integer, List<AlignedAnnotation>> annotations = loadData(annotationFiles);
        assert annotations != null;

        // Re-parsing!
        reparser = new BaseCcgParser.ConstrainedCcgParser(BaseCcgParser.modelFolder, BaseCcgParser.rootCategories,
                1 /* nbest */);

        printSequentialAnalysis(annotations, reparser);
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

    static class DebugBlock {
        double deltaF1;
        String block;
        DebugBlock(double deltaF1, String block) {
            this.deltaF1 = deltaF1;
            this.block = block;
        }
    }

    private static void printSequentialAnalysis(Map<Integer, List<AlignedAnnotation>> annotations,
                                                BaseCcgParser.ConstrainedCcgParser reparser) {
        List<Integer> sentenceIds = annotations.keySet().stream().sorted().collect(Collectors.toList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));

        Results avgBaseline = new Results(),
                avgReranked = new Results(),
                avgReparsed = new Results();
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
            Results currentF1 = learner.getOneBestF1(sentenceId);
            Results currentReparsedF1 = learner.getOneBestF1(sentenceId);
            final List<String> sentence = learner.getSentenceById(sentenceId);
            final List<Parse> parses = learner.allParses.get(sentenceId);
            final int numParses = parses.size();
            final List<GroupedQuery> queries = learner.getQueryPool();
            final Set<Evidence> allEvidenceSet = new HashSet<>();
            int queryCount = 0;
            String sentenceDebuggingString = "";
            double[] penalty = new double[numParses];
            Arrays.fill(penalty, 0);
            for (GroupedQuery query : queries) {
                AlignedAnnotation annotation = getAlignedAnnotation(query, annotations.get(sentenceId));
                if (annotation == null) {
                    continue;
                }
                queryCount ++;
                int[] optionDist = QualityControl.getUserResponses(query, annotation);
                int goldOption = goldSimulator.answerQuestion(query).chosenOptions.get(0);
                Category category = query.getCategory();
                int oracleParseId = learner.getOracleParseId(sentenceId);
                int oracleOption = -1;
                int oneBestOption = -1;
                Response multiResponse = new Response();
                for (int j = 0; j < optionDist.length; j++) {
                    if (query.getAnswerOptions().get(j).getParseIds().contains(oracleParseId)) {
                        oracleOption = j;
                    }
                    if (query.getAnswerOptions().get(j).getParseIds().contains(0)) {
                        oneBestOption = j;
                    }
                    if (optionDist[j] >= minAgreement) {
                        multiResponse.add(j);
                    }
                }

                // Update probability distribution.
                query.computeProbabilities(learner.beliefModel.belief);
                if (QualityControl.queryIsPrepositional(query)) {
                    continue;
                }
                if (skipPronouns && QualityControl.queryContainsPronoun(query)) {
                    continue;
                }

                // Get evidence and reset weight.
                Set<Evidence> evidenceSet = Evidence.getEvidenceFromQuery(query, multiResponse);
                evidenceSet.forEach(ev -> ev.confidence = Evidence.SupertagEvidence.class.isInstance(ev) ?
                                supertagPenaltyWeight : attachmentPenaltyWeight);
                allEvidenceSet.addAll(evidenceSet);

                double[] combinedScore = new double[numParses];
                for (int i = 0; i < learner.allParses.get(sentenceId).size(); i++) {
                    Parse parse = learner.allParses.get(sentenceId).get(i);
                    penalty[i] += evidenceSet.stream()
                            .filter(ev -> ev.hasEvidence(parse))
                            .mapToDouble(ev -> ev.isPositive ? ev.confidence : -ev.confidence).sum();
                    combinedScore[i] = parse.score + penalty[i];
                }
                double expAcc0 = ExpectedAccuracy.compute(learner.beliefModel.belief, learner.allParses.get(sentenceId));
                learner.beliefModel.resetTo(combinedScore);
                Results rerankedF1 = learner.getRerankedF1(sentenceId);
                Results reparsedF1 = new Results();
                Parse reparsed = null;
                if (reparser != null) {
                    reparsed = reparser.parseWithConstraint(learner.sentences.get(sentenceId), allEvidenceSet);
                    reparsedF1 = CcgEvaluation.evaluate(reparsed.dependencies,
                                                        learner.goldParses.get(sentenceId).dependencies);
                }

                // Print.
                String sentenceStr = query.getSentence().stream().collect(Collectors.joining(" "));
                int predId = query.getPredicateIndex();
                int rerankParseId = learner.getRerankParseId(sentenceId);
                String result =  "SID=" + sentenceId + "\t" + sentenceStr + "\n" + "PRED=" + predId+ "\t \t"
                        + query.getQuestion() + "\t" + query.getQuestionKey() + "\t"
                        + String.format("Baseline:: %.3f%%\tOracle:: %.3f%%\tOracle ParseId::%d\tRerank ParseId::%d\n",
                        100.0 * baselineF1.getF1(), 100.0 * oracleF1.getF1(), oracleParseId, rerankParseId);
                result += evidenceSet.stream()
                        .map(ev -> ev.toString(sentence))
                        .collect(Collectors.joining("\n")) + "\n";
                // TODO: add gold, oracle and one-best penalty.
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
                    if (multiResponse.chosenOptions.contains(j)) {
                        match += "U";
                    }
                    GroupedQuery.AnswerOption option = query.getAnswerOptions().get(j);
                    String headStr = option.isNAOption() ? "-" : String.format("%s:%s",
                            DebugHelper.getShortListString(option.getArgumentIds()),
                            option.getArgumentIds().stream().map(sentence::get).collect(Collectors.joining(",")));
                    result += String.format("%-8s\t%.3f\t%-40s\t%-30s\t-\t-\t%s\n", match, option.getProbability(),
                                option.getAnswer(), headStr,
                                DebugHelper.getShortListString(option.getParseIds()));
                }
                String f1Impv = " ";
                if (rerankedF1 != null) {
                    if (rerankedF1.getF1() < currentF1.getF1() - 1e-8) {
                        f1Impv = "[-]";
                    } else if (rerankedF1.getF1() > currentF1.getF1() + 1e-8) {
                        f1Impv = "[+]";
                    }
                }
                //double expAcc = ExpectedAccuracy.compute(learner.beliefModel.belief, learner.allParses.get(sentenceId));
                //String expAccImpv = expAcc0 < expAcc ? "[+++]" : "[---]";
                //result += String.format("Exp acc: %.3f%% - %.3f%% %s", 100.0 * expAcc0, 100.0 * expAcc, expAccImpv);
                result += String.format("onebest penalty:%.3f\tscore:%.3f\n", penalty[0], parses.get(0).score);
                result += String.format("rerank penalty:%.3f\tscore:%.3f\n", penalty[rerankParseId],
                        parses.get(rerankParseId).score);
                result += String.format("oracle penalty:%.3f\tscore:%.3f\n", penalty[oracleParseId],
                        parses.get(oracleParseId).score);
                result += String.format("F1: %.3f%% - %.3f%% %s\n", 100.0 * currentF1.getF1(), 100.0 * rerankedF1.getF1(),
                        f1Impv);
                result += String.format("Reparsed F1: %.3f%%\n", 100.0 * reparsedF1.getF1());
                sentenceDebuggingString += result + "\n";
                currentF1 = rerankedF1;
                currentReparsedF1 = reparsedF1;
            }
            if (currentReparsedF1.getF1() < baselineF1.getF1() - 1e-8) {
                numWorsenedSentences ++;
            } else if (currentReparsedF1.getF1() > baselineF1.getF1() + 1e-8) {
                numImprovedSentences ++;
            } else {
                numUnchangedSentences ++;
            }
            avgBaseline.add(baselineF1);
            avgReranked.add(currentF1);
            avgReparsed.add(currentReparsedF1);
            if (queryCount > 0) {
                sentenceDebuggingString += String.format("Final F1: %.3f%% over %.3f%% baseline.\t",
                        100.0 * currentF1.getF1(),
                        100.0 * baselineF1.getF1()) + (currentF1.getF1() > baselineF1.getF1() ? "Improved." :
                        (currentF1.getF1() < baselineF1.getF1() ? "Worsened." : "Unchanged")) + "\n";
                debugging.add(new DebugBlock(currentF1.getF1() - baselineF1.getF1(), sentenceDebuggingString));
            }
        }
        System.out.println("Baseline:\n" + avgBaseline + "\nRerank:\n" + avgReranked + "\nReparsed:\n" + avgReparsed);
        System.out.println("Num improved:\t" + numImprovedSentences + "\nNum worsened:\t" + numWorsenedSentences +
                "\nNum unchanged:\t" + numUnchangedSentences);
        debugging.stream()
                .sorted((b1, b2) -> Double.compare(b1.deltaF1, b2.deltaF1))
                .filter(b -> Math.abs(b.deltaF1) > 1e-3)
                .forEach(b -> System.out.println(b.block));
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
}
