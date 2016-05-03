package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.AnnotationUtils;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.model.feature.Feature;
import edu.uw.easysrl.util.GuavaCollectors;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * Created by luheng on 4/21/16.
 */
public class CrossValidationExperiment {
    private static final int nBest = 100;
    private static final int randomSeed = 12345;
    
    private static ImmutableList<Integer> trainSents, devSents;

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

        /*************** cross validation ****************/
        final int numRandomRuns = 10;
        Random random = new Random(randomSeed);
        ImmutableList<Integer> randomSeeds = IntStream.range(0, numRandomRuns)
                .boxed().map(r -> random.nextInt())
                .collect(GuavaCollectors.toImmutableList());
        System.out.println("random seeds:\t" + randomSeeds);


        final Map<String, Object> paramsMap = ImmutableMap.of(
                "eta", 0.1,
                "min_child_weight", 1.0,
                "max_depth", 3,
                "objective", "binary:logistic"
        );
        final int numRounds = 100;
        Map<String, Map<String, Double>> aggregatedResults = new HashMap<>();
        for (double trainPortion : ImmutableList.of(0.2, 0.4, 0.6, 0.8)) {
            final String setupKey = "trainPortion=" + trainPortion;
            aggregatedResults.put(setupKey, new HashMap<>());
            for (int i = 0; i < numRandomRuns; i++) {
                ImmutableList<ImmutableList<Integer>> splitSents = ClassificationUtils.jackknife(sentenceIds,
                        ImmutableList.of(trainPortion, 1.0 - trainPortion), randomSeed);
                trainSents = splitSents.get(0);
                devSents = splitSents.get(1);
                try {
                    ImmutableMap<String, Double> results = E2EParsing.reparse(trainSents, devSents, annotations, myParser,
                            featureExtractor, paramsMap, numRounds);
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

        System.out.println();
        for (String setupKey : aggregatedResults.keySet()) {
            System.out.print(setupKey);
            for (String k : aggregatedResults.get(setupKey).keySet()) {
                System.out.println(k + "\t" + aggregatedResults.get(setupKey).get(k) / numRandomRuns);
            }
            System.out.println();
        }
    }
}
