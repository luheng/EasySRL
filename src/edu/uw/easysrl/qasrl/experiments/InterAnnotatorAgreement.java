package edu.uw.easysrl.qasrl.experiments;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.annotation.Annotation;
import edu.uw.easysrl.qasrl.annotation.ccgdev.AnnotationFileLoader;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingConfig;
import edu.uw.easysrl.qasrl.experiments.ReparsingHelper;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.*;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.IntStream;

public class InterAnnotatorAgreement {

    public static void main(String[] args) {
        ParseData corpus;
        Map<Integer, List<AnnotatedQuery>> annotations = AnnotationFileLoader.loadDev();
        int[] agreementCount = new int[6],
                strictAgreementCount = new int[6];
        Arrays.fill(agreementCount, 0);
        Arrays.fill(strictAgreementCount, 0);
        int numTotalAnnotations = 0;
        for  (int sentenceId : annotations.keySet()) {
            for (AnnotatedQuery annotation : annotations.get(sentenceId)) {

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
