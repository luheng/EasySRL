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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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

    static final int nBest = 100;
    static final int maxNumQueriesPerFile = 200;
    static final int numSentences = 2000;
    static final int randomSeed = 12345;

    private static final String csvOutputFilePrefix =
            "./Crowdflower_bioinfer/bioinfer_dev_100best";

    private static final String outputSentenceIdsFile =
            "./Crowdflower_bioinfer/bioinfer_dev_100best";

    private static final String[] reviewedTestQuestionFiles = new String[] {
    };

    private static ImmutableList<Integer> testSentenceIds;

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipPPQuestions = true;
        queryPruningParameters.skipSAdjQuestions = true;  // R5: false // R4: true.
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = -1;   // R4: unspecified.
        queryPruningParameters.minPromptConfidence = 0.1;
    }

    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.attachmentPenaltyWeight = 5.0;
        reparsingParameters.supertagPenaltyWeight = 5.0;
    }

    private static void printQuestionsToAnnotate() throws IOException {
        BioinferCCGCorpus corpus = BioinferCCGCorpus.readDev().get();
        Map<Integer, NBestList> nbestLists = NBestList.loadNBestListsFromFile("bioinfer.dev.100best.out", nBest).get();
        System.out.println(String.format("Load pre-parsed %d-best lists for %d sentences.", nBest, nbestLists.size()));

        AtomicInteger lineCounter = new AtomicInteger(0), fileCounter = new AtomicInteger(0);
        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter.getAndAdd(1)))),
                CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeaderNew);

        for (int sentenceId : nbestLists.keySet()) {
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
        printQuestionsToAnnotate();
    }
}
