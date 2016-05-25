package edu.uw.easysrl.qasrl.annotation.ccgtest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.ParseDataLoader;
import edu.uw.easysrl.qasrl.annotation.AnnotationReader;
import edu.uw.easysrl.qasrl.annotation.CrowdFlowerDataUtils;
import edu.uw.easysrl.qasrl.annotation.RecordedAnnotation;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The corpus is sanitized, and doesn't contain gold parses. :)
 */
public class CrowdflowerDataWriterTest {
    static final int nBest = 100;
    static final int maxNumQueriesPerFile = 630;

    private static ParseData corpus;
    private static Map<Integer, NBestList> nbestLists;
    private static HITLParser hitlParser;

    private static final String csvOutputFilePrefix =
            "./Crowdflower_ccgtest/ccgtest_100best";

    private static final String outputSentenceIdsFile =
            "./Crowdflower_ccgtest/ccgtest.sent_ids.txt";

    private static final String[] reviewedTestQuestionFiles = new String[] {
            "./Crowdflower_unannotated/test_questions/test_question_core_pronoun_r04.tsv",
            "./Crowdflower_unannotated/test_questions/auto_test_questions_all.tsv",
    };

    private static ImmutableList<Integer> testSentenceIds;

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipPPQuestions = true;
        queryPruningParameters.skipSAdjQuestions = true;
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = -1;
        queryPruningParameters.minPromptConfidence = 0.1;
    }

    private static void initializeData() {
        //TODO: sanitize parseData, remove gold parses.
        corpus = ParseDataLoader.loadFromTestPool(false).get();
        nbestLists = NBestList.loadNBestListsFromFile("parses.tagged.test.gold.100best.new.out", nBest).get();
        hitlParser = new HITLParser(corpus, nbestLists);
    }
    private static void printTestQuestions() throws IOException {
        Map<Integer, List<RecordedAnnotation>> annotations = new HashMap<>();
        for (String testQuestionFile : reviewedTestQuestionFiles) {
            AnnotationReader.readReviewedTestQuestionsFromTSV(testQuestionFile)
                    .forEach(annot -> {
                        if (!annotations.containsKey(annot.sentenceId)) {
                            annotations.put(annot.sentenceId, new ArrayList<>());
                        }
                        annotations.get(annot.sentenceId).add(annot);
                    });
        }
        testSentenceIds = annotations.keySet().stream()
                .sorted()
                .collect(GuavaCollectors.toImmutableList());
        Set<String> testQuestionKeys = new HashSet<>();
        final String testQuestionsFile = String.format("%s_test.csv", csvOutputFilePrefix);
        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(testQuestionsFile)),
                CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeaderNew);
        AtomicInteger lineCounter = new AtomicInteger(0);
        testSentenceIds.forEach(sid ->
                        annotations.get(sid).stream()
                                .filter(annot -> {
                                    final String qkey = String.format("%d\t%d\t%s", sid, annot.predicateId, annot.queryPrompt);
                                    if (!testQuestionKeys.contains(qkey)) {
                                        testQuestionKeys.add(qkey);
                                        return true;
                                    }
                                    return false;
                                })
                                .forEach(annot -> {
                                    try {
                                        System.out.println(annot);
                                        CrowdFlowerDataUtils.printRecordToCSVFile(
                                                annot,
                                                10000 + lineCounter.getAndAdd(1),
                                                true, // highlight predicate
                                                csvPrinter);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                })
        );
        csvPrinter.close();
        System.out.println(String.format("Wrote %d test questions to file %s.", lineCounter.get(), testQuestionsFile));
    }

    private static void printQuestionsToAnnotate() throws IOException {
        assert testSentenceIds != null;
        ImmutableList<Integer> sentenceIds = hitlParser.getAllSentenceIds().stream()
                .sorted()
                .collect(GuavaCollectors.toImmutableList());
        AtomicInteger lineCounter = new AtomicInteger(0), fileCounter = new AtomicInteger(0);

        System.out.println("Num. sentences:\t" + sentenceIds.size());
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputSentenceIdsFile)));
        for (int id : sentenceIds) {
            writer.write(id + "\n");
        }
        writer.close();

        hitlParser.setQueryPruningParameters(queryPruningParameters);

        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter.getAndAdd(1)))),
                CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeaderNew);

        for (int sid : sentenceIds) {
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = hitlParser.getNewCoreArgQueriesForSentence(sid);
            for (ScoredQuery<QAStructureSurfaceForm> query : queries) {
                final ImmutableList<String> sentence = hitlParser.getSentence(sid);
                CrowdFlowerDataUtils.printQueryToCSVFileNew(
                        query,
                        sentence,
                        null, // gold options
                        lineCounter.getAndAdd(1),
                        true, // highlight predicate
                        "",
                        csvPrinter);
                if (lineCounter.get() % maxNumQueriesPerFile == 0) {
                    System.out.println(fileCounter.get() + "\t" + lineCounter.get());
                    csvPrinter.close();
                    csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                            String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter.getAndAdd(1)))),
                            CSVFormat.EXCEL.withRecordSeparator("\n"));
                    csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeaderNew);
                }
            }
        }
        System.out.println(lineCounter.get());
        csvPrinter.close();
    }

    public static void main(String[] args) throws IOException {
        initializeData();
        printTestQuestions();
        printQuestionsToAnnotate();
    }
}
