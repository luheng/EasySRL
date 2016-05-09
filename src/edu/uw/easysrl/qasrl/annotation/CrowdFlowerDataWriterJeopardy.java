package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
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
    static final int maxNumQueriesPerFile = 100;

    private final static HITLParser hitlParser = new HITLParser(nBest);

    private static final String csvOutputFilePrefix = "./Crowdflower_unannotated/jeopardy_pp_r23_100best";
    // private static final String csvOutputFilePrefix = "./Crowdflower_temp/jeopardy_pp_r23_100best";

    private static final String[] unreviewedTestQuestionFiles = new String[] {
          //  "Crowdflower_unannotated/test_questions/luheng_20160330-1719.txt",
            "Crowdflower_unannotated/test_questions/Julian_20160330-2349.txt",
    };

    private static final String[] reviewedTestQuestionFiles = new String[] {
            "Crowdflower_unannotated/test_questions/reviewed_test_questions_jeopardy_pp.tsv",
    };

    static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = 0.05;
        queryPruningParameters.minPromptConfidence = 0.1;
    }

    private static void printTestQuestions() throws IOException {
        // Load test questions prepared by the UI.
        Map<Integer, List<RecordedAnnotation>> annotations = new HashMap<>();
        for (String testQuestionFile : reviewedTestQuestionFiles) {
        // for (String testQuestionFile : unreviewedTestQuestionFiles) {
            // TODO: align annotations from different people.
            AnnotationReader.readReviewedTestQuestionsFromTSV(testQuestionFile)
            // AnnotationReader.loadAnnotationRecordsFromFile(testQuestionFile)
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
        for (int sid : annotations.keySet()) {
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = hitlParser.getPPAttachmentQueriesForSentence(sid);
            queries.stream().forEach(query -> {
                Optional<RecordedAnnotation> annotation = annotations.get(sid).stream()
                        .filter(annot -> annot.queryPrompt.equals(query.getPrompt()))
                        .findFirst();
                if (annotation.isPresent()) {
                    final ImmutableList<String> sentence = hitlParser.getSentence(sid);
                    final ImmutableList<Integer> userOptions = annotation.get().userOptionIds;
                    System.out.println(query.toString(sentence,
                            'G', hitlParser.getGoldOptions(query),
                            'U', userOptions));
                    System.out.println("Reason:\t" + annotation.get().comment + "\n");
                    try {
                        CrowdFlowerDataUtils.printQueryToCSVFileNew(
                                query,
                                sentence,
                                userOptions,
                                10000 + lineCounter.getAndAdd(1),
                                false, // highlight predicate
                                annotation.get().comment,
                                csvPrinter);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        csvPrinter.close();
        System.out.println(String.format("Wrote %d test questions to file %s.", lineCounter.get(), testQuestionsFile));
    }

    private static void printQuestionsToAnnotate() throws IOException {
        final ImmutableList<Integer> sentenceIds = CrowdFlowerDataUtils.getRound2And3SentenceIds();
        AtomicInteger lineCounter = new AtomicInteger(0),
                      fileCounter = new AtomicInteger(0);

        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter.getAndAdd(1)))),
                CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeaderNew);

        for (int sid : sentenceIds) {
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = hitlParser.getPPAttachmentQueriesForSentence(sid);
            for (ScoredQuery<QAStructureSurfaceForm> query : queries) {
                final ImmutableList<String> sentence = hitlParser.getSentence(sid);
                System.out.println(query.toString(sentence,
                        'G', hitlParser.getGoldOptions(query),
                        'O', hitlParser.getOracleOptions(query),
                        'B', hitlParser.getOneBestOptions(query)));
                CrowdFlowerDataUtils.printQueryToCSVFileNew(
                        query,
                        sentence,
                        null, // gold options
                        lineCounter.getAndAdd(1),
                        false, // highlight predicate
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
    }

    public static void main(String[] args) throws IOException {
        //final ImmutableList<Integer> testSentenceIds = CrowdFlowerDataUtils.getTestSentenceIds();

        printTestQuestions();
        //printQuestionsToAnnotate();
    }
}
