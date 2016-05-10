package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.ScoredQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by luheng on 4/25/16.
 */
public class PatterDetector {


    /**
     *
     * @param query
     * @param sentence
     * @return
     */
    // TODO: arg ids should be on the same side of the predicate
    public static ImmutableList<ImmutableList<Integer>> getAppositives(final ScoredQuery<QAStructureSurfaceForm> query,
                                                                       final ImmutableList<String> sentence) {
        final int numQAOptions = query.getQAPairSurfaceForms().size();
        final String sentenceStr = sentence.stream().collect(Collectors.joining(" ")).toLowerCase();
        List<ImmutableList<Integer>> appositives = new ArrayList<>();
        for (int i = 0; i < numQAOptions; i++) {
            final String op1 = query.getOptions().get(i);
            if (op1.contains("_AND_")) {
                final String[] segs = op1.split(" _AND_ ");
                if (segs.length == 2 && (sentenceStr.contains(segs[0].toLowerCase() + " , " + segs[1].toLowerCase()) ||
                        sentenceStr.contains(segs[0].toLowerCase() + ". , " + segs[1].toLowerCase()))) {
                    appositives.add(ImmutableList.of(i));
                    continue;
                }
            }
            for (int j = 0; j < numQAOptions; j++) {
                final String op2 = query.getOptions().get(j);
                if (sentenceStr.contains(op1.toLowerCase() + " , " + op2.toLowerCase()) ||
                        sentenceStr.contains(op1.toLowerCase() + ". , " + op2.toLowerCase())) {
                    appositives.add(ImmutableList.of(i, j));
                }
            }
        }
        return ImmutableList.copyOf(appositives);
    }
}
