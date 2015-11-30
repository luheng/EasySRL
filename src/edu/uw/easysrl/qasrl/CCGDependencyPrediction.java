package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;

import edu.stanford.nlp.util.StringUtils;

/**
 * Created by luheng on 11/29/15.
 */
// TODO
public class CCGDependencyPrediction {
    private static Random random = new Random(12345);
    private static List<Integer> sentenceIndices = null;

    private static void jackknife(Map<Integer, List<CCGandQADependency>> allDependencies,
                                  List<CCGandQADependency> trainingDependencies,
                                  List<CCGandQADependency> testDependencies,
                                  double heldOutPortion,
                                  int heldOutFold) {
        if (sentenceIndices == null) {
            sentenceIndices = new ArrayList<>(allDependencies.keySet());
            Collections.shuffle(sentenceIndices, random);
        }
        int numSentences = sentenceIndices.size();
        int heldOutStartIdx = (int) Math.floor(heldOutPortion * heldOutFold * numSentences);
        int heldOutEndIdx = (int) Math.min(numSentences, heldOutPortion * (heldOutFold + 1) * numSentences);
        assert (heldOutStartIdx < numSentences);
        assert (trainingDependencies != null && testDependencies != null);
        trainingDependencies.clear();
        testDependencies.clear();
        for (int i = 0; i < numSentences; i++) {
            List<CCGandQADependency> dependencies = allDependencies.get(sentenceIndices.get(i));
            if (i < heldOutStartIdx || i >= heldOutEndIdx) {
                trainingDependencies.addAll(dependencies);
            } else {
                testDependencies.addAll(dependencies);
            }
        }
        System.out.println("training: " + trainingDependencies.size() + "\ttest: " + testDependencies.size());
    }

    private static void tryStuff(List<CCGandQADependency> mappedDependencies) {
        for (CCGandQADependency mappedDependency : mappedDependencies) {
            QADependency qa = mappedDependency.qaDependency;
            CCGBankDependencies.CCGBankDependency ccg = mappedDependency.ccgDependency;
            List<String> words = mappedDependency.sentence.getWords();
            System.out.println(StringUtils.join(words, " "));

            Category category = ccg.getCategory();
            int argNum = ccg.getArgNumber();
            String ccgInfo = category + "\t" +
                    String.format("%d/%d", argNum, category.getNumberOfArguments()) + "\t" +
                    category.getArgument(argNum);
            System.out.println(qa.toString(words) + "\n" + ccg.toString() + "\t" + ccgInfo + "\n");
        }
    }

    public static void main(String[] args) {
        final double[] sigmaSquaredValues = {0.01, 0.1, 1, 10, 100};
        List<Double> results = new ArrayList<>();
        Map<Integer, List<CCGandQADependency>> allDependencies = PropBankAligner.getCcgAndQADependencies();
        List<CCGandQADependency> trainingDependencies = new ArrayList<>(),
                                  testDependencies = new ArrayList<>();
        jackknife(allDependencies, trainingDependencies, testDependencies, 0.2, 0);
        tryStuff(trainingDependencies);
        /*
        for (double sigmaSquared : sigmaSquaredValues) {
            double avgAccuracy = .0;
            for (int i = 0; i < 5; i++) {
                jackknife(allDependencies, trainingDependencies, testDependencies, 0.2, i);
                double[] weights = train(trainingDependencies, sigmaSquared, new Util.Logger(new File("log")));
                evaluate(trainingDependencies, weights);
                avgAccuracy += evaluate(testDependencies, weights);
                System.out.println();
            }
            results.add(avgAccuracy / 5.0);
            //System.out.println(sigmaSquared + "\t" + avgAccuracy / 5.0);
        }
        for (int i = 0; i < sigmaSquaredValues.length; i++) {
            System.out.println(sigmaSquaredValues[i] + "\t" + results.get(i));
        }
        */
    }
}
