package edu.uw.easysrl.qasrl;

import java.util.List;

/**
 * Created by luheng on 1/17/16.
 */
public abstract class ResponseSimulator {
    public abstract Response answerQuestion(Query query, List<String> sentence, Parse goldParse);
}
