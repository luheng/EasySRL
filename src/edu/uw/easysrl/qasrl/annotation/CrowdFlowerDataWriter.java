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
    static final int nBest = 50;
    static final int maxNumSentences = 100;
    static final int maxNumQueries = 2000;

    // TODO
    static final int maxNumSentencesPerFile = 20;

    static final int numRandomSamples = 10;
    static final int randomSeed = 12345;

    static final int countEvery = 100;

    // TODO: keep track of avg. answer options per query.
    private static final int maxNumOptionsPerQuestion = 6;
    private static final String[] csvHeader = {
            "query_id", "sent_id", "sentence", "pred_id", "pred_head", "question", "answers"};
    private static final String answerDelimiter = " &&& ";

    private static final String csvOutputFile = "test.csv";

    public static void main(String[] args) throws IOException {
        ActiveLearningBySentence baseLearner = new ActiveLearningBySentence(nBest);
        List<double[]>  avgNumQueries = new ArrayList<>(),
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
                avgNumQueries.add(new double[numRandomSamples]);
                oneBestF1.add(new double[numRandomSamples]);
                rerankF1.add(new double[numRandomSamples]);
                oracleF1.add(new double[numRandomSamples]);
                gainF1.add(new double[numRandomSamples]);
            }
        }

        // Initialize CSV printer.
        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(csvOutputFile)),
                                               CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) csvHeader);

        for (int r = 0; r < numRandomSamples; r++) {
            ActiveLearningBySentence learner = new ActiveLearningBySentence(baseLearner);
            Collections.shuffle(sentenceIds, random);
            int queryCounter = 0;

            List<Integer> annotatedSentences = new ArrayList<>();
            for (int sentenceId : sentenceIds) {
                final List<String> sentence = learner.getSentenceById(sentenceId);
                System.out.println("SID=" + sentenceId + "\t" + learner.getSentenceScore(sentenceId));
                System.out.println(sentence.stream().collect(Collectors.joining(" ")));
                List<GroupedQuery> queries = learner.getQueriesBySentenceId(sentenceId).stream()
                        .filter(query -> query.answerEntropy > 1e-2 && query.questionConfidence > 0.1)
                        .collect(Collectors.toList());
                for (GroupedQuery query : queries) {
                    Response gold = responseSimulator.answerQuestion(query);
                    System.out.println(query.getDebuggingInfo(gold));
                    learner.respondToQuery(query, gold);

                    if (r == 0 && annotatedSentences.size() < maxNumSentences) {
                        // Print to CSV files.
                        // "query_id", "sent_id", "sentence", "pred_id", "pred_head","question", "answers"
                        int predicateIndex = query.getPredicateIndex();
                        String sentenceStr = TextGenerationHelper.renderHTMLString(sentence, predicateIndex);
                        List<String> csvRow = new ArrayList<>();
                        csvRow.add(String.valueOf(queryCounter));
                        csvRow.add(String.valueOf(sentenceId));
                        csvRow.add(String.valueOf(sentenceStr));
                        csvRow.add(String.valueOf(predicateIndex));
                        csvRow.add(sentence.get(predicateIndex));
                        csvRow.add(query.getQuestion());
                        csvRow.add(query.getAnswerOptions().stream()
                                .map(GroupedQuery.AnswerOption::getAnswer)
                                .collect(Collectors.joining(answerDelimiter)));
                        csvPrinter.printRecord(csvRow);
                    }
                }
                queryCounter += queries.size();
                annotatedSentences.add(sentenceId);
                if (annotatedSentences.size() % countEvery == 0) {
                    int k = annotatedSentences.size() / countEvery - 1;
                    avgNumQueries.get(k)[r] = queryCounter;
                    oneBestF1.get(k)[r] = 100.0 * learner.getOneBestF1(annotatedSentences).getF1();
                    rerankF1.get(k)[r]  = 100.0 * learner.getRerankedF1(annotatedSentences).getF1();
                    oracleF1.get(k)[r]  = 100.0 * learner.getOracleF1(annotatedSentences).getF1();
                    gainF1.get(k)[r]    = rerankF1.get(k)[r] - oneBestF1.get(k)[r];
                }
            }
        }

        csvPrinter.close();

        for (int k = 0; k < avgNumQueries.size(); k++) {
            System.out.println(String.format("On %d sentences:", (k + 1) * 100));
            System.out.println(String.format("Avg. number of queries:\t%.3f (%.3f)",
                    getAverage(avgNumQueries.get(k)),
                    getStd(avgNumQueries.get(k))));
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
