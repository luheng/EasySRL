package edu.uw.easysrl.qasrl;

import java.util.List;

/**
 * Created by luheng on 1/17/16.
 */
public abstract class ResponseSimulator {
    public abstract Response answerQuestion(QueryOld query, List<String> sentence, Parse goldParse);

    /**** return a single option id ****/
    public abstract int answerQuestion(GroupedQuery query, List<String> sentence, Parse goldParse);
}
