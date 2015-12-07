package edu.uw.easysrl.qasrl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * i.e. AlignedCorpus<SRLDependency, QADependency>
 * Created by luheng on 12/6/15.
 */
public class AlignedCorpus<T1, T2> {
    public Map<Integer, List<AlignedDependency<T1, T2>>> training = null;
    public Map<Integer, List<AlignedDependency<T1, T2>>> dev = null;
    public Map<Integer, List<AlignedDependency<T1, T2>>> test = null;

    public AlignedCorpus() {
        training = new HashMap<>();
        dev = new HashMap<>();
        test = new HashMap<>();
    }

    public void printAnalysis(Map<Integer, List<AlignedDependency<T1, T2>>> data) {
        int numD1 = 0;
        int numD2 = 0;
        int numMappings = 0;
        int numUniquelyMappedD1 = 0;
        int numUniquelyMappedD2 = 0;

        for (int sentIdx : data.keySet()) {
            for (AlignedDependency dep : data.get(sentIdx)) {
                if (dep.dependency2 != null) {
                    numD2 ++;
                }
                if (dep.dependency1 != null) {
                    numD1 ++;
                    if (dep.dependency2 != null) {
                        numMappings ++;
                    }
                    if (dep.d1ToHowManyD2 == 1) {
                        numUniquelyMappedD1 ++;
                    }
                    if (dep.d2ToHowManyD1 == 1) {
                        numUniquelyMappedD2 ++;
                    }
                }
            }
        }
        System.out.println(String.format("D1 coverage:\t%.3f%%", 100.0 * numMappings / numD1));
        System.out.println(String.format("D2 coverage:\t%.3f%%", 100.0 * numMappings / numD2));
        System.out.println(String.format("D1 purity:\t%.3f%%", 100.0 * numUniquelyMappedD1 / numMappings));
        System.out.println(String.format("D2 purity:\t%.3f%%", 100.0 * numUniquelyMappedD2 / numMappings));
    }

    public void printAnalysis() {
        System.out.println("[train]");
        printAnalysis(training);
        System.out.println("[dev]");
        printAnalysis(dev);
    }
}


