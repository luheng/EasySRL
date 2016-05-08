package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.*;
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


/**
 * Putting things together ...
 * Created by luheng on 4/21/16.
 */
public class ClassificationExperimentNew {
    private static final int nBest = 100;
    private static final ImmutableList<Double> split = ImmutableList.of(0.6, 0.4, 0.0);
    private static final int randomSeed = 12345;
    private static HITLParser myParser;
    private static Map<Integer, List<AlignedAnnotation>> annotations;
    private static FeatureExtractor coreArgsFeatureExtractor, cleftingFeatureExtractor;

    private static Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> alignedQueries;
    private static Map<Integer, List<ImmutableList<ImmutableList<Integer>>>> alignedAnnotations;
    private static Map<Integer, List<AlignedAnnotation>> alignedOldAnnotations;

    private static ImmutableList<Integer> trainSents, devSents, testSents;
    private static ImmutableList<DependencyInstance> coreArgTrainInstances, coreArgDevInstances, coreArgTestInstances,
                                                     cleftingTrainInstances, cleftingDevInstances, cleftingTestInstances;

    private static Classifier coreArgClassifier, cleftingClassifier;

    private static final String[] annotationFiles = {
            "./Crowdflower_data/f893900.csv",                   // Round3-pronouns: checkbox, core only, pronouns.
            "./Crowdflower_data/f902142.csv",                   // Round4: checkbox, pronouns, core only, 300 sentences.
            "./Crowdflower_data/f897179.csv",                 // Round2-3: NP clefting questions.
            "./Crowdflower_data/f903842.csv"              // Round4: clefting.
    };

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipPPQuestions = false;
        queryPruningParameters.skipSAdjQuestions = true;
        queryPruningParameters.skipQueriesWithPronounOptions = false;
    }

    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.jeopardyQuestionMinAgreement = 1;
        reparsingParameters.positiveConstraintMinAgreement = 5;
        reparsingParameters.negativeConstraintMaxAgreement = 1;
        reparsingParameters.skipPronounEvidence = false;
        reparsingParameters.jeopardyQuestionWeight = 1.0;
        reparsingParameters.attachmentPenaltyWeight = 1.0;
        reparsingParameters.supertagPenaltyWeight = 1.0;
    }

    private static void initializeData() {
        myParser = new HITLParser(nBest);
        myParser.setQueryPruningParameters(queryPruningParameters);
        myParser.setReparsingParameters(reparsingParameters);
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
                ImmutableSet.of(QueryType.Forward), coreArgsFeatureExtractor, alignedQueries, alignedAnnotations);
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
                "eta", 0.05,
                "min_child_weight", 0.1,
                "max_depth", 3,
                "objective", "binary:logistic"
        );
        final Map<String, Object> params2 = ImmutableMap.of(
                "eta", 0.05,
                "min_child_weight", 5.0,
                "max_depth", 20,
                "objective", "binary:logistic"
        );
        coreArgClassifier = Classifier.trainClassifier(coreArgTrainInstances, params1, 100);
        cleftingClassifier = Classifier.trainClassifier(cleftingTrainInstances, params2, 20);
    }

    private static void reparse() {
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

        /*
        for (int i = 0; i < cleftingDevInstances.size(); i++) {
            final DependencyInstance instance = cleftingDevInstances.get(i);
            final boolean p = (cleftingPred.get(i) > 0.5);
            final String depType = instance.instanceType.toString();
            if (!cleftingPredAcc.containsKey(depType)) {
                cleftingPredAcc.put(depType, new Accuracy());
            }
            cleftingPredAcc.get(depType).add(p == instance.inGold);
        }*/

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

            for (int r = 0; r < alignedQueries.get(sentenceId).size(); r++) {
                final int qid = r;
                final ScoredQuery<QAStructureSurfaceForm> query = alignedQueries.get(sentenceId).get(qid);
                final AlignedAnnotation annotation = alignedOldAnnotations.get(sentenceId).get(qid);
                System.out.println(query.toString(sentence,
                        'G', myParser.getGoldOptions(query),
                        'U', myParser.getUserOptions(query, annotation),
                        'B', myParser.getOneBestOptions(query),
                        '*', AnnotationUtils.getUserResponseDistribution(query, annotation)));

                Set<Constraint> constraints = new HashSet<>(),
                                heuristicConstraints = myParser.getConstraints(query, annotation),
                                oracleConstraints = myParser.getOracleConstraints(query, myParser.getGoldOptions(query));
                Set<String> predictions = new HashSet<>();
                IntStream.range(0, coreArgsPred.size()).boxed()
                        .forEach(i -> {
                            final DependencyInstance inst = coreArgDevInstances.get(i);
                            if (inst.sentenceId == sentenceId && inst.queryId == qid) {
                                final boolean pred = coreArgsPred.get(i) > 0.5;
                                constraints.add(pred ?
                                        new Constraint.AttachmentConstraint(inst.headId, inst.argId, true, 1.0) :
                                        new Constraint.AttachmentConstraint(inst.headId, inst.argId, false, 1.0));
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
                                constraints.add(pred ?
                                        new Constraint.AttachmentConstraint(inst.headId, inst.argId, true, 1.0) :
                                        new Constraint.AttachmentConstraint(inst.headId, inst.argId, false, 1.0));
                                predictions.add(String.format("%s\tGold=%b\tPred=%.2f\t%d:%s-->%d:%s",
                                        pred == inst.inGold ? "[Y]" : "[N]", inst.inGold, cleftingPred.get(i),
                                        inst.headId, sentence.get(inst.headId), inst.argId, sentence.get(inst.argId)));
                            }
                        });

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

                System.out.println(String.format("Baseline  :\t%.2f%%\tunlabeled:\t%.2f%%", 100.0 * baselineF1.getF1(), 100.0 * unlabeledBaselineF1.getF1()));
                System.out.println(String.format("Heuristic :\t%.2f%%\tunlabeled:\t%.2f%%", 100.0 * heuristicReparsedF1.getF1(), 100.0 * unlabeledHeuristicReparsedF1.getF1()));
                System.out.println(String.format("Classifier:\t%.2f%%\tunlabeled:\t%.2f%%", 100.0 * reparsedF1.getF1(), 100.0 * unlabeledReparsedF1.getF1()));
                System.out.println(String.format("Oracle    :\t%.2f%%\tunlabeled:\t%.2f%%", 100.0 * oracleReparsedF1.getF1(), 100.0 * unlabeledOracleReparsedF1.getF1()));

                System.out.println("----- prediction -----");
                predictions.forEach(System.out::println);
                System.out.println("----- classifier constraints -----");
                constraints.forEach(c -> System.out.println(c.toString(sentence)));
                System.out.println("----- heuristic constraints -----");
                heuristicConstraints.forEach(c -> System.out.println(c.toString(sentence)));
                System.out.println("----- oracle constraints -----");
                oracleConstraints.forEach(c -> System.out.println(c.toString(sentence)));
                System.out.println();
            }

            avgBaseline.add(baselineF1);
            avgReparsed.add(reparsedF1);
            avgOracleReparsed.add(oracleReparsedF1);
            avgHeuristicReparsed.add(heuristicReparsedF1);
            avgUnlabeledBaseline.add(unlabeledBaselineF1);
            avgUnlabeledReparsed.add(unlabeledReparsedF1);
            avgUnlabeledOracleReparsed.add(unlabeledOracleReparsedF1);
            avgUnlabeledHeuristicReparsed.add(unlabeledHeuristicReparsedF1);

            System.out.print(String.format("SID=%d\t%.2f%% --> %.2f%%\t\t", sentenceId,
                    100.0 * baselineF1.getF1(), 100.0 * reparsedF1.getF1()));
            if (baselineF1.getF1() + 1e-6 < reparsedF1.getF1()) {
                numImproved ++;
                System.out.println("[improved]");
            } else if (baselineF1.getF1() > reparsedF1.getF1() + 1e-6) {
                numWorsened ++;
                System.out.println("[worsened]");
            } else {
                System.out.println("[unchanged]");
            }
            if (unlabeledBaselineF1.getF1() + 1e-6 < unlabeledReparsedF1.getF1()) {
                numUnlabeledImproved ++;
            } else if (unlabeledBaselineF1.getF1() > unlabeledReparsedF1.getF1() + 1e-6) {
                numUnlabeledWorsened ++;
            }
            System.out.println();
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

        // TODO: print features.
    }

    public static void main(String[] args) {
        initializeData();
        reparse();
    }
}
