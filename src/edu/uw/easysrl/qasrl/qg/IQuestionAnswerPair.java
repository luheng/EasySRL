package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.syntax.grammar.Category;

/**
 * The most basic kind of Question--Answer Pair.
 * A QuestionAnswerPair will be one of many produced by a single parse,
 * representing a question we can ask, its answer,
 * and any other auxiliary information that might be useful.
 *
 * Created by julianmichael on 3/17/2016.
 */
public interface IQuestionAnswerPair {
    int getSentenceId();
    int getParseId();
    int getPredicateIndex();
    Category getPredicateCategory();
    int getArgumentNumber();
    int getArgumentIndex();

    // questionMainIndex will be the predicate if we're asking a normal-style question,
    // and will be the argument if we're asking a flipped-style question.
    // public int getQuestionMainIndex();
    // public QuestionType getQuestionType();
    ImmutableSet<ResolvedDependency> getQuestionDependencies();
    ResolvedDependency getTargetDependency();
    ImmutableSet<ResolvedDependency> getAnswerDependencies();

    String getQuestion();
    String getAnswer();

    Parse getParse();
    double getParseScore();
}