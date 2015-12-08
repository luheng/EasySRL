package edu.uw.easysrl.syntax.model.feature;

import com.carrotsearch.hppc.ObjectDoubleHashMap;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class BinaryFeature extends Feature {
    private static final long serialVersionUID = 1L;
    private final FeatureKey defaultKey;
    private int defaultIndex = 0;
    private double defaultScore = Double.MIN_VALUE;

    BinaryFeature() {
        super();
        this.defaultKey = new FeatureKey(super.id);
    }

    @Override
    public FeatureKey getDefault() {
        return defaultKey;
    }

    @Override
    public void resetDefaultIndex() { defaultIndex = 0; }

    public int getFeatureIndex(final Category category, final Combinator.RuleType ruleClass, final Category left,
                               final Combinator.RuleClass leftRuleClass, final int leftLength, final Category right,
                               final Combinator.RuleClass rightRuleClass,
                               final int rightLength, final List<InputReader.InputWord> sentence, final Map<FeatureKey, Integer> featureToIndex) {
        final FeatureKey featureKey = getFeatureKey(category, ruleClass, left, leftRuleClass, leftLength, right,
                rightRuleClass, rightLength, sentence);
        final Integer result = featureToIndex.get(featureKey);
        if (result == null) {
            if (defaultIndex == 0) {
                defaultIndex = featureToIndex.get(defaultKey);
            }
            return defaultIndex;
        }
        return result;
    }

    public abstract FeatureKey getFeatureKey(final Category category, final Combinator.RuleType ruleClass,
                                             final Category left, final Combinator.RuleClass leftRuleClass, final int leftLength, final Category right,
                                             final Combinator.RuleClass rightRuleClass, int rightLength, List<InputReader.InputWord> sentence);

    public double getFeatureScore(final Category category, final Combinator.RuleType ruleClass, final Category left,
                                  final Combinator.RuleClass leftRuleClass, final int leftLength, final Category right,
                                  final Combinator.RuleClass rightRuleClass, final int rightLength, final List<InputReader.InputWord> sentence,
                                  final ObjectDoubleHashMap<FeatureKey> featureToScore) {
        final FeatureKey featureKey = getFeatureKey(category, ruleClass, left, leftRuleClass, leftLength, right,
                rightRuleClass, rightLength, sentence);
        final double result = featureToScore.getOrDefault(featureKey, Double.MIN_VALUE);
        if (result == Double.MIN_VALUE) {
            if (defaultScore == Double.MIN_VALUE) {
                defaultScore = featureToScore.get(defaultKey);
            }
            return defaultScore;
        }
        return result;
    }

    private final static BinaryFeature leftAndRightFeature = new BinaryFeature() {
        private static final long serialVersionUID = 1L;

        @Override
        public FeatureKey getFeatureKey(final Category result, final Combinator.RuleType ruleClass, final Category left,
                                        final Combinator.RuleClass leftRuleClass, final int leftLength, final Category right,
                                        final Combinator.RuleClass rightRuleClass, final int rightLength, final List<InputReader.InputWord> sentence) {
            return hash(super.id, left.hashCode(), right.hashCode());
        }
    };

    @SuppressWarnings("unused")
    private final static BinaryFeature ruleFeature = new BinaryFeature() {
        private static final long serialVersionUID = 1L;

        @Override
        public FeatureKey getFeatureKey(final Category result, final Combinator.RuleType ruleClass, final Category left,
                                        final Combinator.RuleClass leftRuleClass, final int leftLength, final Category right,
                                        final Combinator.RuleClass rightRuleClass, final int rightLength, final List<InputReader.InputWord> sentence) {
            return hash(super.id, left.hashCode(), right.hashCode(), result.hashCode());
        }
    };

    @SuppressWarnings("unused")
    private final static BinaryFeature ruleClassFeature = new BinaryFeature() {
        private static final long serialVersionUID = 1L;

        @Override
        public FeatureKey getFeatureKey(final Category result, final Combinator.RuleType ruleClass, final Category left,
                                        final Combinator.RuleClass leftRuleClass, final int leftLength, final Category right,
                                        final Combinator.RuleClass rightRuleClass, final int rightLength, final List<InputReader.InputWord> sentence) {
            return hash(super.id, ruleClass.toString().hashCode());
        }
    };

    // Aimed at cases where a unary rule is used, and should apply nearby
    // (e.g. reduced relatives)
    @SuppressWarnings("unused")
    private final static BinaryFeature leftLengthFeature = new BinaryFeature() {
        private static final long serialVersionUID = 1L;

        @Override
        public FeatureKey getFeatureKey(final Category result, final Combinator.RuleType ruleClass, final Category left,
                                        final Combinator.RuleClass leftRuleClass, final int leftLength, final Category right,
                                        final Combinator.RuleClass rightRuleClass, final int rightLength, final List<InputReader.InputWord> sentence) {
            return hash(super.id, leftRuleClass.toString().hashCode(), left.hashCode(), rightLength);
        }
    };

    @SuppressWarnings("unused")
    private final static BinaryFeature rightLengthFeature = new BinaryFeature() {
        private static final long serialVersionUID = 1L;

        @Override
        public FeatureKey getFeatureKey(final Category result, final Combinator.RuleType ruleClass, final Category left,
                                        final Combinator.RuleClass leftRuleClass, final int leftLength, final Category right,
                                        final Combinator.RuleClass rightRuleClass, final int rightLength, final List<InputReader.InputWord> sentence) {
            return hash(super.id, rightRuleClass.toString().hashCode(), right.hashCode(), leftLength);
        }
    };

    public static Collection<BinaryFeature> getFeatures() {
        return Arrays.asList(
                // ruleClassFeature
                leftAndRightFeature);
    }
}
