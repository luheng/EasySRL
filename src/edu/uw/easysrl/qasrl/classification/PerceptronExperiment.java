package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.QueryType;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import edu.uw.easysrl.syntax.evaluation.Results;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    private static final int minFeatureFreq = 3;
    private Classifier coreArgClassifier, cleftingClassifier;

    private static final String[] annotationFiles = {
            "./Crowdflower_data/f893900.csv",                   // Round3-pronouns: checkbox, core only, pronouns.
            "./Crowdflower_data/f902142.csv",                   // Round4: checkbox, pronouns, core only, 300 sentences.
            "./Crowdflower_data/f909211.csv",                 // Round5: core.
            "./Crowdflower_data/f897179.csv",                 // Round2-3: NP clefting questions.
            "./Crowdflower_data/f903842.csv"              // Round4: clefting.
    };

    private QueryPruningParameters queryPruningParameters;
    private HITLParsingParameters reparsingParameters;

    public static void main(String[] args) {
        final int initialRandomSeed = 12345;
        final int numRandomRuns = 10;

        Random random = new Random(initialRandomSeed);
        ImmutableList<Integer> randomSeeds = IntStream.range(0, numRandomRuns)
                .boxed().map(r -> random.nextInt())
                .collect(GuavaCollectors.toImmutableList());
        System.out.println("random seeds:\t" + randomSeeds);

        Map<String, List<Double>> aggregatedResults = new HashMap<>();

        for (int i = 0; i < numRandomRuns; i++) {
            PerceptronExperiment experiment = new PerceptronExperiment(ImmutableList.of(0.6, 0.4), randomSeeds.get(i));
            ImmutableMap<String, Double> results = experiment.runPerceptron(20 /* epochs */, 1.0 /* learning rate */,
                    randomSeeds.get(i));

            results.keySet().forEach(k -> {
                if (!aggregatedResults.containsKey(k)) {
                    aggregatedResults.put(k, new ArrayList<>());
                }
                aggregatedResults.get(k).add(results.get(k));
            });
        }

        // Print aggregated results.
        aggregatedResults.keySet().forEach(k -> {
            final List<Double> results = aggregatedResults.get(k);
            System.out.println(String.format("%s\t%.3f\t%.3f", k, ClassificationUtils.getAverage(results),
                    ClassificationUtils.getStd(results)));
        });
    }

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
        reparsingParameters.attachmentPenaltyWeight = 1.0;
        reparsingParameters.supertagPenaltyWeight = 1.0;
        reparsingParameters.oraclePenaltyWeight = 5.0;

        myParser = new HITLParser(nBest);
        myParser.setQueryPruningParameters(queryPruningParameters);
        myParser.setReparsingParameters(reparsingParameters);

        initializeData(split, randomSeed);
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


        coreArgsFeatureExtractor.addAnswerLexicalFeatures = false;
        coreArgsFeatureExtractor.addCategoryFeatures = false;
        coreArgsFeatureExtractor.addArgumentPositionFeatures = false;
        coreArgsFeatureExtractor.addNAOptionFeature = false;
        coreArgsFeatureExtractor.addTemplateBasedFeatures = false;
        coreArgsFeatureExtractor.addNBestPriorFeatures = false;

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

    private ImmutableMap<String, Double> runPerceptron(final int numEpochs, final double alpha, final int randomSeed) {
        double[] params = new double[coreArgsFeatureExtractor.featureMap.size()];
        double[] avgParams = new double[params.length];
        int counter = 0;

        Arrays.fill(params, 0);
        // Heuristic initialization.
        //params[coreArgsFeatureExtractor.featureMap.lookupString("NumReceivedVotes")] = 0.4;
        //params[coreArgsFeatureExtractor.featureMap.lookupString("BIAS")] = -1.0;
        List<Integer> order = trainSents.stream().collect(Collectors.toList());

        Results trainBaseline = new Results(), devBaseline = new Results();
        trainSents.stream().map(sid -> myParser.getNBestList(sid).getResults(0)).forEach(trainBaseline::add);
        devSents.stream().map(sid -> myParser.getNBestList(sid).getResults(0)).forEach(devBaseline::add);

        for (int epoch = 0; epoch < numEpochs; epoch++) {
            Collections.shuffle(order, new Random(randomSeed));
            Results avgTrainF1 = new Results();
            double maxConstraintStrength = 0.0;

            for (int sid : order) {
                final ImmutableList<DependencyInstance> instances = coreArgTrainInstances.stream()
                        .filter(inst -> inst.sentenceId == sid)
                        .collect(GuavaCollectors.toImmutableList());
                // Compute constraints using weights.
                final ImmutableSet<Constraint> constraints = instances.stream()
                        .map(inst -> getConstraint(inst, params))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(GuavaCollectors.toImmutableSet());
                maxConstraintStrength = Math.max(maxConstraintStrength,
                        constraints.stream().mapToDouble(Constraint::getStrength).max().orElse(0.0));

                final Parse gold = myParser.getGoldParse(sid);
                final Parse reparsed = myParser.getReparsed(sid, constraints);
                final Results reparsedF1 = CcgEvaluation.evaluate(reparsed.dependencies, gold.dependencies);
                avgTrainF1.add(reparsedF1);
                //System.out.println(sid + "\t" + reparsedF1.getF1());
                // Do perceptron update ...
                final int C = counter;
                instances.stream()
                        .filter(inst -> inst.inGold && !inParse(inst, reparsed))
                        .forEach(inst -> inst.features.entrySet()
                                .forEach(e -> params[e.getKey()] += alpha * e.getValue()));
                instances.stream()
                        .filter(inst -> !inst.inGold && inParse(inst, reparsed))
                        .forEach(inst -> inst.features.entrySet()
                                .forEach(e -> params[e.getKey()] -= alpha * e.getValue()));
                for (int i = 0; i < params.length; i++) {
                    avgParams[i] += params[i];
                }
                counter ++;
            }

            final double weightNorm = getL2Norm(params);
            /*
            double[] tempParams = Arrays.copyOf(avgParams, avgParams.length);
            for (int i = 0; i < tempParams.length; i++) {
                tempParams[i] /= counter;
            }
            final Results avgDevF1 = getCorpusF1(devSents, coreArgDevInstances, tempParams);
            */
            System.out.print("Epoch=\t" + epoch);
            System.out.print("\tWeightNorm=\t" + weightNorm);
            System.out.print("\tMaxPenalty=\t" + maxConstraintStrength);
            System.out.print("\tTrainF1=\t" + avgTrainF1.getF1());
            System.out.println();
            //System.out.print("\tDevF1=\t" + avgDevF1.getF1() + "\n");
        }

        for (int i = 0; i < avgParams.length; i++) {
            avgParams[i] /= counter;
        }
        System.out.println("Train-baseline:\n" + trainBaseline);
        System.out.println("Train-reparsed:\n" + getCorpusF1(trainSents, coreArgTrainInstances, avgParams));
        System.out.println("Dev-baseline:\n" + devBaseline);
        final Results devReparsed = getCorpusF1(devSents, coreArgDevInstances, avgParams);
        System.out.println("Dev-reparsed:\n" + devReparsed);

        // Print feature weights.
        for (int fid = 0; fid < avgParams.length; fid++) {
            System.out.println(coreArgsFeatureExtractor.featureMap.getString(fid) + "\t=\t" + avgParams[fid]);
        }

        return ImmutableMap.<String, Double>builder()
                .put("avg_baseline", 100.0 * devBaseline.getF1())
                //.put("avg_unlabeled_baseline", 100.0 * avgUnlabeledBaseline.getF1())
                //.put("avg_heuristic", 100.0 * avgHeuristicReparsed.getF1())
                //.put("avg_unlabeled_heuristic", 100.0 * avgUnlabeledHeuristicReparsed.getF1())
                .put("avg_perceptron", 100.0 * devReparsed.getF1())
                //.put("avg_unlabeled_classifier", 100.0 * avgUnlabeledReparsed.getF1())
                /*.put("avg_oracle", 100.0 * avgOracleReparsed.getF1())
                .put("avg_unlabeled_oracle", 100.0 * avgUnlabeledOracleReparsed.getF1())
                .put("num_processed_sents", 1.0 * devSents.size())
                .put("num_improved_sents", 1.0 * numImproved)
                .put("num_worsened_sents", 1.0 * numWorsened)
                .put("num_unlabeled_improved", 1.0 * numUnlabeledImproved)
                .put("num_unlabeled_worsened", 1.0 * numUnlabeledWorsened)*/
                .build();
    }

    // TODO: get heuristic baseline
    // TODO: print constraints

    private Results getCorpusF1(final List<Integer> sentIds, final List<DependencyInstance> instances, final double[] params) {
        Results avgF1 = new Results();
        for (int sid : sentIds) {
            final ImmutableList<DependencyInstance> sentInstances = instances.stream()
                    .filter(inst -> inst.sentenceId == sid)
                    .collect(GuavaCollectors.toImmutableList());
            final ImmutableSet<Constraint> constraints = sentInstances.stream()
                    .map(inst -> getConstraint(inst, params))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(GuavaCollectors.toImmutableSet());
            final Parse reparsed = myParser.getReparsed(sid, constraints);
            avgF1.add(CcgEvaluation.evaluate(reparsed.dependencies, myParser.getGoldParse(sid).dependencies));
        }
        return avgF1;
    }

    private double getL2Norm(final double[] weights) {
        double l2n = .0;
        for (double d : weights) {
            l2n += d * d;
        }
        return Math.sqrt(l2n);
    }

    private Optional<Constraint.AttachmentConstraint> getConstraint(final DependencyInstance instance,
                                                                    final double[] weights) {
        final double gate = 1e-3; //1.0;
        double f = instance.features.entrySet().stream()
                .mapToDouble(e -> e.getValue() * weights[e.getKey()])
                .sum();
        if (f > gate) {
            return Optional.of(new Constraint.AttachmentConstraint(instance.headId, instance.argId, true, Math.min(f, 10.0)));
        } else if (f < -gate) {
            return Optional.of(new Constraint.AttachmentConstraint(instance.headId, instance.argId, false, Math.min(-f, 10.0)));
        } else {
            return Optional.empty();
        }
    }

    private boolean inParse(final DependencyInstance instance, final Parse parse) {
        return parse.dependencies.stream()
                .anyMatch(d -> d.getHead() == instance.headId && d.getArgument() == instance.argId);
    }
}
