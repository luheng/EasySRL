package edu.uw.easysrl.syntax.model.feature;

import com.carrotsearch.hppc.ObjectDoubleHashMap;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class RootCategoryFeature extends Feature {
    private static final long serialVersionUID = 1L;
    private final FeatureKey defaultKey;
    private int defaultIndex = 0;
    private double defaultScore = Double.MIN_VALUE;

    RootCategoryFeature() {
        super();
        this.defaultKey = new FeatureKey(super.id);
    }

    public int getFeatureIndex(final List<InputReader.InputWord> sentence, final Category category,
                               final Map<FeatureKey, Integer> featureToIndex) {
        final FeatureKey featureKey = getFeatureKey(category, sentence);
        final Integer result = featureToIndex.get(featureKey);
        if (result == null) {
            if (defaultIndex == 0) {
                defaultIndex = featureToIndex.get(defaultKey);
            }
            return defaultIndex;
        }
        return result;
    }

    public double getFeatureScore(final List<InputReader.InputWord> words, final Category category,
                                  final ObjectDoubleHashMap<FeatureKey> featureToScore) {
        final FeatureKey featureKey = getFeatureKey(category, words);
        final double result = featureToScore.getOrDefault(featureKey, Double.MIN_VALUE);
        if (result == Double.MIN_VALUE) {
            if (defaultScore == Double.MIN_VALUE) {
                defaultScore = featureToScore.get(defaultKey);
            }
            return defaultScore;
        }
        return result;
    }

    @Override
    public FeatureKey getDefault() {
        return defaultKey;
    }

    @Override
    public void resetDefaultIndex() { defaultIndex = 0; }

    public abstract FeatureKey getFeatureKey(Category category, List<InputReader.InputWord> sentence);

    public static RootCategoryFeature justCategoryFeature = new RootCategoryFeature() {
        private static final long serialVersionUID = 1L;

        @Override
        public FeatureKey getFeatureKey(final Category category, final List<InputReader.InputWord> sentence) {
            return hash(super.id, category.hashCode());
        }
    };

    public static RootCategoryFeature categoryAndFirstWord = new RootCategoryFeature() {
        private static final long serialVersionUID = 1L;

        @Override
        public FeatureKey getFeatureKey(final Category category, final List<InputReader.InputWord> sentence) {
            return hash(super.id, category.hashCode(), sentence.get(0).word.hashCode());
        }
    };

    public static RootCategoryFeature categoryAndLastWord = new RootCategoryFeature() {
        private static final long serialVersionUID = 1L;

        @Override
        public FeatureKey getFeatureKey(final Category category, final List<InputReader.InputWord> sentence) {
            return hash(super.id, category.hashCode(), sentence.get(sentence.size() - 1).word.hashCode());
        }
    };

    public static RootCategoryFeature categoryAndLength = new RootCategoryFeature() {
        private static final long serialVersionUID = 1L;

        @Override
        public FeatureKey getFeatureKey(final Category category, final List<InputReader.InputWord> sentence) {
            return hash(super.id, category.hashCode(), sentence.size());
        }
    };

    public final static Collection<RootCategoryFeature> features = Arrays.asList(justCategoryFeature,
            categoryAndFirstWord, categoryAndLastWord, categoryAndLength);

}