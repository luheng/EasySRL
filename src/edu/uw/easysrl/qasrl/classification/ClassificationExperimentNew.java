package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.AnnotationUtils;
import edu.uw.easysrl.qasrl.evaluation.Accuracy;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.experiments.DebugPrinter;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.QueryType;
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
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableMap.builder;


/**
 * Putting things together ...
 * Created by luheng on 4/21/16.
 */
public class ClassificationExperimentNew {
    private static final int nBest = 100;

    private HITLParser myParser;
    private Map<Integer, List<AlignedAnnotation>> annotations;
    private FeatureExtractor coreArgsFeatureExtractor, cleftingFeatureExtractor;

    private Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> alignedQueries;
    private Map<Integer, List<ImmutableList<ImmutableList<Integer>>>> alignedAnnotations;
    private Map<Integer, List<AlignedAnnotation>> alignedOldAnnotations;

    private ImmutableList<Integer> trainSents, devSents, testSents;
    private ImmutableList<DependencyInstance> coreArgTrainInstances, coreArgDevInstances, coreArgTestInstances,
            cleftingTrainInstances, cleftingDevInstances, cleftingTestInstances;

    private Classifier coreArgClassifier, cleftingClassifier;

    private static final String[] annotationFiles = {
            "./Crowdflower_data/f893900.csv",                   // Round3-pronouns: checkbox, core only, pronouns.
            "./Crowdflower_data/f902142.csv",                   // Round4: checkbox, pronouns, core only, 300 sentences.
            "./Crowdflower_data/f897179.csv",                 // Round2-3: NP clefting questions.
            "./Crowdflower_data/f903842.csv"              // Round4: clefting.
    };

    private QueryPruningParameters queryPruningParameters;
    private HITLParsingParameters reparsingParameters;

    ClassificationExperimentNew(final ImmutableList<Double> split, final int randomSeed) {
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
        testSents = splitSents.get(2);
        System.out.println(trainSents.size() + "\t" + devSents.size() + "\t" + testSents.size());
        System.out.println(trainSents);
        System.out.println(devSents);
        System.out.println(testSents);

        sentenceIds.forEach(sid -> ClassificationUtils.getQueriesAndAnnotationsForSentence(sid, annotations.get(sid),
                myParser, alignedQueries, alignedAnnotations, alignedOldAnnotations));

        coreArgsFeatureExtractor = new FeatureExtractor();
        cleftingFeatureExtractor = new FeatureExtractor();

        coreArgTrainInstances = ClassificationUtils.getInstances(trainSents, myParser,
                ImmutableSet.of(QueryType.Forward), coreArgsFeatureExtractor, alignedQueries, alignedAnnotations)
                .stream()
              /*  .filter(inst -> {
                    final Set<ResolvedDependency> oneBest = myParser.getNBestList(inst.sentenceId).getParse(0).dependencies;
                    boolean inOneBest = oneBest.stream().anyMatch(d -> d.getHead() == inst.headId && d.getArgument() == inst.argId);
                    return inOneBest != inst.inGold;
                }) */
                .collect(GuavaCollectors.toImmutableList());

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

        final Map<String, Object> params1 = ImmutableMap.of(
                "eta", 0.1, // 0.05,
                "min_child_weight", 0.1,
                "max_depth", 3,
                "objective", "binary:logistic"
        );
        /*
        final Map<String, Object> params2 = ImmutableMap.of(
                "eta", 0.05,
                "min_child_weight", 0.1,
                "max_depth", 3,
                "objective", "binary:logistic"
        );*/
        coreArgClassifier = Classifier.trainClassifier(coreArgTrainInstances, params1, 100);
        cleftingClassifier = Classifier.trainClassifier(cleftingTrainInstances, params1, 30);
    }

