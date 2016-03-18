package edu.uw.easysrl.qasrl.analysis;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.pomdp.POMDP;
import edu.uw.easysrl.syntax.evaluation.Results;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Compute the following
 * 1. Attachment accuracy: F1 conditioned on label correct.
 * 2. Label accuracy: F1 conditioned on attachment correct.
 * 3. F1 conditioned on undirected attachment correct.
 * Created by luheng on 3/17/16.
 */

public class BaselineAnalysis {
    static Map<String, Results> attachmentAccuracy;
    static Table<String, String, Integer> labelConfusion;

    public static void main(String[] args) {
        POMDP learner = new POMDP(100 /* nbest */, 10000 /* horizon */, 0.0 /* money penalty */);
        attachmentAccuracy = new HashMap<>();
        labelConfusion = HashBasedTable.create();

        for (int sid : learner.allParses.keySet()) {
            Parse parse = learner.allParses.get(sid).get(0);
            Parse gold = learner.goldParses.get(sid);

            Table<String, Integer, Boolean> predDeps = HashBasedTable.create(),
                                            goldDeps = HashBasedTable.create();
            Table<Integer, Integer, String> predDeps2 = HashBasedTable.create(),
                                            goldDeps2 = HashBasedTable.create();

            parse.dependencies.forEach(dep -> {
                String label = dep.getCategory() + "." + dep.getArgNumber();
                String qkey = dep.getHead() + "\t" + label;
                predDeps.put(qkey, dep.getArgument(), Boolean.TRUE);
                predDeps2.put(dep.getHead(), dep.getArgument(), label);
            });
            gold.dependencies.forEach(dep -> {
                String label = dep.getCategory() + "." + dep.getArgNumber();
                String qkey = dep.getHead() + "\t" + label;
                goldDeps.put(qkey, dep.getArgument(), Boolean.TRUE);
                goldDeps2.put(dep.getHead(), dep.getArgument(), label);
            });

            for (String qkey : predDeps.rowKeySet()) {
                if (!goldDeps.containsRow(qkey)) {
                    continue;
                }
                String label = qkey.split("\\t")[1];
                Set<Integer> predArgs = predDeps.row(qkey).keySet(), goldArgs = goldDeps.row(qkey).keySet();
                Results results = new Results(predArgs.size(),
                        (int) predArgs.stream().filter(goldArgs::contains).count(), goldArgs.size());
                if (!attachmentAccuracy.containsKey(label)) {
                    attachmentAccuracy.put(label, results);
                } else {
                    attachmentAccuracy.get(label).add(results);
                }
            }
            for (int head : predDeps2.rowKeySet()) {
                if (!goldDeps2.containsRow(head)) {
                    continue;
                }
                Set<Integer> predArgs = predDeps2.row(head).keySet(), goldArgs = goldDeps2.row(head).keySet();
                predArgs.stream().filter(goldArgs::contains).forEach(arg -> {
                    String predLabel = predDeps2.get(head, arg), goldLabel = goldDeps2.get(head, arg);
                    int count = labelConfusion.contains(goldLabel, predLabel) ?
                            labelConfusion.get(goldLabel, predLabel) : 0;
                    labelConfusion.put(goldLabel, predLabel, count + 1);
                });
            }
        }

        // Print attachment accuracy.
        attachmentAccuracy.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(-e1.getValue().getFrequency(), -e2.getValue().getFrequency()))
                .forEach(e -> {
                    final Results r = e.getValue();
                    System.out.println(
                            String.format("%-20s\t%.3f\t%.3f\t%.3f\t%d", e.getKey(), r.getPrecision(), r.getRecall(),
                                    r.getF1(), r.getFrequency()));
                });

        // Print label confusion.
        labelConfusion.cellSet().stream()
                .filter(c -> !c.getRowKey().equals(c.getColumnKey()))
                .sorted((c1, c2) -> Integer.compare(-c1.getValue(), -c2.getValue()))
                .forEach(c -> {
                    int freq = c.getValue();
                    int goldFreq = labelConfusion.row(c.getRowKey()).entrySet().stream().mapToInt(Entry::getValue).sum();
                    System.out.println(
                            String.format("%-20s\t%-20s\t%d\t%.3f%%", c.getRowKey(), c.getColumnKey(), freq,
                                    100.0 * freq / goldFreq));
                });
    }
}
