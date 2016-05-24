package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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

    private static boolean isCheckboxVersion = true;
    private static boolean highlightPredicates = false;



    private static final String csvOutputFilePrefix = "./Crowdflower_temp/crowdflower_dev_100best";

    // Data and parser.
    private static final String preparsedFile = "parses.100best.out";
    private static ParseData parseData;
    private static BaseCcgParser parser;
    private static ImmutableMap<Integer, NBestList> nbestLists;
    private static ResponseSimulatorGold goldSimulator;

    private static QueryPruningParameters queryPruningParameters = new QueryPruningParameters();

    public static void main(String[] args) throws IOException {
        // Initialize data.
        parseData = ParseDataLoader.loadFromDevPool().get();
        parser = new BaseCcgParser.MockParser(preparsedFile, nBest);
        nbestLists = NBestList.getAllNBestLists(parser, parseData.getSentenceInputWords());
        goldSimulator = new ResponseSimulatorGold(parseData);

        Set<Integer> heldOutSentences = new HashSet<>();
        // Print test questions.
        try {
            CrowdFlowerTestQuestionGenerator.printTestQuestions(heldOutSentences, parseData, nbestLists,
                    new QueryPruningParameters(), goldSimulator,
                    new String[] { CrowdFlowerDataUtils.cfRound1AnnotationFile, },
                    String.format("%s_test.csv", csvOutputFilePrefix),
                    isCheckboxVersion, highlightPredicates);
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (int sid : CrowdFlowerDataUtils.otherHeldOutSentences) {
            heldOutSentences.add(sid);
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
                final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = isCheckboxVersion ?
                        ExperimentUtils.generateAllCheckboxQueries(sentenceId, sentence, nbestLists.get(sentenceId),
                                queryPruningParameters) :
                        ExperimentUtils.generateAllRadioButtonQueries(sentenceId, sentence, nbestLists.get(sentenceId),
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
                            csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeader);
                            fileCounter.getAndIncrement();
                        }
                    }
                    // Write query to file.
                    for (ScoredQuery<QAStructureSurfaceForm> query : queries) {
                        CrowdFlowerDataUtils.printQueryToCSVFile(query,
                                parseData.getSentences().get(sentenceId),
                                null /* gold options */,
                                lineCounter.getAndIncrement(),
                                highlightPredicates,
                                csvPrinter);
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
