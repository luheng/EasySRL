package edu.uw.easysrl.qasrl.qg.surfaceform;

import edu.uw.easysrl.qasrl.qg.*;

import edu.uw.easysrl.dependencies.ResolvedDependency;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Most basic instance of QAPairSurfaceForm, with just the bare necessities.
 * Created by julianmichael on 3/17/16.
 */
public class QADependenciesSurfaceForm extends BasicQAPairSurfaceForm {
    public ImmutableSet<ResolvedDependency> getQuestionDependencies() {
        return questionDeps;
    }

    public ImmutableSet<ResolvedDependency> getAnswerDependencies() {
        return answerDeps;
    }

    private ImmutableSet<ResolvedDependency> questionDeps;
    private ImmutableSet<ResolvedDependency> answerDeps;

    public QADependenciesSurfaceForm(int sentenceId,
                                     String question,
                                     String answer,
                                     ImmutableList<QuestionAnswerPair> qaPairs,
                                     ImmutableSet<ResolvedDependency> questionDeps,
                                     ImmutableSet<ResolvedDependency> answerDeps) {
        super(sentenceId, question, answer, qaPairs);
        this.questionDeps = questionDeps;
        this.answerDeps = answerDeps;
    }
}
