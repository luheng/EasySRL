package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.QualityControl;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils.*;
import edu.uw.easysrl.qasrl.model.Evidence;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.QAPairAggregators;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryGenerators;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Replicating the reparsing results ...
 * Created by luheng on 3/20/16.
 */
public class ReparsingExperiment {

    private static final int nBest = 100;
    private static final int maxNumOptionsPerQuestion = 6;
    /*
    static {
        GroupedQuery.maxNumNonNAOptionsPerQuery = maxNumOptionsPerQuestion - 2;
    }*/

    static ParseData devData;
    static ImmutableList<ImmutableList<String>> sentences;
    static ImmutableList<ImmutableList<InputReader.InputWord>> inputSentences;
    static ImmutableList<Parse> goldParses;

    // Query pruning parameters.
    private static QueryPruningParameters queryPruningParameters = new QueryPruningParameters();

    private static final String[] annotationFiles = {
            // "./Crowdflower_data/f878213.csv",
            // "./Crowdflower_data/f882410.csv",
            "./Crowdflower_data/all-checkbox-responses.csv"
    };

    static final int minAgreement = 2;
    static final double supertagPenaltyWeight = 1.0;
    static final double attachmentPenaltyWeight = 1.0;

    static final int ppQuestionMinAgreement = 4;
    static final double ppQuestionWeight = 1.0;

    static final boolean skipPrepositionalQuestions = true;
    static final boolean skipPronounEvidence = true;

    static final int maxTagsPerWord = 50;
    static BaseCcgParser parser;
    static BaseCcgParser.ConstrainedCcgParser reparser;

    static Map<Integer, List<AlignedAnnotation>> annotations;

    public static void main(String[] args) {
        devData = ParseData.loadFromDevPool().get();
        sentences = devData.getSentences();
        inputSentences = devData.getSentenceInputWords();
        goldParses = devData.getGoldParses();
        System.out.println(String.format("Read %d sentences from the dev set.", sentences.size()));

        String preparsedFile = "parses.100best.out";
        parser = new BaseCcgParser.MockParser(preparsedFile, nBest);
        System.err.println("Parse initialized.");

        annotations = ExperimentUtils.loadCrowdflowerAnnotation(annotationFiles);
        assert annotations != null;
        // Re-parsing!
        reparser = new BaseCcgParser.ConstrainedCcgParser(BaseCcgParser.modelFolder, BaseCcgParser.rootCategories,
                maxTagsPerWord, 1 /* nbest */);

        runExperiment();
    }

