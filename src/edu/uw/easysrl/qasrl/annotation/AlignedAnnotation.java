package edu.uw.easysrl.qasrl.annotation;

import java.io.IOException;
import java.util.*;

/**
 * Created by luheng on 2/13/16.
 */
public class AlignedAnnotation extends RecordedAnnotation {
    Map<String, Integer> annotatorToAnswerId;
    Map<String, String> annotatorToComment;
    int[] answerDist;

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
        this.answerStrings = annotation.answerStrings;
        this.answerId = annotation.answerId;
        this.goldAnswerId = annotation.goldAnswerId;
        annotatorToAnswerId = new HashMap<>();
        annotatorToComment = new HashMap<>();
        answerDist = new int[answerStrings.size()];
        Arrays.fill(answerDist, 0);
    }

    boolean addAnnotation(String annotator, RecordedAnnotation annotation) {
        // Some annotation records may contain duplicates.
        if (this.isSameQuestionAs(annotation) && !annotatorToAnswerId.containsKey(annotator)) {
            annotatorToAnswerId.put(annotator, annotation.answerId);
            answerDist[annotation.answerId] ++;
            if (annotation.comment != null && !annotation.comment.isEmpty()) {
                annotatorToComment.put(annotator, annotation.comment);
            }
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

    @Override
    public String toString() {
        // Number of iteration in user session.
        String result = "ITER=" + iterationId + "\n"
                + "SID=" + sentenceId + "\t" + sentenceString + "\n"
                + "PRED=" + predicateId + "\t" + predicateString + "\t" + predicateCategory + "." + argumentNumber + "\n"
                + "QID=" + questionId + "\t" + question + "\n";
        for (int i = 0; i < answerStrings.size(); i++) {
            String match = "";
            for (int j = 0; j < answerDist[i]; j++) {
                match += "*";
            }
            if (i == goldAnswerId) {
                match += "G";
            }
            result += String.format("%-8s\t%d\t%s\n", match, i, answerStrings.get(i));
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
                .filter(r -> r.answerDist[r.goldAnswerId] == 4)
                .sorted((r1, r2) -> Integer.compare(r1.sentenceId, r2.sentenceId))
                .forEach(System.out::print);
    }
}