package edu.uw.easysrl.qasrl.query;

import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import com.google.common.collect.ImmutableList;

/**
 * General interface that must be implemented by any query, i.e.,
 * an instance that we show to an annotator.
 *
 * This interface and subclasses of it should be for DATA, not LOGIC.
 *
 * TODO: We should probably add a boolean for checkbox/multiple choice.
 * Is there anything else we might do...?
 *
 * Created by julianmichael on 3/17/2016.
 */
public interface Query<QA extends QAPairSurfaceForm> {
    public int getSentenceId();
    public String getPrompt();
    public ImmutableList<String> getOptions();
}
