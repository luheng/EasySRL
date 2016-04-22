package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.Annotation;
import edu.uw.easysrl.qasrl.annotation.AnnotationUtils;
import edu.uw.easysrl.qasrl.evaluation.Accuracy;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.util.GuavaCollectors;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;

import javax.print.attribute.HashAttributeSet;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by luheng on 4/21/16.
 */
public class ClassificationExperiment {
    private static final int nBest = 100;
    private static HITLParser myParser;
    private static ReparsingHistory myHistory;
    private static Map<Integer, List<AlignedAnnotation>> annotations;
    private static FeatureExtractor featureExtractor;

    private static final String[] annotationFiles = {
            "./Crowdflower_data/f878213.csv",                // Round1: radio-button, core + pp
            "./Crowdflower_data/f882410.csv",                // Round2: radio-button, core only
            //  "./Crowdflower_data/all-checkbox-responses.csv", // Round3: checkbox, core + pp
            //  "./Crowdflower_data/f891522.csv",                // Round4: jeopardy checkbox, pp only
            "./Crowdflower_data/f893900.csv",                   // Round3-pronouns: checkbox, core only, pronouns.
            "./Crowdflower_data/f897179.csv"                 // Round2-3: NP clefting questions.
    };

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipPPQuestions = false;
        queryPruningParameters.skipSAdjQuestions = true;
    }

    public static void main(String[] args) {
        myParser = new HITLParser(nBest);
        myParser.setQueryPruningParameters(queryPruningParameters);
        myHistory = new ReparsingHistory(myParser);
        annotations = ExperimentUtils.loadCrowdflowerAnnotation(annotationFiles);
        featureExtractor = new FeatureExtractor();
        assert annotations != null;
        runExperiment();
    }

    private static void getQueriesAndAnnotationsForSentence(int sentenceId, final List<AlignedAnnotation> annotations,
                                                            List<ScoredQuery<QAStructureSurfaceForm>> queryList,
                                                            List<ImmutableList<ImmutableList<Integer>>> annotationList) {
        boolean isCheckboxStyle = !annotations.stream()
                .anyMatch(annot -> annot.answerOptions.stream()
                        .anyMatch(op -> op.contains(QAPairAggregatorUtils.answerDelimiter)));

        assert queryList != null;
        assert annotationList != null;

        List<ScoredQuery<QAStructureSurfaceForm>> allQueries = new ArrayList<>();
        allQueries.addAll(myParser.getCoreArgumentQueriesForSentence(sentenceId, isCheckboxStyle));
        // queryList.addAll(myHTILParser.getPPAttachmentQueriesForSentence(sentenceId));
        allQueries.addAll(myParser.getPronounCoreArgQueriesForSentence(sentenceId));
        allQueries.addAll(myParser.getCleftedQuestionsForSentence(sentenceId));

        allQueries.stream()
                .forEach(query -> {
                    AlignedAnnotation annotation = ExperimentUtils.getAlignedAnnotation(query, annotations);
                    if (annotation != null) {
                        queryList.add(query);
                        annotationList.add(AnnotationUtils.getAllUserResponses(query, annotation));
                    }
                });
    }

    private static ImmutableList<int[]> getAllAttachments(final ImmutableList<String> sentence,
                                                          final ScoredQuery<QAStructureSurfaceForm> query) {
        return IntStream.range(0, sentence.size())
                .boxed()
                .flatMap(headId -> IntStream.range(0, sentence.size())
                        .boxed()
                        .filter(argId -> query.getQAPairSurfaceForms().stream()
                                .anyMatch(qa -> DependencyInstanceHelper.containsDependency(qa, headId, argId)))
                        .map(argId -> new int[] { headId, argId }))
                .collect(GuavaCollectors.toImmutableList());
    }

    private static ImmutableList<DependencyInstance> getInstances(final List<Integer> sentIds) {
        return sentIds.stream()
                .flatMap(sid -> {
                    final Parse gold = myParser.getGoldParse(sid);
                    final NBestList nbestList = myParser.getNBestList(sid);
                    final ImmutableList<String> sentence = myParser.getSentence(sid);
                    final List<ScoredQuery<QAStructureSurfaceForm>> queryList = new ArrayList<>();
                    final List<ImmutableList<ImmutableList<Integer>>> annotationList = new ArrayList<>();
                    getQueriesAndAnnotationsForSentence(sid, annotations.get(sid), queryList, annotationList);
                    return IntStream.range(0, queryList.size())
                            .boxed()
                            .flatMap(qid -> {
                                final ScoredQuery<QAStructureSurfaceForm> query = queryList.get(qid);
                                final ImmutableList<ImmutableList<Integer>> annotation = annotationList.get(qid);
                                return getAllAttachments(sentence, query).stream().map(attachment -> {
                                    final int headId = attachment[0];
                                    final int argId = attachment[1];
                                    final boolean inGold = gold.dependencies.stream()
                                            .anyMatch(dep -> dep.getHead() == headId && dep.getArgument() == argId);
                                    return new DependencyInstance(
                                            sid, headId, argId, inGold,
                                            featureExtractor.getDependencyInstanceFeatures(headId, argId, query,
                                                    annotation, sid, sentence, nbestList));
                                });
                            });
                })
                .collect(GuavaCollectors.toImmutableList());
    }

    private static DMatrix getDMatrix(ImmutableList<DependencyInstance> instances) throws XGBoostError {
        final int numInstances = instances.size();
        final float[] labels = new float[numInstances];
        final long[] rowHeaders = new long[numInstances + 1];
        rowHeaders[0] = 0;
        for (int i = 0; i < numInstances; i++) {
            rowHeaders[i + 1] = rowHeaders[i] + instances.get(i).features.size();
        }
        final int numValues = (int) rowHeaders[numInstances];
        final int[] colIndices = new int[numValues];
        final float[] data = new float[numValues];
        AtomicInteger ptr = new AtomicInteger(0);
        for (int i = 0; i < numInstances; i++) {
            final DependencyInstance instance = instances.get(i);
            labels[i] = instance.inGold ? 1 : 0;
            instance.features.keySet().stream().sorted()
                    .forEach(fid -> {
                        colIndices[ptr.get()] = fid;
                        data[ptr.getAndIncrement()] = instance.features.get(fid).floatValue();
                    });
        }

        DMatrix dmat = new DMatrix(rowHeaders, colIndices, data, DMatrix.SparseType.CSR);
        dmat.setLabel(labels);
        return dmat;
    }

    private static void runExperiment() {
        List<Integer> sentenceIds = annotations.keySet().stream().sorted().collect(Collectors.toList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        System.out.println("Queried " + sentenceIds.size() + " sentences. Total number of questions:\t" +
                annotations.entrySet().stream().mapToInt(e -> e.getValue().size()).sum());


        List<List<Integer>> splitSents = ClassificationUtils.jackknife(sentenceIds, ImmutableList.of(0.7, 0.15, 0.15), 12345);
        final List<Integer> trainSents = splitSents.get(0),
                devSents = splitSents.get(1),
                testSents = splitSents.get(2);
        System.out.println(trainSents.size() + "\t" + devSents.size() + "\t" + testSents.size());

        ImmutableList<DependencyInstance> trainingInstances = getInstances(trainSents),
                                          devInstances = getInstances(devSents);
        final int numPositive = (int) trainingInstances.stream().filter(inst -> inst.inGold).count();
        System.out.println(String.format("Extracted %d training samples and %d features, %d positive and %d negative.",
                trainingInstances.size(), featureExtractor.featureMap.size(),
                numPositive, trainingInstances.size() - numPositive));
        featureExtractor.freeze();

        // TODO: print out training samples.
        // TODO: compute precision/recall and tune by threshold.

        DMatrix trainData, devData;
        try {
            trainData = getDMatrix(trainingInstances);
            devData = getDMatrix(devInstances);
            final Map<String, Object> paramsMap = ImmutableMap.of(
                    "eta", 0.1,
                    "max_depth", 2,
                    "objective", "binary:logistic"
            );
            final Map<String, DMatrix> watches = ImmutableMap.of(
                    "train", trainData,
                    "dev", devData
            );
            final int round = 5;
            Booster booster = XGBoost.train(trainData, paramsMap, round, watches, null, null);
            float[][] pred = booster.predict(devData);

            Map<Integer, Set<Constraint>> constraints = new HashMap<>();
            for (int i = 0; i < devInstances.size(); i++) {
                final DependencyInstance instance = devInstances.get(i);
                final boolean p = pred[i][0] > 0.5;
                if (p == instance.inGold) {
                    continue;
                }
                final int sentenceId = instance.sentenceId;
                final ImmutableList<String> sentence = myParser.getSentence(sentenceId);
                System.out.println(String.format("\n%d\t%s", sentenceId, TextGenerationHelper.renderString(sentence)));
                System.out.println(String.format("%d:%s ---> %d:%s", instance.headId, sentence.get(instance.headId),
                        instance.argId, sentence.get(instance.argId)));
                System.out.println(String.format("%b\t%.2f", instance.inGold, pred[i][0]));

                if (!constraints.containsKey(sentenceId)) {
                    constraints.put(sentenceId, new HashSet<>());
                }
                if (pred[i][0] > 0.6) {
                    constraints.get(sentenceId).add(
                            new Constraint.AttachmentConstraint(instance.headId, instance.argId, true, 1.0));
                }
                if (pred[i][0] < 0.4) {
                    constraints.get(sentenceId).add(
                            new Constraint.AttachmentConstraint(instance.headId, instance.argId, false, 1.0));
                }
            }

            Results avgBaseline = new Results(),
                    avgOracle = new Results(),
                    avgUnlabeledBaseline = new Results(),
                    avgUnlabeledOracle = new Results();

            // Re-parsing ...
            for (int sentenceId : constraints.keySet()) {
                final ImmutableList<String> sentence = myParser.getSentence(sentenceId);
                final Parse gold = myParser.getGoldParse(sentenceId);
                final Parse reparsed = myParser.getReparsed(sentenceId, constraints.get(sentenceId));

                final Results baselineF1 = myParser.getNBestList(sentenceId).getResults(0);
                final Results oracleF1 = CcgEvaluation.evaluate(reparsed.dependencies, gold.dependencies);
                final Results unlabeledBaselineF1 = CcgEvaluation.evaluateUnlabeled(
                        myParser.getNBestList(sentenceId).getParse(0).dependencies, gold.dependencies);
                final Results unlabeledOracleF1 = CcgEvaluation.evaluateUnlabeled(reparsed.dependencies,
                        gold.dependencies);

                System.out.println(sentenceId + "\t" + TextGenerationHelper.renderString(sentence));
                constraints.get(sentenceId)
                        .forEach(c -> System.out.println(c.toString(sentence)));

                System.out.println(baselineF1);
                System.out.println(oracleF1);
                System.out.println(unlabeledBaselineF1);
                System.out.println(unlabeledOracleF1);
                System.out.println();

                avgBaseline.add(baselineF1);
                avgOracle.add(oracleF1);
                avgUnlabeledBaseline.add(unlabeledBaselineF1);
                avgUnlabeledOracle.add(unlabeledOracleF1);
            }

            System.out.println(avgBaseline);
            System.out.println(avgOracle);
            System.out.println(avgUnlabeledBaseline);
            System.out.println(avgUnlabeledOracle);


            //booster,("modelInfo.txt", "featureMap.txt", false)
            /*
            System.out.println(booster.getFeatureScore(feat).entrySet().stream()
                        .map(e -> String.format("%s:\t%d", e.getKey(), e.getValue()))
                        .collect(Collectors.joining("\n")));
            */
        } catch (XGBoostError e) {
            e.printStackTrace();
        }

        // TODO: prepare samples.

    }
}
