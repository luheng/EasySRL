package edu.uw.easysrl.qasrl.qg.surfaceform;

import edu.uw.easysrl.qasrl.qg.*;

import com.google.common.collect.ImmutableList;

/**
 * Most basic instance of QAPairSurfaceForm, with just the bare necessities.
 * Created by julianmichael on 3/17/16.
 */
public final class BasicQAPairSurfaceForm implements QAPairSurfaceForm {
    @Override
    public int getSentenceId() {
        return sentenceId;
    }

    @Override
    public String getQuestion() {
        return question;
    }

    @Override
    public String getAnswer() {
        return answer;
    }

    @Override
    public ImmutableList<IQuestionAnswerPair> getQAPairs() {
        return qaPairs;
    }

    private final int sentenceId;
    private final String question;
    private final String answer;
    private final ImmutableList<IQuestionAnswerPair> qaPairs;

    public BasicQAPairSurfaceForm(int sentenceId,
                                  String question,
                                  String answer,
                                  ImmutableList<IQuestionAnswerPair> qaPairs) {
        this.sentenceId = sentenceId;
        this.question = question;
        this.answer = answer;
        this.qaPairs = qaPairs;
    }
}
