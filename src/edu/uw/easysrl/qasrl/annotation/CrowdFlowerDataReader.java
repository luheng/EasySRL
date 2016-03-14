package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.syntax.grammar.Category;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Created by luheng on 2/24/16.
 */
public class CrowdFlowerDataReader {

    private static int maxNumAnnotators = 5;

    public static List<AlignedAnnotation> readAggregatedAnnotationFromFile(String filePath, boolean isCheckbox)
            throws IOException {
        FileReader fileReader = new FileReader(filePath);
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.withHeader().parse(fileReader);

        List<RecordedAnnotation> annotations = new ArrayList<>();
        for (CSVRecord record : records) {
            //System.out.println(record);

            if (record.get("_golden").equals("true")) {
                continue;
            }

            // TODO: move this RecordedAnnotation.
            RecordedAnnotation annotation = new RecordedAnnotation();
            annotation.iterationId = -1; // unknown
            annotation.sentenceId = Integer.parseInt(record.get("sent_id"));
            annotation.sentenceString = record.get("sentence");
            annotation.predicateId = Integer.parseInt(record.get("pred_id"));
            annotation.predicateString = record.get("pred_head");
            String qkey = record.get("question_key");
            String[] qkeyInfo = qkey.split("\\.");
            annotation.predicateCategory = Category.valueOf(qkeyInfo[1]);
            annotation.argumentNumber = Integer.parseInt(qkeyInfo[2]);
            annotation.questionId = Integer.parseInt(record.get("query_id"));
            annotation.question = record.get("question");
            String[] options = record.get("answers").split("\n");
            Collections.addAll(annotation.optionStrings, options);

            if (!isCheckbox) {
                annotation.answer = record.get("choice");
                annotation.answerId = -1;
                for (int i = 0; i < options.length; i++) {
                    if (options[i].equals(annotation.answer)) {
                        annotation.answerId = i;
                        break;
                    }
                }
                if (annotation.answerId < 0) {
                    System.err.println(record);
                }
            } else {
                annotation.multiAnswers = new ArrayList<>();
                annotation.multiAnswerIds = new ArrayList<>();
                Collections.addAll(annotation.multiAnswers, record.get("choice").split("\n"));
                for (int i = 0; i < options.length; i++) {
                    if (annotation.multiAnswers.contains(options[i])) {
                        annotation.multiAnswerIds.add(i);
                    }
                }
                if (annotation.multiAnswerIds.size() == 0) {
                    System.err.print(record);
                }
            }
            annotation.goldAnswerId = -1;
            annotation.comment = record.get("comment");

            // Crowdflower stuff
            annotation.annotatorId = record.get("_worker_id");
            annotation.trust = Double.parseDouble(record.get("_trust"));
            annotations.add(annotation);
        }
        System.out.println("Read " + annotations.size() + " annotation records.");

        // Align annotations.
        List<AlignedAnnotation> alignedAnnotations = AlignedAnnotation.getAlignedAnnotations(annotations);
        System.out.println("Getting " + alignedAnnotations.size() + " aligned annotations.");
        int[] agreementCount = new int[maxNumAnnotators + 1];
        Arrays.fill(agreementCount, 0);
        alignedAnnotations.forEach(annotation -> {
            if (annotation.getNumAnnotated() > 5) {
                System.out.println(annotation);
            }
            int maxAgree = 0;
            for (int agr : annotation.answerDist) {
                maxAgree = Math.max(maxAgree, agr);
            }
            agreementCount[maxAgree] ++;
        });

        for (int i = 0; i < agreementCount.length; i++) {
            System.out.println(i + "\t" + agreementCount[i] + "\t" + 100.0 * agreementCount[i] / alignedAnnotations.size());
        }

        double[] iaa = computeAgreement(alignedAnnotations, maxNumAnnotators);
        InterAnnotatorAgreement.printKappa(iaa);
        return alignedAnnotations;
    }

    // _unit_id	_created_at	_golden	_id	_missed	_started_at	_tainted	_channel	_trust	_worker_id	_country
    // _region	_city	_ip	choice	comment	orig__golden	answers	choice_gold	choice_gold_reason	pred_head
    // pred_id	query_id	question	question_confidence	question_key	question_uncertainty
    // sent_id	sentence

    public static void main(String[] args) throws IOException {
        readAggregatedAnnotationFromFile(args[0], false /* checkbox */);
    }

    public static double[] computeAgreement(final List<AlignedAnnotation> alignedAnnotations, int maxNumAnnotators) {
        int[] annotationCount = new int[maxNumAnnotators + 1];
        double[] agreement = new double[maxNumAnnotators + 1];
        double[] changeAgreement = new double[maxNumAnnotators + 1];
        double[] kappa = new double[maxNumAnnotators + 1];

        Arrays.fill(annotationCount, 0);
        Arrays.fill(agreement, 0.0);
        Arrays.fill(changeAgreement, 0.0);
        double norm = .0;

        for (AlignedAnnotation annotation : alignedAnnotations) {
            for (int i = 2; i <= annotation.getNumAnnotated(); i++) {
                annotationCount[i]++;
            }
            int numAnnotators = annotation.getNumAnnotated();
            int numOptions = annotation.optionStrings.size();
            for (int i = 2; i <= numAnnotators; i++) {
                changeAgreement[i] += InterAnnotatorAgreement.computeAgreementChance(numAnnotators, i /* agreement */, numOptions);
            }
            boolean[] agreed = new boolean[numAnnotators + 1];
            Arrays.fill(agreed, false);
            for (int d : annotation.answerDist) {
                for (int i = 2; i <= d; i++) {
                    agreed[i] = true;
                }
            }
            for (int i = 2; i <= numAnnotators; i++) {
                if (agreed[i]) {
                    agreement[i] += 1.0;
                }
            }
            norm += 1.0;
        }
        for (int i = 2; i < kappa.length; i++) {
            agreement[i] /= norm;
            changeAgreement[i] /= norm;
            kappa[i] = 1.0 - (1.0 - agreement[i]) / (1.0 - changeAgreement[i]);
            //System.out.println(String.format("%d annotators\t%d-agreement\tKappa: %.3f%%\ton %d questions",
            //        numAnnotators, i, 100.0 * kappa[i], annotationCount[numAnnotators]));
        }
        return kappa;
    }

}
