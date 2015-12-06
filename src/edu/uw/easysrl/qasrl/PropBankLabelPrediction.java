package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.util.Util;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 11/23/15.
 */
public class PropBankLabelPrediction {
    private static final int minFeatureCount = 5;
    private static final QASrlFeatureHelper featureHelper = new QASrlFeatureHelper();
    private static final Random random = new Random(12345);
    private static Map<Integer, List<AlignedDependency<SRLDependency, QADependency>>> allDependencies;
    private static List<Integer> sentenceIndices = null;

    public static double[] train(List<AlignedDependency<SRLDependency, QADependency>> trainingDependencies,
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

    private static double evaluate(List<AlignedDependency<SRLDependency, QADependency>> testDependencies,
                                   double[] weights) {
        // TODO: confusion matrix
        int numClasses = Structure.LabelPredictionInstance.numClasses;
        double[][] confusion = new double[numClasses][numClasses];
        for (int i = 0; i < numClasses; i++) {
            Arrays.fill(confusion[i], 0.0);
        }
        double accuracy = .0;
        for (AlignedDependency<SRLDependency, QADependency> dependency : testDependencies) {
            Structure.LabelPredictionInstance testInstance = Structure.newLabelPredictionInstance(dependency,
                    featureHelper);
            testInstance.updateScores(weights);
            int bestLabel = testInstance.getBest();
            String goldSrlLabel = Structure.LabelPredictionInstance.classes[testInstance.goldCliqueId].toString();
            String predSrlLabel = Structure.LabelPredictionInstance.classes[bestLabel].toString();
            List<String> words = dependency.sentence.getWords();
            //System.out.println(goldSrlLabel + "\t" + predSrlLabel + "\t" + dependency.qaDependency.toString(words));
            if (testInstance.goldCliqueId == bestLabel) {
                accuracy += 1.0;
            }
            confusion[testInstance.goldCliqueId][bestLabel] += 1.0;
        }
        accuracy /= testDependencies.size();
        System.out.println("accuracy:\t" + accuracy);
        // Row-normalize confusion matrix.
        // Just print.
        System.out.print("-");
        for (int i = 0; i < numClasses; i++) {
            System.out.print("\t" + Structure.LabelPredictionInstance.classes[i]);
        }
        System.out.println();
        for (int i = 0; i < numClasses; i++) {
            System.out.print(Structure.LabelPredictionInstance.classes[i]);
            for (int j = 0; j < numClasses; j++) {
                System.out.print("\t" + confusion[i][j]);
            }
            System.out.println();
        }
        System.out.println();
        return accuracy;
    }

    private static void jackknife(Map<Integer, List<AlignedDependency<SRLDependency, QADependency>>> allDependencies,
                                  List<AlignedDependency<SRLDependency, QADependency>> trainingDependencies,
                                  List<AlignedDependency<SRLDependency, QADependency>> testDependencies,
                                  double heldOutPortion,
                                  int heldOutFold) {
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
            List<AlignedDependency<SRLDependency, QADependency>> dependencies =
                    allDependencies.get(sentenceIndices.get(i)).stream()
                        .filter(dep -> dep.dependency1 != null && dep.dependency2 != null)
                        .collect(Collectors.toList());
            if (i < heldOutStartIdx || i >= heldOutEndIdx) {
                trainingDependencies.addAll(dependencies);
            } else {
                testDependencies.addAll(dependencies);
            }
        }
        System.out.println("training: " + trainingDependencies.size() + "\ttest: " + testDependencies.size());
    }

    public static void main(String[] args) {
        final double[] sigmaSquaredValues = {0.01, 0.1, 1, 10, 100};
        List<Double> results = new ArrayList<>();
        Map<Integer, List<AlignedDependency<SRLDependency, QADependency>>> allDependencies =
                PropBankAligner.getPbAndQADependencies();
        List<AlignedDependency<SRLDependency, QADependency>> trainingDependencies = new ArrayList<>(),
                                                             testDependencies = new ArrayList<>();
        for (double sigmaSquared : sigmaSquaredValues) {
            double avgAccuracy = .0;
            for (int i = 0; i < 5; i++) {
                jackknife(allDependencies, trainingDependencies, testDependencies, 0.2, i);
                double[] weights = train(trainingDependencies, sigmaSquared, new Util.Logger(new File("log")));
                evaluate(trainingDependencies, weights);
                avgAccuracy += evaluate(testDependencies, weights);
                System.out.println();
            }
            results.add(avgAccuracy / 5.0);
            //System.out.println(sigmaSquared + "\t" + avgAccuracy / 5.0);
        }
        for (int i = 0; i < sigmaSquaredValues.length; i++) {
            System.out.println(sigmaSquaredValues[i] + "\t" + results.get(i));
        }
    }
}
