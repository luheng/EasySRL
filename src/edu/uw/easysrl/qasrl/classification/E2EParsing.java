package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.AnnotationUtils;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
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
import java.util.stream.IntStream;

/**
 * Created by luheng on 5/2/16.
 */
public class E2EParsing {
    static ImmutableMap<String, Double> reparse(final ImmutableList<Integer> trainSents,
                                                        final ImmutableList<Integer> devSents,
                                                        final Map<Integer, List<AlignedAnnotation>> annotations,
                                                        final HITLParser myParser,
                                                        final FeatureExtractor featureExtractor,
                                                        final Map<String, Object> paramsMap,
                                                        final int numRounds) throws XGBoostError {

        Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> alignedQueries = new HashMap<>();
        Map<Integer, List<ImmutableList<ImmutableList<Integer>>>> alignedAnnotations = new HashMap<>();
        Map<Integer, List<AlignedAnnotation>> alignedOldAnnotations = new HashMap<>();
        final ImmutableList<DependencyInstance> trainInstances = ClassificationUtils.getInstances(trainSents, myParser,
                featureExtractor, annotations, alignedQueries, alignedAnnotations, alignedOldAnnotations);
        final int numPositive = (int) trainInstances.stream().filter(inst -> inst.inGold).count();
        System.out.println(String.format("Extracted %d training samples and %d features, %d positive and %d negative.",
                trainInstances.size(), featureExtractor.featureMap.size(),
                numPositive, trainInstances.size() - numPositive));
        featureExtractor.freeze();
        final ImmutableList<DependencyInstance> devInstances = ClassificationUtils.getInstances(devSents, myParser,
                featureExtractor, annotations, alignedQueries, alignedAnnotations, alignedOldAnnotations);

        DMatrix trainData = ClassificationUtils.getDMatrix(trainInstances);
        DMatrix devData = ClassificationUtils.getDMatrix(devInstances);
        final Map<String, DMatrix> watches = ImmutableMap.of("train", trainData, "dev", devData);
        Booster booster = XGBoost.train(trainData, paramsMap, numRounds, watches, null, null);

        Map<Integer, Set<Constraint>> constraints = new HashMap<>();
        Map<Integer, Set<Constraint>> heursticConstraints = new HashMap<>();
        final float[][] pred = booster.predict(devData);
        int baselineAcc = 0, classifierAcc = 0, numInstances = 0;
        Results avgBaseline = new Results(),
                avgReparsed = new Results(),
                avgHeuristicReparsed = new Results(),
                avgUnlabeledBaseline = new Results(),
                avgUnlabeledReparsed = new Results(),
                avgUnlabeledHeuristicReparsed = new Results();
        int numImproved = 0, numWorsened = 0;

        for (int sentenceId : devSents) {
            constraints.put(sentenceId, new HashSet<>());
            heursticConstraints.put(sentenceId, new HashSet<>());
            for (int qid = 0; qid < alignedQueries.get(sentenceId).size(); qid++) {
                final ScoredQuery<QAStructureSurfaceForm> query = alignedQueries.get(sentenceId).get(qid);
                final ImmutableList<ImmutableList<Integer>> annotation = alignedAnnotations.get(sentenceId).get(qid);
                final int naOptionId = query.getBadQuestionOptionId().getAsInt();
                final int numNAVotes = (int) annotation.stream().filter(ops -> ops.contains(naOptionId)).count();
                if (numNAVotes > myParser.getReparsingParameters().negativeConstraintMaxAgreement) {
                    constraints.get(sentenceId).add(new Constraint.SupertagConstraint(query.getPredicateId().getAsInt(),
                            query.getPredicateCategory().get(), false,
                            myParser.getReparsingParameters().supertagPenaltyWeight));
                }
                heursticConstraints.get(sentenceId).addAll(
                        myParser.getConstraints(alignedQueries.get(sentenceId).get(qid),
                                alignedOldAnnotations.get(sentenceId).get(qid)));
            }
        }

        for (int i = 0; i < devInstances.size(); i++) {
            final DependencyInstance instance = devInstances.get(i);
            final boolean p = (pred[i][0] > 0.5);
            final int sentenceId = instance.sentenceId;
            final ImmutableList<String> sentence = myParser.getSentence(sentenceId);
            final ScoredQuery<QAStructureSurfaceForm> query = alignedQueries.get(sentenceId).get(instance.queryId);
            final ImmutableList<QAStructureSurfaceForm> qaList = query.getQAPairSurfaceForms();
            final AlignedAnnotation annotation = alignedOldAnnotations.get(sentenceId).get(instance.queryId);
            final int[] userDist = AnnotationUtils.getUserResponseDistribution(query, annotation);
            if (pred[i][0] > 0.5) {
                constraints.get(sentenceId).add(
                        new Constraint.AttachmentConstraint(instance.headId, instance.argId, true, 1.0));
            }
            if (pred[i][0] < 0.5) {
                constraints.get(sentenceId).add(
                        new Constraint.AttachmentConstraint(instance.headId, instance.argId, false, 1.0));
            }
            /************** Get baseline accuracy *************************/
            final ImmutableList<Integer> options = IntStream.range(0, qaList.size()).boxed()
                    .filter(op -> DependencyInstanceHelper.containsDependency(sentence, qaList.get(op),
                            instance.headId, instance.argId))
                    .collect(GuavaCollectors.toImmutableList());
            boolean baselinePrediction = options.stream().mapToInt(op -> userDist[op]).max().orElse(0) >= 3;
            baselineAcc += (baselinePrediction == instance.inGold) ? 1 : 0;
            classifierAcc += (p == instance.inGold) ? 1 : 0;
            numInstances++;
        }

        for (int sentenceId : devSents) {
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
        }
        return ImmutableMap.<String, Double>builder()
                .put("BaselineAcc", 100.0 * baselineAcc / numInstances)
                .put("ClassifierAcc", 100.0 * classifierAcc / numInstances)
                .put("avgBaseline", avgBaseline.getF1())
                .put("avgHeuristicReparsed", avgHeuristicReparsed.getF1())
                .put("avgReparsed", avgReparsed.getF1())
                .put("avgUnlabeledBaseline", avgUnlabeledBaseline.getF1())
                .put("avgUnlabeledHeuristicReparsed", avgUnlabeledHeuristicReparsed.getF1())
                .put("avgUnlabeledReparsed", avgUnlabeledReparsed.getF1())
                .put("NumProcessedSents", 1.0 * devSents.size())
                .put("NumImproved", 1.0 * numImproved)
                .put("NumWorsened", 1.0 * numWorsened).build();
    }
}
