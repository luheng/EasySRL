package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by luheng on 5/5/16.
 */
public class Classifier {
    ImmutableList<DependencyInstance> trainInstances;
    DMatrix trainData;
    Booster booster;

    public static Classifier trainClassifier(final ImmutableList<DependencyInstance> trainInstances,
                                             final Map<String, Object> paramsMap,
                                             final int numRounds) {
        Classifier classifier = new Classifier();
        classifier.trainInstances = trainInstances;
        try {
            classifier.trainData = ClassificationUtils.getDMatrix(trainInstances);
            final Map<String, DMatrix> watches = ImmutableMap.of("train", classifier.trainData);
            classifier.booster = XGBoost.train(classifier.trainData, paramsMap, numRounds, watches, null, null);
        } catch (XGBoostError e) {
            e.printStackTrace();
            return null;
        }
        return classifier;
    }

    // TODO: predict
    public ImmutableList<Double> predict(final ImmutableList<DependencyInstance> devInstances) {
        List<Double> predList = new ArrayList<>();
        try {
            DMatrix devData = ClassificationUtils.getDMatrix(devInstances);
            float[][] pred = booster.predict(devData);
            for (float[] p : pred) {
                predList.add(new Double(p[0]));
            }
        } catch (XGBoostError e) {
            e.printStackTrace();
            return null;
        }
        return ImmutableList.copyOf(predList);
    }
}
