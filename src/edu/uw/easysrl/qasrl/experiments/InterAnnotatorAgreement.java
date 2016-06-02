package edu.uw.easysrl.qasrl.experiments;

import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.annotation.ccgdev.AnnotationFileLoader;


import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


public class InterAnnotatorAgreement {

    public static void main(String[] args) {
        Map<Integer, List<AnnotatedQuery>> annotations = AnnotationFileLoader.loadTest();
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
}
