package edu.uw.easysrl.qasrl.experiments;

import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.annotation.ccgdev.AnnotationFileLoader;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import static edu.uw.easysrl.util.GuavaCollectors.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


public class InterAnnotatorAgreement {

    public static void computeInterAnnotatorAgreement(Map<Integer, List<AnnotatedQuery>> annotations) {
        int[] agreementCount = new int[6],
                strictAgreementCount = new int[6];
        Arrays.fill(agreementCount, 0);
        Arrays.fill(strictAgreementCount, 0);
        int numTotalAnnotations = 0;
        for  (int sentenceId : annotations.keySet()) {
            for (AnnotatedQuery annotation : annotations.get(sentenceId)) {
                numTotalAnnotations ++;


                int[] optionDist = new int[annotation.optionStrings.size()];
                Arrays.fill(optionDist, 0);
                annotation.responses.forEach(res -> res.forEach(r -> optionDist[r] ++));

                int maxAgree = 0;
                for (int agr : optionDist) {
                    maxAgree = Math.max(maxAgree, agr);
                }
                agreementCount[maxAgree] ++;
                final int strictAgreement = annotation.responses.stream()
                        .collect(Collectors.groupingBy(Function.identity()))
                        .values().stream()
                        .map(Collection::size)
                        .max(Integer::compare).get();
                strictAgreementCount[strictAgreement] ++;
            }
        }

        System.out.println("Agreement:");
        for (int i = 0; i < agreementCount.length; i++) {
            if (agreementCount[i] > 0) {
                System.out.println(String.format("%d\t%d\t%.2f%%", i, agreementCount[i],
                        100.0 * agreementCount[i] / numTotalAnnotations));
            }
        }
        System.out.println("Strict Agreement:");
        for (int i = 0; i < strictAgreementCount.length; i++) {
            if (strictAgreementCount[i] > 0) {
                System.out.println(String.format("%d\t%d\t%.2f%%", i, strictAgreementCount[i],
                        100.0 * strictAgreementCount[i] / numTotalAnnotations));
            }
        }
    }

    // sameSource: says whether the two arguments are the same,
    // so we avoid counting an annotator's agreement with him/herself.
    public static void computeCrossSourceInterAnnotatorAgreement(Map<Integer, List<AnnotatedQuery>> annotations1,
                                                                 Map<Integer, List<AnnotatedQuery>> annotations2,
                                                                 boolean sameSource) {
        // int numTotalAnnotations = 0;
        int numInstances = 0, numAgreedInstances = 0, numStrictlyAgreedInstances = 0;
        int numCheckboxInstances = 0, numAgreedCheckboxInstances = 0;

        // in case hiccups lead them to annotate different sets, like annotator miscounts
        ImmutableSet<Integer> bothAnnotationSentences = annotations1.keySet().stream()
            .filter(annotations2.keySet()::contains)
            .collect(toImmutableSet());

        // ImmutableSet<Integer> annotation1Only = annotations1.keySet().stream()
        //     .filter(i -> !bothAnnotationSentences.contains(i))
        //     .collect(toImmutableSet());

        // ImmutableSet<Integer> annotation2Only = annotations2.keySet().stream()
        //     .filter(i -> !bothAnnotationSentences.contains(i))
        //     .collect(toImmutableSet());

        // System.out.println(annotation1Only);
        // System.out.println();
        // System.out.println(annotation2Only);

        for(int sentenceId : bothAnnotationSentences) {
            List<AnnotatedQuery> sentenceAnnotations1 = annotations1.get(sentenceId);
            List<AnnotatedQuery> sentenceAnnotations2 = annotations2.get(sentenceId);
            for (AnnotatedQuery annotation1 : sentenceAnnotations1) {
                Optional<AnnotatedQuery> annotation2Opt = sentenceAnnotations2.stream()
                    .filter(anno2 -> anno2.questionString.equals(annotation1.questionString))
                    .findFirst();
                if(!annotation2Opt.isPresent()) {
                    continue;
                }
                AnnotatedQuery annotation2 = annotation2Opt.get();

                for(ImmutableList<Integer> response1 : annotation1.responses) {
                    for(ImmutableList<Integer> response2 : annotation2.responses) {
                        numInstances++;
                        if(response1.containsAll(response2) || response2.containsAll(response1)) {
                            numAgreedInstances++;
                        }
                        if(response1.containsAll(response2) && response2.containsAll(response1)) {
                            numStrictlyAgreedInstances++;
                        }
                        for(int i = 0; i < annotation1.optionStrings.size(); i++) {
                            numCheckboxInstances++;
                            if(response1.contains(i) == response2.contains(i)) {
                                numAgreedCheckboxInstances++;
                            }
                        }
                    }
                    numCheckboxInstances -= annotation1.optionStrings.size();
                    numAgreedCheckboxInstances -= annotation1.optionStrings.size();
                    if(sameSource) {
                        numInstances--;
                        numAgreedInstances--;
                        numStrictlyAgreedInstances--;
                    }
                }
            }
        }

        System.out.println("Agreement:");
        System.out.println(String.format("Total pair instances: %d", numInstances));
        System.out.println(String.format("Number agreed: %d (%.2f%%)",
                                         numAgreedInstances,
                                         100.0 * numAgreedInstances / numInstances));
        System.out.println(String.format("Number strictly agreed: %d (%.2f%%)",
                                         numStrictlyAgreedInstances,
                                         100.0 * numStrictlyAgreedInstances / numInstances));
        System.out.println(String.format("Total checkbox-pair instances: %d", numCheckboxInstances));
        System.out.println(String.format("Number checkbox agreed: %d (%.2f%%)",
                                         numAgreedCheckboxInstances,
                                         100.0 * numAgreedCheckboxInstances / numCheckboxInstances));
    }

    public static void main(String[] args) {
        // Map<Integer, List<AnnotatedQuery>> annotations = AnnotationFileLoader.loadBioinferUpwork();
        // computeInterAnnotatorAgreement(annotations);

        Map<Integer, List<AnnotatedQuery>> upworkAnnotations = AnnotationFileLoader.loadBioinferUpwork();
        Map<Integer, List<AnnotatedQuery>> crowdFlowerAnnotations = AnnotationFileLoader.loadBioinfer();
        System.out.println("Cross-source:");
        computeCrossSourceInterAnnotatorAgreement(upworkAnnotations, crowdFlowerAnnotations, false);
        System.out.println("Within CF:");
        computeCrossSourceInterAnnotatorAgreement(crowdFlowerAnnotations, crowdFlowerAnnotations, true);
        System.out.println("Within Upwork:");
        computeCrossSourceInterAnnotatorAgreement(upworkAnnotations, upworkAnnotations, true);
    }
}
