package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.AnnotationUtils;
import edu.uw.easysrl.qasrl.corpora.CountDictionary;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryType;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by luheng on 4/21/16.
 */
public class ClassificationUtils {

    static int maxAllowedNAVotes = 1;

    /**
     * Split a list of objects into n parts.
     * @param objectList
     * @param ratio: list of size n.
     * @param randomSeed
     * @return
     */
    static <O extends Object> ImmutableList<ImmutableList<O>> jackknife(final List<O> objectList,
                                                                        final List<Double> ratio,
                                                                        final int randomSeed) {
        final int totalNum = objectList.size();
        final List<Integer> shuffledIds = IntStream.range(0, totalNum).boxed().collect(Collectors.toList());
        Collections.shuffle(shuffledIds, new Random(randomSeed));

        assert ratio.stream().mapToDouble(r -> r).sum() <= 1.0;

        final ImmutableList<Integer> splits = IntStream.range(0, ratio.size() + 1)
                .boxed()
                .map(i -> (int) Math.floor(totalNum * IntStream.range(0, i).mapToDouble(ratio::get).sum()))
                .collect(GuavaCollectors.toImmutableList());

        return IntStream.range(0, ratio.size())
                .boxed()
                .map(i -> shuffledIds.subList(splits.get(i), splits.get(i+1))
                        .stream()
                        .map(objectList::get)
                        .collect(GuavaCollectors.toImmutableList()))
                .collect(GuavaCollectors.toImmutableList());
    }

    static double getAverage(Collection<Double> results) {
        return results.stream().mapToDouble(Double::valueOf).average().orElse(0.0);
    }

    static double getStd(Collection<Double> results) {
        final double mean = getAverage(results);
        return Math.sqrt(results.stream().mapToDouble(r -> (r - mean) * (r - mean)).sum() / results.size());
    }

    static void printXGBoostFeatures(final CountDictionary featureMap, final Map<String, Integer> featureScores) {
        for (String feat : featureScores.keySet()) {
            final int featId = Integer.parseInt(feat.substring(1));
            System.out.println(String.format("%s\t%d", featureMap.getString(featId), featureScores.get(feat)));
        }
    }

