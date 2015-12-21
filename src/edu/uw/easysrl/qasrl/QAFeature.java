package edu.uw.easysrl.qasrl;

import com.carrotsearch.hppc.ObjectDoubleHashMap;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.model.feature.Feature;

import java.util.List;
import java.util.Map;

/**
 * Created by luheng on 11/23/15.
 */
// TODO: figure this out later
public abstract class QAFeature extends Feature {

    /*
    public static abstract class QuestionWordFeature extends Feature {
        private final FeatureKey defaultKey;
        private int defaultIndex = 0;
        private double defaultScore = Double.MIN_VALUE;

        QuestionWordFeature() {
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

        public abstract FeatureKey getFeatureKey(final int ruleID, final List<InputReader.InputWord> sentence, final int spanStart,
                                                 final int spanEnd);

    }
*/
}
