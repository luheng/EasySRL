package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.AnnotationUtils;
import edu.uw.easysrl.qasrl.annotation.CrowdFlowerDataUtils;
import edu.uw.easysrl.qasrl.evaluation.Accuracy;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.evaluation.TicToc;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils.*;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.model.HeuristicHelper;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.Prepositions;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.qasrl.ui.Colors;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.parser.ParserAStar;
import edu.uw.easysrl.util.GuavaCollectors;
import scala.tools.nsc.doc.base.comment.Title;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Round1-3: We can change about 10% of the sentences and improve about 5%.
 * Average cost: if we don't ask PP questions, avg cost is about 30c per sentence ...
 * Created by luheng on 3/20/16.
 */
public class ReparsingExperiment {
    private static final int nBest = 100;
    private static HITLParser myHTILParser;
    private static ReparsingHistory myHistory;
    private static Map<Integer, List<AlignedAnnotation>> annotations;

    private static final String[] annotationFiles = {
          //  "./Crowdflower_data/f878213.csv",                // Round1: radio-button, core + pp
          //  "./Crowdflower_data/f882410.csv",                // Round2: radio-button, core only
          //  "./Crowdflower_data/all-checkbox-responses.csv", // Round3: checkbox, core + pp
          //  "./Crowdflower_data/f891522.csv",                // Round4: jeopardy checkbox, pp only
            "./Crowdflower_data/f893900.csv",                   // Round3-pronouns: checkbox, core only, pronouns.
        //    "./Crowdflower_data/f897179.csv",                 // Round2-3: NP clefting questions.
            "./Crowdflower_data/f902142.csv",                   // Round4: checkbox, pronouns, core only, 300 sentences.
         //   "./Crowdflower_data/f903842.csv",                   // Round4: np-clefting prnouns
            "./Crowdflower_data/f909211.csv",                   // Round5: checkbox, pronouns, core only, 300+ sentences.
    };

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.maxNumOptionsPerQuery = 6;
        queryPruningParameters.skipPPQuestions = true;
        queryPruningParameters.skipSAdjQuestions = true;
        /*
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = 0.05;
        queryPruningParameters.minPromptConfidence = 0.1;
        */
        queryPruningParameters.minOptionConfidence = -1;
        queryPruningParameters.minOptionEntropy = -1;
        queryPruningParameters.minPromptConfidence = -1;
    }

    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.jeopardyQuestionMinAgreement = 1;
        reparsingParameters.positiveConstraintMinAgreement = 5;
        reparsingParameters.negativeConstraintMaxAgreement = 1;
        reparsingParameters.skipPronounEvidence = false;
        reparsingParameters.jeopardyQuestionWeight = 1.0;
        reparsingParameters.oraclePenaltyWeight = 5.0;
        reparsingParameters.attachmentPenaltyWeight = 2.0;
        reparsingParameters.supertagPenaltyWeight = 2.0;
    }

    public static void main(String[] args) {
        myHTILParser = new HITLParser(nBest);
        myHTILParser.setQueryPruningParameters(queryPruningParameters);
        myHTILParser.setReparsingParameters(reparsingParameters);
        annotations = ExperimentUtils.loadCrowdflowerAnnotation(annotationFiles);
        assert annotations != null;
        myHistory = new ReparsingHistory(myHTILParser);
        runExperiment();
    }

    private static void runExperiment() {
        // final Collection<Integer> round1And2Ids = CrowdFlowerDataUtils.getRound1And2SentenceIds();
        List<Integer> sentenceIds = myHTILParser.getAllSentenceIds();
                //annotations.keySet().stream().sorted().collect(Collectors.toList());
                /*myHTILParser.getAllSentenceIds().stream()
                        .filter(id -> !round1And2Ids.contains(id))
                        .collect(Collectors.toList());*/
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        System.out.println("Queried " + sentenceIds.size() + " sentences. Total number of questions:\t" +
            annotations.entrySet().stream().mapToInt(e -> e.getValue().size()).sum());

        /*
        ImmutableSet<String> annotators =
                annotations.values().stream()
                        .flatMap(al -> al.stream())
                        .flatMap(annot -> annot.annotatorToAnswerIds.keySet().stream())
                        .sorted()
                        .collect(GuavaCollectors.toImmutableSet());
        annotators.stream().forEach(System.err::println); */

        // Stats.
        ImmutableList<Results> optionAccuracy = IntStream.range(0, 5).boxed()
                .map(ignore -> new Results())
                .collect(GuavaCollectors.toImmutableList());
        ImmutableList<Accuracy> goldStrictMatch = IntStream.range(0, 5).boxed()
                .map(ignore -> new Accuracy())
                .collect(GuavaCollectors.toImmutableList());
        int numMatchedAnnotations = 0;

        List<DebugBlock> debugging = new ArrayList<>();
        Results avgBaseline = new Results(),
                avgReranked = new Results(),
                avgReparsed = new Results(),
                avgUnlabeledBaseline = new Results(),
                avgUnlabeledReranked = new Results(),
                avgUnlabeledReparsed = new Results();

        BaseCcgParser baseParser = null; // new BaseCcgParser.AStarParser(BaseCcgParser.modelFolder, 1);

        int counter = 0;
        TicToc.tic();
        for (int sentenceId : sentenceIds) {
            myHistory.addSentence(sentenceId);
            if (++counter % 100 == 0) {
                System.out.println(String.format("Processed %d sentences ... in %d seconds", counter, TicToc.toc()));
                TicToc.tic();
            }

            final ImmutableList<String> sentence = myHTILParser.getSentence(sentenceId);
            final NBestList nBestList = myHTILParser.getNBestList(sentenceId);
            final int numParses = nBestList.getN();
            final Parse goldParse = myHTILParser.getGoldParse(sentenceId);
            final Parse baselineParse = baseParser == null ?
                    myHTILParser.getParse(sentenceId, 0) :
                    baseParser.parse(sentenceId, myHTILParser.getInputSentence(sentenceId));
            Parse reparse = baselineParse, oracleReparse = baselineParse;

            final Results baselineF1 = CcgEvaluation.evaluate(baselineParse.dependencies, goldParse.dependencies);
            final Results unlabeledBaselineF1 = CcgEvaluation.evaluateUnlabeled(baselineParse.dependencies,
                    goldParse.dependencies);
            avgBaseline.add(baselineF1);
            avgUnlabeledBaseline.add(unlabeledBaselineF1);

            final List<AlignedAnnotation> annotated = annotations.get(sentenceId);
            if (annotated == null || annotated.isEmpty()) {
                avgReparsed.add(baselineF1);
                avgReranked.add(baselineF1);
                avgUnlabeledReranked.add(unlabeledBaselineF1);
                avgUnlabeledReparsed.add(unlabeledBaselineF1);
                continue;
            }
            boolean isCheckboxStyle = !annotated.stream()
                    .anyMatch(annot -> annot.answerOptions.stream()
                            .anyMatch(op -> op.contains(QAPairAggregatorUtils.answerDelimiter)));
            boolean isClefting = annotated.stream()
                    .anyMatch(annot -> annot.queryPrompt.startsWith("What is it"));

            List<ScoredQuery<QAStructureSurfaceForm>> queryList = new ArrayList<>();
            if (!isClefting) {
                if (!isCheckboxStyle) {
                    queryList.addAll(myHTILParser.getCoreArgumentQueriesForSentence(sentenceId, false /* radiobutton version*/));
                } else {
                    queryList.addAll(myHTILParser.getPronounCoreArgQueriesForSentence(sentenceId));
                }
            } else {
                queryList.addAll(myHTILParser.getCleftedQuestionsForSentence(sentenceId));
            }

            Results currentF1 = baselineF1,
                    oracleF1 = baselineF1,
                    rerankedF1 = baselineF1,
                    reparsedF1 = baselineF1,
                    unlabeledRerankedF1 = unlabeledBaselineF1,
                    unlabeledReparsedF1 = unlabeledBaselineF1;

            final Set<Constraint> allConstraints = new HashSet<>(), allOracleConstraints = new HashSet<>();
            String sentenceDebuggingString = "";
            double[] penalty = new double[numParses];
            Arrays.fill(penalty, 0);

            for (ScoredQuery<QAStructureSurfaceForm> query : queryList) {
                AlignedAnnotation annotation = ExperimentUtils.getAlignedAnnotation(query, annotations.get(sentenceId));
                ImmutableList<Integer> goldOptions    = myHTILParser.getGoldOptions(query),
                                       oneBestOptions = myHTILParser.getOneBestOptions(query),
                                       oracleOptions  = myHTILParser.getOracleOptions(query);
                if (annotation == null) {
                    System.out.println(Colors.ANSI_GREEN + "======New question======\n"
                            + query.toString(sentence, 'G', goldOptions, 'B', oneBestOptions, 'O', oracleOptions)
                            + "\n" + Colors.ANSI_RESET);
                    continue;
                }
                int[] optionDist = AnnotationUtils.getUserResponseDistribution(query, annotation);
                ImmutableList<ImmutableList<Integer>> responses = AnnotationUtils.getAllUserResponses(query, annotation);
                ImmutableList<ImmutableList<Integer>> newResponses = HeuristicHelper.adjustVotes(sentence, query, responses);
                int[] newOptionDist = new int[optionDist.length];
                newResponses.stream().forEach(resp -> resp.stream().forEach(op -> newOptionDist[op]++));

                ImmutableList<Integer> userOptions    = myHTILParser.getUserOptions(query, annotation),
                                       userOptions2   = myHTILParser.getUserOptions(query, newOptionDist);

                // Update stats.
                for (int i = 0; i < 5; i++) {
                    final int minAgr = i + 1;
                    ImmutableList<Integer> options = IntStream.range(0, optionDist.length).boxed()
                            .filter(id -> optionDist[id] >= minAgr)
                            .collect(GuavaCollectors.toImmutableList());
                    if (options.size() > 0) {
                        goldStrictMatch.get(i).add(options.containsAll(goldOptions) && goldOptions.containsAll(options));
                        optionAccuracy.get(i).add(new Results(options.size(),
                                (int) options.stream().filter(goldOptions::contains).count(), goldOptions.size()));
                    }
                }
                numMatchedAnnotations ++;

                /*if (userOptions.isEmpty()) {
                    continue;
                }*/
                if (Prepositions.prepositionWords.contains(sentence.get(query.getPredicateId().getAsInt()).toLowerCase())) {
                    continue;
                }
                if (query.isJeopardyStyle() && userOptions.contains(query.getBadQuestionOptionId().getAsInt())) {
                    continue;
                }
                ImmutableSet<Constraint> constraints = myHTILParser.getConstraints(query, newOptionDist),
                                         oracleConstraints = myHTILParser.getOracleConstraints(query); //, goldOptions);

                // Set constraint strength proportional to sentence length.
                //constraints.stream().forEach(c -> c.setStrength(0.2 * sentence.size()));

                allConstraints.addAll(constraints);
                allOracleConstraints.addAll(oracleConstraints);

                int rerankedId = myHTILParser.getRerankedParseId(sentenceId, allConstraints);
                if (constraints.size() > 0) {
                    // Reparse only when there're new constraints.
                    reparse = myHTILParser.getReparsed(sentenceId, allConstraints);
                }
                oracleReparse = myHTILParser.getNBestList(sentenceId)
                        .getParse(myHTILParser.getRerankedParseId(sentenceId, allOracleConstraints));
                rerankedF1 = nBestList.getResults(rerankedId);
                unlabeledRerankedF1 = CcgEvaluation.evaluateUnlabeled(nBestList.getParse(rerankedId).dependencies,
                                                                      goldParse.dependencies);
                reparsedF1 = CcgEvaluation.evaluate(reparse.dependencies, goldParse.dependencies);
                unlabeledReparsedF1 = CcgEvaluation.evaluateUnlabeled(reparse.dependencies, goldParse.dependencies);
                oracleF1 = CcgEvaluation.evaluate(oracleReparse.dependencies, goldParse.dependencies);

                myHistory.addEntry(sentenceId, query, userOptions, constraints, oracleConstraints, reparse,
                                   oracleReparse, rerankedId, reparsedF1, rerankedF1, oracleF1);

                // Print debugging information.
                String result = query.toString(sentence,
                        'G', goldOptions,
                        'O', oracleOptions,
                        'B', oneBestOptions,
                        'U', userOptions,
                        'R', userOptions2,
                        '*', optionDist);
                // Debugging.
                // result += "-----\n" + annotation.toString() + "\n";
                /*
                result += "-----\n" + appositives.stream()
                        .map(ap -> ap.stream().map(query.getOptions()::get).collect(Collectors.joining("\t---\t")))
                        .collect(Collectors.joining("\n")) + "\n";
                        */

                // Evidence.
                result += allConstraints.stream()
                        .map(c -> "Penalizing:\t \t" + c.toString(sentence))
                        .collect(Collectors.joining("\n")) + "\n";
                // Improvement.
                String f1Impv = " ";
                if (reparsedF1 != null) {
                    if (reparsedF1.getF1() < currentF1.getF1() - 1e-8) {
                        f1Impv = "[-]";
                    } else if (reparsedF1.getF1() > currentF1.getF1() + 1e-8) {
                        f1Impv = "[+]";
                    }
                }
                result += String.format("F1: %.3f%% -> %.3f%% %s\n",
                        100.0 * currentF1.getF1(), 100.0 * reparsedF1.getF1(), f1Impv);
                result += String.format("Reranked F1: %.3f%%\n", 100.0 * rerankedF1.getF1());
                result += String.format("Reparsed F1: %.3f%%\n", 100.0 * reparsedF1.getF1());
                result += String.format("Oracle F1: %.3f%%\n",   100.0 * oracleF1.getF1());
                sentenceDebuggingString += result + "\n";
                currentF1 = reparsedF1;
            }

            avgReparsed.add(reparsedF1);
            avgReranked.add(rerankedF1);
            avgUnlabeledReranked.add(unlabeledRerankedF1);
            avgUnlabeledReparsed.add(unlabeledReparsedF1);

            Optional<Results> lastReparsedResult = myHistory.getLastReparsingResult(sentenceId);
            if (lastReparsedResult.isPresent()) {
                double deltaF1 = lastReparsedResult.get().getF1() - baselineF1.getF1();
                String changeStr = deltaF1 < -1e-6 ? "Worsened." : (deltaF1 > 1e-6 ? "Improved." : "Unchanged.");
                sentenceDebuggingString += String.format("Final F1: %.3f%% over %.3f%% baseline.\t%s\n",
                        100.0 * lastReparsedResult.get().getF1(), 100.0 * baselineF1.getF1(), changeStr);
                DebugBlock debugBlock = new DebugBlock(deltaF1, sentenceDebuggingString);
                debugBlock.oracleDeltaF1 = oracleF1.getF1() - lastReparsedResult.get().getF1();
                debugging.add(debugBlock);
            }
        }

        myHistory.printSummary();
        System.out.println("Labeled baseline:\n" + avgBaseline);
        System.out.println("Labeled reranked:\n" + avgReranked);
        System.out.println("Labeled reparsed:\n" + avgReparsed);
        System.out.println("Unlabeled baseline:\n" + avgUnlabeledBaseline);
        System.out.println("Unlabeled reranked:\n" + avgUnlabeledReranked);
        System.out.println("Unlabeled reparsed:\n" + avgUnlabeledReparsed);

        debugging.stream()
                .sorted((b1, b2) -> Double.compare(b1.deltaF1, b2.deltaF1))
        //                .filter(b -> b.oracleDeltaF1 > 1e-3)
        //         .filter(b -> Math.abs(b.deltaF1) > 1e-3)
                .forEach(b -> System.out.println(b.block));

        System.out.println("Num. matched annotations:\t" + numMatchedAnnotations);
        for (int i = 0; i < 5; i++) {
            System.out.println(String.format("Min agreement=%d\tCoverage=%.2f%%\t%s\t%s",
                    i + 1, 100.0 * goldStrictMatch.get(i).getNumTotal() / numMatchedAnnotations,
                    goldStrictMatch.get(i), optionAccuracy.get(i)));
        }
    }
}
