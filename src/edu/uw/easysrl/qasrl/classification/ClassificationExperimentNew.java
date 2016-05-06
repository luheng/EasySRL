package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.AnnotationUtils;
import edu.uw.easysrl.qasrl.evaluation.Accuracy;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
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

        final Map<String, Object> paramsMap = ImmutableMap.of(
                "eta", 0.1,
                "min_child_weight", 1.0,
                "max_depth", 3,
                "objective", "binary:logistic"
        );
        coreArgClassifier = Classifier.trainClassifier(coreArgTrainInstances, paramsMap, 30);
        cleftingClassifier = Classifier.trainClassifier(cleftingTrainInstances, paramsMap, 30);
    }

    private static void reparse() {

        Map<Integer, Set<Constraint>> constraints = new HashMap<>();
        Map<Integer, Set<Constraint>> heursticConstraints = new HashMap<>();
        final ImmutableList<Double> coreArgsPred = coreArgClassifier.predict(coreArgDevInstances),
                                    cleftingPred = cleftingClassifier.predict(cleftingDevInstances);

        Map<String, Accuracy> coreArgsPredAcc = new HashMap<>(),
                              cleftingPredAcc = new HashMap<>();

        for (int sentenceId : devSents) {
            constraints.put(sentenceId, new HashSet<>());
            heursticConstraints.put(sentenceId, new HashSet<>());
            for (int qid = 0; qid < alignedQueries.get(sentenceId).size(); qid++) {
                final ScoredQuery<QAStructureSurfaceForm> query = alignedQueries.get(sentenceId).get(qid);
                final ImmutableList<ImmutableList<Integer>> annotation = alignedAnnotations.get(sentenceId).get(qid);
                final int naOptionId = query.getBadQuestionOptionId().getAsInt();
                final int numNAVotes = (int) annotation.stream().filter(ops -> ops.contains(naOptionId)).count();
                if (numNAVotes > reparsingParameters.negativeConstraintMaxAgreement) {
                    constraints.get(sentenceId).add(new Constraint.SupertagConstraint(query.getPredicateId().getAsInt(),
                            query.getPredicateCategory().get(), false, reparsingParameters.supertagPenaltyWeight));
                }
                heursticConstraints.get(sentenceId).addAll(
                        myParser.getConstraints(alignedQueries.get(sentenceId).get(qid),
                                alignedOldAnnotations.get(sentenceId).get(qid)));
            }
        }


        for (int i = 0; i < coreArgDevInstances.size(); i++) {
            final DependencyInstance instance = coreArgDevInstances.get(i);
            final boolean p = (coreArgsPred.get(i) > 0.5);
            constraints.get(instance.sentenceId).add(p ?
                    new Constraint.AttachmentConstraint(instance.headId, instance.argId, true, 1.0) :
                    new Constraint.AttachmentConstraint(instance.headId, instance.argId, false, 1.0));
            final String depType = instance.instanceType.toString();
            if (!coreArgsPredAcc.containsKey(depType)) {
                coreArgsPredAcc.put(depType, new Accuracy());
            }
            coreArgsPredAcc.get(depType).add(p == instance.inGold);
        }


        for (int i = 0; i < cleftingDevInstances.size(); i++) {
            final DependencyInstance instance = cleftingDevInstances.get(i);
            final boolean p = (cleftingPred.get(i) > 0.5);
            constraints.get(instance.sentenceId).add(p ?
                    new Constraint.AttachmentConstraint(instance.headId, instance.argId, true, 1.0) :
                    new Constraint.AttachmentConstraint(instance.headId, instance.argId, false, 1.0));
            final String depType = instance.instanceType.toString();
            if (!cleftingPredAcc.containsKey(depType)) {
                cleftingPredAcc.put(depType, new Accuracy());
            }
            cleftingPredAcc.get(depType).add(p == instance.inGold);
        }

        System.out.println("Core arg acc:\t");
        coreArgsPredAcc.keySet().forEach(depType -> System.out.println(depType + '\t' + coreArgsPredAcc.get(depType)));
        System.out.println("Clefting acc:\t");
        cleftingPredAcc.keySet().forEach(depType -> System.out.println(depType + '\t' + cleftingPredAcc.get(depType)));

        Results avgBaseline = new Results(),
                avgReparsed = new Results(),
                avgHeuristicReparsed = new Results(),
                avgUnlabeledBaseline = new Results(),
                avgUnlabeledReparsed = new Results(),
                avgUnlabeledHeuristicReparsed = new Results();
        int numImproved = 0, numWorsened = 0;

        // Re-parsing ...
        for (int sentenceId : devSents) {
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

            if (baselineF1.getF1() + 1e-6 < reparsedF1.getF1()) {
                numImproved ++;
            }
            if (baselineF1.getF1() > reparsedF1.getF1() + 1e-6) {
                numWorsened ++;
            }
            if (baselineF1.getF1() <= reparsedF1.getF1()) {
                continue;
            }

            /**************************** Debugging ! ************************/
            System.out.println(sentenceId + "\t" + TextGenerationHelper.renderString(sentence));
            for (int qid = 0; qid < alignedQueries.get(sentenceId).size(); qid++) {
                final ScoredQuery<QAStructureSurfaceForm> query = alignedQueries.get(sentenceId).get(qid);
                final AlignedAnnotation annotation = alignedOldAnnotations.get(sentenceId).get(qid);
                System.out.println(query.toString(sentence,
                        'G', myParser.getGoldOptions(query),
                        'U', myParser.getUserOptions(query, annotation),
                        'B', myParser.getOneBestOptions(query),
                        '*', AnnotationUtils.getUserResponseDistribution(query, annotation)));

                System.out.println("----- classifier constraints -----");
                constraints.get(sentenceId).stream()
                        .forEach(c -> System.out.println(c.toString(sentence)));
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

        System.out.println("Num processed sentences:\t" + devSents.size());
        System.out.println("Num improved:\t" + numImproved);
        System.out.println("Num worsened:\t" + numWorsened);
    }

    public static void main(String[] args) {
        initializeData();
        reparse();
    }
}
