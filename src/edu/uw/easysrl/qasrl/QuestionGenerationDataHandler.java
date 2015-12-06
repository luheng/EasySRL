package edu.uw.easysrl.qasrl;
;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.util.CountDictionary;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.util.StringUtils;

/**
 * Data analysis for question generation using CCG dependencies.
 * Created by luheng on 12/3/15.
 */
public class QuestionGenerationDataHandler {

    private static void jackknife() {

    }

    // Data format example:
    // line #1: <s> Pat <v> built </v> a <a> robot </a> </s> what was built ?
    // line #2: (S[dcl]\NP)/NP 2
    private static void outputEncoderDecoderTrainingData(File outputFile, List<AlignedDependency> dependencies)
            throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
        int lineCounter = 0;
        for (AlignedDependency dependency : dependencies) {
            CCGBankDependency ccg = (CCGBankDependency) dependency.dependency1;
            QADependency qa = (QADependency) dependency.dependency2;
            if (ccg == null || qa == null) {
                continue;
            }
            List<String> words = dependency.sentence.getWords();
            int predIdx = ccg.getSentencePositionOfPredicate();
            int argIdx = ccg.getSentencePositionOfArgument();
            String line = "<s>";
            for (int idx = 0; idx < words.size(); idx++) {
                if (idx == predIdx) {
                    line += String.format(" <v> %s </v>", words.get(idx));
                } else if (idx == argIdx) {
                    line += String.format(" <a> %s </a>", words.get(idx));
                } else {
                    line += " " + words.get(idx);
                }
            }
            line += " </s> " + StringUtils.join(qa.getQuestion(), " ").toLowerCase() + " ?\n";
            line += ccg.getCategory() + " " + ccg.getArgNumber() + "\n";
            writer.write(line);
            lineCounter ++;
            //System.out.print(line);
        }
        writer.close();
        System.out.println(String.format("Writing %d lines to %s.", lineCounter, outputFile.toString()));
    }

    // TODO: compute coverage and purity
    private static void analyzeData(Map<Integer, List<AlignedDependency>> data) {
        CountDictionary ccgLabels = new CountDictionary(),
                mappedCcgLabels = new CountDictionary(),
                uniquelyMappedCcgLabels = new CountDictionary();
        for (int sentIdx : data.keySet()) {
            for (AlignedDependency dep : data.get(sentIdx)) {
                CCGBankDependency ccgDep = (CCGBankDependency) dep.dependency1;
                if (ccgDep == null) {
                    continue;
                }
                String ccgInfo = String.format("%s_%d", ccgDep.getCategory(), ccgDep.getArgNumber());
                ccgLabels.addString(ccgInfo);
                if (dep.dependency2 != null) {
                    mappedCcgLabels.addString(ccgInfo);
                }
                if (dep.numD1toD2 == 1 && dep.numD2toD1 == 1) {
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

    public static void main(String[] args) {
        Map<Integer, List<AlignedDependency<CCGBankDependency, QADependency>>> training =
                PropBankAligner.getCcgAndQADependencies();
        System.out.println(training.size());
        Map<Integer, List<AlignedDependency<CCGBankDependency, QADependency>>> dev =
                PropBankAligner.getCcgAndQADependenciesDev();
        System.out.println(dev.size());
        List<AlignedDependency> trainingList = new ArrayList<>(), devList = new ArrayList<>();
        for (int sentIdx : training.keySet()) {
            trainingList.addAll(training.get(sentIdx));
        }
        for (int sentIdx : dev.keySet()) {
            devList.addAll(dev.get(sentIdx));
        }
        File outputFile1 = new File("ccg_qg.training.txt"),
             outputFile2 = new File("ccg_qg.dev.txt");
        try {
            outputEncoderDecoderTrainingData(outputFile1, trainingList);
            outputEncoderDecoderTrainingData(outputFile2, devList);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
