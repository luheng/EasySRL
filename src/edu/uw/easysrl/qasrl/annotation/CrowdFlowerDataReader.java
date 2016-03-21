package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Reads AlignedAnnotation from Crowdflower.
 * Created by luheng on 2/24/16.
 */
public class CrowdFlowerDataReader {

    private static int maxNumAnnotators = 10;

    /**
     *
     * @param filePath Annotation file.
     * @return A list of AlignedAnnotation objects (Aggregated annotations)
     * @throws IOException
     */
    public static List<AlignedAnnotation> readAggregatedAnnotationFromFile(String filePath) throws IOException {
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
            annotation.answers = ImmutableList.copyOf(record.get("choice").split("\n"));
            annotation.answerIds = IntStream.range(0, options.length)
                    .boxed()
                    .filter(id -> annotation.answers.contains(options[id]))
                    .collect(GuavaCollectors.toImmutableList());
            if (annotation.answerIds.size() == 0) {
                System.err.print("Unannotated:\t" + record);
            }
            annotation.goldAnswerIds = ImmutableList.of(-1);
            annotation.comment = record.get("comment");

            // Crowdflower stuff
            annotation.annotatorId = record.get("_worker_id");
            annotation.trust = Double.parseDouble(record.get("_trust"));
            annotations.add(annotation);
        }
        System.out.println("Read " + annotations.size() + " annotation records.");

        // Align and aggregated annotations.
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

        // TODO: recover IAA ..
        //double[] iaa = computeAgreement(alignedAnnotations, maxNumAnnotators);
        //InterAnnotatorAgreement.printKappa(iaa);
        return alignedAnnotations;
    }

    // _unit_id	_created_at	_golden	_id	_missed	_started_at	_tainted	_channel	_trust	_worker_id	_country
    // _region	_city	_ip	choice	comment	orig__golden	answers	choice_gold	choice_gold_reason	pred_head
    // pred_id	query_id	question	question_confidence	question_key	question_uncertainty
    // sent_id	sentence

    public static void main(String[] args) throws IOException {
        readAggregatedAnnotationFromFile(args[0]);
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
//                changeAgreement[i] += InterAnnotatorAgreement.computeAgreementChance(numAnnotators, i /* agreement */, numOptions);
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