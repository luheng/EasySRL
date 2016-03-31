package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.Util;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Create Crowdflower data for jeopardy-style queries.
 *
 * 1. Sample sentences (or reuse previous sentences)
 * 2. Sample test sentences or (reuse previous sentences)
 * 3. Write candidate questions to csv.
 * 4. Write candidate test questions to csv.
 * Created by luheng on 3/25/16.
 */
public class CrowdFlowerDataWriterJeopardy {

    static final int nBest = 100;
    static final boolean usePronouns = false;

    private static final String csvOutputFilePrefix = "./Crowdflower_unannotated/jeopardy_pp_r23_100best";

    private static final String[] testQuestionFiles = new String[] {
            "Crowdflower_unannotated/test_questions/luheng_20160330-1719.txt",
    };

    static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = 0.05;
        queryPruningParameters.minPromptConfidence = 0.1;
    }

    public static void main(String[] args) throws IOException {

        final ImmutableList<Integer> testSentenceIds = CrowdFlowerDataUtils.getTestSentenceIds();
        final ImmutableList<Integer> sentenceIds = CrowdFlowerDataUtils.getRound2And3SentenceIds();

        HITLParser hitlParser = new HITLParser(nBest);

        // Print candidate test questions.
        /*
        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                String.format("%s_test.csv", csvOutputFilePrefix))),
                CSVFormat.EXCEL.withRecordSeparator("\n"));
        AtomicInteger lineCounter = new AtomicInteger(0);
        for (int sid : testSentenceIds) {
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
                    hitlParser.getPPAttachmentQueriesForSentence(sid, usePronouns);
            queries.forEach(query -> {
                try {
                    CrowdFlowerDataUtils.printQueryToCSVFile(
                            query,
                            hitlParser.getSentence(sid),
                            hitlParser.getGoldOptions(query),
                            10000 + lineCounter.getAndAdd(1),
                            false, // highlight predicate
                            csvPrinter);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
        csvPrinter.close();
        */



        // Load test questions prepared by the UI.
        for (String testQuestionFile : testQuestionFiles) {
            ImmutableList<RecordedAnnotation> annotations  =
                    RecordedAnnotation.loadAnnotationRecordsFromFile(testQuestionFile);
            annotations.forEach(annot ->
                    System.out.println(annot)
            );
        }
    }
}
