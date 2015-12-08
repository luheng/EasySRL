package edu.uw.easysrl.syntax.evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * keys are separated by whitespaces: i.e. MAXCHART=5\tBEAM=0.3, etc.
 * Created by luheng on 12/7/15.
 */
public class ResultsTable {
    Map<String, List<Double>> results;
    List<String> orderedKeyList;

    public ResultsTable() {
        results = new HashMap<>();
        orderedKeyList = new ArrayList<>();
    }

    public void add(String key, double result) {
        if (!results.containsKey(key)) {
            results.put(key, new ArrayList<>());
            orderedKeyList.add(key);
        }
        results.get(key).add(result);
    }

    public void add(Results results) {
        add("F1", results.getF1());
        add("Precision",results.getPrecision());
        add("Recall",results.getRecall());
    }

    public void add(String keyPrefix, Results results) {
        add(keyPrefix + "\t" + "F1", results.getF1());
        add(keyPrefix + "\t" + "Precision",results.getPrecision());
        add(keyPrefix + "\t" + "Recall",results.getRecall());
    }

    public void addAll(String key, List<Double> otherResults) {
        if (otherResults != null) {
            if (!results.containsKey(key)) {
                results.put(key, new ArrayList<>());
                orderedKeyList.add(key);
            }
            results.get(key).addAll(otherResults);
        }
    }

    public void addAll(ResultsTable otherResults) {
        for (String key : otherResults.orderedKeyList) {
            addAll(key, otherResults.get(key));
        }
    }

    public void addAll(String keyPrefix, ResultsTable otherResults) {
        for (String key : otherResults.orderedKeyList) {
            addAll(keyPrefix + "\t" + key, otherResults.get(key));
        }
    }

    public List<Double> get(String key) {
        return results.get(key);
    }

    // TODO: need testing
    public List<Double> get(String[] partialKeys) {
        List<Double> gathered = new ArrayList<>();
        for (String key : orderedKeyList) {
            String keyStr = "\t" + key.trim() + "\t";
            boolean match = true;
            for (String pk : partialKeys) {
                if (!keyStr.contains("\t" + pk.trim() + "\t")) {
                    match = false;
                    break;
                }
            }
            if (match) {
                gathered.addAll(results.get(key));
            }
        }
        return gathered;
    }

    public double getMean(String key) {
        return results.containsKey(key) ? results.get(key).stream().mapToDouble(r->r).average().getAsDouble() : 0;
    }

    public double getStandardDeviation(String key) {
        if (!results.containsKey(key)) {
            return 0;
        }
        double mean = getMean(key);
        return Math.sqrt(results.get(key).stream().mapToDouble(r->(r-mean)*(r-mean)).average().getAsDouble());
    }

    public double getMean(String[] partialKeys) {
        List<Double> res = get(partialKeys);
        return res == null ? 0 : res.stream().mapToDouble(r->r).average().getAsDouble();
    }

    public Map<String, Double> getAveraged() {
        Map<String, Double> avg = new HashMap<>();
        for (String key : orderedKeyList) {
            avg.put(key, getMean(key));
        }
        return avg;
    }

    public void printAggregated() {
        orderedKeyList.forEach(key -> System.out.println(String.format("%s\t%.6f\t%.6f", key, getMean(key),
                getStandardDeviation(key))));
    }

    @Override
    public String toString() {
        // TODO: sort by key string
        String str = "";
        for (String key : orderedKeyList) {
            str += key;
            for (Double result : results.get(key)) {
                str += "\t"+ result;
            }
            str += "\n";
        }
        return str;
    }

}
