package edu.uw.easysrl.qasrl.corpora;

import java.util.List;

/**
 * Created by luheng on 11/9/15.
 */
public interface AbstractAnswerAligner {
    List<Integer> align(List<String[]> answers, List<String> words);
}
