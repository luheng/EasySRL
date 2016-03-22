package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.experiments.DebugPrinter;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryGenerators;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.util.GuavaCollectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * For analysis purposes for now.
 * Created by luheng on 2/9/16.
 */
public class CrowdFlowerDataWriter {
    static final int nBest = 100;
    static final int maxNumSentences = 300;
    static final int maxNumSentencesPerFile = 100;
    static final int numRandomSamples = 1; // 10;
    static final int randomSeed = 104743;

    static final int countEvery = 100;
    static final boolean skipPPQuestions = true;
    static final boolean skipPronounQuestions = false;
    private static boolean highlightPredicate = false;
    private static int minAgreementForTestQuestions = 4;

    // Option pruning.
    private static final int maxNumOptionsPerQuestion = 8;

    // Query pruning parameters.
    private static QueryPruningParameters queryPruningParameters = new QueryPruningParameters(
            1,    /* top K */
            0.1,  /* min question confidence */
            0.05, /* min answer confidence */
            0.05  /* min attachment entropy */
    );

    // Fields for Crowdflower test questions.
    private static final String[] csvHeader = {
            "query_id", "question_confidence", "question_uncertainty", "sent_id", "sentence", "pred_id", "pred_head",
            "question_key", "question", "answers", "_golden ", "choice_gold", "choice_gold_reason"};

    private static final String answerDelimiter = " ### ";

    private static final String cfRound1AnnotationFilePath = "./Crowdflower_data/f878213.csv";

    private static final String csvOutputFilePrefix = "./Crowdflower_temp/crowdflower_dev_100best";

    // Sentences that happened to appear in instructions ...
    private static final int[] otherHeldOutSentences = { 1695, };

    // Data and parser.
    private static final String preparsedFile = "parses.100best.out";
    private static ParseData parseData;
    private static BaseCcgParser parser;
    private static Map<Integer, NBestList> nbestLists;
    private static ResponseSimulatorGold goldSimulator;

