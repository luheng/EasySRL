package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.SynchronousQueue;

/**
 * Compute inter-annotator agreement.
 * Created by luheng on 2/13/16.
 */

public class InterAnnotatorAgreement {

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
        computeAgreement(annotations, null);
        computeAgreement(annotations, ImmutableSet.of("Julian", "Luke"));
        computeAgreement(annotations, ImmutableSet.of("Julian", "Mike"));
        computeAgreement(annotations, ImmutableSet.of("Luke", "Mike"));
    }

    /**
     * Compute inter-annotator agreement given (not necessarily aligned) annotation records from different people.
     * Cohen's Kappa:
     *      k = 1 - (1 - p_o) / (1 - p_e)
     *      p_o: Observed agreement among raters. p_o = #times_agreed_questions / #total_aligned_questions
     *      p_e: Chance agreement. p_e = \sum_q ( 1.0 / #options(q)) / #total_aligned_questions
     * @param annotations: A map from annotator_identifier to all the annotation records.
     * @param annotators: a subset of annotators.
     */
    public static void computeAgreement(Map<String, List<RecordedAnnotation>> annotations,
                                        Collection<String> annotators) {
        if (annotators == null) {
            annotators = annotations.keySet();
        }
        int numAnnotators = annotators.size();
        Map<String, AlignedAnnotation> alignedAnnotations = new HashMap<>();

        System.out.println("Number of annotators:\t" + numAnnotators);
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

        int numDoubleAnnotated = 0, numTripleAnnotated = 0, numAllAnnotated = 0;
        double doubleAgreement = .0, tripleAgreement = .0, doubleChanceAgreement = .0, tripleChanceAgreement = .0;
        for (AlignedAnnotation annotation : alignedAnnotations.values()) {
            if (annotation.getNumAnnotated() >= 2) {
                numDoubleAnnotated ++;
            }
            if (annotation.getNumAnnotated() >= 3) {
                numTripleAnnotated ++;
            }
            if (annotation.getNumAnnotated() == numAnnotators) {
                numAllAnnotated ++;
                int numOptions = annotation.answerStrings.size();
                doubleChanceAgreement += computeAgreementChance(numAnnotators, 2, numOptions);
                tripleChanceAgreement += computeAgreementChance(numAnnotators, 3, numOptions);
                for (int d : annotation.answerDist) {
                    if (d >= 2) {
                        doubleAgreement += 1.0;
                    }
                    if (d >= 3) {
                        tripleAgreement += 1.0;
                    }
                }
            }
        }
        doubleAgreement /= numAllAnnotated;
        tripleAgreement /= numAllAnnotated;
        doubleChanceAgreement /= numAllAnnotated;
        tripleChanceAgreement /= numAllAnnotated;
        //System.out.println(doubleChanceAgreement + ", " + tripleChanceAgreement);
        double kappaDouble = 1.0 - (1.0 - doubleAgreement) / (1.0 - doubleChanceAgreement);
        double kappaTriple = 1.0 - (1.0 - tripleAgreement) / (1.0 - tripleChanceAgreement);

        System.out.println("Number double annotated:\t" + numDoubleAnnotated
                + "\tnum triple annotated:\t" + numTripleAnnotated);
        System.out.println("Cohen\'s kappa:\t" + kappaDouble + " (double),\t" + kappaTriple + " (triple).");
    }

    private static double computeAgreementChance(int total, int agreement, int options) {
        if (total < agreement) {
            return 0.0;
        }
        if (agreement == 1) {
            return 1.0;
        }
        double p = 1.0 / options;
        return  p * computeAgreementChance(total - 1, agreement - 1, options)
                    + (1.0 - p) * computeAgreementChance(total - 1, agreement, options);
    }
}
