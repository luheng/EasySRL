package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by luheng on 4/28/16.
 */
public class GridSearch {

    final static ImmutableList<Double> etaValues = ImmutableList.of(0.05, 0.1, 0.2);
    final static ImmutableList<Integer> treeDepthValues = ImmutableList.of(3, 4, 5, 7, 10);
    final static ImmutableList<Double> minChildWeightValues = ImmutableList.of(1.0, 1.5, 2.0);
    final static int numRounds = 50;

    public static void runGridSearch(final DMatrix trainData, final int numFolds) {
        final List<String> results = new ArrayList<>();
        etaValues.forEach(eta ->
                treeDepthValues.forEach(treeDepth ->
                        minChildWeightValues.forEach(minChildWeight -> {
                            final ImmutableMap<String, Object> paramsMap = ImmutableMap.of(
                                    "eta", eta,
                                    "min_child_weight", minChildWeight,
                                    "max_depth", treeDepth,
                                    "objective", "binary:logistic",
                                    "silent", 1
                            );
                            try {
                                String[] cv = XGBoost.crossValidation(trainData, paramsMap, numRounds, numFolds, null, null, null);
                                results.add(String.format("%s\t%s", paramsMap, cv[cv.length - 1]));
                            } catch (XGBoostError e) {
                                e.printStackTrace();
                            }
                        })
                )
        );
        System.out.println("\n" + ImmutableList.copyOf(results).stream().collect(Collectors.joining("\n")));
    }
}
