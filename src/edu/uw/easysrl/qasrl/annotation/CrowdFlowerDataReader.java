package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.syntax.grammar.Category;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.DataLoader;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.QueryGeneratorBothWays;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;

/**
 * Created by luheng on 2/24/16.
 */
public class CrowdFlowerDataReader {

    public static List<AlignedAnnotation> readAggregatedCheckboxAnnotationFromFile(String filePath) throws IOException {
        FileReader fileReader = new FileReader(filePath);
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.withHeader().parse(fileReader);

        final List<List<InputReader.InputWord>> sentencesWords = new ArrayList<>();
        final List<Parse> goldParses = new ArrayList<>();
        DataLoader.readDevPool(sentencesWords, goldParses);
        final List<List<String>> sentencesLists = sentencesWords
            .stream()
            .map(sent -> sent.stream().map(iw -> iw.word).collect(Collectors.toList()))
            .collect(Collectors.toList());
        final List<String> sentences = sentencesLists
            .stream()
            .map(TextGenerationHelper::renderString)
            .collect(Collectors.toList());
        final Map<Integer, List<QuestionAnswerPairReduced>> allGoldQAPairs = new HashMap<>();

        List<RecordedCheckboxAnnotation> checkboxAnnotations = new ArrayList<>();
        for (CSVRecord record : records) {
            //System.out.println(record);

            if (record.get("_golden").equals("true")) {
                continue;
            }

            final int sentenceId = Integer.parseInt(record.get("sent_id"));
            final List<QuestionAnswerPairReduced> goldQAPairs;
            if(allGoldQAPairs.containsKey(sentenceId)) {
                goldQAPairs = allGoldQAPairs.get(sentenceId);
            } else {
                final List<Parse> goldParse = new LinkedList<>();
                goldParse.add(goldParses.get(sentenceId));
                final QueryGeneratorBothWays queryGen = new QueryGeneratorBothWays(sentenceId,
                                                                                   sentencesLists.get(sentenceId),
                                                                                   goldParse);
                goldQAPairs = queryGen.allQAPairs;
                allGoldQAPairs.put(sentenceId, goldQAPairs);
            }
            checkboxAnnotations.addAll(RecordedCheckboxAnnotation.loadFromCSVRecordCrowdFlower(record, goldQAPairs));
                                       // .stream()
                                       // .filter(anno -> !anno.answer.toLowerCase().contains("bad question."))
                                       // .collect(Collectors.toList()));
        }
        RecordedCheckboxAnnotation.printStats(checkboxAnnotations);
        List<Annotation> annotations = new ArrayList<>(checkboxAnnotations);

        System.out.println("Read " + annotations.size() + " annotation records.");

        // Align annotations.
        List<AlignedAnnotation> alignedAnnotations = AlignedAnnotation.getAlignedAnnotations(annotations);
        System.out.println("Constructed " + alignedAnnotations.size() + " aligned annotations.");
        int total = 0;
        int majorityCorrect = 0;
        int[] agreementCount = new int[6];
        Arrays.fill(agreementCount, 0);
        for(AlignedAnnotation annotation : alignedAnnotations) {
            if (annotation.getNumAnnotated() > 5) {
                System.out.println(annotation);
            }
            int chosenAnswer = 0;
            int maxAgree = 0;
            for(int i = 0; i < annotation.answerDist.length; i++) {
                int agr = annotation.answerDist[i];
                if(agr > maxAgree) {
                    chosenAnswer = i;
                    maxAgree = agr;
                }
            }
            agreementCount[maxAgree]++;

            total++;
            if(chosenAnswer == annotation.goldAnswerId) {
                majorityCorrect++;
            }
        }
        System.out.println("Accuracy of majority vote: " + ((double) majorityCorrect) / total);

        for (int i = 0; i < agreementCount.length; i++) {
            System.out.println(i + "\t" + agreementCount[i] + "\t" + 100.0 * agreementCount[i] / alignedAnnotations.size());
        }

        double[] iaa = computeAgreement(alignedAnnotations, 5 /* number of judgements per row */);
        InterAnnotatorAgreement.printKappa(iaa);
        double fleissKappa = InterAnnotatorAgreement.fleissKappa(checkboxAnnotations, 5);
        System.out.println(String.format("Fleiss's Kappa for 5 annotators: %.2f", fleissKappa));
        return alignedAnnotations;
    }

    public static List<AlignedAnnotation> readAggregatedAnnotationFromFile(String filePath) throws IOException {
        FileReader fileReader = new FileReader(filePath);
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.withHeader().parse(fileReader);

        List<Annotation> annotations = new ArrayList<>();
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
            String[] answers = record.get("answers").split("\n");
            annotation.answer = record.get("choice");
            annotation.answerStrings = new ArrayList<>();
            annotation.answerId = -1;
            for (int i = 0; i < answers.length; i++) {
                annotation.answerStrings.add(answers[i]);
                if (answers[i].equals(annotation.answer)) {
                    annotation.answerId = i;
                }
            }
            if (annotation.answerId < 0) {
                System.err.println(record);
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
        int[] agreementCount = new int[6];
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

        double[] iaa = computeAgreement(alignedAnnotations, 6 /* number of judgements per row */);
        InterAnnotatorAgreement.printKappa(iaa);
        return alignedAnnotations;
    }

    // _unit_id	_created_at	_golden	_id	_missed	_started_at	_tainted	_channel	_trust	_worker_id	_country
    // _region	_city	_ip	choice	comment	orig__golden	answers	choice_gold	choice_gold_reason	pred_head
    // pred_id	query_id	question	question_confidence	question_key	question_uncertainty
    // sent_id	sentence

    public static void main(String[] args) throws IOException {
        readAggregatedCheckboxAnnotationFromFile(args[0]);
        // readAggregatedAnnotationFromFile(args[0]);
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
            int numOptions = annotation.answerOptions.size();
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
