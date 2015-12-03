package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.util.CountDictionary;

import java.util.List;
import java.util.Map;

/**
 * Data analysis for question generation using CCG dependencies.
 * Created by luheng on 12/3/15.
 */
public class QGDataAnalysis {

    public static void main(String[] args) {
        Map<Integer, List<CCGandQADependency>> data = PropBankAligner.getCcgAndQADependencies();
        CountDictionary ccgLabels = new CountDictionary(),
                        mappedCcgLabels = new CountDictionary(),
                        uniquelyMappedCcgLabels = new CountDictionary();
        for (int sentIdx : data.keySet()) {
            for (CCGandQADependency dep : data.get(sentIdx)) {
                CCGBankDependencies.CCGBankDependency ccgDep = dep.ccgDependency;
                if (ccgDep == null) {
                    continue;
                }
                String ccgInfo = String.format("%s_%d", ccgDep.getCategory(), ccgDep.getArgNumber());
                ccgLabels.addString(ccgInfo);
                if (dep.qaDependency != null) {
                    mappedCcgLabels.addString(ccgInfo);
                }
                if (dep.numCCGtoQAMaps == 1 && dep.numQAtoCCGMaps == 1) {
                    uniquelyMappedCcgLabels.addString(ccgInfo);
                }
            }
        }
        for (String ccgLabel : ccgLabels.getStrings()) {
            if (ccgLabels.getCount(ccgLabel) < 2) {
                continue;
            }
            System.out.println(ccgLabel + "\t" + ccgLabels.getCount(ccgLabel) + "\t" +
                    mappedCcgLabels.getCount(ccgLabel) + '\t' + uniquelyMappedCcgLabels.getCount(ccgLabel));
        }
    }

}
