package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.QualityControl;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils.*;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;

import java.util.*;
import java.util.stream.Collectors;

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
            "./Crowdflower_data/f878213.csv",
            "./Crowdflower_data/f882410.csv",
            "./Crowdflower_data/all-checkbox-responses.csv",
            "./Crowdflower_data/f891522.csv",
    };

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
    }

    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.jeopardyQuestionMinAgreement = 3;
        reparsingParameters.jeopardyQuestionWeight = 0.000;
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
        List<Integer> sentenceIds = annotations.keySet().stream().sorted().collect(Collectors.toList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        System.out.println("Queried " + sentenceIds.size() + " sentences. Total number of questions:\t" +
            annotations.entrySet().stream().mapToInt(e -> e.getValue().size()).sum());

        List<DebugBlock> debugging = new ArrayList<>();
        for (int sentenceId : sentenceIds) {
            final ImmutableList<String> sentence = myHTILParser.getSentence(sentenceId);
            final NBestList nBestList = myHTILParser.getNBestList(sentenceId);

            final List<AlignedAnnotation> annotated = annotations.get(sentenceId);
            boolean isJeopardyStyle = annotated.stream()
                    .anyMatch(annot -> annot.answerOptions.stream()
                            .anyMatch(op -> op.endsWith("?")));
            boolean isCheckboxStyle = !annotated.stream()
                    .anyMatch(annot -> annot.answerOptions.stream()
                            .anyMatch(op -> op.contains(QAPairAggregatorUtils.answerDelimiter)));

            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList = isJeopardyStyle ?
                            myHTILParser.getPPAttachmentQueriesForSentence(sentenceId) :
                            myHTILParser.getCoreArgumentQueriesForSentence(sentenceId, isCheckboxStyle);

            final Results baselineF1 = nBestList.getResults(0);
            Results currentF1 = nBestList.getResults(0);

            final int numParses = nBestList.getN();
            final Set<Constraint> allConstraintSet = new HashSet<>();
            String sentenceDebuggingString = "";
            double[] penalty = new double[numParses];
            Arrays.fill(penalty, 0);

            myHistory.addSentence(sentenceId);

            for (ScoredQuery<QAStructureSurfaceForm> query : queryList) {
                //System.out.println(query.toString(sentence));
                AlignedAnnotation annotation = ExperimentUtils.getAlignedAnnotation(query, annotations.get(sentenceId));
                if (annotation == null) {
                    continue;
                }
                int[] optionDist = QualityControl.getUserResponses(query, annotation);
                ImmutableList<Integer> goldOptions    = myHTILParser.getGoldOptions(query),
                                       oneBestOptions = myHTILParser.getOneBestOptions(query),
                                       oracleOptions  = myHTILParser.getOracleOptions(query),
                                       userOptions    = myHTILParser.getUserOptions(query, annotation);
                if (userOptions.size() == 0) {
                    continue;
                }
                /*if (query.isJeopardyStyle() && userOptions.contains(query.getBadQuestionOptionId().getAsInt())) {
                    continue;
                }*/
                ImmutableSet<Constraint> constraintSet = myHTILParser.getConstraints(query, userOptions);
                if (constraintSet == null || constraintSet.isEmpty()) {
                    continue;
                }
                allConstraintSet.addAll(constraintSet);

                int rerankedId = myHTILParser.getRerankedParseId(sentenceId, allConstraintSet);
                Parse reparse = myHTILParser.getReparsed(sentenceId, allConstraintSet);
                Results rerankedF1 = nBestList.getResults(rerankedId);
                Results reparsedF1 = CcgEvaluation.evaluate(reparse.dependencies,
                        myHTILParser.getGoldParse(sentenceId).dependencies);

                myHistory.addEntry(sentenceId, query, userOptions, constraintSet, reparse, rerankedId, reparsedF1,
                                   rerankedF1);

                // Print debugging information.
                String result = query.toString(sentence,
                        'G', goldOptions,
                        'O', oracleOptions,
                        'B', oneBestOptions,
                        'U', userOptions,
                        '*', optionDist);
                // Evidence.
                result += constraintSet.stream()
                        .map(ev -> "Penalizing:\t" + ev.toString(sentence))
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
                sentenceDebuggingString += result + "\n";
                currentF1 = reparsedF1;
            }
            Optional<Results> lastReparsedResult = myHistory.getLastReparsingResult(sentenceId);
            if (lastReparsedResult.isPresent()) {
                double deltaF1 = lastReparsedResult.get().getF1() - baselineF1.getF1();
                String changeStr = deltaF1 < -1e-6 ? "Worsened." : (deltaF1 > 1e-6 ? "Improved." : "Unchanged.");
                sentenceDebuggingString += String.format("Final F1: %.3f%% over %.3f%% baseline.\t%s\n",
                        100.0 * lastReparsedResult.get().getF1(), 100.0 * baselineF1.getF1(), changeStr);
                debugging.add(new DebugBlock(deltaF1, sentenceDebuggingString));
            }
        }

        myHistory.printSummary();

        debugging.stream()
                .sorted((b1, b2) -> Double.compare(b1.deltaF1, b2.deltaF1))
                // .filter(b -> Math.abs(b.deltaF1) > 1e-3)
                .forEach(b -> System.out.println(b.block));
    }
}
