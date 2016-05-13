package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.corpora.CountDictionary;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by luheng on 5/11/16.
 */
public class PerceptronFeatureExtractor {
    CountDictionary featureMap;
    private boolean acceptNewFeatures = true;

    PerceptronFeatureExtractor() {
        featureMap = new CountDictionary();
        acceptNewFeatures = true;
    }

    void freeze() {
        acceptNewFeatures = false;
    }

    ImmutableMap<Integer, Double> getFeatures(final DependencyInstance instance,
                                              final double xgbPrediction) {
        Map<Integer, Double> features = new HashMap<>();
        Stream.of(0.1, 0.2, 0.3, 0.4, 0.5).forEach(threshold -> {
            if (xgbPrediction < threshold) {
                addFeature(features, String.format("%s_p<%.2f", instance.instanceType, threshold), 1.0);
            }
        });
        Stream.of(0.9, 0.8, 0.7, 0.6, 0.5).forEach(threshold -> {
            if (xgbPrediction > threshold) {
                addFeature(features, String.format("%s_p>%.2f", instance.instanceType, threshold), 1.0);
            }
        });
        addFeature(features, instance.instanceType + "_BIAS", 1.0);
        return ImmutableMap.copyOf(features);
    }

    private boolean addFeature(final Map<Integer, Double> features, String featureName, double featureValue) {
        int featureId = featureMap.addString(featureName, acceptNewFeatures);
        if (featureId < 0) {
            return false;
        }
        features.put(featureId, featureValue);
        return true;
    }

    public void printFeature(final Map<Integer, Double> features) {
        for (int fid : features.keySet()) {
            System.out.println(featureMap.getString(fid) + "\t=\t" + features.get(fid));
        }
    }
}