    public static void main(String[] args) throws IOException {
        // Initialize data.
        parseData = ParseData.loadFromDevPool().get();
        parser = new BaseCcgParser.MockParser(preparsedFile, nBest);
        nbestLists = ExperimentUtils.getAllNBestLists(parser, parseData.getSentenceInputWords());
        goldSimulator = new ResponseSimulatorGold(parseData);

        Set<Integer> heldOutSentences = new HashSet<>();
        // Print test questions.
        try {
            printTestQuestions(heldOutSentences);
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<double[]> avgNumQueries = new ArrayList<>(),
                avgOptionsPerQuery = new ArrayList<>(),
                avgNumGoldNAQueries = new ArrayList<>(),
                oneBestF1 = new ArrayList<>(),
                rerankF1 = new ArrayList<>(),
                oracleF1 = new ArrayList<>(),
                gainF1 = new ArrayList<>();
        Random random = new Random(randomSeed);
        List<Integer> sentenceIds = nbestLists.keySet().stream()
                .filter(sid -> !heldOutSentences.contains(sid))
                .collect(Collectors.toList());
        for (int i = 1; i < sentenceIds.size(); i++) {
            if (i % countEvery == 0) {
                avgOptionsPerQuery.add(new double[numRandomSamples]);
                avgNumQueries.add(new double[numRandomSamples]);
                avgNumGoldNAQueries.add(new double[numRandomSamples]);
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
            AtomicInteger lineCounter = new AtomicInteger();
            AtomicInteger fileCounter = new AtomicInteger();
            AtomicInteger queryCounter = new AtomicInteger();
            AtomicInteger optionCounter = new AtomicInteger();
            // Queries that has only one option except for N/A.
            AtomicInteger numNAQueries = new AtomicInteger();
            List<Integer> annotatedSentences = new ArrayList<>();
            Results rerank = new Results(),
                    oracle = new Results(),
                    oneBest = new Results();
            // Process questions.
            for (int sentenceId : sentenceIds) {
                final ImmutableList<String> sentence = parseData.getSentences().get(sentenceId);
                final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
                        ExperimentUtils.generateAllCheckboxQueries(sentenceId, sentence, nbestLists.get(sentenceId),
                                queryPruningParameters);
                final NBestList nbestList = nbestLists.get(sentenceId);
                final int oracleId = nbestList.getOracleId();
                nbestList.cacheResults(parseData.getGoldParses().get(sentenceId));

                // Print query to .csv file.
                if (r == 0 && annotatedSentences.size() < maxNumSentences) {
                    int numSentences = annotatedSentences.size();
                    if (numSentences <= maxNumSentences && numSentences % maxNumSentencesPerFile == 0) {
                        // Close previous CSV file.
                        if (lineCounter.get() > 0) {
                            csvPrinter.close();
                        }
                        // Create a new CSV file.
                        if (numSentences < maxNumSentences) {
                            csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                                    String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter.get()))),
                                    CSVFormat.EXCEL.withRecordSeparator("\n"));
                            csvPrinter.printRecord((Object[]) csvHeader);
                            fileCounter.getAndIncrement();
                        }
                    }
                    // Write query to file.
                    for (ScoredQuery<QAStructureSurfaceForm> query : queries) {
                        printQueryToCSVFile(query, null /* gold options */, lineCounter.getAndIncrement(), csvPrinter);
                    }
                }
                // Simulation and count.
                for (ScoredQuery<QAStructureSurfaceForm> query : queries) {
                    final ImmutableList<String> options = query.getOptions();
                    int numOptions = options.size();

                    ImmutableList<Integer> goldResponse = goldSimulator.respondToQuery(query);

                    boolean oracleIsNA = false;
                    boolean goldIsNA = goldResponse.contains(query.getBadQuestionOptionId().getAsInt());

                    if (goldIsNA) {
                        numNAQueries.getAndIncrement();
                    }

                    if (r == 0 && lineCounter.get() < 500) {
                        System.out.println("OracleID=" + nbestList.getOracleId());
                        System.out.println(query.toString(sentence));
                        System.out.println(DebugPrinter.getShortListString(goldResponse));
                    }
                    // TODO: reparse.
                    optionCounter.getAndAdd(options.size());
                }
                queryCounter.getAndAdd(queries.size());
                annotatedSentences.add(sentenceId);

                oracle.add(nbestList.getResults(oracleId));
                oneBest.add(nbestList.getResults(0 /* onebest */));
                rerank.add(nbestList.getResults(0 /* TODO */ ));

                // Compute stats.
                if (annotatedSentences.size() % countEvery == 0) {
                    int k = annotatedSentences.size() / countEvery - 1;
                    avgNumQueries.get(k)[r]             = queryCounter.get();
                    avgNumGoldNAQueries.get(k)[r]       = numNAQueries.get();
                    avgOptionsPerQuery.get(k)[r]        = 1.0 * optionCounter.get() / queryCounter.get();
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
            System.out.println(String.format("Avg. number of N/A queries:\t%.3f (%.3f)",
                    getAverage(avgNumGoldNAQueries.get(k)), getStd(avgNumGoldNAQueries.get(k))));
            System.out.println(String.format("Percentage of N/A queries:\t%.3f%%",
                    100.0 * getAverage(avgNumGoldNAQueries.get(k)) / getAverage(avgNumQueries.get(k))));
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

    private static void printTestQuestions(Set<Integer> heldOutSentences) throws IOException {
        // Load test questions from pilot study.
        List<AlignedAnnotation> pilotAnnotations = AlignedAnnotation.getAllAlignedAnnotationsFromPilotStudy();
        // Load test questions from previous annotation.
        List<AlignedAnnotation> cfRound1Annotations = CrowdFlowerDataReader.readAggregatedAnnotationFromFile(
                cfRound1AnnotationFilePath);
        pilotAnnotations.stream().forEach(a -> heldOutSentences.add(a.sentenceId));
        cfRound1Annotations.stream().forEach(a -> heldOutSentences.add(a.sentenceId));
        for (int sid : otherHeldOutSentences) {
            heldOutSentences.add(sid);
        }
        // Extract high-agreement questions from pilot study.
        List<AlignedAnnotation> agreedAnnotations = new ArrayList<>();
        pilotAnnotations.stream()
                .filter(a -> {
                    int numJudgements = a.getNumAnnotated();
                    int numOptions = a.answerDist.length;
                    return numOptions > 3 && numJudgements >= 3 && a.answerDist[a.goldAnswerIds.get(0)] == numJudgements;
                })
                .forEach(agreedAnnotations::add);
        cfRound1Annotations.stream()
                .filter(a -> {
                    boolean highAgreement = false;
                    for (int i = 0; i < a.answerDist.length; i++) {
                        highAgreement |= (a.answerDist[i] >= 4);
                    }
                    return highAgreement;
                })
                .forEach(agreedAnnotations::add);
        System.out.println("Number of held-out sentences:\t" + heldOutSentences.size());
        System.out.println("Number of high-agreement annotations:\t" + agreedAnnotations.size());

        // Initialize CSV printer.
        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                String.format("%s_test.csv", csvOutputFilePrefix))), CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) csvHeader);
        // Write test questions.
        int numTestQuestions = 0;
        for (AlignedAnnotation test : agreedAnnotations) {
            final int sentenceId = test.sentenceId;
            final ImmutableList<String> sentence = parseData.getSentences().get(sentenceId);
            String agreedAnswer = "";
            if (test.goldAnswerIds != null) {
                // Inconsistency of answer delimiter..
                agreedAnswer = test.answerOptions.get(test.goldAnswerIds.get(0))
                        .replace(" # ", QAPairAggregatorUtils.answerDelimiter);
            } else {
                for (int i = 0; i < test.answerOptions.size(); i++) {
                    if (test.answerDist[i] >= minAgreementForTestQuestions) {
                        agreedAnswer = test.answerOptions.get(i);
                        break;
                    }
                }
            }
            for (ScoredQuery<QAStructureSurfaceForm> query : ExperimentUtils.generateAllRadioButtonQueries(
                    sentenceId,
                    sentence,
                    nbestLists.get(sentenceId),
                    queryPruningParameters)) {
                if (query.getPredicateId().getAsInt() == test.predicateId &&
                        query.getPrompt().equalsIgnoreCase(test.question)) {
                    final ImmutableList<String> options = query.getOptions();
                    final int goldOptionId = goldSimulator.respondToQuery(query).get(0);
                    final String goldOptionStr = options.get(goldOptionId);

                    boolean agreedMatchesGold = goldOptionStr.equalsIgnoreCase(agreedAnswer) ||
                            (goldOptionStr.equals(QueryGenerators.kBadQuestionOptionString) &&
                                    agreedAnswer.startsWith("Question is not"));
                    if (agreedMatchesGold) {
                        printQueryToCSVFile(query, ImmutableList.of(goldOptionId),
                                10000 + numTestQuestions /* lineCounter */, csvPrinter);
                        numTestQuestions ++;
                    } else {
                        System.err.println(test.toString() + "---\n" + query.toString(sentence) + "---\n" + goldOptionStr);
                    }
                }
            }
        }
        System.out.println("Wrote " + numTestQuestions + " test questions to file.");
        csvPrinter.close();
    }

    private static void printQueryToCSVFile(final ScoredQuery<QAStructureSurfaceForm> query,
                                            ImmutableList<Integer> goldOptionIds, int lineCounter,
                                            final CSVPrinter csvPrinter) throws IOException {
        // Print to CSV files.
        // "query_id", "question_confidence", "question_uncertainty", "sent_id", "sentence", "pred_id", "pred_head",
        // "question_key", "question", "answers", "_golden ", "choice_gold", "choice_gold_reason"
        int predicateIndex = query.getPredicateId().getAsInt();
        int sentenceId = query.getSentenceId();
        final List<String> sentence = parseData.getSentences().get(query.getSentenceId());
        final String sentenceStr = TextGenerationHelper.renderHTMLSentenceString(sentence, predicateIndex, highlightPredicate);
        final ImmutableList<String> options = query.getOptions();
        List<String> csvRow = new ArrayList<>();
        csvRow.add(String.valueOf(lineCounter));
        csvRow.add(String.format("%.3f", query.getPromptScore()));
        csvRow.add(String.format("%.3f", 0.0)); // TODO: answer entropy
        csvRow.add(String.valueOf(sentenceId));
        csvRow.add(String.valueOf(sentenceStr));
        csvRow.add(String.valueOf(predicateIndex));
        csvRow.add(sentence.get(predicateIndex));
        csvRow.add(query.getQueryKey());
        csvRow.add(query.getPrompt());
        csvRow.add(options.stream().collect(Collectors.joining(answerDelimiter)));
        if (goldOptionIds == null) {
            csvRow.add(""); // _gold
            csvRow.add(""); // choice_gold
            csvRow.add(""); // choice_gold_reason
        } else {
            csvRow.add("TRUE");
            csvRow.add(goldOptionIds.stream().map(options::get).collect(Collectors.joining("\n")));
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