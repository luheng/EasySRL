package edu.uw.easysrl.qasrl;

import com.google.common.collect.Lists;
import edu.uw.easysrl.syntax.training.TrainingParameters;
import edu.uw.easysrl.util.Util;
import lbfgsb.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Created by luheng on 11/23/15.
 */
public class OptimizationHelper {

    private static class InvertedLossFunction extends LossFunction {
        private final LossFunction lossFunction;

        private InvertedLossFunction(final LossFunction lossFunction) {
            super(lossFunction.trainingData, lossFunction.logger);
            this.lossFunction = lossFunction;
        }

        @Override
        public FunctionValues getValues2(final double[] featureWeights,
                                         final List<Structure.LabelPredictionInstance> trainingData) {
            final FunctionValues result = lossFunction.getValues2(featureWeights, trainingData);
            final double[] newGradient = new double[featureWeights.length];
            for (int i = 0; i < newGradient.length; i++) {
                newGradient[i] = -result.gradient[i];
            }
            return new FunctionValues(-result.functionValue, newGradient);
        }
    }

    static class LogLossFunction extends LossFunction {
        private LogLossFunction(final List<Structure.LabelPredictionInstance> trainingData,
                                final Util.Logger logger) {
            super(trainingData, logger);
        }

        @Override
        FunctionValues getValues2(final double[] featureWeights,
                                  final List<Structure.LabelPredictionInstance> trainingData) {
            final double[] modelExpectation = new double[featureWeights.length];
            final double[] goldExpectation = new double[featureWeights.length];
            double loglikelihood = 0.0;
            for (final Structure.LabelPredictionInstance trainingExample : trainingData) {
                loglikelihood += computeExpectationsForTrainingExample(trainingExample, featureWeights,
                        modelExpectation, goldExpectation);
            }
            final double[] gradient = Util.subtract(goldExpectation, modelExpectation);
            final FunctionValues result = new FunctionValues(loglikelihood, gradient);
            return result;
        }

        private double computeExpectationsForTrainingExample(final Structure.LabelPredictionInstance trainingExample,
                                                             final double[] featureWeights,
                                                             double[] modelExpections,
                                                             double[] goldExpectation) {
            trainingExample.updateScores(featureWeights);
            trainingExample.countEmpiricalFeatures(goldExpectation);
            trainingExample.countExpectedFeatures(modelExpections);
            double logLikelihood = trainingExample.getUnnormalizedLogLikelihood() - trainingExample.getLogNorm();
            // Preconditions.checkState(loglikelihood < 0.1, "Positive charts have higher score than complete charts");
            return logLikelihood;
        }
    }

    static abstract class LossFunction implements DifferentiableFunction {
        private final List<Structure.LabelPredictionInstance> trainingData;
        private int iterationNumber;
        private final Util.Logger logger;

        private LossFunction(final List<Structure.LabelPredictionInstance> trainingData, final Util.Logger logger) {
            super();
            this.trainingData = trainingData;
            this.logger = logger;
        }

        abstract FunctionValues getValues2(double[] featureWeights, List<Structure.LabelPredictionInstance> trainingData);

        @Override
        public final FunctionValues getValues(final double[] featureWeights) {
            logger.log("Calculating expectations for iteration: " + iterationNumber);
            final FunctionValues result = getValues2(featureWeights, trainingData);
            logger.log("Done. Loss = " + result.functionValue);
            iterationNumber++;
            return result;
        }
    }

    private static class RegularizedLossFunction extends LossFunction {
        private final LossFunction lossFunction;

        private RegularizedLossFunction(final LossFunction lossFunction, final double sigmaSquared) {
            super(lossFunction.trainingData, lossFunction.logger);
            this.sigmaSquared = sigmaSquared;
            this.lossFunction = lossFunction;
        }

        private final double sigmaSquared;

