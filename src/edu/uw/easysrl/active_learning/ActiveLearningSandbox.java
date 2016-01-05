package edu.uw.easysrl.active_learning;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.main.InputReader.InputWord;

import java.util.ArrayList;
import java.util.List;

/**
 * Active Learning experiments.
 * Created by luheng on 1/5/16.
 */
public class ActiveLearningSandbox {
    // Training pool.
    static List<List<InputWord>> sentences;
    static List<CCGBankDependencies.DependencyParse> goldParses;

    private static void initialize() {
        sentences = new ArrayList<>();
        goldParses = new ArrayList<>();
        ActiveLearningInputReader.readDevPool(sentences, goldParses);
    }

    public static void main(String[] args) {
        initialize();
    }
}
