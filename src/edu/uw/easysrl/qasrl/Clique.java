package edu.uw.easysrl.qasrl;

import java.util.Arrays;

/**
 * Created by luheng on 11/23/15.
 */
public class Clique {
    int[] featureIndices;
    double[] featureValues;
    int numFeatures;

    public Clique(int[] featureIndices) {
        this.featureIndices = featureIndices;
        this.numFeatures = featureIndices.length;
        this.featureValues = new double[numFeatures];
        Arrays.fill(featureValues, 1.0);
    }

    public Clique(int[] featureIndices, double[] featureValues) {
        this.featureIndices = featureIndices;
        this.featureValues = featureValues;
        this.numFeatures = featureIndices.length;
    }

    public double getScore(double[] featureWeights) {
        double score = .0;
        for (int i = 0; i < numFeatures; i++) {
            score += featureWeights[featureIndices[i]] * featureValues[i];
        }
        return score;
    }

    public double getLogScore(double[] featureWeights) {
        return Math.log(getScore(featureWeights));
    }

    public void countFeatures(double[] counts, double weight) {
        for (int i = 0; i < numFeatures; i++) {
            counts[featureIndices[i]] += featureValues[i] * weight;
        }
    }
}
