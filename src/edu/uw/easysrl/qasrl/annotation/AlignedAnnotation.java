package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.*;

/**
 * Created by luheng on 2/13/16.
 */
public class AlignedAnnotation extends RecordedAnnotation {
    Map<String, ImmutableList<Integer>> annotatorToAnswerIds;
    Map<String, String> annotatorToComment;
    public List<String> answerOptions;
    public int[] answerDist;
    public double[] answerTrust;

    AlignedAnnotation(RecordedAnnotation annotation) {
        super();
        this.iterationId = annotation.iterationId;
        this.sentenceId = annotation.sentenceId;
        this.sentenceString = annotation.sentenceString;
        this.predicateId = annotation.predicateId;
        this.argumentNumber = annotation.argumentNumber;
        this.predicateCategory = annotation.predicateCategory;
        this.predicateString = annotation.predicateString;
        this.questionId = annotation.questionId;
        this.question = annotation.question;
        this.optionStrings = annotation.optionStrings;
        this.answerIds = annotation.answerIds;
        this.goldAnswerIds = annotation.goldAnswerIds;
        annotatorToAnswerIds = new HashMap<>();
        annotatorToComment = new HashMap<>();
        answerDist = new int[optionStrings.size()];
        answerTrust = new double[optionStrings.size()];
        Arrays.fill(answerDist, 0);
        Arrays.fill(answerTrust, 0.0);
    }

    boolean addAnnotation(String annotator, RecordedAnnotation annotation) {
        // TODO: check options are the same.
        if (answerOptions == null) {
            answerOptions = annotation.optionStrings;
        }
        // Some annotation records may contain duplicates.
        if (this.isSameQuestionAs(annotation) && !annotatorToAnswerIds.containsKey(annotator)) {
            annotatorToAnswerIds.put(annotator, annotation.answerIds);
            annotation.answerIds.forEach(answerId -> {
                answerDist[answerId]++;
                answerTrust[answerId] += annotation.trust;
            });
            if (annotation.comment != null && !annotation.comment.isEmpty()) {
                annotatorToComment.put(annotator, annotation.comment);
            }
            return true;
        }
        return false;
    }

    int getNumAnnotated() {
        return annotatorToAnswerIds.size();
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

    public static List<AlignedAnnotation> getAlignedAnnotations(List<RecordedAnnotation> annotations) {
        Map<String, AlignedAnnotation> alignedAnnotations = new HashMap<>();

        annotations.forEach(annotation -> {
            String queryKey = "SID=" + annotation.sentenceId + "_PRED=" + annotation.predicateId + "_ARGNUM=" +
                    annotation.argumentNumber + "_Q=" + annotation.question;
            if (!alignedAnnotations.containsKey(queryKey)) {
                alignedAnnotations.put(queryKey, new AlignedAnnotation(annotation));
            }
            AlignedAnnotation alignedAnnotation = alignedAnnotations.get(queryKey);
            assert alignedAnnotation.isSameQuestionAs(annotation);
            alignedAnnotation.addAnnotation(annotation.annotatorId, annotation);
        });
        return new ArrayList<>(alignedAnnotations.values());
    }

    @Override
    public String toString() {
        // Number of iteration in user session.
        String result = "ITER=" + iterationId + "\n"
                + "SID=" + sentenceId + "\t" + sentenceString + "\n"
                + "PRED=" + predicateId + "\t" + predicateString + "\t" + predicateCategory + "." + argumentNumber + "\n"
                + "QID=" + questionId + "\t" + question + "\n";
        for (int i = 0; i < optionStrings.size(); i++) {
            String match = "";
            for (int j = 0; j < answerDist[i]; j++) {
                match += "*";
            }
            if (goldAnswerIds != null && goldAnswerIds.contains(i)) {
                match += "G";
            }
            result += String.format("%-8s\t%d\t%s\n", match, i, optionStrings.get(i));
        }
        for (String annotator : annotatorToComment.keySet()) {
            result += annotator + ":\t" + annotatorToComment.get(annotator) + "\n";
        }
        result += "\n";
        return result;
    }

    public static List<AlignedAnnotation> getAllAlignedAnnotationsFromPilotStudy() {
        Map<String, List<RecordedAnnotation>> annotations = new HashMap<>();
        String[] pilotAnnotationFiles = new String[] {
                "pilot_annotation/Julian_20160211-2216.txt",
                "pilot_annotation/Luke_20160212-1257.txt",
                "pilot_annotation/Mike_20160211-2125.txt",
                "pilot_annotation/Luheng_20160213-1722.txt"
        };
        for (String fileName : pilotAnnotationFiles) {
            String[] info = fileName.split("/");
            String annotator = info[info.length - 1].split("_")[0];
            System.out.println(annotator);
            try {
                annotations.put(annotator, RecordedAnnotation.loadAnnotationRecordsFromFile(fileName));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return AlignedAnnotation.getAlignedAnnotations(annotations, null);
    }

    public static void main(String[] args) {
        Map<String, List<RecordedAnnotation>> annotations = new HashMap<>();
        for (String fileName : args) {
            String[] info = fileName.split("/");
            String annotator = info[info.length - 1].split("_")[0];
            System.out.println(annotator);
            try {
                annotations.put(annotator, RecordedAnnotation.loadAnnotationRecordsFromFile(fileName));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        List<AlignedAnnotation> alignedAnnotations = AlignedAnnotation.getAlignedAnnotations(annotations, null);
        alignedAnnotations.stream()
                .filter(r -> r.answerDist[r.goldAnswerIds.get(0)] == 4)
                .sorted((r1, r2) -> Integer.compare(r1.sentenceId, r2.sentenceId))
                .forEach(System.out::print);
    }
}