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

    /**
     * All scores are in log scale.
     * p(y|x) = exp(w.f(x,y)) / sum_y' (exp(w.f(x,y')))
     */
    // FIXME: clique structure and clique ID
    public static class MultiClassStructure extends AbstractStructure {
        Clique goldClique;
        Clique[] allCliques;
        double[] cliqueScores;
        protected int goldCliqueId;
        static double backoff = 1e-8;

        public static final void setBackoff(double backoff) {
            MultiClassStructure.backoff = backoff;
        }

        public MultiClassStructure(int goldCliqueId, Clique goldClique, Clique[] allCliques) {
            this.goldClique = goldClique;
            this.allCliques = allCliques;
            this.cliqueScores = new double[allCliques.length];
            this.goldCliqueId = goldCliqueId;
        }

        @Override
        public void updateScores(double[] featureWeights) {
            logLikelihood = goldClique.getLogScore(featureWeights);
            for (int c = 0; c < cliqueScores.length; c++) {
                cliqueScores[c] = allCliques[c].getLogScore(featureWeights);
                cliqueScores[c] = Math.log(Math.exp(cliqueScores[c]) + backoff);
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
                allCliques[c].countFeatures(counts, Math.exp(cliqueScores[c] - logNorm));
            }
        }

        public int getBest() {
            int best = 0;
            for (int c = 1; c < allCliques.length; c++) {
                if (cliqueScores[c] > cliqueScores[best]) {
                    best = c;
                }
            }
            return best;
        }
    }

    public static class LabelPredictionInstance extends MultiClassStructure {
        public static int numClasses = SRLFrame.getAllSrlLabels().size();
        public static SRLFrame.SRLLabel[] classes = SRLFrame.getAllSrlLabels()
                .toArray(new SRLFrame.SRLLabel[numClasses]);
        public LabelPredictionInstance(int goldCliqueId, Clique goldClique, Clique[] allCliques) {
            super(goldCliqueId, goldClique, allCliques);
        }
    }

    public static LabelPredictionInstance newLabelPredictionInstance(MappedDependency dependency,
                                                                     QASrlFeatureHelper featureHelper) {
        SRLDependency srlDep  = dependency.srlDependency;
        List<Integer> argumentIndices = new ArrayList<>(srlDep.getArgumentPositions());
        Collections.sort(argumentIndices);
        Clique[] allCliques = new Clique[LabelPredictionInstance.numClasses];
        int goldCliqueId = 0;
        for (int c = 0; c < LabelPredictionInstance.numClasses; c++) {
            SRLFrame.SRLLabel role = LabelPredictionInstance.classes[c];
            allCliques[c] = featureHelper.getClique(
                    dependency.pbSentence,
                    new SRLDependency(srlDep.getPredicate(), srlDep.getPredicateIndex(),
                                      argumentIndices, role,
                                      srlDep.getPreposition()),
                    dependency.qaDependency);
            if (role == srlDep.getLabel()) {
                goldCliqueId = c;
            }
        }

        Clique goldClique = featureHelper.getClique(dependency.pbSentence, srlDep, dependency.qaDependency);
        return new Structure.LabelPredictionInstance(goldCliqueId, goldClique, allCliques);
    }
}
