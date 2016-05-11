package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.QueryType;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 5/10/16.
 */
public class PerceptronExperiment {
    private static final int nBest = 100;

    private HITLParser myParser;
    private Map<Integer, List<AlignedAnnotation>> annotations;
    private FeatureExtractor coreArgsFeatureExtractor, cleftingFeatureExtractor;

    private Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> alignedQueries;
    private Map<Integer, List<ImmutableList<ImmutableList<Integer>>>> alignedAnnotations;
    private Map<Integer, List<AlignedAnnotation>> alignedOldAnnotations;

    private ImmutableList<Integer> trainSents, devSents;
    private ImmutableList<DependencyInstance> coreArgTrainInstances, coreArgDevInstances,
                                              cleftingTrainInstances, cleftingDevInstances;

    private Classifier coreArgClassifier, cleftingClassifier;

    private static final String[] annotationFiles = {
            "./Crowdflower_data/f893900.csv",                   // Round3-pronouns: checkbox, core only, pronouns.
            "./Crowdflower_data/f902142.csv",                   // Round4: checkbox, pronouns, core only, 300 sentences.
            "./Crowdflower_data/f897179.csv",                 // Round2-3: NP clefting questions.
            "./Crowdflower_data/f903842.csv"              // Round4: clefting.
    };

    private QueryPruningParameters queryPruningParameters;
    private HITLParsingParameters reparsingParameters;

    PerceptronExperiment(final ImmutableList<Double> split, final int randomSeed) {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipPPQuestions = false;
        queryPruningParameters.skipSAdjQuestions = true;
        queryPruningParameters.skipQueriesWithPronounOptions = false;

        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.jeopardyQuestionMinAgreement = 1;
        reparsingParameters.positiveConstraintMinAgreement = 5;
        reparsingParameters.negativeConstraintMaxAgreement = 1;
        reparsingParameters.skipPronounEvidence = false;
        reparsingParameters.jeopardyQuestionWeight = 1.0;
        reparsingParameters.attachmentPenaltyWeight = 5.0;
        reparsingParameters.supertagPenaltyWeight = 5.0;
        reparsingParameters.oraclePenaltyWeight = 5.0;

        myParser = new HITLParser(nBest);
        myParser.setQueryPruningParameters(queryPruningParameters);
        myParser.setReparsingParameters(reparsingParameters);

        initializeData(split, randomSeed);
        runPerceptron(50 /* epochs ? */, 0.1 /* learning rate */, randomSeed);
    }

    private void initializeData(final ImmutableList<Double> split, final int randomSeed) {
        annotations = ExperimentUtils.loadCrowdflowerAnnotation(annotationFiles);
        alignedQueries = new HashMap<>();
        alignedAnnotations = new HashMap<>();
        alignedOldAnnotations = new HashMap<>();
        assert annotations != null;

        ImmutableList<Integer> sentenceIds = annotations.keySet().stream()
                .sorted()
                .collect(GuavaCollectors.toImmutableList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        System.out.println("Queried " + sentenceIds.size() + " sentences. Total number of questions:\t" +
                annotations.entrySet().stream().mapToInt(e -> e.getValue().size()).sum());

        ImmutableList<ImmutableList<Integer>> splitSents = ClassificationUtils.jackknife(sentenceIds, split, randomSeed);
        trainSents = splitSents.get(0);
        devSents = splitSents.get(1);
        System.out.println(trainSents);
        System.out.println(devSents);

        sentenceIds.forEach(sid -> ClassificationUtils.getQueriesAndAnnotationsForSentence(sid, annotations.get(sid),
                myParser, alignedQueries, alignedAnnotations, alignedOldAnnotations));

        coreArgsFeatureExtractor = new FeatureExtractor();
        cleftingFeatureExtractor = new FeatureExtractor();

        coreArgTrainInstances = ClassificationUtils.getInstances(trainSents, myParser,
                ImmutableSet.of(QueryType.Forward), coreArgsFeatureExtractor, alignedQueries, alignedAnnotations)
                .stream()
                .collect(GuavaCollectors.toImmutableList());

        int numCriticalGoldConstraints = (int) coreArgTrainInstances.stream()
                .filter(inst -> inst.inGold != inst.inOneBest)
                .count();
        System.out.println(String.format("Percentage of critical gold constraints: %.3f%%.",
                100.0 * numCriticalGoldConstraints / coreArgTrainInstances.size()));

        cleftingTrainInstances = ClassificationUtils.getInstances(trainSents, myParser,
                ImmutableSet.of(QueryType.Clefted), cleftingFeatureExtractor, alignedQueries, alignedAnnotations);

        coreArgsFeatureExtractor.freeze();
        cleftingFeatureExtractor.freeze();

        final int numPositiveCoreArgs = (int) coreArgTrainInstances.stream().filter(inst -> inst.inGold).count();
        System.out.println(String.format("Extracted %d training samples and %d features, %d positive and %d negative.",
                coreArgTrainInstances.size(), coreArgsFeatureExtractor.featureMap.size(),
                numPositiveCoreArgs, coreArgTrainInstances.size() - numPositiveCoreArgs));

        final int numPositiveClefting = (int) cleftingTrainInstances.stream().filter(inst -> inst.inGold).count();
        System.out.println(String.format("Extracted %d training samples and %d features, %d positive and %d negative.",
                cleftingTrainInstances.size(), cleftingFeatureExtractor.featureMap.size(),
                numPositiveClefting, cleftingTrainInstances.size() - numPositiveClefting));


        coreArgDevInstances = ClassificationUtils.getInstances(devSents, myParser, ImmutableSet.of(QueryType.Forward),
                coreArgsFeatureExtractor, alignedQueries, alignedAnnotations);
        cleftingDevInstances = ClassificationUtils.getInstances(devSents, myParser, ImmutableSet.of(QueryType.Clefted),
                cleftingFeatureExtractor, alignedQueries, alignedAnnotations);
    }

    private void runPerceptron(final int numEpochs, final double alpha, final int randomSeed) {
        double[] params = new double[coreArgsFeatureExtractor.featureMap.size()];
        Arrays.fill(params, 0);
        // Heuristic initialization.
        params[coreArgsFeatureExtractor.featureMap.lookupString("NumReceivedVotes")] = 0.4;
        params[coreArgsFeatureExtractor.featureMap.lookupString("BIAS")] = -1.0;
        List<Integer> order = trainSents.stream().collect(Collectors.toList());
        for (int e = 0; e < numEpochs; e++) {
            Collections.shuffle(order, new Random(randomSeed));
            for (int sid : order) {
                // Compute constraints using weights.
                coreArgTrainInstances.stream()
                        .filter(inst -> inst.sentenceId == sid)
                        .map(inst -> getConstraint(inst, params))
                        .collect(GuavaCollectors.toImmutableSet());
            }
        }
    }

    private Constraint.AttachmentConstraint getConstraint(final DependencyInstance instance, final double[] weights) {
        double f = instance.features.entrySet().stream()
                .mapToDouble(e -> e.getValue() * weights[e.getKey()])
                .sum();
        return f > 0 ?
                new Constraint.AttachmentConstraint(instance.headId, instance.argId, true, f) :
                new Constraint.AttachmentConstraint(instance.headId, instance.argId, false, -f);
    }
}
