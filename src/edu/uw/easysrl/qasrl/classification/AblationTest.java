package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;

import java.util.*;
import java.util.stream.Collectors;


/**
 * Created by luheng on 4/21/16.
 */
public class AblationTest {
    private static final int nBest = 100;
    private static final ImmutableList<Double> split = ImmutableList.of(0.6, 0.4, 0.0);
    private static final int randomSeed = 12345;
    private static HITLParser myParser;
    private static Map<Integer, List<AlignedAnnotation>> annotations;

    private static Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> alignedQueries;
    private static Map<Integer, List<ImmutableList<ImmutableList<Integer>>>> alignedAnnotations;
    private static Map<Integer, List<AlignedAnnotation>> alignedOldAnnotations;
    private static ImmutableList<Integer> trainSents;

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
        myParser = new HITLParser(nBest);
        myParser.setQueryPruningParameters(queryPruningParameters);
        myParser.setReparsingParameters(reparsingParameters);
        annotations = ExperimentUtils.loadCrowdflowerAnnotation(annotationFiles);
        alignedQueries = new HashMap<>();
        alignedAnnotations = new HashMap<>();
        alignedOldAnnotations = new HashMap<>();

        assert annotations != null;

        /**************** Prepare data ********************/
        ImmutableList<Integer> sentenceIds = annotations.keySet().stream()
                .sorted()
                .collect(GuavaCollectors.toImmutableList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        System.out.println("Queried " + sentenceIds.size() + " sentences. Total number of questions:\t" +
                annotations.entrySet().stream().mapToInt(e -> e.getValue().size()).sum());
        sentenceIds.forEach(sid -> ClassificationUtils.getQueriesAndAnnotationsForSentence(sid, annotations.get(sid),
                myParser, alignedQueries, alignedAnnotations, alignedOldAnnotations));

        ImmutableList<ImmutableList<Integer>> splitSents = ClassificationUtils.jackknife(sentenceIds, split, randomSeed);
        trainSents = splitSents.get(0);

        FeatureExtractor featureExtractor;

        System.out.println("All Features");
        featureExtractor = new FeatureExtractor();
        runExperiment(featureExtractor, "All Features");

        featureExtractor = new FeatureExtractor();
        featureExtractor.addCategoryFeatures = false;
        featureExtractor.addNAOptionFeature = false;
        featureExtractor.addAnswerLexicalFeatures = false;
        featureExtractor.addArgumentPositionFeatures = false;
        featureExtractor.addTemplateBasedFeatures = false;
        runExperiment(featureExtractor, "Base Features");

        featureExtractor = new FeatureExtractor();
        featureExtractor.addCategoryFeatures = false;
        featureExtractor.addNAOptionFeature = false;
        featureExtractor.addAnswerLexicalFeatures = false;
        featureExtractor.addArgumentPositionFeatures = false;
        featureExtractor.addTemplateBasedFeatures = true;
        runExperiment(featureExtractor, "Add template features");

        /*
        System.out.println("-Prior Features");
        featureExtractor = new FeatureExtractor();
        featureExtractor.addNBestPriorFeatures = false;
        runExperiment(featureExtractor, "-Prior Features");

        System.out.println("-Category Features");
        featureExtractor = new FeatureExtractor();
        featureExtractor.addCategoryFeatures = false;
        runExperiment(featureExtractor, "-Category Features");

        System.out.println("-Argument position features");
        featureExtractor = new FeatureExtractor();
        featureExtractor.addArgumentPositionFeatures = false;
        runExperiment(featureExtractor, "-Argument position features");

        System.out.println("-Answer lexical features");
        featureExtractor = new FeatureExtractor();
        featureExtractor.addAnswerLexicalFeatures = false;
        runExperiment(featureExtractor, "-Answer lexical features");

        System.out.println("-NA option features");
        featureExtractor = new FeatureExtractor();
        featureExtractor.addNAOptionFeature = false;
        runExperiment(featureExtractor, "-NA option features");

        System.out.println("-Annotation features");
        featureExtractor = new FeatureExtractor();
        featureExtractor.addAnnotationFeatures = false;
        runExperiment(featureExtractor, "-Annotation features");

        System.out.println("-Template Features");
        featureExtractor = new FeatureExtractor();
        featureExtractor.addTemplateBasedFeatures = false;
        runExperiment(featureExtractor, "-Template features");
        */
    }

    private static void runExperiment(final FeatureExtractor featureExtractor, final String message) {
        ImmutableList<DependencyInstance> trainingInstances = ClassificationUtils
                .getInstances(trainSents, myParser, featureExtractor, alignedQueries, alignedAnnotations);

        final int numPositive = (int) trainingInstances.stream().filter(inst -> inst.inGold).count();
        System.out.println(String.format("Extracted %d training samples and %d features, %d positive and %d negative.",
                trainingInstances.size(), featureExtractor.featureMap.size(),
                numPositive, trainingInstances.size() - numPositive));

        try {
            DMatrix trainData = ClassificationUtils.getDMatrix(trainingInstances);
            final int nfold = 5;
            double result = GridSearch.runGridSearch(trainData, nfold);
            System.out.println("[avg]\t" + message + "\t" + result);

            final Map<String, Object> paramsMap = ImmutableMap.of(
                    "eta", 0.1,
                    "min_child_weight", 0.1,
                    "max_depth", 3,
                    "objective", "binary:logistic"
            );
            final Map<String, DMatrix> watches = ImmutableMap.of("train", trainData);
            final int round = 100;
            Booster booster = XGBoost.train(trainData, paramsMap, round, watches, null, null);
            ClassificationUtils.printXGBoostFeatures(featureExtractor.featureMap, booster.getFeatureScore(""));
        } catch (XGBoostError e) {
            e.printStackTrace();
        }
    }
}
