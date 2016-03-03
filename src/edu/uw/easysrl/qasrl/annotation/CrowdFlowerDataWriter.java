package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.pomdp.POMDP;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Group query by sentences.
 * Created by luheng on 2/9/16.
 */
public class CrowdFlowerDataWriter {
    static final int nBest = 100;
    static final int maxNumSentences = 100;
    static final int maxNumSentencesPerFile = 50;
    static final int numRandomSamples = 10;
    static final int randomSeed = 104743;

    static final int countEvery = 100;
    private static final double minQuestionConfidence = 0.05;
    private static final double minAnswerEntropy = 0.05;

    private static boolean highlightPredicate = false;
    private static boolean skipBinaryQueries = true;
    private static final int maxNumOptionsPerQuestion = 8;
    static {
        GroupedQuery.maxNumNonNAOptionsPerQuery = maxNumOptionsPerQuestion - 2;
    }

    // Fields for Crowdflower test questions.
    private static final String[] csvHeader = {
            "query_id", "question_confidence", "question_uncertainty", "sent_id", "sentence", "pred_id", "pred_head",
            "question_key", "question", "answers", "_golden ", "choice_gold", "choice_gold_reason"};

    private static final String answerDelimiter = " ### ";

    private static final String csvOutputFilePrefix = "./Crowdflower_round2/crowdflower_dev_100best";

    // Sentences that happened to appear in instructions ...
    private static final int[] otherHeldOutSentences = { 1695, };

    public static void main(String[] args) throws IOException {
        POMDP learner = new POMDP(nBest, 10000 /* horizon */, 0.0 /* money penalty */);
        Set<Integer> heldOutSentences = new HashSet<>();
        // Print test questions.
        try {
            printTestQuestions(learner, heldOutSentences);
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<double[]> avgNumQueries = new ArrayList<>(), avgOptionsPerQuery = new ArrayList<>(),
                       avgNumBinaryQueries = new ArrayList<>(),
                       oneBestF1 = new ArrayList<>(), rerankF1 = new ArrayList<>(), oracleF1 = new ArrayList<>(),
                       gainF1 = new ArrayList<>();

        Random random = new Random(randomSeed);
        List<Integer> sentenceIds = learner.allParses.keySet().stream()
                .filter(sid -> !heldOutSentences.contains(sid))
                .collect(Collectors.toList());

        for (int i = 1; i < sentenceIds.size(); i++) {
            if (i % countEvery == 0) {
                avgOptionsPerQuery.add(new double[numRandomSamples]);
                avgNumQueries.add(new double[numRandomSamples]);
                avgNumBinaryQueries.add(new double[numRandomSamples]);
                oneBestF1.add(new double[numRandomSamples]);
                rerankF1.add(new double[numRandomSamples]);
                oracleF1.add(new double[numRandomSamples]);
                gainF1.add(new double[numRandomSamples]);
            }
        }

        assert maxNumSentences % maxNumSentencesPerFile == 0;
        CSVPrinter csvPrinter = null;

        for (int r = 0; r < numRandomSamples; r++) {
            Collections.shuffle(sentenceIds, random);
            int lineCounter = 0;
            int fileCounter = 0;
            int queryCounter = 0;
            int optionCounter = 0;

            // Queries that has only one option except for N/A.
            int numBinaryQueries = 0;
            List<Integer> annotatedSentences = new ArrayList<>();
            Results rerank = new Results(), oracle = new Results(), oneBest = new Results();

            // Process questions.
            for (int sentenceId : sentenceIds) {
                learner.initializeForSentence(sentenceId);
                List<GroupedQuery> queries = learner.getQueryPool().stream()
                        .filter(query -> query.answerEntropy > minAnswerEntropy
                                            && query.questionConfidence > minQuestionConfidence
                                            && (!skipBinaryQueries || query.attachmentUncertainty > 1e-6))
                        .collect(Collectors.toList());
                // Print query to .csv file.
                if (r == 0 && annotatedSentences.size() < maxNumSentences) {
                    int numSentences = annotatedSentences.size();
                    if (numSentences <= maxNumSentences && numSentences % maxNumSentencesPerFile == 0) {
                        if (lineCounter > 0) {
                            csvPrinter.close();
                        }
                        if (numSentences < maxNumSentences) {
                            csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                                    String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter))),
                                    CSVFormat.EXCEL.withRecordSeparator("\n"));
                            csvPrinter.printRecord((Object[]) csvHeader);
                            fileCounter ++;
                        }
                    }
                    for (GroupedQuery query : queries) {
                        printQueryToCSVFile(query, -1 /* gold option id */, lineCounter, csvPrinter);
                        lineCounter ++;
                    }
                }
                for (GroupedQuery query : queries) {
                    int oracleId = learner.getOracleParseId(sentenceId);
                    Response oracleResponse = new Response();
                    for (int i = 0; i < query.getAnswerOptions().size(); i++) {
                        if (query.getAnswerOptions().get(i).getParseIds().contains(oracleId)) {
                            oracleResponse.add(i);
                        }
                    }
                    if (r == 0 && lineCounter < 100) {
                       // System.out.println("SID=" + sentenceId);
                        //System.out.println(sentence.stream().collect(Collectors.joining(" ")));
                        System.out.println("OracleID=" + learner.getOracleParseId(sentenceId));
                        System.out.println(query.getDebuggingInfo(oracleResponse));
                    }
                    learner.receiveObservationForQuery(query, oracleResponse);
                    optionCounter += query.getAnswerOptions().size();
                    if (query.attachmentUncertainty < 1e-6) {
                        numBinaryQueries ++;
                    }
                }
                queryCounter += queries.size();
                annotatedSentences.add(sentenceId);
                rerank.add(learner.getRerankedF1(sentenceId));
                oracle.add(learner.getOracleF1(sentenceId));
                oneBest.add(learner.getOneBestF1(sentenceId));

