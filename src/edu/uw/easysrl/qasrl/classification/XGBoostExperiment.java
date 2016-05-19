package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableMap;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;

import java.util.Map;

/**
 * Trying out XGBoost and stuff ...
 * Created by luheng on 4/21/16.
 */
public class XGBoostExperiment {

    final static String demoPath = "/home/luheng/Tools/xgboost/demo/data/";

    public static void main(final String[] args) throws XGBoostError {
        System.out.println("how bout this");
        final DMatrix trainData = new DMatrix(demoPath + "agaricus.txt.train");
        final DMatrix testData = new DMatrix(demoPath + "agaricus.txt.test");
        final Map<String, Object> paramsMap = ImmutableMap.of(
                "eta", 0.1,
                "max_depth", 2,
                "objective", "binary:logistic"
        );
        final Map<String, DMatrix> watches = ImmutableMap.of(
                "train", trainData,
                "test", testData
        );
        final int round = 2;
        Booster booster = XGBoost.train(trainData, paramsMap, round, watches, null, null);


        //System.out.println(trainData.getLabel());
        float[][] predicts = booster.predict(testData);
        //float[][] leafPredicts = booster.predictLeaf(testData, 0, true);
    }
}
