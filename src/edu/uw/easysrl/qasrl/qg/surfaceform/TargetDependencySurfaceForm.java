package edu.uw.easysrl.qasrl.qg.surfaceform;

import edu.uw.easysrl.qasrl.qg.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import com.google.common.collect.ImmutableList;

/**
 * For QA Pair surface forms that are exclusive to one target dependency.
 *
 * You can imagine surface forms have the same string questions and answers,
 * but have different target dependencies.
 *
 * You can also imagine surface forms that include multiple correct answers
 * in their "answer".
 *
 * This is neither of those.
 *
 * Created by julianmichael on 3/18/2016.
 */
public final class TargetDependencySurfaceForm implements QAPairSurfaceForm {

    public ResolvedDependency getTargetDependency() {
        return targetDep;
    }

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
    public ImmutableList<QuestionAnswerPair> getQAPairs() {
        return qaPairs;
    }

    private final int sentenceId;
    private final String question;
    private final String answer;
    private final ImmutableList<QuestionAnswerPair> qaPairs;

    private final ResolvedDependency targetDep;

    public TargetDependencySurfaceForm(int sentenceId,
                                       String question,
                                       String answer,
                                       ImmutableList<QuestionAnswerPair> qaPairs,
                                       ResolvedDependency targetDep) {
        this.sentenceId = sentenceId;
        this.question   =  question;
        this.answer     =  answer;
        this.qaPairs    =  qaPairs;
        this.targetDep  =  targetDep;
    }
}
