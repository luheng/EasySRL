package edu.uw.easysrl.qasrl.analysis;

import edu.uw.easysrl.qasrl.Parse;


import java.util.Collection;
import java.util.List;

/**
 * Created by luheng on 3/3/16.
 */
public class AnalysisHelper {

    public static double getScore(final Collection<Parse> parseList) {
        return parseList.stream().mapToDouble(parse -> parse.score).sum();
    }
}
