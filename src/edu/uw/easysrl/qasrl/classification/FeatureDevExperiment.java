package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.*;
import com.google.common.collect.Constraint;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.AnnotationUtils;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.model.*;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
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
public class FeatureDevExperiment {
    private static final int nBest = 100;
    private static final ImmutableList<Double> split = ImmutableList.of(0.6, 0.4, 0.0);
    private static final int randomSeed = 12345;
    private static HITLParser myParser;
    private static Map<Integer, List<AlignedAnnotation>> annotations;
    private static FeatureExtractor featureExtractor;

    private static Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> alignedQueries;
    private static Map<Integer, List<ImmutableList<ImmutableList<Integer>>>> alignedAnnotations;
    private static Map<Integer, List<AlignedAnnotation>> alignedOldAnnotations;

    private static ImmutableList<Integer> trainSents;
    private static ImmutableList<DependencyInstance> trainingInstances;
    private static ImmutableList<ImmutableList<Integer>> cvTrainSents, cvDevSents;

    private static final int numFolds = 5;

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
        featureExtractor = new FeatureExtractor();
        assert annotations != null;
        ImmutableList<Integer> sentenceIds = annotations.keySet().stream()
                .sorted()
                .collect(GuavaCollectors.toImmutableList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        System.out.println("Queried " + sentenceIds.size() + " sentences. Total number of questions:\t" +
                annotations.entrySet().stream().mapToInt(e -> e.getValue().size()).sum());
        ImmutableList<ImmutableList<Integer>> splitSents = ClassificationUtils.jackknife(sentenceIds, split, randomSeed);
        trainSents = splitSents.get(0);
        trainingInstances = ClassificationUtils.getInstances(trainSents, myParser, featureExtractor, annotations,
                alignedQueries, alignedAnnotations, alignedOldAnnotations);
        final int numPositive = (int) trainingInstances.stream().filter(inst -> inst.inGold).count();
        System.out.println(String.format("Extracted %d training samples and %d features, %d positive and %d negative.",
                trainingInstances.size(), featureExtractor.featureMap.size(),
                numPositive, trainingInstances.size() - numPositive));
        featureExtractor.freeze();

        // Prepare cv splits.
        cvDevSents = ClassificationUtils.jackknife(trainSents,
                IntStream.range(0, numFolds).boxed()
                        .map(i -> 1.0 / numFolds)
                        .collect(GuavaCollectors.toImmutableList()),
                randomSeed);
        cvTrainSents = cvDevSents.stream()
                .map(devIds -> trainSents.stream()
                        .filter(id -> !devIds.contains(id))
                        .collect(GuavaCollectors.toImmutableList()))
                .collect(GuavaCollectors.toImmutableList());

        runExperiments();
        // Look at misclassified instances.

        /*
        try {
            DMatrix trainData = ClassificationUtils.getDMatrix(trainingInstances);
            GridSearch.runGridSearch(trainData, numFolds);
        } catch (XGBoostError e) {
            e.printStackTrace();
        }
        */
    }

    private static void runExperiments() {
        for (int i = 0; i < cvTrainSents.size(); i++) {
            final ImmutableList<DependencyInstance> trainInsts =
                    ClassificationUtils.getInstances(cvTrainSents.get(i), myParser, featureExtractor, annotations,
                            alignedQueries, alignedAnnotations, alignedOldAnnotations);
            final ImmutableList<DependencyInstance> devInsts =
                    ClassificationUtils.getInstances(cvDevSents.get(i), myParser, featureExtractor, annotations,
                            alignedQueries, alignedAnnotations, alignedOldAnnotations);
            try {
                final DMatrix trainMat = ClassificationUtils.getDMatrix(trainInsts);
                final DMatrix devMat = ClassificationUtils.getDMatrix(devInsts);
                System.out.println(String.format("======================= FOLD%d =========================", i + 1));
                final Map<String, Object> paramsMap = ImmutableMap.of(
                        "eta", 0.1,
                        "min_child_weight", 0.1,
                        "max_depth", 3,
                        "objective", "binary:logistic",
                        "silent", 1
                );
                final Map<String, DMatrix> watches = ImmutableMap.of(
                        "train", trainMat,
                        "dev", devMat
                );
                final int round = 100;
                Booster booster = XGBoost.train(trainMat, paramsMap, round, watches, null, null);

                runAnalysis(booster, devInsts, devMat);
            } catch (XGBoostError e) {
                e.printStackTrace();
            }
        }
    }

    private static void runAnalysis(final Booster booster, final ImmutableList<DependencyInstance> instances,
                                    final DMatrix data) throws XGBoostError {
        final float[][] pred = booster.predict(data);
        int baselineAcc = 0, accuracy = 0, errorCount = 0, numInstances = 0;
        for (int i = 0; i < instances.size(); i++) {
            final DependencyInstance instance = instances.get(i);
            final boolean p = (pred[i][0] > 0.5);

            final int sentenceId = instance.sentenceId;
            final ImmutableList<String> sentence = myParser.getSentence(sentenceId);
            final ScoredQuery<QAStructureSurfaceForm> query = alignedQueries.get(sentenceId).get(instance.queryId);
            final ImmutableList<QAStructureSurfaceForm> qaList = query.getQAPairSurfaceForms();
            final AlignedAnnotation annotation = alignedOldAnnotations.get(sentenceId).get(instance.queryId);
            final int[] userDist = AnnotationUtils.getUserResponseDistribution(query, annotation);

            /************** Get baseline accuracy *************************/
            final ImmutableList<Integer> options = IntStream.range(0, qaList.size()).boxed()
                    .filter(op -> DependencyInstanceHelper.containsDependency(sentence, qaList.get(op),
                            instance.headId, instance.argId))
                    .collect(GuavaCollectors.toImmutableList());
            boolean baselinePrediction = options.stream().mapToInt(op -> userDist[op]).max().orElse(0) >= 3;

            baselineAcc += (baselinePrediction == instance.inGold) ? 1 : 0;
            accuracy += (p == instance.inGold) ? 1 : 0;
            numInstances++;

            if (p != instance.inGold) {
                System.out.println();
                System.out.println(errorCount + "\t" + query.toString(sentence,
                        'G', myParser.getGoldOptions(query),
                        '*', userDist));
                System.out.println(String.format("%d:%s ---> %d:%s", instance.headId, sentence.get(instance.headId),
                        instance.argId, sentence.get(instance.argId)));
                System.out.println(String.format("%b\t%.2f\t%b", instance.inGold, pred[i][0], baselinePrediction));
                featureExtractor.printFeature(instance.features);
                errorCount ++;
            }
        }
        System.out.println("Accuracy:\t" + 100.0 * accuracy / numInstances);
        System.out.println("Baseline accuracy:\t" + 100.0 * baselineAcc / numInstances);
        System.out.println("==================== Feature Weights ====================");
        ClassificationUtils.printXGBoostFeatures(featureExtractor.featureMap, booster.getFeatureScore(""));
    }
}
