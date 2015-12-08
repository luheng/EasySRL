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

    public ResultsTable() {
        results = new HashMap<>();
    }

    public void add(String key, double result) {
        if (!results.containsKey(key)) {
            results.put(key, new ArrayList<>());
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
            }
            results.get(key).addAll(otherResults);
        }
    }

    public void addAll(ResultsTable otherResults) {
        for (String key : otherResults.results.keySet()) {
            addAll(key, otherResults.get(key));
        }
    }

    public void addAll(String keyPrefix, ResultsTable otherResults) {
        for (String key : otherResults.results.keySet()) {
            addAll(keyPrefix + "\t" + key, otherResults.get(key));
        }
    }

    public List<Double> get(String key) {
        return results.get(key);
    }

    public double getAverage(String key) {
        return results.containsKey(key) ? 0 : results.get(key).stream().mapToDouble(r->r).average().getAsDouble();
    }

    public List<Double> get(String[] partialKeys) {
        List<Double> gathered = new ArrayList<>();
        for (String key : results.keySet()) {
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

    public double getAveraged(String[] partialKeys) {
        List<Double> res = get(partialKeys);
        return res == null ? 0 : res.stream().mapToDouble(r->r).average().getAsDouble();
    }

    public Map<String, Double> getAveraged() {
        Map<String, Double> avg = new HashMap<>();
        for (String key : results.keySet()) {
            avg.put(key, getAverage(key));
        }
        return avg;
    }

    public void printAggregated() {
        Map<String, Double> avg = getAveraged();
        avg.keySet().forEach(key -> System.out.println(String.format("%s\t%.6f", key, avg.get(key))));
    }

    @Override
    public String toString() {
        String str = "";
        for (String key : results.keySet()) {
            str += key;
            for (Double result : results.get(key)) {
                str += "\t"+ result;
            }
            str += "\n";
        }
        return str;
    }

}
