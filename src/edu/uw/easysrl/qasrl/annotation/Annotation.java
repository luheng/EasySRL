package edu.uw.easysrl.qasrl.annotation;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Annotation record from a single annotator.
 * Created by luheng on 2/12/16.
 */
public abstract class Annotation {

    public int sentenceId;
    public String sentenceString;
    public String comment;
    public double trust;
    public String annotatorId;

    // TODO these fields don't really belong
    public String question;
    public int predicateId;

    protected Annotation() {}

    public Annotation(Annotation other) {
        this.sentenceId = other.sentenceId;
        this.sentenceString = other.sentenceString;
        this.comment = other.comment;
        this.trust = other.trust;
        this.annotatorId = other.annotatorId;
        this.question = other.question;
        this.predicateId = other.predicateId;
    }

    public abstract List<String> getAnswerOptions();

    public abstract int getAnswerId();

    public abstract int getGoldAnswerId();

    public abstract String getAnnotationKey();

    public boolean isSameQuestionAs(Annotation other) {
        return this.getAnnotationKey().equals(other.getAnnotationKey());
    }

    public int getNumAnswers() {
        return getAnswerOptions().size();
    }

    public static class BasicAnnotation extends Annotation {
        public List<String> answerOptions;
        public int answerId;
        public int goldAnswerId;
        public String annotationKey;

        BasicAnnotation() {}

        public BasicAnnotation(Annotation other) {
            super(other);
            this.answerOptions = other.getAnswerOptions();
            this.answerId = other.getAnswerId();
            this.goldAnswerId = other.getGoldAnswerId();
            this.annotationKey = other.getAnnotationKey();
        }

        public List<String> getAnswerOptions() {
            return answerOptions;
        }

        public int getAnswerId() {
            return answerId;
        }

        public int getGoldAnswerId() {
            return goldAnswerId;
        }

        public String getAnnotationKey() {
            return annotationKey;
        }
    }

}
