package edu.uw.easysrl.syntax.training;

import edu.uw.easysrl.syntax.model.feature.ArgumentSlotFeature;
import edu.uw.easysrl.syntax.model.feature.BilexicalFeature;
import edu.uw.easysrl.syntax.model.feature.DenseLexicalFeature;
import edu.uw.easysrl.syntax.model.feature.FeatureSet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;

/**
 * Created by luheng on 10/28/15.
 */
public class TrainingParameters implements Serializable {
    private final int minimumFeatureFrequency;
    /**
     *
     */
    private static final long serialVersionUID = 6752386432642051238L;
    private final FeatureSet featureSet;
    private final double sigmaSquared;
    private final int maxDependencyLength;
    private final File modelFolder;
    private final double costFunctionWeight;

    public TrainingParameters(final int maxDependencyLength, final FeatureSet featureSet,
                              final double sigmaSquared, final int minimumFeatureFrequency, final File modelFolder,
                              final double costFunctionWeight) {
        super();
        this.featureSet = featureSet;
        this.sigmaSquared = sigmaSquared;
        this.maxDependencyLength = maxDependencyLength;
        this.minimumFeatureFrequency = minimumFeatureFrequency;
        this.modelFolder = modelFolder;
        this.costFunctionWeight = costFunctionWeight;
    }

    private Object readResolve() {
        // Hack to deal with transient DenseLexicalFeature
        try {
            return new TrainingParameters(maxDependencyLength, featureSet.setSupertaggingFeature(new File(
                    modelFolder, "pipeline")), sigmaSquared, minimumFeatureFrequency, modelFolder,
                    costFunctionWeight);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public DenseLexicalFeature getLexicalCategoryFeatures() {
        return featureSet.lexicalCategoryFeatures;
    }

    public File getLogFile() {
        return new File(getModelFolder(), "log");
    }

    public File getModelFolder() {
        return modelFolder;
    }

    public File getFeaturesFile() {
        return new File(getModelFolder(), "features");
    }

    public File getWeightsFile() {
        return new File(getModelFolder(), "weights");
    }

    public File getFeatureToIndexFile() {
        return new File(getModelFolder(), "featureToIndex");
    }

    public int getMaxDependencyLength() {
        return maxDependencyLength;
    }

    public int getMinimumFeatureFrequency() {
        return minimumFeatureFrequency;
    }

    public FeatureSet getFeatureSet() {
        return featureSet;
    }

    public Collection<ArgumentSlotFeature> getArgumentslotfeatures() {
        return featureSet.argumentSlotFeatures;
    }

    public Collection<BilexicalFeature> getDependencyFeatures() {
        return featureSet.dependencyFeatures;

    }

    public double getSigmaSquared() {
        return sigmaSquared;
    }

    public double getCostFunctionWeight() {
        return costFunctionWeight;
    }

}