package edu.uw.easysrl.qasrl.qg.surfaceform;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.qg.IQuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.syntax.grammar.Category;

/**
 * Stores a list of QuestionStructure and AnswerStructure.
 * Created by luheng on 3/19/16.
 */
public class QAStructureSurfaceForm implements QAPairSurfaceForm {

    public int getPredicateIndex() {
        return questionStructures.get(0).predicateIndex;
    }

    public Category getCategory() {
        return questionStructures.get(0).category;
    }

    public int getArgumentNumber() {
        return questionStructures.get(0).targetArgNum;
    }

    public ImmutableList<Integer> getArgumentIndices() {
        return answerStructures.get(0).argumentIndices;
    }

    public ImmutableList<QuestionStructure> getQuestionStructures() {
        return questionStructures;
    }

    public ImmutableList<AnswerStructure> getAnswerStructures() {
        return answerStructures;
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
    public ImmutableList<IQuestionAnswerPair> getQAPairs() {
        return qaPairs;
    }

    private final int sentenceId;
    private final String question;
    private final String answer;
    private final ImmutableList<IQuestionAnswerPair> qaPairs;

    private final ImmutableList<QuestionStructure> questionStructures;
    private final ImmutableList<AnswerStructure> answerStructures;

    public QAStructureSurfaceForm(int sentenceId,
                                  String question,
                                  String answer,
                                  ImmutableList<IQuestionAnswerPair> qaPairs,
                                  ImmutableList<QuestionStructure> questionStructures,
                                  ImmutableList<AnswerStructure> answerStructures) {
        this.sentenceId = sentenceId;
        this.question   =  question;
        this.answer     =  answer;
        this.qaPairs    =  qaPairs;
        this.questionStructures = questionStructures;
        this.answerStructures = answerStructures;
    }
}