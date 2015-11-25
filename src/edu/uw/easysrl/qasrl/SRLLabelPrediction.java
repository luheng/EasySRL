package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.util.Util;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 11/23/15.
 */
public class SRLLabelPrediction {
    private static final int minFeatureCount = 5;
    private static final QASrlFeatureHelper featureHelper = new QASrlFeatureHelper();
    private static final Random random = new Random(12345);
    private static Map<Integer, List<MappedDependency>> allDependencies;
    private static List<Integer> sentenceIndices = null;

    public static double[] train(List<MappedDependency> trainingDependencies,
                                 double sigmaSquared,
                                 Util.Logger trainingLogger) {
        featureHelper.extractFrequentFeatures(trainingDependencies, minFeatureCount);
        List<Structure.LabelPredictionInstance> trainingData = trainingDependencies.stream()
                .map(dep -> Structure.newLabelPredictionInstance(dep, featureHelper))
                .collect(Collectors.toList());
        final int numFeatures = featureHelper.getNumFeatures();
        /**
         * Create loss function.
         */

        final OptimizationHelper.LossFunction lossFunction = OptimizationHelper.getLossFunction(trainingData,
                sigmaSquared, trainingLogger);

        final double[] weights = new double[numFeatures];
        Random random = new Random(12345);
        for (int i = 0; i < numFeatures; i++) {
            weights[i] = random.nextDouble() / 100.0;
        }
        /**
         * Train!
         */
        OptimizationHelper.TrainingAlgorithm algorithm = OptimizationHelper.makeLBFGS();
        algorithm.train(lossFunction, weights);

        return weights;
    }

    private static void evaluate(List<MappedDependency> testDependencies, double[] weights) {
        double accuracy = .0;
        for (MappedDependency dependency : testDependencies) {
            Structure.LabelPredictionInstance testInstance = Structure.newLabelPredictionInstance(dependency,
                    featureHelper);
            testInstance.updateScores(weights);
            int bestLabel = testInstance.getBest();
            String goldSrlLabel = Structure.LabelPredictionInstance.classes[testInstance.goldCliqueId].toString();
            String predSrlLabel = Structure.LabelPredictionInstance.classes[bestLabel].toString();
            List<String> words = dependency.pbSentence.getWords();
            System.out.println(goldSrlLabel + "\t" + predSrlLabel + "\t" + dependency.qaDependency.toString(words));
            if (testInstance.goldCliqueId == bestLabel) {
                accuracy += 1.0;
            }
        }
        System.out.println("accuracy:\t" + accuracy / testDependencies.size());
    }

    private static void jackknife(Map<Integer, List<MappedDependency>> allDependencies,
                                  List<MappedDependency> trainingDependencies,
                                  List<MappedDependency> testDependencies,
                                  double heldOutPortion,
                                  int heldOutFold) {
        // TODO
        if (sentenceIndices == null) {
            sentenceIndices = new ArrayList<>(allDependencies.keySet());
            Collections.shuffle(sentenceIndices, random);
        }
        int numSentences = sentenceIndices.size();
        int heldOutStartIdx = (int) Math.floor(heldOutPortion * heldOutFold * numSentences);
        int heldOutEndIdx = (int) Math.min(numSentences, heldOutPortion * (heldOutFold + 1) * numSentences);
        assert (heldOutStartIdx < numSentences);
        assert (trainingDependencies != null && testDependencies != null);
        trainingDependencies.clear();
        testDependencies.clear();
        for (int i = 0; i < numSentences; i++) {
            List<MappedDependency> dependencies = allDependencies.get(sentenceIndices.get(i));
            if (i < heldOutStartIdx || i >= heldOutEndIdx) {
                trainingDependencies.addAll(dependencies);
            } else {
                testDependencies.addAll(dependencies);
            }
        }
        System.out.println("training: " + trainingDependencies.size() + "\ttest: " + testDependencies.size());
    }

    public static void main(String[] args) {
        final double sigmaSquared = 0.5;
        Map<Integer, List<MappedDependency>> allDependencies = PropBankAligner.getMappedDependencies();
        List<MappedDependency> trainingDependencies = new ArrayList<>(),
                               testDependencies = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            jackknife(allDependencies, trainingDependencies, testDependencies, 0.2, i);
            double[] weights = train(trainingDependencies, sigmaSquared, new Util.Logger(new File("log")));
            evaluate(testDependencies, weights);
        }
    }
}
