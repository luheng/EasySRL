package edu.uw.easysrl.qasrl.qg.surfaceform;

import edu.uw.easysrl.qasrl.qg.*;

import com.google.common.collect.ImmutableList;

/**
 * Most basic instance of QAPairSurfaceForm, with just the bare necessities.
 * Created by julianmichael on 3/17/16.
 */
public final class BasicQAPairSurfaceForm implements QAPairSurfaceForm {
    public int getSentenceId() {
        return sentenceId;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

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