    private ImmutableMap<String, Double> reparse(boolean skipCleftingQueries) {
        final ImmutableList<Double> coreArgsPred = coreArgClassifier.predict(coreArgDevInstances),
                                    cleftingPred = cleftingClassifier.predict(cleftingDevInstances);
        Map<String, Accuracy> coreArgsPredAcc = new HashMap<>(),
                              cleftingPredAcc = new HashMap<>();

        for (int i = 0; i < coreArgDevInstances.size(); i++) {
            final DependencyInstance instance = coreArgDevInstances.get(i);
            final boolean p = (coreArgsPred.get(i) > 0.5);
            final String depType = instance.instanceType.toString();
            if (!coreArgsPredAcc.containsKey(depType)) {
                coreArgsPredAcc.put(depType, new Accuracy());
            }
            coreArgsPredAcc.get(depType).add(p == instance.inGold);
        }

        for (int i = 0; i < cleftingDevInstances.size(); i++) {
            final DependencyInstance instance = cleftingDevInstances.get(i);
            final boolean p = (cleftingPred.get(i) > 0.5);
            final String depType = instance.instanceType.toString();
            if (!cleftingPredAcc.containsKey(depType)) {
                cleftingPredAcc.put(depType, new Accuracy());
            }
            cleftingPredAcc.get(depType).add(p == instance.inGold);
        }

        System.out.println("CoreArgs accuracy:\t");
        coreArgsPredAcc.keySet().forEach(depType -> System.out.println(depType + '\t' + coreArgsPredAcc.get(depType)));
        System.out.println("Clefting accuracy:\t");
        cleftingPredAcc.keySet().forEach(depType -> System.out.println(depType + '\t' + cleftingPredAcc.get(depType)));

        System.out.println();

        Results avgBaseline = new Results(),
                avgReparsed = new Results(),
                avgHeuristicReparsed = new Results(),
                avgOracleReparsed = new Results(),
                avgUnlabeledBaseline = new Results(),
                avgUnlabeledReparsed = new Results(),
                avgUnlabeledHeuristicReparsed = new Results(),
                avgUnlabeledOracleReparsed = new Results();
        int numImproved = 0, numWorsened = 0, numUnlabeledImproved = 0, numUnlabeledWorsened = 0;

        for (int sentenceId : devSents) {
            Set<Constraint> allConstraints = new HashSet<>(),
                            allHeuristicConstraints = new HashSet<>(),
                            allOracleConstraints = new HashSet<>();

            final ImmutableList<String> sentence = myParser.getSentence(sentenceId);
            final Parse gold = myParser.getGoldParse(sentenceId);
            final Results baselineF1 = myParser.getNBestList(sentenceId).getResults(0);
            final Results unlabeledBaselineF1 = CcgEvaluation.evaluateUnlabeled(
                    myParser.getNBestList(sentenceId).getParse(0).dependencies, gold.dependencies);
            Results reparsedF1 = baselineF1, heuristicReparsedF1 = baselineF1, oracleReparsedF1 = baselineF1,
                    unlabeledReparsedF1 = unlabeledBaselineF1, unlabeledHeuristicReparsedF1 = unlabeledBaselineF1,
                    unlabeledOracleReparsedF1 = unlabeledReparsedF1;

            String debugBuffer = "";

            for (int r = 0; r < alignedQueries.get(sentenceId).size(); r++) {
                final int qid = r;
                final ScoredQuery<QAStructureSurfaceForm> query = alignedQueries.get(sentenceId).get(qid);
                final AlignedAnnotation annotation = alignedOldAnnotations.get(sentenceId).get(qid);

                if (skipCleftingQueries && query.getQueryType() == QueryType.Clefted) {
                    continue;
                }

                debugBuffer += query.toString(sentence,
                        'G', myParser.getGoldOptions(query),
                        'U', myParser.getUserOptions(query, annotation),
                        'B', myParser.getOneBestOptions(query),
                        '*', AnnotationUtils.getUserResponseDistribution(query, annotation)) + "\n";

                Set<Constraint> constraints = new HashSet<>(),
                                heuristicConstraints = myParser.getConstraints(query, annotation),
                                oracleConstraints = myParser.getOracleConstraints(query); //, myParser.getGoldOptions(query));

                Set<String> predictions = new HashSet<>();
                IntStream.range(0, coreArgsPred.size()).boxed()
                        .forEach(i -> {
                            final DependencyInstance inst = coreArgDevInstances.get(i);
                            if (inst.sentenceId == sentenceId && inst.queryId == qid) {
                                final boolean pred = coreArgsPred.get(i) > 0.5;
                                Constraint newConstraint = pred ?
                                        new Constraint.AttachmentConstraint(inst.headId, inst.argId, true, reparsingParameters.attachmentPenaltyWeight) :
                                        new Constraint.AttachmentConstraint(inst.headId, inst.argId, false, reparsingParameters.attachmentPenaltyWeight);
                                newConstraint.prediction = coreArgsPred.get(i);
                               // if (coreArgsPred.get(i) > 0.7 || coreArgsPred.get(i) < 0.2) {
                                    constraints.add(newConstraint);
                               // }
                                predictions.add(String.format("%s\tGold=%b\tPred=%.2f\t%d:%s-->%d:%s",
                                        pred == inst.inGold ? "[Y]" : "[N]", inst.inGold, coreArgsPred.get(i),
                                        inst.headId, sentence.get(inst.headId), inst.argId, sentence.get(inst.argId)));
                            }
                        });

                IntStream.range(0, cleftingPred.size()).boxed()
                        .forEach(i -> {
                            final DependencyInstance inst = cleftingDevInstances.get(i);
                            if (inst.sentenceId == sentenceId && inst.queryId == qid) {
                                final boolean pred = cleftingPred.get(i) > 0.5;
                                Constraint newConstraint = pred ?
                                        new Constraint.AttachmentConstraint(inst.headId, inst.argId, true, reparsingParameters.attachmentPenaltyWeight) :
                                        new Constraint.AttachmentConstraint(inst.headId, inst.argId, false, reparsingParameters.attachmentPenaltyWeight);
                                newConstraint.prediction = cleftingPred.get(i);
                                //if (cleftingPred.get(i) > 0.7 || cleftingPred.get(i) < 0.5) {
                                    constraints.add(newConstraint);
                                    predictions.add(String.format("%s\tGold=%b\tPred=%.2f\t%d:%s-->%d:%s",
                                            pred == inst.inGold ? "[Y]" : "[N]", inst.inGold, cleftingPred.get(i),
                                            inst.headId, sentence.get(inst.headId), inst.argId, sentence.get(inst.argId)));
                                //}
                            }
                        });

                heuristicConstraints.stream().filter(Constraint.SupertagConstraint.class::isInstance)
                        .forEach(constraints::add);

                allConstraints.addAll(constraints);
                allHeuristicConstraints.addAll(heuristicConstraints);
                allOracleConstraints.addAll(oracleConstraints);

                final Parse reparsed = myParser.getReparsed(sentenceId, allConstraints);
                final Parse heuristicReparsed = myParser.getReparsed(sentenceId, allHeuristicConstraints);
                final Parse oracleReparsed = myParser.getReparsed(sentenceId, allOracleConstraints);
                reparsedF1 = CcgEvaluation.evaluate(reparsed.dependencies, gold.dependencies);
                heuristicReparsedF1 = CcgEvaluation.evaluate(heuristicReparsed.dependencies, gold.dependencies);
                oracleReparsedF1 = CcgEvaluation.evaluate(oracleReparsed.dependencies, gold.dependencies);
                unlabeledReparsedF1 = CcgEvaluation.evaluateUnlabeled(reparsed.dependencies, gold.dependencies);
                unlabeledHeuristicReparsedF1 = CcgEvaluation.evaluateUnlabeled(heuristicReparsed.dependencies, gold.dependencies);
                unlabeledOracleReparsedF1 = CcgEvaluation.evaluateUnlabeled(oracleReparsed.dependencies, gold.dependencies);

                debugBuffer += String.format("Baseline  :\t%.2f%%\tunlabeled:\t%.2f%%\n", 100.0 * baselineF1.getF1(), 100.0 * unlabeledBaselineF1.getF1())
                        + String.format("Heuristic :\t%.2f%%\tunlabeled:\t%.2f%%\n", 100.0 * heuristicReparsedF1.getF1(), 100.0 * unlabeledHeuristicReparsedF1.getF1())
                        + String.format("Classifier:\t%.2f%%\tunlabeled:\t%.2f%%\n", 100.0 * reparsedF1.getF1(), 100.0 * unlabeledReparsedF1.getF1())
                        + String.format("Oracle    :\t%.2f%%\tunlabeled:\t%.2f%%\n", 100.0 * oracleReparsedF1.getF1(), 100.0 * unlabeledOracleReparsedF1.getF1())
                        + "----- prediction -----\n"
                        + predictions.stream().collect(Collectors.joining("\n")) + "\n"
                        + "----- classifier constraints -----\n"
                        + constraints.stream().map(c -> c.toString(sentence)).collect(Collectors.joining("\n")) + "\n"
                        + "----- heuristic constraints -----\n"
                        + heuristicConstraints.stream().map(c -> c.toString(sentence)).collect(Collectors.joining("\n")) + "\n"
                        + "----- oracle constraints -----\n"
                        + oracleConstraints.stream().map(c -> c.toString(sentence)).collect(Collectors.joining("\n")) + "\n\n";

                if (!heuristicConstraints.stream().anyMatch(Constraint.SupertagConstraint.class::isInstance)) {
                    // Compute positive/negative F1 of heuristic vs. oracle.
                    final Set<ResolvedDependency> onebest = myParser.getNBestList(sentenceId).getParse(0).dependencies;
                    final ImmutableSet<Constraint.AttachmentConstraint> positiveOracle = oracleConstraints.stream()
                            .filter(Constraint.AttachmentConstraint.class::isInstance)
                            .filter(Constraint::isPositive)
                            .map(c -> (Constraint.AttachmentConstraint) c)
                            .filter(c -> !onebest.stream().anyMatch(d -> c.getHeadId() == d.getHead()
                                                                    && c.getArgId() == d.getArgument()))
                            .collect(GuavaCollectors.toImmutableSet());
                    final ImmutableSet<Constraint.AttachmentConstraint> negativeOracle = oracleConstraints.stream()
                            .filter(Constraint.AttachmentConstraint.class::isInstance)
                            .filter(c -> !c.isPositive())
                            .map(c -> (Constraint.AttachmentConstraint) c)
                            .filter(c -> onebest.stream().anyMatch(d -> c.getHeadId() == d.getHead()
                                                                    && c.getArgId() == d.getArgument()))
                            .collect(GuavaCollectors.toImmutableSet());
                    heuristicPositive.add(F1.computeConstraintF1(
                            heuristicConstraints.stream()
                                    .filter(Constraint.AttachmentConstraint.class::isInstance)
                                    .filter(Constraint::isPositive)
                                    .map(c -> (Constraint.AttachmentConstraint) c)
                                    .filter(c -> !onebest.stream().anyMatch(d -> c.getHeadId() == d.getHead()
                                                                                && c.getArgId() == d.getArgument()))
                                    .collect(GuavaCollectors.toImmutableSet()),
                            positiveOracle));
                    heuristicNegative.add(F1.computeConstraintF1(
                            heuristicConstraints.stream()
                                    .filter(Constraint.AttachmentConstraint.class::isInstance)
                                    .filter(c -> !c.isPositive())
                                    .map(c -> (Constraint.AttachmentConstraint) c)
                                    .filter(c -> onebest.stream().anyMatch(d -> c.getHeadId() == d.getHead()
                                                                            && c.getArgId() == d.getArgument()))
                                    .collect(GuavaCollectors.toImmutableSet()),
                            negativeOracle));
                    classifierPositive.entrySet()
                            .forEach(e -> {
                                final double threshold = e.getKey();
                                e.getValue().add(F1.computeConstraintF1(
                                        constraints.stream()
                                                .filter(Constraint.AttachmentConstraint.class::isInstance)
                                                .filter(Constraint::isPositive)
                                                .filter(c -> c.prediction > threshold + 1e-6)
                                                .map(c -> (Constraint.AttachmentConstraint) c)
                                                .filter(c -> !onebest.stream().anyMatch(d -> c.getHeadId() == d.getHead()
                                                                                    && c.getArgId() == d.getArgument()))
                                                .collect(GuavaCollectors.toImmutableSet()),
                                        positiveOracle));
                            });
                    classifierNegative.entrySet()
                            .forEach(e -> {
                                final double threshold = e.getKey();
                                e.getValue().add(F1.computeConstraintF1(
                                        constraints.stream()
                                                .filter(Constraint.AttachmentConstraint.class::isInstance)
                                                .filter(c -> !c.isPositive())
                                                .filter(c -> c.prediction < threshold - 1e-6)
                                                .map(c -> (Constraint.AttachmentConstraint) c)
                                                .filter(c -> onebest.stream().anyMatch(d -> c.getHeadId() == d.getHead()
                                                                                    && c.getArgId() == d.getArgument()))
                                                .collect(GuavaCollectors.toImmutableSet()),
                                        negativeOracle));
                            });
                }
            }

            avgBaseline.add(baselineF1);
            avgReparsed.add(reparsedF1);
            avgOracleReparsed.add(oracleReparsedF1);
            avgHeuristicReparsed.add(heuristicReparsedF1);
            avgUnlabeledBaseline.add(unlabeledBaselineF1);
            avgUnlabeledReparsed.add(unlabeledReparsedF1);
            avgUnlabeledOracleReparsed.add(unlabeledOracleReparsedF1);
            avgUnlabeledHeuristicReparsed.add(unlabeledHeuristicReparsedF1);

            if (baselineF1.getF1() + 1e-6 < reparsedF1.getF1()) {
                numImproved++;
            } else if (baselineF1.getF1() > reparsedF1.getF1() + 1e-6) {
                numWorsened++;
            }
            if (unlabeledBaselineF1.getF1() + 1e-6 < unlabeledReparsedF1.getF1()) {
                numUnlabeledImproved++;
            } else if (unlabeledBaselineF1.getF1() > unlabeledReparsedF1.getF1() + 1e-6) {
                numUnlabeledWorsened++;
            }

            if (reparsedF1.getF1() + 1e-6 < heuristicReparsedF1.getF1()) {
                System.out.println(debugBuffer);
                System.out.print(String.format("SID=%d\t%.2f%% --> %.2f%%\t\t", sentenceId,
                        100.0 * baselineF1.getF1(), 100.0 * reparsedF1.getF1()));
                if (baselineF1.getF1() + 1e-6 < reparsedF1.getF1()) {
                    System.out.println("[improved]");
                } else if (baselineF1.getF1() > reparsedF1.getF1() + 1e-6) {
                    System.out.println("[worsened]");
                } else {
                    System.out.println("[unchanged]");
                }
                System.out.println();
            }
        }

        System.out.println();
        System.out.println(String.format("Avg. Baseline  :\t%.2f%%\tunlabeled:\t%.2f%%", 100.0 * avgBaseline.getF1(), 100.0 * avgUnlabeledBaseline.getF1()));
        System.out.println(String.format("Avg. Heuristic :\t%.2f%%\tunlabeled:\t%.2f%%", 100.0 * avgHeuristicReparsed.getF1(), 100.0 * avgUnlabeledHeuristicReparsed.getF1()));
        System.out.println(String.format("Avg. Classifier:\t%.2f%%\tunlabeled:\t%.2f%%", 100.0 * avgReparsed.getF1(), 100.0 * avgUnlabeledReparsed.getF1()));
        System.out.println(String.format("Avg. Oracle    :\t%.2f%%\tunlabeled:\t%.2f%%", 100.0 * avgOracleReparsed.getF1(), 100.0 * avgUnlabeledOracleReparsed.getF1()));
        System.out.println();
        System.out.println("Num processed sentences:\t" + devSents.size());
        System.out.println("Num improved:\t" + numImproved);
        System.out.println("Num worsened:\t" + numWorsened);
        System.out.println("Num unlabeled improved:\t" + numUnlabeledImproved);
        System.out.println("Num unlabeled worsened:\t" + numUnlabeledWorsened);

        return ImmutableMap.<String, Double>builder()
                .put("avg_baseline", 100.0 * avgBaseline.getF1())
                .put("avg_unlabeled_baseline", 100.0 * avgUnlabeledBaseline.getF1())
                .put("avg_heuristic", 100.0 * avgHeuristicReparsed.getF1())
                .put("avg_unlabeled_heuristic", 100.0 * avgUnlabeledHeuristicReparsed.getF1())
                .put("avg_classifier", 100.0 * avgReparsed.getF1())
                .put("avg_unlabeled_classifier", 100.0 * avgUnlabeledReparsed.getF1())
                .put("avg_oracle", 100.0 * avgOracleReparsed.getF1())
                .put("avg_unlabeled_oracle", 100.0 * avgUnlabeledOracleReparsed.getF1())
                .put("num_processed_sents", 1.0 * devSents.size())
                .put("num_improved_sents", 1.0 * numImproved)
                .put("num_worsened_sents", 1.0 * numWorsened)
                .put("num_unlabeled_improved", 1.0 * numUnlabeledImproved)
                .put("num_unlabeled_worsened", 1.0 * numUnlabeledWorsened)
                .build();
    }

