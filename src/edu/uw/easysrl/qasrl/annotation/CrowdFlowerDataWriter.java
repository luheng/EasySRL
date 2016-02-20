package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Group query by sentences.
 * Created by luheng on 2/9/16.
 */
public class CrowdFlowerDataWriter {
    static final int nBest = 100;
    static final int maxNumSentences = 100;
    // static final int maxNumQueries = 2000;

    // TODO: hold out sentences we used in examples and test questions.
    static final int maxNumSentencesPerFile = 20;

    static final int numRandomSamples = 10;
    static final int randomSeed = 12345;

    static final int countEvery = 100;

    private static final double minQuestionConfidence = 0.1;
    private static final double minAnswerEntropy = 0.1;

    private static boolean highlightPredicate = false;
    private static boolean skipBinaryQueries = true;

    // private static final int maxNumOptionsPerQuestion = 6;
    private static final String[] csvHeader = {
            "query_id", "confidence", "uncertainty", "sent_id", "sentence", "pred_id", "pred_head", "question",
            "answers"};
    private static final String answerDelimiter = " ### ";

    private static final String csvOutputFilePrefix = "crowdflower_test_100best";

    public static void main(String[] args) throws IOException {
        ActiveLearningBySentence baseLearner = new ActiveLearningBySentence(nBest);
        List<double[]>  avgNumQueries = new ArrayList<>(),
                        avgOptionsPerQuery = new ArrayList<>(),
                        avgNumBinaryQueries = new ArrayList<>(),
                        oneBestF1 = new ArrayList<>(),
                        rerankF1 = new ArrayList<>(),
                        oracleF1 = new ArrayList<>(),
                        gainF1 = new ArrayList<>();
        ResponseSimulator responseSimulator = new ResponseSimulatorGold(baseLearner.goldParses, new QuestionGenerator(),
                                                                        true /* Allow label match */);
        Random random = new Random(randomSeed);
        List<Integer> sentenceIds = new ArrayList<>(baseLearner.getAllSentenceIds());
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
            ActiveLearningBySentence learner = new ActiveLearningBySentence(baseLearner);
            Collections.shuffle(sentenceIds, random);
            int lineCounter = 0;
            int fileCounter = 0;
            int queryCounter = 0;
            int optionCounter = 0;
            // Queries that has only one option except for N/A.
            int numBinaryQueries = 0;
            List<Integer> annotatedSentences = new ArrayList<>();

            if (r == 0) {
                // Initialize CSV printer.
                csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                        String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter))),
                        CSVFormat.EXCEL.withRecordSeparator("\n"));
                csvPrinter.printRecord((Object[]) csvHeader);
            }
            for (int sentenceId : sentenceIds) {
                final List<String> sentence = learner.getSentenceById(sentenceId);
                List<GroupedQuery> queries = learner.getQueriesBySentenceId(sentenceId).stream()
                        .filter(query -> query.answerEntropy > minAnswerEntropy
                                            && query.questionConfidence > minQuestionConfidence
                                            && (!skipBinaryQueries || query.attachmentUncertainty > 1e-6))
                        .collect(Collectors.toList());
                // Print query to .csv file.
                if (r == 0 && annotatedSentences.size() < maxNumSentences) {
                    for (GroupedQuery query : queries) {
                        // Print to CSV files.
                        // "query_id", "confidence, "uncertainty", "sent_id", "sentence", "pred_id", "pred_head",
                        // "question", "answers"
                        int predicateIndex = query.getPredicateIndex();
                        String sentenceStr = TextGenerationHelper.renderHTMLSentenceString(sentence, predicateIndex,
                                highlightPredicate);
                        List<String> csvRow = new ArrayList<>();
                        csvRow.add(String.valueOf(lineCounter));
                        csvRow.add(String.format("%.3f", query.questionConfidence));
                        csvRow.add(String.format("%.3f", query.attachmentUncertainty));
                        csvRow.add(String.valueOf(sentenceId));
                        csvRow.add(String.valueOf(sentenceStr));
                        csvRow.add(String.valueOf(predicateIndex));
                        csvRow.add(sentence.get(predicateIndex));
                        csvRow.add(query.getQuestion());
                        csvRow.add(query.getAnswerOptions().stream()
                                .map(ao -> ao.getAnswer().replace(" &&& ", " <strong> & </strong> "))
                                .collect(Collectors.joining(answerDelimiter)));
                        csvPrinter.printRecord(csvRow);
                        lineCounter ++;
                    }
                    int numSentences = annotatedSentences.size() + 1;
                    if (numSentences <= maxNumSentences && numSentences % maxNumSentencesPerFile == 0) {
                        csvPrinter.close();
                        if (numSentences < maxNumSentences) {
                            fileCounter++;
                            csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                                    String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter))),
                                    CSVFormat.EXCEL.withRecordSeparator("\n"));
                            csvPrinter.printRecord((Object[]) csvHeader);
                        }
                    }
                }
                for (GroupedQuery query : queries) {
                    Response gold = responseSimulator.answerQuestion(query);
                    if (r == 0 && lineCounter < 50) {
                        System.out.println("SID=" + sentenceId + "\t" + learner.getSentenceScore(sentenceId));
                        System.out.println(sentence.stream().collect(Collectors.joining(" ")));
                        System.out.println(query.getDebuggingInfo(gold));
                    }
                    learner.respondToQuery(query, gold);
                    optionCounter += query.getAnswerOptions().size();
                    if (query.attachmentUncertainty < 1e-6) {
                        numBinaryQueries ++;
                    }
                }
                queryCounter += queries.size();
                annotatedSentences.add(sentenceId);
                if (annotatedSentences.size() % countEvery == 0) {
                    int k = annotatedSentences.size() / countEvery - 1;
                    avgNumQueries.get(k)[r] = queryCounter;
                    avgNumBinaryQueries.get(k)[r] = numBinaryQueries;
                    avgOptionsPerQuery.get(k)[r] = 1.0 * optionCounter / queryCounter;
                    oneBestF1.get(k)[r] = 100.0 * learner.getOneBestF1(annotatedSentences).getF1();
                    rerankF1.get(k)[r]  = 100.0 * learner.getRerankedF1(annotatedSentences).getF1();
                    oracleF1.get(k)[r]  = 100.0 * learner.getOracleF1(annotatedSentences).getF1();
                    gainF1.get(k)[r]    = rerankF1.get(k)[r] - oneBestF1.get(k)[r];
                }
            }
        }
        for (int k = 0; k < avgNumQueries.size(); k++) {
            System.out.println(String.format("On %d sentences:", (k + 1) * 100));
            System.out.println(String.format("Avg. number of queries:\t%.3f (%.3f)",
                    getAverage(avgNumQueries.get(k)),
                    getStd(avgNumQueries.get(k))));
            System.out.println(String.format("Avg. number of binary queries:\t%.3f (%.3f)",
                    getAverage(avgNumBinaryQueries.get(k)),
                    getStd(avgNumBinaryQueries.get(k))));
            System.out.println(String.format("Avg. number of options per query:\t%.3f (%.3f)",
                    getAverage(avgOptionsPerQuery.get(k)),
                    getStd(avgOptionsPerQuery.get(k))));
            System.out.println(String.format("Avg. 1-best F1:\t%.3f%%\t%.3f%%",
                    getAverage(oneBestF1.get(k)),
                    getStd(oneBestF1.get(k))));
            System.out.println(String.format("Avg. rerank F1:\t%.3f%%\t%.3f%%",
                    getAverage(rerankF1.get(k)),
                    getStd(rerankF1.get(k))));
            System.out.println(String.format("Avg. oracle F1:\t%.3f%%\t%.3f%%",
                    getAverage(oracleF1.get(k)),
                    getStd(oracleF1.get(k))));
            System.out.println(String.format("Avg. F1 gain  :\t%.3f%%\t%.3f%%",
                    getAverage(gainF1.get(k)),
                    getStd(gainF1.get(k))));
        }
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
