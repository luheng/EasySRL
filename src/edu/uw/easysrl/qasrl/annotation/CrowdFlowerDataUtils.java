package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by luheng on 3/22/16.
 */
public class CrowdFlowerDataUtils {
    // Fields for Crowdflower test questions.
    public static final String[] csvHeader = {
            "query_id", "question_confidence", "question_uncertainty", "sent_id", "sentence", "pred_id", "pred_head",
            "question_key", "question", "answers", "_golden ", "choice_gold", "choice_gold_reason"};

    public static final String answerDelimiter = " ### ";

    public static void printQueryToCSVFile(final ScoredQuery<QAStructureSurfaceForm> query,
                                           final ImmutableList<String> sentence,
                                           final ImmutableList<Integer> goldOptionIds,
                                           final int lineCounter,
                                           final boolean highlightPredicate,
                                           final CSVPrinter csvPrinter) throws IOException {
        // Print to CSV files.
        // "query_id", "question_confidence", "question_uncertainty", "sent_id", "sentence", "pred_id", "pred_head",
        // "question_key", "question", "answers", "_golden ", "choice_gold", "choice_gold_reason"
        int predicateIndex = query.getPredicateId().getAsInt();
        int sentenceId = query.getSentenceId();
        final String sentenceStr = TextGenerationHelper.renderHTMLSentenceString(sentence, predicateIndex,
                highlightPredicate);
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
}
