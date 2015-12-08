package edu.uw.easysrl.syntax.training;

import com.google.common.collect.Multimap;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.parser.AbstractParser;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;

/**
 * Created by luheng on 11/2/15.
 */

public class TrainingDataParameters implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int maxChartSize;
    private final double supertaggerBeam;
    private final double supertaggerBeamForGoldCharts;
    private final int maxTrainingSentenceLength;
    private final Collection<Category> possibleRootCategories;
    private final File existingModel;

    TrainingDataParameters(final double supertaggerBeam, final int maxTrainingSentenceLength,
                           final Collection<Category> possibleRootCategories, final File existingModel, final int maxChartSize,
                           final double supertaggerBeamForGoldCharts) {
        super();
        this.supertaggerBeam = supertaggerBeam;
        this.maxTrainingSentenceLength = maxTrainingSentenceLength;
        this.possibleRootCategories = possibleRootCategories;
        this.existingModel = existingModel;
        this.supertaggerBeamForGoldCharts = supertaggerBeamForGoldCharts;

        this.maxChartSize = maxChartSize;

        try {
            this.unaryRules = AbstractParser.loadUnaryRules(new File(existingModel, "unaryRules"));
        } catch (final IOException e) {
            throw new RuntimeException();
        }

    }

    private final Multimap<Category, AbstractParser.UnaryRule> unaryRules;

    public Multimap<Category, AbstractParser.UnaryRule> getUnaryRules() {
        return unaryRules;
    }

    public File getExistingModel() {
        return existingModel;
    }

    public Collection<Category> getPossibleRootCategories() {
        return possibleRootCategories;
    }

    public int getMaxChartSize() {
        return maxChartSize;
    }

    public int getMaxTrainingSentenceLength() {
        return maxTrainingSentenceLength;
    }

    public double getSupertaggerBeam() {
        return supertaggerBeam;
    }

    public double getSupertaggerBeamForGoldCharts() {
        return supertaggerBeamForGoldCharts;
    }
}