    static ImmutableList<String> resultKeys = ImmutableList.of(
            "avg_baseline",
            "avg_unlabeled_baseline",
            "avg_heuristic",
            "avg_unlabeled_heuristic",
            "avg_classifier",
            "avg_unlabeled_classifier",
            "avg_oracle",
            "avg_unlabeled_oracle",
            "num_processed_sents",
            "num_improved_sents",
            "num_worsened_sents",
            "num_unlabeled_improved",
            "num_unlabeled_worsened"
    );

    static F1 heuristicPositive = new F1(), heuristicNegative = new F1();
    static HashMap<Double, F1> classifierPositive = new HashMap<>(), classifierNegative = new HashMap<>();
    static {
        Stream.of(0.5, 0.6, 0.7, 0.8, 0.9).forEach(t -> classifierPositive.put(t, new F1()));
        Stream.of(0.5, 0.4, 0.3, 0.2, 0.1).forEach(t -> classifierNegative.put(t, new F1()));
    }

    public static void main(String[] args) {
        final int initialRandomSeed = 12345;
        final int numRandomRuns = 10;

        Random random = new Random(initialRandomSeed);
        ImmutableList<Integer> randomSeeds = IntStream.range(0, numRandomRuns)
                .boxed().map(r -> random.nextInt())
                .collect(GuavaCollectors.toImmutableList());
        System.out.println("random seeds:\t" + randomSeeds);

        Map<String, List<Double>> aggregatedResults = new HashMap<>();
        Map<String, List<Double>> aggregatedResultsNoClefting = new HashMap<>();
        for (int i = 0; i < numRandomRuns; i++) {
            ClassificationExperimentNew experiment = new ClassificationExperimentNew(ImmutableList.of(0.6, 0.4, 0.0), randomSeeds.get(i)); //12345);
            //ImmutableMap<String, Double> results1 = experiment.reparse(false /* with clefting */);
            ImmutableMap<String, Double> results2 = experiment.reparse(true /* no clefting */);
            /*
            results1.keySet().forEach(k -> {
                if (!aggregatedResults.containsKey(k)) {
                    aggregatedResults.put(k, new ArrayList<>());
                }
                aggregatedResults.get(k).add(results1.get(k));
            });*/
            results2.keySet().forEach(k -> {
                if (!aggregatedResultsNoClefting.containsKey(k)) {
                    aggregatedResultsNoClefting.put(k, new ArrayList<>());
                }
                aggregatedResultsNoClefting.get(k).add(results2.get(k));
            });
        }

        // Print aggregated results.
        /*
        resultKeys.forEach(k -> {
            final List<Double> results = aggregatedResults.get(k);
            System.out.println(String.format("%s\t%.3f\t%.3f", k, ClassificationUtils.getAverage(results),
                    ClassificationUtils.getStd(results)));
        }); */
        System.out.println("No clefting");
        resultKeys.forEach(k -> {
            final List<Double> results = aggregatedResultsNoClefting.get(k);
            System.out.println(String.format("%s\t%.3f\t%.3f", k, ClassificationUtils.getAverage(results),
                    ClassificationUtils.getStd(results)));
        });

        System.out.println("HeuristicPositive:\t" + heuristicPositive);
        System.out.println("HeuristicNegative:\t" + heuristicNegative);
        classifierPositive.entrySet().forEach(e -> System.out.println("Classifier > " + e.getKey() + "\t" + e.getValue()));
        classifierNegative.entrySet().forEach(e -> System.out.println("Classifier < " + e.getKey() + "\t" + e.getValue()));

    }
}
