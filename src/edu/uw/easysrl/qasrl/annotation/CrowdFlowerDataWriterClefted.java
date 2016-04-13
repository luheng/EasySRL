package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.grammar.Category;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Create Crowdflower data for pronoun-style core questions.
 *
 * 1. Sample sentences (or reuse previous sentences)
 * 2. Sample test sentences or (reuse previous sentences)
 * 3. Write candidate questions to csv.
 * 4. Write candidate test questions to csv.
 * Created by luheng on 3/25/16.
 */
public class CrowdFlowerDataWriterClefted {

    static final int nBest = 100;
    static final boolean usePronouns = false;
    static final int maxNumQueriesPerFile = 100;

    private final static HITLParser hitlParser = new HITLParser(nBest);
    private final static ReparsingHistory history = new ReparsingHistory(hitlParser);

    private static final String csvOutputFilePrefix = "./Crowdflower_temp/clefted_100best";

    private static final String[] reviewedTestQuestionFiles = new String[] {
    };

    static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipPPQuestions = false;
        queryPruningParameters.skipSAdjQuestions = true;
        queryPruningParameters.minOptionConfidence = 0.1;
        queryPruningParameters.minOptionEntropy = 0.1;
        queryPruningParameters.minPromptConfidence = 0.05;
    }
    static HITLParsingParameters reparsingParamters;
    static {
        reparsingParamters = new HITLParsingParameters();
        reparsingParamters.attachmentPenaltyWeight = 5.0;
        reparsingParamters.supertagPenaltyWeight = 5.0;
    }

    private static void printQuestionsToAnnotate() throws IOException {
        final ImmutableList<Integer> sentenceIds = CrowdFlowerDataUtils.getRound2And3SentenceIds();
        AtomicInteger lineCounter = new AtomicInteger(0),
                fileCounter = new AtomicInteger(0);

        hitlParser.setQueryPruningParameters(queryPruningParameters);
        hitlParser.setReparsingParameters(reparsingParamters);

        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter.getAndAdd(1)))),
                CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeaderNew);

        for (int sid : sentenceIds) {
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
                    hitlParser.getCleftedQuestionsForSentence(sid);
            history.addSentence(sid);
            for (ScoredQuery<QAStructureSurfaceForm> query : queries) {
                // Skip certain type of questions.
                if (query.getQAPairSurfaceForms().get(0).getAnswerStructures().get(0).headIsVP) {
                    continue;
                }

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
                final int predId = query.getPredicateId().getAsInt();
                final List<Category> goldCats = hitlParser.getGoldParse(sid).categories;
                query.getQAPairSurfaceForms().forEach(qa -> {
                    System.out.println(qa.getAnswer() + "\t" + qa.getAnswerStructures().stream()
                            .flatMap(astr -> astr.adjunctDependencies.stream())
                            .distinct()
                            .map(dep -> dep.getHead() == predId ?
                                sentence.get(dep.getArgument()) + "." + goldCats.get(dep.getArgument()) :
                                sentence.get(dep.getHead()) + "." + goldCats.get(dep.getHead()))
                            .collect(Collectors.joining(" / "))
                    );
                });
                System.out.println();

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
        //final ImmutableList<Integer> testSentenceIds = CrowdFlowerDataUtils.getTestSentenceIds();
        // printTestQuestions();
        printQuestionsToAnnotate();
    }
}
