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
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
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
import scala.tools.cmd.gen.AnyVals;

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
    private static final ImmutableList<Double> split = ImmutableList.of(0.7, 0.15, 0.15);
    private static final int randomSeed = 12345;
    private static HITLParser myParser;
    private static ReparsingHistory myHistory;
    private static Map<Integer, List<AlignedAnnotation>> annotations;
    private static FeatureExtractor featureExtractor;

    private static Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> alignedQueries;
    private static Map<Integer, List<ImmutableList<ImmutableList<Integer>>>> alignedAnnotations;
    private static Map<Integer, List<AlignedAnnotation>> alignedOldAnnotations;

    private static List<Integer> trainSents, devSents, testSents;
    private static ImmutableList<DependencyInstance> trainingInstances, devInstances, testInstances;

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
        myHistory = new ReparsingHistory(myParser);
        annotations = ExperimentUtils.loadCrowdflowerAnnotation(annotationFiles);
        alignedQueries = new HashMap<>();
        alignedAnnotations = new HashMap<>();
        alignedOldAnnotations = new HashMap<>();
        featureExtractor = new FeatureExtractor();
        assert annotations != null;

        /**************** Prepare data ********************/
        List<Integer> sentenceIds = annotations.keySet().stream().sorted().collect(Collectors.toList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        System.out.println("Queried " + sentenceIds.size() + " sentences. Total number of questions:\t" +
                annotations.entrySet().stream().mapToInt(e -> e.getValue().size()).sum());

        List<List<Integer>> splitSents = ClassificationUtils.jackknife(sentenceIds, split, randomSeed);
        trainSents = splitSents.get(0);
        devSents = splitSents.get(1);
        testSents = splitSents.get(2);
        System.out.println(trainSents.size() + "\t" + devSents.size() + "\t" + testSents.size());

        trainingInstances = getInstances(trainSents);
        final int numPositive = (int) trainingInstances.stream().filter(inst -> inst.inGold).count();
        System.out.println(String.format("Extracted %d training samples and %d features, %d positive and %d negative.",
                trainingInstances.size(), featureExtractor.featureMap.size(),
                numPositive, trainingInstances.size() - numPositive));
        featureExtractor.freeze();

        devInstances = getInstances(devSents);
        testInstances = getInstances(testSents);

        try {
            runExperiment();
        } catch (XGBoostError e) {
            e.printStackTrace();
        }
    }

    private static void getQueriesAndAnnotationsForSentence(int sentenceId, final List<AlignedAnnotation> annotations) {
        boolean isCheckboxStyle = !annotations.stream()
                .anyMatch(annot -> annot.answerOptions.stream()
                        .anyMatch(op -> op.contains(QAPairAggregatorUtils.answerDelimiter)));

        List<ScoredQuery<QAStructureSurfaceForm>> queryList = new ArrayList<>();
        List<ImmutableList<ImmutableList<Integer>>> annotationList = new ArrayList<>();
        List<AlignedAnnotation> oldAnnotations = new ArrayList<>();

        List<ScoredQuery<QAStructureSurfaceForm>> allQueries = new ArrayList<>();
        allQueries.addAll(myParser.getCoreArgumentQueriesForSentence(sentenceId, isCheckboxStyle));
        allQueries.addAll(myParser.getPronounCoreArgQueriesForSentence(sentenceId));
        allQueries.addAll(myParser.getCleftedQuestionsForSentence(sentenceId));

        allQueries.stream()
                .forEach(query -> {
                    AlignedAnnotation annotation = ExperimentUtils.getAlignedAnnotation(query, annotations);
                    if (annotation != null) {
                        ImmutableList<ImmutableList<Integer>> allResponses =
                                AnnotationUtils.getAllUserResponses(query, annotation);
                        if (allResponses.size() == 5) {
                            query.setQueryId(queryList.size());
                            queryList.add(query);
                            annotationList.add(allResponses);
                            oldAnnotations.add(annotation);
                        }
                    }
                });

        alignedQueries.put(sentenceId, queryList);
        alignedAnnotations.put(sentenceId, annotationList);
        alignedOldAnnotations.put(sentenceId, oldAnnotations);
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
                    getQueriesAndAnnotationsForSentence(sid, annotations.get(sid));
                    return IntStream.range(0, alignedQueries.get(sid).size())
                            .boxed()
                            .flatMap(qid -> {
                                final ScoredQuery<QAStructureSurfaceForm> query = alignedQueries.get(sid).get(qid);
                                final ImmutableList<ImmutableList<Integer>> annotation = alignedAnnotations.get(sid).get(qid);
                                return getAllAttachments(sentence, query).stream().map(attachment -> {
                                    final int headId = attachment[0];
                                    final int argId = attachment[1];
                                    final boolean inGold = gold.dependencies.stream().anyMatch(
                                            dep -> dep.getHead() == headId && dep.getArgument() == argId);
                                    return new DependencyInstance(
                                            sid, qid, headId, argId, inGold,
                                            featureExtractor.getDependencyInstanceFeatures(
                                                    headId, argId, query, annotation, sentence, nbestList));
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

    private static void reparse(final Booster booster, final ImmutableList<DependencyInstance> instances,
                                final DMatrix data) throws XGBoostError {

        Map<Integer, Set<Constraint>> constraints = new HashMap<>();
        Map<Integer, Set<Constraint>> heursticConstraints = new HashMap<>();
        final float[][] pred = booster.predict(data);

        for (int i = 0; i < instances.size(); i++) {
            final DependencyInstance instance = instances.get(i);
            final boolean p = pred[i][0] > 0.5;
            final int sentenceId = instance.sentenceId;
            final ImmutableList<String> sentence = myParser.getSentence(sentenceId);
            if (p != instance.inGold) {
                System.out.println();
                final ScoredQuery<QAStructureSurfaceForm> query = alignedQueries.get(sentenceId).get(instance.queryId);
                final AlignedAnnotation annotation = alignedOldAnnotations.get(sentenceId).get(instance.queryId);
                System.out.println(query.toString(sentence,
                        'G', myParser.getGoldOptions(query),
                        '*', AnnotationUtils.getUserResponseDistribution(query, annotation)));

                System.out.println(String.format("%d:%s ---> %d:%s", instance.headId, sentence.get(instance.headId),
                        instance.argId, sentence.get(instance.argId)));
                System.out.println(String.format("%b\t%.2f", instance.inGold, pred[i][0]));

                featureExtractor.printFeature(instance.features);
            }
            if (!constraints.containsKey(sentenceId)) {
                constraints.put(sentenceId, new HashSet<>());
            }
            if (!heursticConstraints.containsKey(sentenceId)) {
                heursticConstraints.put(sentenceId, new HashSet<>());
            }
            if (pred[i][0] > 0.51) {
                constraints.get(sentenceId).add(
                        new Constraint.AttachmentConstraint(instance.headId, instance.argId, true, 5.0));
            }
            if (pred[i][0] < 0.49) {
                constraints.get(sentenceId).add(
                        new Constraint.AttachmentConstraint(instance.headId, instance.argId, false, 5.0));
            }
            IntStream.range(0, alignedQueries.get(sentenceId).size()).boxed()
                    .forEach(qid -> heursticConstraints.get(sentenceId).addAll(
                            myParser.getConstraints(alignedQueries.get(sentenceId).get(qid),
                                    alignedOldAnnotations.get(sentenceId).get(qid))));
        }

        Results avgBaseline = new Results(),
                avgReparsed = new Results(),
                avgHeuristicReparsed = new Results(),
                avgUnlabeledBaseline = new Results(),
                avgUnlabeledReparsed = new Results(),
                avgUnlabeledHeuristicReparsed = new Results();

        // Re-parsing ...
        for (int sentenceId : constraints.keySet()) {
            final ImmutableList<String> sentence = myParser.getSentence(sentenceId);
            final Parse gold = myParser.getGoldParse(sentenceId);
            final Parse reparsed = myParser.getReparsed(sentenceId, constraints.get(sentenceId));
            final Parse heuristicReparsed = myParser.getReparsed(sentenceId, heursticConstraints.get(sentenceId));
            final Results baselineF1 = myParser.getNBestList(sentenceId).getResults(0);
            final Results reparsedF1 = CcgEvaluation.evaluate(reparsed.dependencies, gold.dependencies);
            final Results heuristicReparsedF1 = CcgEvaluation.evaluate(heuristicReparsed.dependencies,
                    gold.dependencies);
            final Results unlabeledBaselineF1 = CcgEvaluation.evaluateUnlabeled(
                    myParser.getNBestList(sentenceId).getParse(0).dependencies, gold.dependencies);
            final Results unlabeledReparsedF1 = CcgEvaluation.evaluateUnlabeled(reparsed.dependencies,
                    gold.dependencies);
            final Results unlabeledHeuristicReparsedF1 = CcgEvaluation.evaluateUnlabeled(heuristicReparsed.dependencies,
                    gold.dependencies);

            avgBaseline.add(baselineF1);
            avgReparsed.add(reparsedF1);
            avgHeuristicReparsed.add(heuristicReparsedF1);
            avgUnlabeledBaseline.add(unlabeledBaselineF1);
            avgUnlabeledReparsed.add(unlabeledReparsedF1);
            avgUnlabeledHeuristicReparsed.add(unlabeledHeuristicReparsedF1);

            /**************************** Debugging ! ************************/
            System.out.println(sentenceId + "\t" + TextGenerationHelper.renderString(sentence));
            for (int qid = 0; qid < alignedQueries.get(sentenceId).size(); qid++) {
                final ScoredQuery<QAStructureSurfaceForm> query = alignedQueries.get(sentenceId).get(qid);
                final AlignedAnnotation annotation = alignedOldAnnotations.get(sentenceId).get(qid);
                System.out.println(query.toString(sentence,
                        'G', myParser.getGoldOptions(query),
                        'U', myParser.getUserOptions(query, annotation),
                        '*', AnnotationUtils.getUserResponseDistribution(query, annotation)));
                // System.out.println(annotation);
                System.out.println("----- classifier predictions -----");
                for (int instId = 0; instId < instances.size(); instId++) {
                    final DependencyInstance instance = instances.get(instId);
                    if (instance.sentenceId == sentenceId && instance.queryId == qid) {
                        System.out.println(String.format("%d:%s ---> %d:%s:\t%b\t%.2f",
                                instance.headId, sentence.get(instance.headId),
                                instance.argId, sentence.get(instance.argId),
                                instance.inGold, pred[instId][0]));
                    }
                }
                System.out.println("----- old constraints -----");
                myParser.getConstraints(query, annotation)
                        .forEach(c -> System.out.println(c.toString(sentence)));
                System.out.println();
            }
            System.out.println(baselineF1);
            System.out.println(heuristicReparsedF1);
            System.out.println(reparsedF1);
            System.out.println("---");
            System.out.println(unlabeledBaselineF1);
            System.out.println(unlabeledHeuristicReparsedF1);
            System.out.println(unlabeledReparsedF1);
            System.out.println();
        }

        System.out.println(avgBaseline);
        System.out.println(avgHeuristicReparsed);
        System.out.println(avgReparsed);
        System.out.println(avgUnlabeledBaseline);
        System.out.println(avgUnlabeledHeuristicReparsed);
        System.out.println(avgUnlabeledReparsed);
    }

    private static void runExperiment() throws XGBoostError {
        // TODO: print out training samples.
        // TODO: compute precision/recall and tune by threshold.
        DMatrix trainData = getDMatrix(trainingInstances);
        DMatrix devData = getDMatrix(devInstances);
        DMatrix testData = getDMatrix(testInstances);
        final Map<String, Object> paramsMap = ImmutableMap.of(
                "eta", 0.1,
                "max_depth", 10,
                "objective", "binary:logistic"
        );
        final Map<String, DMatrix> watches = ImmutableMap.of(
                "train", trainData,
                "dev", devData,
                "test", testData
        );
        final int round = 10;
        Booster booster = XGBoost.train(trainData, paramsMap, round, watches, null, null);
        reparse(booster, trainingInstances, trainData);
        //reparse(booster, testInstances, testData);

        booster.saveModel("model.txt");
        //booster,("modelInfo.txt", "featureMap.txt", false)
        System.out.println(booster.getFeatureScore("").entrySet().stream()
                    .map(e -> String.format("%s:\t%d", e.getKey(), e.getValue()))
                    .collect(Collectors.joining("\n")));
    }
}
