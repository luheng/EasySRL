package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.util.CompUtils;

import java.util.*;

/**
 * Created by luheng on 11/23/15.
 * For structured prediction ...
 * TODO: combine with FeatureForest
 */

public class Structure {

    public static abstract class AbstractStructure {
        protected double logLikelihood, logNorm;

        protected AbstractStructure() {
        }

        public abstract void updateScores(double[] featureWeights);

        public double getUnnormalizedLogLikelihood() {
            return logLikelihood;
        }

        public double getLogNorm() {
            return logNorm;
        }

        public abstract void countEmpiricalFeatures(double[] counts);

        public abstract void countExpectedFeatures(double[] counts);
    }

    public static class FirstOrderSequence extends AbstractStructure {
        Clique[] goldCliques;
        Clique[][] allCliques;
        double[][] cliqueScores, nodeMarginals;
        double[][][] edgeMarginals;
        int sequenceLength;

        protected FirstOrderSequence(Clique[] goldCliques, Clique[][] allCliques) {
            super();
            this.goldCliques = goldCliques;
            this.allCliques = allCliques;
            this.sequenceLength = goldCliques.length;
        }

        @Override
        public void updateScores(double[] featureWeights) {
            // compute scores
            logLikelihood = .0;
            for (int i = 0; i < sequenceLength; i++) {
                logLikelihood += goldCliques[i].getLogScore(featureWeights);
            }
            // forward backword ...
        }

        @Override
        public void countEmpiricalFeatures(double[] counts) {
            for (int i = 0; i < sequenceLength; i++) {
                goldCliques[i].countFeatures(counts, 1.0);
            }
        }

        @Override
        public void countExpectedFeatures(double[] counts) {
            for (int i = 0; i < sequenceLength; i++) {
                for (int j = 0; j < allCliques[i].length; j++) {
                    allCliques[i][j].countFeatures(counts, nodeMarginals[i][j] - logNorm);
                }
            }
        }
    }

    public static class MultiClassStructure extends AbstractStructure {
        Clique goldClique;
        Clique[] allCliques;
        double[] cliqueScores;

        public MultiClassStructure(Clique goldClique, Clique[] allCliques) {
            this.goldClique = goldClique;
            this.allCliques = allCliques;
            this.cliqueScores = new double[allCliques.length];
        }

        @Override
        public void updateScores(double[] featureWeights) {
            logLikelihood = goldClique.getLogScore(featureWeights);
            for (int c = 0; c < cliqueScores.length; c++) {
                cliqueScores[c] = allCliques[c].getLogScore(featureWeights);
            }
            logNorm = CompUtils.logSumExp(cliqueScores, cliqueScores.length);
        }

        @Override
        public void countEmpiricalFeatures(double[] counts) {
            goldClique.countFeatures(counts, 1.0);
        }

        @Override
        public void countExpectedFeatures(double[] counts) {
            for (int c = 0; c < allCliques.length; c++) {
                allCliques[c].countFeatures(counts, cliqueScores[c] - logNorm);
            }
        }
    }

    public static class LabelPredictionInstance extends MultiClassStructure {
        public static int numClasses = SRLFrame.getAllSrlLabels().size();
        public static SRLFrame.SRLLabel[] classes = SRLFrame.getAllSrlLabels()
                .toArray(new SRLFrame.SRLLabel[numClasses]);

        public LabelPredictionInstance(Clique goldClique, Clique[] allCliques) {
            super(goldClique, allCliques);
        }
    }

    public static LabelPredictionInstance newLabelPredictionInstance(MappedDependency dependency,
                                                                     QASrlFeatureHelper featureHelper) {
        SRLDependency srlDep  = dependency.srlDependency;
        List<Integer> argumentIndices = new ArrayList<>(srlDep.getArgumentPositions());
        Collections.sort(argumentIndices);
        int numClasses = SRLFrame.SRLLabel.numberOfLabels();
        Clique[] allCliques = new Clique[numClasses];
        for (int c = 0; c < LabelPredictionInstance.numClasses; c++) {
            SRLFrame.SRLLabel role = LabelPredictionInstance.classes[c];
            allCliques[c] = featureHelper.getClique(
                    dependency.pbSentence,
                    new SRLDependency(srlDep.getPredicate(), srlDep.getPredicateIndex(), argumentIndices, role,
                                      srlDep.getPreposition()),
                    dependency.qaDependency);
        }
        Clique goldClique = featureHelper.getClique(dependency.pbSentence, srlDep, dependency.qaDependency);
        return new Structure.LabelPredictionInstance(goldClique, allCliques);
    }
}
