package edu.uw.easysrl.qasrl.annotation;

import java.io.IOException;
import java.util.*;
import java.util.stream.*;
import java.util.function.Function;

import edu.uw.easysrl.syntax.grammar.Category;

/**
 * Created by luheng on 2/13/16.
 */
public class AlignedAnnotation {
    Map<String, Integer> annotatorToAnswerId;
    Map<String, String> annotatorToComment;
    public int[] answerDist;
    public double[] answerTrust;

    public String annotationKey;
    public int goldAnswerId;
    public int sentenceId;
    public String sentenceString;
    public List<String> answerOptions;

    public int predicateId;
    public String question;

    // Current accuracy
    // double rerankF1, oracleF1, onebestF1;

    // Crowdflower computed stuff.
    // double trust;

    private AlignedAnnotation() {
        super();
    }

    AlignedAnnotation(Annotation annotation) {
        this();
        this.sentenceId = annotation.sentenceId;
        this.sentenceString = annotation.sentenceString;
        this.answerOptions = annotation.getAnswerOptions();
        this.goldAnswerId = annotation.getGoldAnswerId();
        this.annotationKey = annotation.getAnnotationKey();
        this.question = annotation.question;
        this.predicateId = annotation.predicateId;
        annotatorToAnswerId = new HashMap<>();
        annotatorToComment = new HashMap<>();
        answerDist = new int[answerOptions.size()];
        answerTrust = new double[answerOptions.size()];
        Arrays.fill(answerDist, 0);
        Arrays.fill(answerTrust, 0.0);
    }

    public Optional<Annotation> aggregate(Function<List<Integer>, Optional<Integer>> chooseAnswer, String strategyName) {
        if(getNumAnnotated() == 0) {
            return Optional.empty();
        }
        List<Integer> answerCounts = new ArrayList<>(answerDist.length);
        for(int i : answerDist) {
            answerCounts.add(i);
        }
        Optional<Integer> answerOpt = chooseAnswer.apply(answerCounts);
        if(!answerOpt.isPresent()) {
            return Optional.empty();
        }
        int answer = answerOpt.get();
        Annotation.BasicAnnotation ann = new Annotation.BasicAnnotation();
        ann.sentenceId = this.sentenceId;
        ann.sentenceString = this.sentenceString;
        ann.comment = strategyName + " of " + answerDist[answer] + " out of " + getNumAnnotated() + "annotators";
        ann.trust = answerTrust[answer];
        ann.annotatorId = strategyName;
        ann.answerOptions = this.answerOptions;
        ann.answerId = answer;
        ann.goldAnswerId = this.goldAnswerId;
        ann.annotationKey = this.annotationKey;
        return Optional.of(ann);
    }

    boolean addAnnotation(String annotator, Annotation annotation) {
        if(annotationKey.equals(annotation.getAnnotationKey())) {
            if(annotatorToAnswerId.containsKey(annotator)) {
                // we don't usually see an annotation by the same person more than once... but sometimes we do, and... oh well.
                return true;
                // other options include not allowing the responses to differ:
                // return annotatorToAnswerId.get(annotator).equals(annotation.getAnswerId());
                // or averaging the responses somehow? (probably not)
            } else {
                annotatorToAnswerId.put(annotator, annotation.getAnswerId());
                answerDist[annotation.getAnswerId()]++;
                answerTrust[annotation.getAnswerId()] += annotation.trust;
                if (annotation.comment != null && !annotation.comment.isEmpty()) {
                    annotatorToComment.put(annotator, annotation.comment);
                }
                return true;
            }
        } else {
            return false;
        }
    }

    public int getNumAnswers() {
        return answerOptions.size();
    }

    int getNumAnnotated() {
        return annotatorToAnswerId.size();
    }

    public static List<AlignedAnnotation> getAlignedAnnotations(Map<String, List<Annotation>> annotations) {
        Set<String> annotators = annotations.keySet();
        Map<String, AlignedAnnotation> alignedAnnotations = new HashMap<>();
        for (String annotator : annotators) {
            annotations.get(annotator).forEach(annotation -> {
                    String queryKey = annotation.getAnnotationKey();
                    if (!alignedAnnotations.containsKey(queryKey)) {
                        alignedAnnotations.put(queryKey, new AlignedAnnotation(annotation));
                    }
                    AlignedAnnotation alignedAnnotation = alignedAnnotations.get(queryKey);
                    boolean addSuccessful = alignedAnnotation.addAnnotation(annotator, annotation);
                    assert addSuccessful;
            });
        }
        return new ArrayList<>(alignedAnnotations.values());
    }

    public static List<AlignedAnnotation> getAlignedAnnotations(List<Annotation> annotations) {
        Map<String, AlignedAnnotation> alignedAnnotations = new HashMap<>();

        annotations.forEach(annotation -> {
                String queryKey = annotation.getAnnotationKey();
                if (!alignedAnnotations.containsKey(queryKey)) {
                    alignedAnnotations.put(queryKey, new AlignedAnnotation(annotation));
                }
                AlignedAnnotation alignedAnnotation = alignedAnnotations.get(queryKey);
                boolean addSuccessful = alignedAnnotation.addAnnotation(annotation.annotatorId, annotation);
                assert addSuccessful;
            });
        return new ArrayList<>(alignedAnnotations.values());
    }

    @Override
    public String toString() {
        // Number of iteration in user session.
        String result = annotationKey + "\n";
        for (int i = 0; i < answerOptions.size(); i++) {
            String match = "";
            for (int j = 0; j < answerDist[i]; j++) {
                match += "*";
            }
            if (i == goldAnswerId) {
                match += "G";
            }
            result += String.format("%-8s\t%d\t%s\n", match, i, answerOptions.get(i));
        }
        for (String annotator : annotatorToComment.keySet()) {
            result += annotator + ":\t" + annotatorToComment.get(annotator) + "\n";
        }
        result += "\n";
        return result;
    }

    public static List<AlignedAnnotation> getAllAlignedAnnotationsFromPilotStudy() {
        Map<String, List<Annotation>> annotations = new HashMap<>();
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
                annotations.put(annotator, new ArrayList<>(RecordedAnnotation.loadAnnotationRecordsFromFile(fileName)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return AlignedAnnotation.getAlignedAnnotations(annotations);
    }

    public static void main(String[] args) {
        Map<String, List<Annotation>> annotations = new HashMap<>();
        for (String fileName : args) {
            String[] info = fileName.split("/");
            String annotator = info[info.length - 1].split("_")[0];
            System.out.println(annotator);
            try {
                annotations.put(annotator, new ArrayList<>(RecordedAnnotation.loadAnnotationRecordsFromFile(fileName)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        List<AlignedAnnotation> alignedAnnotations = AlignedAnnotation.getAlignedAnnotations(annotations);
        alignedAnnotations.stream()
                .filter(r -> r.answerDist[r.goldAnswerId] == 4)
                .sorted((r1, r2) -> Integer.compare(r1.sentenceId, r2.sentenceId))
                .forEach(System.out::print);
    }
}
