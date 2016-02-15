package edu.uw.easysrl.qasrl.annotation;

import java.util.*;

/**
 * Created by luheng on 2/13/16.
 */
public class AlignedAnnotation extends RecordedAnnotation {
    Map<String, Integer> annotatorToAnswerId;
    int[] answerDist;

    AlignedAnnotation(RecordedAnnotation annotation) {
        super();
        this.iterationId = annotation.iterationId;
        this.sentenceId = annotation.sentenceId;
        this.predicateId = annotation.predicateId;
        this.argumentNumber = annotation.argumentNumber;
        this.predicateCategory = annotation.predicateCategory;
        this.questionId = annotation.questionId;
        this.question = annotation.question;
        this.answerStrings = annotation.answerStrings;
        this.answerId = annotation.answerId;
        this.goldAnswerId = annotation.goldAnswerId;
        annotatorToAnswerId = new HashMap<>();
        answerDist = new int[answerStrings.size()];
        Arrays.fill(answerDist, 0);
    }

    boolean addAnnotation(String annotator, RecordedAnnotation annotation) {
        // Some annotation records may contain duplicates.
        if (this.isSameQuestionAs(annotation) && !annotatorToAnswerId.containsKey(annotator)) {
            annotatorToAnswerId.put(annotator, annotation.answerId);
            answerDist[annotation.answerId] ++;
            return true;
        }
        return false;
    }

    int getNumAnnotated() {
        return annotatorToAnswerId.size();
    }

    public static List<AlignedAnnotation> getAlignedAnnotations(Map<String, List<RecordedAnnotation>> annotations,
                                                                Collection<String> annotators) {
        if (annotators == null) {
            annotators = annotations.keySet();
        }
        Map<String, AlignedAnnotation> alignedAnnotations = new HashMap<>();
        for (String annotator : annotators) {
            annotations.get(annotator).forEach(annotation -> {
                String queryKey = "SID=" + annotation.sentenceId + "_PRED=" + annotation.predicateId + "_ARGNUM=" +
                        annotation.argumentNumber + "_Q=" + annotation.question;
                if (!alignedAnnotations.containsKey(queryKey)) {
                    alignedAnnotations.put(queryKey, new AlignedAnnotation(annotation));
                }
                AlignedAnnotation alignedAnnotation = alignedAnnotations.get(queryKey);
                assert alignedAnnotation.isSameQuestionAs(annotation);
                alignedAnnotation.addAnnotation(annotator, annotation);
            });
        }
        return new ArrayList<>(alignedAnnotations.values());
    }
}