package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Create Crowdflower data for pronoun-style core questions.
 *
 * 1. Sample sentences (or reuse previous sentences)
 * 2. Sample test sentences or (reuse previous sentences)
 * 3. Write candidate questions to csv.
 * 4. Write candidate test questions to csv.
 * Created by luheng on 3/25/16.
 */
public class CrowdFlowerDataWriterCorePronouns {

    static final int nBest = 100;
    static final int maxNumQueriesPerFile = 200;
    static final int numSentences = 2000;
    static final int randomSeed = 12345;

    private final static HITLParser hitlParser = new HITLParser(nBest);
    private final static ReparsingHistory history = new ReparsingHistory(hitlParser);

    private static final String csvOutputFilePrefix =
           // "./Crowdflower_unannotated/pronoun_core_r4_100best";
          //  "./Crowdflower_unannotated/pronoun_core_r5_100best";
            "./Crowdflower_temp/pronoun_core_r12_100best";

    private static final String outputSentenceIdsFile =
          //  "./Crowdflower_unannotated/pronoun_core_r5_100best.sent_ids.txt";
            "./Crowdflower_temp/pronoun_core_r12_100best.sent_ids.txt";

    private static final String[] reviewedTestQuestionFiles = new String[] {
            "./Crowdflower_unannotated/test_questions/test_question_core_pronoun_r04.tsv",
         //   "./Crowdflower_temp/test_questions/test_question_core_pronoun_r04.tsv",
    };

    private static ImmutableList<Integer> testSentenceIds;

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipSAdjQuestions = true;  // R5: false // R4: true.
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = 0.05;   // R4: unspecified.
        queryPruningParameters.minPromptConfidence = 0.1;
    }

    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.attachmentPenaltyWeight = 5.0;
        reparsingParameters.supertagPenaltyWeight = 5.0;
    }

    private static void printTestQuestions() throws IOException {
        // Load test questions prepared by the UI.
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
        testSentenceIds = annotations.keySet().stream().sorted().collect(GuavaCollectors.toImmutableList());
        final String testQuestionsFile = String.format("%s_test.csv", csvOutputFilePrefix);
        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(testQuestionsFile)),
                CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeaderNew);
        AtomicInteger lineCounter = new AtomicInteger(0);
        for (int sid : annotations.keySet()) {
            annotations.get(sid).stream().forEach(annot -> {
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
            });
        }
        csvPrinter.close();
        System.out.println(String.format("Wrote %d test questions to file %s.", lineCounter.get(), testQuestionsFile));
    }

    private static void printQuestionsToAnnotate() throws IOException {
        assert testSentenceIds != null;
        /*
        final ImmutableList<Integer> allSentenceIds = hitlParser.getAllSentenceIds().stream()
                .filter(id -> !testSentenceIds.contains(id))
                .collect(GuavaCollectors.toImmutableList());
                */
        final ImmutableList<Integer> sentenceIds = CrowdFlowerDataUtils
                //.sampleNewSentenceIds(numSentences, randomSeed, allSentenceIds);
                .getRound1And2SentenceIds();
        AtomicInteger lineCounter = new AtomicInteger(0),
                      fileCounter = new AtomicInteger(0);

        // TODO: sanity check: not overlapping with test sentence ids.

        System.out.println("Num. sentences:\t" + sentenceIds.size());
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputSentenceIdsFile)));
        for (int id : sentenceIds) {
            writer.write(id + "\n");
        }
        writer.close();

        hitlParser.setQueryPruningParameters(queryPruningParameters);
        hitlParser.setReparsingParameters(reparsingParameters);

        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter.getAndAdd(1)))),
                CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeaderNew);

        for (int sid : sentenceIds) {
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
                    hitlParser.getPronounCoreArgQueriesForSentence(sid);
            history.addSentence(sid);
            for (ScoredQuery<QAStructureSurfaceForm> query : queries) {
                final ImmutableList<String> sentence = hitlParser.getSentence(sid);
                final ImmutableList<Integer> goldOptionIds = hitlParser.getGoldOptions(query);
                CrowdFlowerDataUtils.printQueryToCSVFileNew(
                        query,
                        sentence,
                        null, // gold options
                        lineCounter.getAndAdd(1),
                        true, // highlight predicate
                        "",
                        csvPrinter);
                history.addEntry(sid, query, goldOptionIds, hitlParser.getConstraints(query, goldOptionIds));
                history.printLatestHistory();
                if (lineCounter.get() % maxNumQueriesPerFile == 0) {
                    csvPrinter.close();
                    csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                            String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter.getAndAdd(1)))),
                            CSVFormat.EXCEL.withRecordSeparator("\n"));
                    csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeaderNew);
                }
            }
        }
        csvPrinter.close();
        history.printSummary();
    }

    public static void main(String[] args) throws IOException {
        printTestQuestions();
        printQuestionsToAnnotate();
    }
}
