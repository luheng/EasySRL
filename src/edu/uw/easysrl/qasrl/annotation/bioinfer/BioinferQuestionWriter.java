package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.corpora.BioinferCCGCorpus;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.QuestionGenerationPipeline;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import static edu.uw.easysrl.util.GuavaCollectors.*;

/**
 * Create Crowdflower data for pronoun-style core questions.
 *
 * 1. Sample sentences (or reuse previous sentences)
 * 2. Sample test sentences or (reuse previous sentences)
 * 3. Write candidate questions to csv.
 * 4. Write candidate test questions to csv.
 * Created by luheng on 3/25/16.
 */
public class BioinferQuestionWriter {

    // static final int nBest = 100;
    static final int maxNumQueriesPerFile = 500;

    private static final String csvOutputFilePrefix =
        // "./Crowdflower_temp/bioinfer_dev_100best";
        // "./Crowdflower_temp/bioinfer_test_100best";
        // "./Crowdflower_temp/bioinfer_upwork_interview_100best";
        "./Crowdflower_temp/bioinfer_upwork_run_100best";

    private static final String[] reviewedTestQuestionFiles = new String[] {
        // "./Crowdflower_unannotated/test_questions/bioinfer_test_1to27.tsv"
        // "./Crowdflower_unannotated/test_questions/bioinfer_test_questions_for_test_run.tsv"
        "./Crowdflower_unannotated/test_questions/bioinfer_test_upwork_test_run.tsv"
    };

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipPPQuestions = true;
        queryPruningParameters.skipSAdjQuestions = true;  // R5: false // R4: true.
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = -1;   // R4: unspecified.
        queryPruningParameters.minPromptConfidence = 0.1;
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
        final String testQuestionsFile = String.format("%s_test.csv", csvOutputFilePrefix);
        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(testQuestionsFile)),
                CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeaderNew);
        AtomicInteger lineCounter = new AtomicInteger(0);
        annotations.keySet().stream().forEach(sid ->
                annotations.get(sid).stream()
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

    private static void printQuestionsToAnnotate(BioinferCCGCorpus corpus,
                                                 Map<Integer, NBestList> nbestLists,
                                                 ImmutableList<Integer> sentenceIds) throws IOException {
        AtomicInteger lineCounter = new AtomicInteger(0), fileCounter = new AtomicInteger(0);
        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter.getAndAdd(1)))),
                CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeaderNew);
        for (int sentenceId : sentenceIds) {
            final NBestList nbestList = nbestLists.get(sentenceId);
            List<ScoredQuery<QAStructureSurfaceForm>> queryList = QuestionGenerationPipeline.coreArgQGPipeline
                    .setQueryPruningParameters(queryPruningParameters)
                    .generateAllQueries(sentenceId, nbestList);
            // Assign query ids.
            IntStream.range(0, queryList.size()).forEach(i -> queryList.get(i).setQueryId(i));
            // Print test questions.
            for (ScoredQuery<QAStructureSurfaceForm> query : queryList) {
                final ImmutableList<Integer> oneBestOptions = IntStream.range(0, query.getOptions().size())
                        .filter(i -> query.getOptionToParseIds().get(i).contains(0))
                        .boxed()
                        .collect(GuavaCollectors.toImmutableList());
                System.out.println(query.toString(
                        corpus.getSentence(sentenceId),
                        'B', oneBestOptions));
                CrowdFlowerDataUtils.printQueryToCSVFileNew(
                        query,
                        corpus.getSentence(sentenceId),
                        null, // gold options
                        lineCounter.getAndAdd(1),
                        true, // highlight predicate
                        "",
                        csvPrinter);

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
        System.out.println(String.format("Extracted %d queries.", lineCounter.get()));
    }


    public static void main(String[] args) throws IOException {
        // BioinferCCGCorpus corpus = BioinferCCGCorpus.readDev().get();
        // Map<Integer, NBestList> nbestLists = NBestList.loadNBestListsFromFile("bioinfer.dev.100best.out", 100).get();
        // System.out.println(String.format("Load pre-parsed %d-best lists for %d sentences.", 100, nbestLists.size()));
        // ImmutableList<Integer> sentenceIds = IntStream.range(100, 300)
        //     .filter(nbestLists.keySet()::contains)
        //     .limit(100)
        //     .boxed()
        //     .collect(toImmutableList());
        // printQuestionsToAnnotate(corpus, nbestLists, sentenceIds);
        printTestQuestions();
    }
}