    static void getQueriesAndAnnotationsForSentence(int sentenceId, final List<AlignedAnnotation> annotations,
                                                    final HITLParser myParser,
                                                    final Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> alignedQueries,
                                                    final Map<Integer, List<ImmutableList<ImmutableList<Integer>>>> alignedAnnotations,
                                                    final Map<Integer, List<AlignedAnnotation>> alignedOldAnnotations) {
        boolean isCheckboxStyle = !annotations.stream()
                .anyMatch(annot -> annot.answerOptions.stream()
                        .anyMatch(op -> op.contains(QAPairAggregatorUtils.answerDelimiter)));

        List<ScoredQuery<QAStructureSurfaceForm>> queryList = new ArrayList<>();
        List<ImmutableList<ImmutableList<Integer>>> annotationList = new ArrayList<>();
        List<AlignedAnnotation> oldAnnotations = new ArrayList<>();

        List<ScoredQuery<QAStructureSurfaceForm>> allQueries = new ArrayList<>();
        allQueries.addAll(myParser.getCoreArgumentQueriesForSentence(sentenceId, isCheckboxStyle));
        myParser.getPronounCoreArgQueriesForSentence(sentenceId).stream()
                .filter(query -> !allQueries.stream().anyMatch(q -> query.getPrompt().equals(q.getPrompt()) &&
                        q.getPredicateId().getAsInt() == query.getPredicateId().getAsInt()))
                .forEach(allQueries::add);
        allQueries.addAll(myParser.getCleftedQuestionsForSentence(sentenceId));

        allQueries.stream()
                .forEach(query -> {
                    AlignedAnnotation annotation = ExperimentUtils.getAlignedAnnotation(query, annotations);
                    if (annotation != null) {
                        ImmutableList<ImmutableList<Integer>> allResponses = AnnotationUtils.getAllUserResponses(query, annotation);
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


    static ImmutableList<DependencyInstance> getInstances(final List<Integer> sentIds,
                                                          final HITLParser myParser,
                                                          final FeatureExtractor featureExtractor,
                                                          final Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> alignedQueries,
                                                          final Map<Integer, List<ImmutableList<ImmutableList<Integer>>>> alignedAnnotations) {
        return getInstances(sentIds, myParser, ImmutableSet.of(QueryType.Forward, QueryType.Clefted), featureExtractor,
                alignedQueries, alignedAnnotations);
    }

    static ImmutableList<DependencyInstance> getInstances(final List<Integer> sentIds,
                                                          final HITLParser myParser,
                                                          final ImmutableSet<QueryType> allowedQueryTypes,
                                                          final FeatureExtractor featureExtractor,
                                                          final Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> alignedQueries,
                                                          final Map<Integer, List<ImmutableList<ImmutableList<Integer>>>> alignedAnnotations) {
        return sentIds.stream()
                .flatMap(sid -> {
                    final Parse gold = myParser.getGoldParse(sid);
                    final NBestList nbestList = myParser.getNBestList(sid);
                    final ImmutableList<String> sentence = myParser.getSentence(sid);
                    return IntStream.range(0, alignedQueries.get(sid).size())
                            .boxed()
                            .filter(qid -> allowedQueryTypes.contains(alignedQueries.get(sid).get(qid).getQueryType()))
                            .flatMap(qid -> {
                                final ScoredQuery<QAStructureSurfaceForm> query = alignedQueries.get(sid).get(qid);
                                final ImmutableList<ImmutableList<Integer>> annotation = alignedAnnotations.get(sid).get(qid);
                                final int naOptionId = query.getBadQuestionOptionId().getAsInt();
                                final int numNAVotes = (int) annotation.stream().filter(ops -> ops.contains(naOptionId)).count();
                                final Set<ResolvedDependency> oneBest = myParser.getNBestList(sid).getParse(0).dependencies;
                                if (numNAVotes > maxAllowedNAVotes) {
                                    return Stream.empty();
                                }
                                return IntStream.range(0, sentence.size())
                                        .boxed()
                                        .flatMap(headId -> IntStream.range(0, sentence.size())
                                                .boxed()
                                                .filter(argId -> argId.intValue() != headId.intValue())
                                                .filter(argId -> DependencyInstanceHelper.getDependencyType(query, headId, argId) != DependencyInstanceType.NONE)
                                                //.filter(argId -> !(query.getQueryType() == QueryType.Clefted &&
                                                //        DependencyInstanceHelper.getDependencyType(query, headId, argId) != DependencyInstanceType.PPGovernor))
                                                .map(argId -> {
                                                    final DependencyInstanceType dtype =
                                                            DependencyInstanceHelper.getDependencyType(query, headId, argId);
                                                    final boolean inGold = gold.dependencies.stream()
                                                            .anyMatch(d -> d.getHead() == headId && d.getArgument() == argId);
                                                    final boolean inOneBest = oneBest.stream()
                                                            .anyMatch(d -> d.getHead() == headId && d.getArgument() == argId);
                                                    final ImmutableMap<Integer, Double> features = query.getQueryType() == QueryType.Forward ?
                                                            featureExtractor.getCoreArgFeatures(headId, argId, dtype, query, annotation, sentence, nbestList) :
                                                            featureExtractor.getNPCleftingFeatures(headId, argId, dtype, query, annotation, sentence, nbestList);
                                                    return new DependencyInstance(sid, qid, headId, argId, query.getQueryType(), dtype, inGold, inOneBest, features);
                                                }));
                            });
                })
                .collect(GuavaCollectors.toImmutableList());
    }

    static DMatrix getDMatrix(ImmutableList<DependencyInstance> instances)
            throws XGBoostError {
        final int numInstances = instances.size();
        final float[] labels = new float[numInstances];
        final float[] weights = new float[numInstances];
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
            weights[i] = (instance.inGold == instance.inOneBest) ? 1 : 10;
            instance.features.keySet().stream().sorted()
                    .forEach(fid -> {
                        colIndices[ptr.get()] = fid;
                        data[ptr.getAndIncrement()] = instance.features.get(fid).floatValue();
                    });
        }
        DMatrix dmat = new DMatrix(rowHeaders, colIndices, data, DMatrix.SparseType.CSR);
        dmat.setLabel(labels);
        dmat.setWeight(weights);
        return dmat;
    }

}