        @Override
        FunctionValues getValues2(final double[] featureWeights,
                                  final List<Structure.LabelPredictionInstance> trainingData) {
            final FunctionValues unregularized = lossFunction.getValues2(featureWeights, trainingData);
            // L2 regularization
            double loglikelihood = unregularized.functionValue;
            final double[] gradient = unregularized.gradient;
            for (int i = 0; i < featureWeights.length; i++) {
                loglikelihood = loglikelihood - (Math.pow(featureWeights[i], 2) / (2.0 * sigmaSquared));
                gradient[i] = gradient[i] - (featureWeights[i] / sigmaSquared);
            }
            final FunctionValues result = new FunctionValues(loglikelihood, gradient);
            return result;
        }
    }

    private static class ParallelLossFunction extends LossFunction {
        private final LossFunction lossFunction;
        private final int numThreads;

        private ParallelLossFunction(final LossFunction lossFunction, final int numThreads) {
            super(lossFunction.trainingData, lossFunction.logger);
            this.lossFunction = lossFunction;
            this.numThreads = numThreads;
        }

        @Override
        public FunctionValues getValues2(final double[] featureWeights,
                                         final List<Structure.LabelPredictionInstance> trainingData) {
            final Collection<Callable<FunctionValues>> tasks = new ArrayList<>();
            final int batchSize = trainingData.size() / numThreads;
            for (final List<Structure.LabelPredictionInstance> batch : Lists.partition(trainingData, batchSize)) {
                tasks.add(new Callable<FunctionValues>() {
                    @Override
                    public FunctionValues call() throws Exception {
                        try {
                            return lossFunction.getValues2(featureWeights, batch);
                        } catch (final Exception e) {
                            e.printStackTrace();
                            throw e;
                        }
                    }
                });
            }
            final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            List<Future<FunctionValues>> results;
            try {
                results = executor.invokeAll(tasks);
                final double[] totalGradient = new double[featureWeights.length];
                double totalLoss = 0.0;
                for (final Future<FunctionValues> result : results) {
                    final FunctionValues values = result.get();
                    totalLoss += values.functionValue;
                    Util.add(totalGradient, values.gradient);
                }
                executor.shutdown(); // always reclaim resources
                return new FunctionValues(totalLoss, totalGradient);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    static abstract class TrainingAlgorithm {
        abstract void train(DifferentiableFunction lossFunction, double[] weights);
    }

    static LossFunction getUnregularizedLossFunction(final List<Structure.LabelPredictionInstance> data,
                                                     final Util.Logger trainingLogger) {
        LossFunction lossFunction = new LogLossFunction(data, trainingLogger);
        lossFunction = new ParallelLossFunction(lossFunction, Runtime.getRuntime().availableProcessors());
        return lossFunction;
    }

    private static LossFunction getRegularizedAndInvertedLossFunction(LossFunction lossFunction,
                                                                      final double sigmaSquared) {
        lossFunction = new RegularizedLossFunction(lossFunction, sigmaSquared);
        lossFunction = new InvertedLossFunction(lossFunction);
        return lossFunction;
    }

    static LossFunction getLossFunction(final List<Structure.LabelPredictionInstance> data,
                                        final double sigmaSquared,
                                        final Util.Logger trainingLogger) {
        LossFunction lossFunction = getUnregularizedLossFunction(data, trainingLogger);
        lossFunction = getRegularizedAndInvertedLossFunction(lossFunction, sigmaSquared);
        return lossFunction;
    }

    static TrainingAlgorithm makeLBFGS() {
        return new TrainingAlgorithm() {
            @Override
            void train(final DifferentiableFunction lossFunction, final double[] weights) {
                try {
                    final Minimizer alg = new Minimizer();
                    alg.setDebugLevel(5);
                    alg.getStopConditions().setFunctionReductionFactor(Math.pow(10, 1)); // 10
                    alg.getStopConditions().setMaxIterations(200);
                    final List<Bound> bounds = new ArrayList<>();
                    final Bound unbounded = new Bound(null, null);
                    bounds.add(unbounded);
                    for (int i = 0; i < weights.length - 1; i++) {
                        bounds.add(unbounded);
                    }
                    alg.setBounds(bounds);
                    final Result result = alg.run(lossFunction, weights);
                    System.err.println("Converged after: " + result.iterationsInfo);
                    System.arraycopy(result.point, 0, weights, 0, result.point.length);
                } catch (final LBFGSBException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
