package edu.uw.easysrl.qasrl.qg;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.TextGenerationHelper.TextWithDependencies;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;

public class QuestionAnswerPairReduced {
    public final int predicateIndex;
    public final Category predicateCategory;
    public final int questionMainIndex;
    public final QuestionType questionType;
    public final List<String> question;
    public final Set<ResolvedDependency> questionDeps;
    public final ResolvedDependency targetDep;
    public final List<String> answer;
    public final Set<ResolvedDependency> answerDeps;

    // these are lazily loaded by the render methods
    private String questionString = null;
    private String answerString = null;

    // questionMainIndex will be the predicate if we're asking a normal-style question,
    // and will be the argument if we're asking a flipped-style question.
    public QuestionAnswerPairReduced(int predicateIndex, Category predicateCategory,
                              int questionMainIndex, QuestionType questionType,
                              Set<ResolvedDependency> questionDeps, List<String> question,
                              ResolvedDependency targetDep, TextWithDependencies answer) {
        this.predicateIndex = predicateIndex;
        this.predicateCategory = predicateCategory;
        this.questionMainIndex = questionMainIndex;
        this.questionType = questionType;
        this.questionDeps = questionDeps;
        this.question = question;
        this.targetDep = targetDep;
        this.answer = answer.tokens;
        this.answerDeps = answer.dependencies;
    }

    public String renderQuestion() {
        if(questionString == null) {
            String str = TextGenerationHelper.renderString(question);
            if(!str.isEmpty()) {
                str = Character.toUpperCase(str.charAt(0)) + str.substring(1) + "?";
            }
            questionString = str;
        }
        return questionString;
    }

    public String renderAnswer() {
        if(answerString == null) {
            answerString = TextGenerationHelper.renderString(answer);
        }
        return answerString;
    }

    // sheesh, so much effort just for a sane ADT...
    public static interface QuestionType {}

    public static class SupersenseQuestionType implements QuestionType {
        public final MultiQuestionTemplate.Supersense supersense;
        public SupersenseQuestionType(MultiQuestionTemplate.Supersense supersense) {
            this.supersense = supersense;
        }
        public String toString() {
            return supersense.toString();
        }
    }

    public static class StandardQuestionType implements QuestionType {
        public final MultiQuestionTemplate.QuestionType type;
        public StandardQuestionType(MultiQuestionTemplate.QuestionType type) {
            this.type = type;
        }
        public String toString() {
            return type.toString();
        }
    }
}
