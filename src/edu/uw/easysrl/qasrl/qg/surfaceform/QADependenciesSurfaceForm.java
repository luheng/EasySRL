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

    public ImmutableSet<ResolvedDependency> getAllPossibleAnswerDependencies() {
        return allPossibleAnswerDeps;
    }

    public ImmutableSet<ImmutableSet<ResolvedDependency>> getAnswerDependencySets() {
        return answerDepSets;
    }

    private ImmutableSet<ResolvedDependency> questionDeps;
    private ImmutableSet<ResolvedDependency> allPossibleAnswerDeps;
    private ImmutableSet<ImmutableSet<ResolvedDependency>> answerDepSets;

    public QADependenciesSurfaceForm(int sentenceId,
                                     String question,
                                     String answer,
                                     ImmutableList<QuestionAnswerPair> qaPairs,
                                     ImmutableSet<ResolvedDependency> questionDeps,
                                     ImmutableSet<ImmutableSet<ResolvedDependency>> answerDepSets) {
        super(sentenceId, question, answer, qaPairs);
        this.questionDeps = questionDeps;
        this.answerDepSets = answerDepSets;
        ImmutableSet.Builder<ResolvedDependency> allPossibleAnswerDepsBuilder = new ImmutableSet.Builder<>();
        for(ImmutableSet<ResolvedDependency> answerDeps : answerDepSets) {
            allPossibleAnswerDepsBuilder.addAll(answerDeps);
        }
        this.allPossibleAnswerDeps = allPossibleAnswerDepsBuilder.build();
    }
}
