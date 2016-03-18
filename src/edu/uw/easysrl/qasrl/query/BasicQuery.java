package edu.uw.easysrl.qasrl.query;

import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import com.google.common.collect.ImmutableList;

/**
 * Most basic implementation of the QAPairSurfaceForm interface;
 * contains the bare necessities and nothing else.
 *
 * This class is for DATA, not LOGIC.
 *
 * Created by julianmichael on 3/17/16.
 */
public class BasicQuery<QA extends QAPairSurfaceForm> implements Query<QA> {

    public int getSentenceId() {
        return sentenceId;
    }

    public String getPrompt() {
        return prompt;
    }

    public ImmutableList<String> getOptions() {
        return options;
    }

    public ImmutableList<QA> getQAPairSurfaceForms() {
        return qaPairSurfaceForms;
    }

    private final int sentenceId;
    private final String prompt;
    private final ImmutableList<String> options;
    private final ImmutableList<QA> qaPairSurfaceForms;

    public BasicQuery(int sentenceId, String prompt, ImmutableList<String> options, ImmutableList<QA> qaPairSurfaceForms) {
        this.sentenceId = sentenceId;
        this.prompt = prompt;
        this.options = options;
        this.qaPairSurfaceForms = qaPairSurfaceForms;
    }
}