    private static void runExperiment() {
        List<Integer> sentenceIds = annotations.keySet().stream().sorted().collect(Collectors.toList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        System.out.println("Queried " + sentenceIds.size() + " sentences.");
        Results avgBaseline = new Results(),
                avgReranked = new Results(),
                avgReparsed = new Results(),
                avgBeselineChanged = new Results(),
                avgReparsedChanged = new Results(),
                avgOracleChanged = new Results(),
                avgReparsedOracle = new Results();
        int numWorsenedSentences = 0,
                numUnchangedSentences = 0,
                numImprovedSentences = 0,
                numChanged = 0;

        // learner.setQueryPruningParameters(queryPruningParameters);
        // ResponseSimulator goldSimulator = new ResponseSimulatorGold(learner.goldParses);

        List<DebugBlock> debugging = new ArrayList<>();
        for (int sentenceId : sentenceIds) {
            final ImmutableList<String> sentence = sentences.get(sentenceId);
            final Optional<NBestList> nBestListOpt = NBestList.getNBestList(parser, sentenceId, inputSentences.get(sentenceId));
            if (!nBestListOpt.isPresent()) {
                continue;
            }
            final NBestList nBestList = nBestListOpt.get();
            final List<AlignedAnnotation> annotated = annotations.get(sentenceId);
            boolean isRadioButtonVersion = annotated.stream()
                    .anyMatch(annot -> annot.answerOptions.stream()
                            .anyMatch(op -> op.contains(QAPairAggregatorUtils.answerDelimiter)));

            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList =
                    isRadioButtonVersion ?
                            ExperimentUtils.generateAllRadioButtonQueries(sentenceId, sentence, nBestList, queryPruningParameters) :
                            ExperimentUtils.generateAllCheckboxQueries(sentenceId, sentence, nBestList, queryPruningParameters);

            nBestList.cacheResults(goldParses.get(sentenceId));
            int oracleId = nBestList.getOracleId();
            Results oracleF1 = nBestList.getResults(oracleId);
            Results baselineF1 = nBestList.getResults(0);
            Results currentF1 = nBestList.getResults(0);
            Results currentReparsedF1 = nBestList.getResults(0);

            List<Parse> reparsed = null;
            List<Results> reparsedF1 = null;

            final int numParses = nBestList.getN();

            final Set<Evidence> allEvidenceSet = new HashSet<>();
            int queryCount = 0;
            String sentenceDebuggingString = "";
            double[] penalty = new double[numParses];
            Arrays.fill(penalty, 0);
            for (ScoredQuery<QAStructureSurfaceForm> query : queryList) {
                AlignedAnnotation annotation = ExperimentUtils.getAlignedAnnotation(query, annotations.get(sentenceId));
                if (annotation == null) {
                    continue;
                }
                queryCount ++;
                int[] optionDist = QualityControl.getUserResponses(query, annotation);
                //int goldOption = goldSimulator.answerQuestion(query).chosenOptions.get(0);
                int oracleOption = -1;
                int oneBestOption = -1;
                Set<Integer> userResponses = new HashSet<>();
                boolean isPPQuestion =  QualityControl.queryIsPrepositional(query);
                for (int j = 0; j < optionDist.length; j++) {
                    /*
                    if (query.getAnswerOptions().get(j).getParseIds().contains(oracleParseId)) {
                        oracleOption = j;
                    }
                    if (query.getAnswerOptions().get(j).getParseIds().contains(0)) {
                        oneBestOption = j;
                    }
                    */
                    if ((!isPPQuestion && optionDist[j] >= minAgreement) ||
                            (isPPQuestion && optionDist[j] >= ppQuestionMinAgreement)) {
                        userResponses.add(j);
                    }
                }
                // Update probability distribution.
                // query.(learner.beliefModel.belief);
                if (skipPrepositionalQuestions && isPPQuestion) {
                    continue;
                }
                // Get evidence and reset weight.
                double questionTypeWeight = isPPQuestion ? ppQuestionWeight : 1.0;
                Set<Evidence> evidenceSet = Evidence.getEvidenceFromQuery(query, userResponses, skipPronounEvidence)
                        .stream()
                        .filter(ev -> !(isPPQuestion && Evidence.AttachmentEvidence.class.isInstance(ev)))
                        .collect(Collectors.toSet());
                evidenceSet.forEach(ev -> ev.setConfidence(questionTypeWeight *
                        (Evidence.SupertagEvidence.class.isInstance(ev) ? supertagPenaltyWeight :
                                attachmentPenaltyWeight)));
                allEvidenceSet.addAll(evidenceSet);
                double[] combinedScore = new double[numParses];
                int rerankedId = 0;
                for (int i = 0; i < nBestList.getN(); i++) {
                    Parse parse = nBestList.getParse(i);
                    penalty[i] += evidenceSet.stream()
                            .filter(ev -> ev.hasEvidence(parse))
                            .mapToDouble(ev -> ev.isPositive() ? ev.getConfidence() : -ev.getConfidence()).sum();
                    combinedScore[i] = parse.score + penalty[i];
                    if (combinedScore[i] > combinedScore[rerankedId] + 1e-6) {
                        rerankedId = i;
                    }
                }
                Results rerankedF1 = nBestList.getResults(rerankedId);

                if (reparser != null) {
                    reparsed = new ArrayList<>();
                    reparsed.add(reparser.parseWithConstraint(inputSentences.get(sentenceId), allEvidenceSet));
                    final Parse gold = goldParses.get(sentenceId);
                    reparsedF1 = reparsed.stream().map(p -> CcgEvaluation.evaluate(p.dependencies, gold.dependencies))
                            .collect(Collectors.toList());
                }
                // Print.
                String sentenceStr = sentence.stream().collect(Collectors.joining(" "));
                int predId = query.getQAPairSurfaceForms().get(0).getPredicateIndex();
                Category category = query.getQAPairSurfaceForms().get(0).getCategory();
                int argNum = query.getQAPairSurfaceForms().get(0).getArgumentNumber();
                String result =  "SID=" + sentenceId + "\t" + sentenceStr + "\n" + "PRED=" + predId+ "\t \t"
                        + query.getPrompt() + "\t" + category + "." + argNum + "\t"
                        + String.format("Baseline:: %.3f%%\tOracle:: %.3f%%\tOracle ParseId::%d\tRerank ParseId::%d\n",
                        100.0 * baselineF1.getF1(), 100.0 * oracleF1.getF1(), oracleId, rerankedId);
                result += evidenceSet.stream()
                        .map(ev -> "Penalizing:\t" + ev.toString(sentences.get(sentenceId)))
                        .collect(Collectors.joining("\n")) + "\n";
                // TODO: add gold, oracle and one-best penalty.
                for (int j = 0; j < optionDist.length; j++) {
                    String match = "";
                    for (int k = 0; k < optionDist[j]; k++) {
                        match += "*";
                    }
                    //if (j == goldOption) { match += "G"; }
                    if (j == oracleOption) {
                        match += "O";
                    }
                    if (j == oneBestOption) {
                        match += "B";
                    }
                    if (userResponses.contains(j)) {
                        match += "U";
                    }
                    String option = query.getOptions().get(j);
                    String headStr = "-", parseIdsStr = "-";
                    if (j < query.getQAPairSurfaceForms().size()) {
                        QAStructureSurfaceForm qa = query.getQAPairSurfaceForms().get(j);
                        final List<Integer> argList = qa.getArgumentIndices();
                        headStr = DebugPrinter.getShortListString(argList) + ":" +
                                argList.stream().map(sentence::get).collect(Collectors.joining(","));
                        parseIdsStr = DebugPrinter.getShortListString(qa.getAnswerStructures().get(0).parseIds);
                    }
                    result += String.format("%-8s\t%.3f\t%-40s\t%-30s\t-\t-\t%s\n", match,
                            query.getOptionScores().get(j), option, headStr, parseIdsStr);
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
                /*
                result += String.format("onebest penalty:%.3f\tscore:%.3f\n", penalty[0], parses.get(0).score);
                result += String.format("rerank penalty:%.3f\tscore:%.3f\n", penalty[rerankParseId],
                        parses.get(rerankParseId).score);
                result += String.format("oracle penalty:%.3f\tscore:%.3f\n", penalty[oracleParseId],
                        parses.get(oracleParseId).score);
                */
                result += String.format("F1: %.3f%% -> %.3f%% %s\n",
                        100.0 * currentReparsedF1.getF1(),
                        100.0 * reparsedF1.get(0).getF1(), f1Impv);
                result += String.format("Reranked F1: %.3f%%\n", 100.0 * rerankedF1.getF1());
                result += String.format("Reparsed F1: %.3f%%\n", 100.0 * reparsedF1.get(0).getF1());
                sentenceDebuggingString += result + "\n";
                currentF1 = rerankedF1;
                currentReparsedF1 = reparsedF1.get(0);
            }
            boolean changedOneBest = reparsed != null &&
                    CcgEvaluation.evaluate(reparsed.get(0).dependencies, nBestList.getParse(0).dependencies).getF1()
                            < 1.0 - 1e-3;
            double deltaF1 = currentReparsedF1.getF1() - baselineF1.getF1();
            String changeStr;
            if (deltaF1 < - 1e-8) {
                numWorsenedSentences ++;
                changeStr = "Worsened.";
            } else if (deltaF1 > 1e-8) {
                numImprovedSentences ++;
                changeStr = "Improved.";
            } else {
                numUnchangedSentences ++;
                changeStr = "Unchanged.";
            }
            avgBaseline.add(baselineF1);
            avgReranked.add(currentF1);
            avgReparsed.add(currentReparsedF1);
            if (changedOneBest) {
                avgBeselineChanged.add(baselineF1);
                avgReparsedChanged.add(currentReparsedF1);
                avgOracleChanged.add(oracleF1);

                // Get oracle for re-parsed.
                Results newOracle = reparsedF1.stream().max((r1, r2) -> Double.compare(r1.getF1(), r2.getF1())).get();
                avgReparsedOracle.add(newOracle);

                numChanged ++;
            }
            if (queryCount > 0) {
                sentenceDebuggingString += String.format("Final F1: %.3f%% over %.3f%% baseline.\t%s\n",
                        100.0 * currentReparsedF1.getF1(), 100.0 * baselineF1.getF1(), changeStr);
                debugging.add(new DebugBlock(deltaF1, sentenceDebuggingString));
            }
        }
        System.out.println("Baseline:\n" + avgBaseline + "\nRerank:\n" + avgReranked + "\nReparsed:\n" + avgReparsed);
        System.out.println("Baseline-changed\n" + avgBeselineChanged + "\nReparsed-changed:\n" + avgReparsedChanged +
                "\nOracle-changed\n" + avgOracleChanged);
        //+ "\nReparsed-oracle:\n" + avgReparsedOracle);
        System.out.println("Num improved: " + numImprovedSentences + "\nNum worsened: " + numWorsenedSentences +
                "\nNum unchanged: " + numUnchangedSentences);
        System.out.println("Num changed sentences: " + numChanged);
        debugging.stream()
                .sorted((b1, b2) -> Double.compare(b1.deltaF1, b2.deltaF1))
                // .filter(b -> Math.abs(b.deltaF1) > 1e-3)
                .forEach(b -> System.out.println(b.block));
    }
}
