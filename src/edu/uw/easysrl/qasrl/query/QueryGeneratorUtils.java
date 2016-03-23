package edu.uw.easysrl.qasrl.query;

import com.google.common.collect.ImmutableCollection;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;

import java.util.Collection;

/**
 * Created by luheng on 3/22/16.
 */
public class QueryGeneratorUtils {

    public static String kBadQuestionOptionString = "Bad question.";
    public static String kUnlistedAnswerOptionString = "Answer is not listed.";

    public static double computeEntropy(Collection<Double> scores) {
        final double sum = scores.stream().mapToDouble(s -> s).sum();
        return scores.stream()
                .mapToDouble(s -> s / sum)
                .filter(p -> p > 0)
                .map(p -> p * Math.log(p))
                .sum() / Math.log(2);
    }
}
