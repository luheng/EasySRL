package edu.uw.easysrl.syntax.model.feature;

import com.carrotsearch.hppc.ObjectDoubleHashMap;
import edu.uw.easysrl.main.InputReader;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class UnaryRuleFeature extends Feature {
    private final FeatureKey defaultKey;
    private int defaultIndex = 0;
    private double defaultScore = Double.MIN_VALUE;

    UnaryRuleFeature() {
        super();
        this.defaultKey = new FeatureKey(super.id);
    }

    private static final long serialVersionUID = 1L;

    public double getFeatureScore(final int ruleID, final List<InputReader.InputWord> sentence, final int spanStart,
                                  final int spanEnd, final ObjectDoubleHashMap<FeatureKey> featureToScore) {
        final FeatureKey featureKey = getFeatureKey(ruleID, sentence, spanStart, spanEnd);
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
    public void resetDefaultIndex() {
        defaultIndex = 0;
    }

    public int getFeatureIndex(final int ruleID, final List<InputReader.InputWord> words, final int startIndex,
                               final int spanEnd, final Map<FeatureKey, Integer> featureToIndexMap) {
        final Integer result = featureToIndexMap.get(getFeatureKey(ruleID, words, startIndex, spanEnd));
        if (result == null) {
            if (defaultIndex == 0) {
                defaultIndex = featureToIndexMap.get(defaultKey);
            }
            return defaultIndex;
        }
        return result;
    }

    public abstract FeatureKey getFeatureKey(final int ruleID, final List<InputReader.InputWord> sentence,
                                             final int spanStart, final int spanEnd);

    private final static UnaryRuleFeature unaryRuleIDFeature = new UnaryRuleFeature() {
        private static final long serialVersionUID = 1L;

        @Override
        public FeatureKey getFeatureKey(final int ruleID, final List<InputReader.InputWord> sentence, final int spanStart,
                                        final int spanEnd) {
            return hash(super.id, ruleID);
        }
    };

    @SuppressWarnings("unused")
    private final static UnaryRuleFeature unaryRuleIDandLengthFeature = new UnaryRuleFeature() {
        private static final long serialVersionUID = 1L;

        @Override
        public FeatureKey getFeatureKey(final int ruleID, final List<InputReader.InputWord> sentence, final int spanStart,
                                        final int spanEnd) {
            return hash(super.id, ruleID, Math.min(10, spanEnd - spanStart));
        }
    };

    @SuppressWarnings("unused")
    private final static UnaryRuleFeature unaryRuleIDandPreviousWordFeature = new UnaryRuleFeature() {
        private static final long serialVersionUID = 1L;

        @Override
        public FeatureKey getFeatureKey(final int ruleID, final List<InputReader.InputWord> sentence, final int spanStart,
                                        final int spanEnd) {
            return hash(super.id, ruleID, (spanStart == 0 ? "" : sentence.get(spanStart - 1).word).hashCode());
        }
    };

    public final static Collection<UnaryRuleFeature> unaryRules = Arrays.asList(unaryRuleIDFeature);
}
