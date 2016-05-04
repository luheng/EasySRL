package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.Annotation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;
import ml.dmlc.xgboost4j.java.XGBoostError;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by luheng on 5/3/16.
 */
public class TuningPenaltyThresholdsAndWeights {
    private static final int nBest = 100;
    private static final int randomSeed = 12345;

    private static final String[] annotationFiles = {
            "./Crowdflower_data/f893900.csv",                   // Round3-pronouns: checkbox, core only, pronouns.
            "./Crowdflower_data/f902142.csv"                   // Round4: checkbox, pronouns, core only, 300 sentences.
    };

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipPPQuestions = false;
        queryPruningParameters.skipSAdjQuestions = true;
    }

    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.jeopardyQuestionMinAgreement = 1;
        reparsingParameters.positiveConstraintMinAgreement = 5;
        reparsingParameters.negativeConstraintMaxAgreement = 1;
        reparsingParameters.skipPronounEvidence = true;
        reparsingParameters.jeopardyQuestionWeight = 1.0;
        reparsingParameters.attachmentPenaltyWeight = 1.0;
        reparsingParameters.supertagPenaltyWeight = 1.0;
    }

    public static void main(String[] args) {
        HITLParser myParser = new HITLParser(nBest);
        myParser.setQueryPruningParameters(queryPruningParameters);
        myParser.setReparsingParameters(reparsingParameters);

        Map<Integer, List<AlignedAnnotation>> annotations = ExperimentUtils.loadCrowdflowerAnnotation(annotationFiles);

        FeatureExtractor featureExtractor = new FeatureExtractor();
        featureExtractor.addArgumentPositionFeatures = false;
        featureExtractor.addCategoryFeatures = false;
        featureExtractor.addAnswerLexicalFeatures = false;
        featureExtractor.addNAOptionFeature = false;

        assert annotations != null;

        /**************** Prepare data ********************/
        ImmutableList<Integer> sentenceIds = annotations.keySet().stream()
                .sorted()
                .collect(GuavaCollectors.toImmutableList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        System.out.println("Queried " + sentenceIds.size() + " sentences. Total number of questions:\t" +
                annotations.entrySet().stream().mapToInt(e -> e.getValue().size()).sum());

        final Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> alignedQueries = new HashMap<>();
        final Map<Integer, List<ImmutableList<ImmutableList<Integer>>>> alignedAnnotations = new HashMap<>();
        final Map<Integer, List<AlignedAnnotation>> alignedOldAnnotations = new HashMap<>();
        sentenceIds.forEach(sid -> ClassificationUtils.getQueriesAndAnnotationsForSentence(sid, annotations.get(sid),
                myParser, alignedQueries, alignedAnnotations, alignedOldAnnotations));

        /*************** cross validation ****************/
        final Map<String, Object> paramsMap = ImmutableMap.of(
                "eta", 0.1,
                "min_child_weight", 1.0,
                "max_depth", 3,
                "objective", "binary:logistic"
        );
        final int numRounds = 100;
        final ImmutableList<Double> split = ImmutableList.of(0.15, 0.15, 0.15, 0.15, 0.15, 0.25);
        ImmutableList<ImmutableList<Integer>> splitSents = ClassificationUtils.jackknife(sentenceIds, split, randomSeed);

        Map<String, Map<String, Double>> aggregatedResults = new HashMap<>();

        for (double negativeThreshold : ImmutableList.of(0.1, 0.3, 0.499)) {
            for (double positiveThreshold : ImmutableList.of(0.501, 0.7, 0.9)) {
                for (double weight : ImmutableList.of(1.0, 2.0, 5.0)) {
                    final String setupKey = String.format("NegThreshold=%.2f\tPosThreshold=%.2f\tWeight=%.2f",
                            negativeThreshold, positiveThreshold, weight);
                    aggregatedResults.put(setupKey, new HashMap<>());
                    for (int i : ImmutableList.of(0, 1, 2, 3, 4)) {
                        ImmutableList<Integer> devSents = splitSents.get(i);
                        ImmutableList<Integer> trainSents = IntStream.range(0, 5).boxed()
                                .filter(j -> j != i)
                                .flatMap(j -> splitSents.get(j).stream())
                                .distinct().sorted()
                                .collect(GuavaCollectors.toImmutableList());
                        try {
                            ImmutableMap<String, Double> results = E2EParsing.reparse(trainSents, devSents,
                                    alignedQueries, alignedAnnotations, alignedOldAnnotations,
                                    myParser, featureExtractor, paramsMap, numRounds,
                                    negativeThreshold, positiveThreshold, weight, weight);
                            for (String k : results.keySet()) {
                                double r = aggregatedResults.get(setupKey).containsKey(k) ?
                                        aggregatedResults.get(setupKey).get(k) : 0.0;
                                aggregatedResults.get(setupKey).put(k, r + results.get(k));
                            }
                        } catch (XGBoostError e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        System.out.println();
        for (String setupKey : aggregatedResults.keySet()) {
            System.out.println(setupKey);
            for (String k : aggregatedResults.get(setupKey).keySet()) {
                System.out.println(k + "\t" + aggregatedResults.get(setupKey).get(k) / 5);
            }
            System.out.println();
        }
    }
}