                if (annotatedSentences.size() % countEvery == 0) {
                    int k = annotatedSentences.size() / countEvery - 1;
                    avgNumQueries.get(k)[r] = queryCounter;
                    avgNumBinaryQueries.get(k)[r] = numBinaryQueries;
                    avgOptionsPerQuery.get(k)[r] = 1.0 * optionCounter / queryCounter;
                    oneBestF1.get(k)[r] = 100.0 * oneBest.getF1();
                    rerankF1.get(k)[r]  = 100.0 * rerank.getF1();
                    oracleF1.get(k)[r]  = 100.0 * oracle.getF1();
                    gainF1.get(k)[r]    = rerankF1.get(k)[r] - oneBestF1.get(k)[r];
                }
            }
        }
        // Print aggregated results.
        for (int k = 0; k < avgNumQueries.size(); k++) {
            System.out.println(String.format("On %d sentences:", (k + 1) * 100));
            System.out.println(String.format("Avg. number of queries:\t%.3f (%.3f)",
                    getAverage(avgNumQueries.get(k)), getStd(avgNumQueries.get(k))));
            System.out.println(String.format("Avg. number of binary queries:\t%.3f (%.3f)",
                    getAverage(avgNumBinaryQueries.get(k)), getStd(avgNumBinaryQueries.get(k))));
            System.out.println(String.format("Avg. number of options per query:\t%.3f (%.3f)",
                    getAverage(avgOptionsPerQuery.get(k)), getStd(avgOptionsPerQuery.get(k))));
            System.out.println(String.format("Avg. 1-best F1:\t%.3f%%\t%.3f%%",
                    getAverage(oneBestF1.get(k)), getStd(oneBestF1.get(k))));
            System.out.println(String.format("Avg. rerank F1:\t%.3f%%\t%.3f%%",
                    getAverage(rerankF1.get(k)), getStd(rerankF1.get(k))));
            System.out.println(String.format("Avg. oracle F1:\t%.3f%%\t%.3f%%",
                    getAverage(oracleF1.get(k)), getStd(oracleF1.get(k))));
            System.out.println(String.format("Avg. F1 gain  :\t%.3f%%\t%.3f%%",
                    getAverage(gainF1.get(k)), getStd(gainF1.get(k))));
        }
    }

    private static void printTestQuestions(POMDP learner, Set<Integer> heldOutSentences) throws IOException {
        List<AlignedAnnotation> pilotAnnotations = AlignedAnnotation.getAllAlignedAnnotationsFromPilotStudy();
        pilotAnnotations.stream().forEach(a -> heldOutSentences.add(a.sentenceId));
        for (int sid : otherHeldOutSentences) {
            heldOutSentences.add(sid);
        }
        // Extract high-agreement questions.
        List<AlignedAnnotation> agreedAnnotations = pilotAnnotations.stream()
                .filter(annot -> {
                    int numJudgements = annot.getNumAnnotated();
                    int numOptions = annot.answerDist.length;
                    return numOptions > 3 && numJudgements >= 3 && annot.answerDist[annot.goldAnswerId] == numJudgements;
                }).collect(Collectors.toList());

        System.out.println("Number of held-out sentences:\t" + heldOutSentences.size());
        System.out.println("Number of high-agreement annotations:\t" + agreedAnnotations.size());

        // Initialize CSV printer.
        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                String.format("%s_test.csv", csvOutputFilePrefix))), CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) csvHeader);

        // Write test questions.
        int numTestQuestions = 0;
        for (AlignedAnnotation test : agreedAnnotations) {
            int sentenceId = test.sentenceId;
            String goldAnswer = test.answerStrings.get(test.goldAnswerId).replace(" # ", " _AND_ ");
            learner.initializeForSentence(sentenceId);
            List<GroupedQuery> queries = learner.getQueryPool();
            for (GroupedQuery query : queries) {
                if (query.getPredicateIndex() == test.predicateId &&
                        query.getQuestion().equalsIgnoreCase(test.question)) {
                    int goldAnswerId = -1;
                    for (int i = 0; i < query.getAnswerOptions().size(); i++) {
                        GroupedQuery.AnswerOption ao = query.getAnswerOptions().get(i);
                        if (ao.getAnswer().equalsIgnoreCase(goldAnswer) ||
                                (GroupedQuery.BadQuestionOption.class.isInstance(ao) &&
                                        goldAnswer.startsWith("Question is not"))) {
                            goldAnswerId = i;
                            break;
                        }
                    }
                    if (goldAnswerId >= 0) {
                        printQueryToCSVFile(query, goldAnswerId, 10000 + numTestQuestions /* lineCounter */,
                                csvPrinter);
                        numTestQuestions ++;
                    } else {
                        System.err.println(test.toString() + "\n---\n" + query.getDebuggingInfo(new Response(-1)));
                    }
                }
            }
        }
        System.out.println("Wrote " + numTestQuestions + " test questions to file.");
        csvPrinter.close();
    }

    /**
     *
     * @param query
     * @param goldAnswerId: -1 if gold is unknown (means this line is not a test question).
     * @param lineCounter
     * @param csvPrinter
     */
    private static void printQueryToCSVFile(final GroupedQuery query, int goldAnswerId, int lineCounter,
                                            final CSVPrinter csvPrinter) throws IOException {
        // Print to CSV files.
        // "query_id", "question_confidence", "question_uncertainty", "sent_id", "sentence", "pred_id", "pred_head",
        // "question_key", "question", "answers", "_golden ", "choice_gold", "choice_gold_reason"
        int predicateIndex = query.getPredicateIndex();
        int sentenceId = query.getSentenceId();
        final List<String> sentence = query.getSentence();
        final String sentenceStr = TextGenerationHelper.renderHTMLSentenceString(sentence, predicateIndex,
                highlightPredicate);
        final List<String> answerStrings = query.getAnswerOptions().stream()
                .map(ao -> ao.getAnswer()) //.replace(" &&& ", " <strong> & </strong> "))
                .collect(Collectors.toList());
        List<String> csvRow = new ArrayList<>();
        csvRow.add(String.valueOf(lineCounter));
        csvRow.add(String.format("%.3f", query.questionConfidence));
        csvRow.add(String.format("%.3f", query.attachmentUncertainty));
        csvRow.add(String.valueOf(sentenceId));
        csvRow.add(String.valueOf(sentenceStr));
        csvRow.add(String.valueOf(predicateIndex));
        csvRow.add(sentence.get(predicateIndex));
        csvRow.add(query.getQuestionKey());
        csvRow.add(query.getQuestion());
        csvRow.add(answerStrings.stream().collect(Collectors.joining(answerDelimiter)));
        if (goldAnswerId < 0) {
            csvRow.add(""); // _gold
            csvRow.add(""); // choice_gold
            csvRow.add(""); // choice_gold_reason
        } else {
            csvRow.add("TRUE");
            csvRow.add(answerStrings.get(goldAnswerId));
            csvRow.add("Based on high-agreement of workers.");
        }
        csvPrinter.printRecord(csvRow);
    }

    private static double getAverage(final double[] arr) {
        double sum = .0;
        for (double a : arr) sum += a;
        return sum / arr.length;
    }

    private static double getStd(final double[] arr) {
        double std = .0;
        double mean = getAverage(arr);
        for (double a : arr) std += (a - mean) * (a - mean);
        return Math.sqrt(std / arr.length);
    }
}
