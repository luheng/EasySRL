package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
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
    private static HITLParser myHTILParser;
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
        myHTILParser = new HITLParser(nBest);
        myHTILParser.setQueryPruningParameters(queryPruningParameters);
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
        allQueries.addAll(myHTILParser.getCoreArgumentQueriesForSentence(sentenceId, isCheckboxStyle));
        // queryList.addAll(myHTILParser.getPPAttachmentQueriesForSentence(sentenceId));
        allQueries.addAll(myHTILParser.getPronounCoreArgQueriesForSentence(sentenceId));
        allQueries.addAll(myHTILParser.getCleftedQuestionsForSentence(sentenceId));

        allQueries.stream()
                .forEach(query -> {
                    AlignedAnnotation annotation = ExperimentUtils.getAlignedAnnotation(query, annotations);
                    if (annotation != null) {
                        queryList.add(query);
                        annotationList.add(AnnotationUtils.getAllUserResponses(query, annotation));
                    }
                });
    }

    private static ImmutableList<int[]> getAllAttachments(final ScoredQuery<QAStructureSurfaceForm> query) {
        Table<Integer, Integer, Boolean> attachments = HashBasedTable.create();
        query.getQAPairSurfaceForms().stream()
                .forEach(qa -> qa.getQuestionStructures().forEach(q -> qa.getAnswerStructures().forEach(a -> {
                    a.argumentIndices.forEach(argId ->
                            attachments.put(q.predicateIndex, argId, Boolean.TRUE));
                    a.adjunctDependencies.forEach(dep ->
                            attachments.put(dep.getHead(), dep.getArgument(), Boolean.TRUE));
                    // TODO: handle PP arg
                })));
        return attachments.cellSet().stream()
                .map(c -> new int[] { c.getRowKey(), c.getColumnKey() })
                .collect(GuavaCollectors.toImmutableList());
    }

    private static ImmutableList<DependencyInstance> getInstances(final List<Integer> sentIds) {
        return sentIds.stream()
                .flatMap(sid -> {
                    final Parse goldParse = myHTILParser.getGoldParse(sid);
                    final NBestList nbestList = myHTILParser.getNBestList(sid);
                    final ImmutableList<String> sentence = myHTILParser.getSentence(sid);
                    final List<ScoredQuery<QAStructureSurfaceForm>> queryList = new ArrayList<>();
                    final List<ImmutableList<ImmutableList<Integer>>> annotationList = new ArrayList<>();
                    getQueriesAndAnnotationsForSentence(sid, annotations.get(sid), queryList, annotationList);
                    return IntStream.range(0, queryList.size())
                            .boxed()
                            .flatMap(qid -> {
                                final ScoredQuery<QAStructureSurfaceForm> query = queryList.get(qid);
                                final ImmutableList<ImmutableList<Integer>> annotation = annotationList.get(qid);
                                return getAllAttachments(query).stream().map(d ->
                                        featureExtractor.getDependencyInstance(
                                                d[0], d[1],
                                                query, annotation,
                                                sid, sentence,
                                                goldParse, nbestList));
                            });
                })
                .collect(GuavaCollectors.toImmutableList());
    }

    private static DMatrix getDMatrix(ImmutableList<DependencyInstance> instances) throws XGBoostError {
        final int numInstances = instances.size();
        //float[] labels = new float[numInstances];
        final long[] rowHeaders = new long[numInstances];
        rowHeaders[0] = 0;
        for (int i = 0; i < numInstances - 1; i++) {
            rowHeaders[i + 1] = rowHeaders[i] + instances.get(i).features.size() + 1;
        }
        final int numValues = (int) rowHeaders[numInstances - 1] + instances.get(numInstances - 1).features.size();
        final int[] colIndices = new int[numValues];
        final float[] data = new float[numValues];
        AtomicInteger ptr = new AtomicInteger(0);
        for (int i = 0; i < numInstances; i++) {
            final DependencyInstance instance = instances.get(i);
            colIndices[ptr.get()] = 0;
            data[ptr.getAndIncrement()] = instance.inGold ? 1 : 0;
            instance.features.keySet().stream().sorted()
                    .forEach(fid -> {
                        colIndices[ptr.get()] = fid;
                        data[ptr.getAndIncrement()] = instance.features.get(fid).floatValue();
                    });
        }
        return new DMatrix(rowHeaders, colIndices, data, DMatrix.SparseType.CSR);
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

        ImmutableList<DependencyInstance> trainingInstances = getInstances(trainSents);
        System.out.println(String.format("Extracted %d training samples and %d features.",
                trainingInstances.size(), featureExtractor.featureMap.size()));
        featureExtractor.freeze();

        DMatrix trainData, devData;
        try {
            trainData = getDMatrix(trainingInstances);
            devData = getDMatrix(getInstances(devSents));

            final Map<String, Object> paramsMap = ImmutableMap.of(
                    "eta", 0.1,
                    "max_depth", 2,
                    "objective", "binary:logistic"
            );
            final Map<String, DMatrix> watches = ImmutableMap.of(
                    "train", trainData,
                    "dev", devData
            );
            final int round = 2;
            Booster booster = XGBoost.train(trainData, paramsMap, round, watches, null, null);
        } catch (XGBoostError e) {
            e.printStackTrace();
        }

        // TODO: prepare samples.

    }
}
    