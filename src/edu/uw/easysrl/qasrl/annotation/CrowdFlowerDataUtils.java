package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.QueryGeneratorUtils;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.FileWriter;
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

    public static final String[] csvHeaderNew = {
            "query_id", "sent_id", "sentence", "query_prompt", "options", "_golden ", "choice_gold", "choice_gold_reason",
            "query_key", "jeopardy_style", "query_confidence", "query_uncertainty" };

    public static final String answerDelimiter = " ### ";


    public static final String cfRound1AnnotationFile =  "./Crowdflower_data/f878213.csv" ;
    public static final String cfRound2AnnotationFile =  "./Crowdflower_data/f882410.csv" ;
    public static final String cfRound3AnnotationFile =  "./Crowdflower_data/all-checkbox-responses.csv" ;

    // Sentences that happened to appear in instructions ...
    public static final int[] otherHeldOutSentences = { 1695, };


    public static ImmutableList<Integer> getTestSentenceIds() {
        List<Integer> heldOutSentences = new ArrayList<>();

        // Load test questions from pilot study.
        List<AlignedAnnotation> pilotAnnotations = AlignedAnnotation.getAllAlignedAnnotationsFromPilotStudy();

        // Load test questions from previous annotation.
        List<AlignedAnnotation> cfAnnotations = new ArrayList<>();
        try {
            cfAnnotations.addAll(CrowdFlowerDataReader.readAggregatedAnnotationFromFile(cfRound1AnnotationFile));
        } catch (IOException e) {
            e.printStackTrace();
        }

        pilotAnnotations.stream().forEach(a -> heldOutSentences.add(a.sentenceId));
        cfAnnotations.stream().forEach(a -> heldOutSentences.add(a.sentenceId));
        for (int sid : otherHeldOutSentences) {
            heldOutSentences.add(sid);
        }
        return heldOutSentences.stream().distinct().sorted().collect(GuavaCollectors.toImmutableList());
    }

    public static ImmutableList<Integer> getRound2And3SentenceIds() {
        List<AlignedAnnotation> cfAnnotations = new ArrayList<>();
        try {
            cfAnnotations.addAll(CrowdFlowerDataReader.readAggregatedAnnotationFromFile(cfRound2AnnotationFile));
            cfAnnotations.addAll(CrowdFlowerDataReader.readAggregatedAnnotationFromFile(cfRound3AnnotationFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return cfAnnotations.stream()
                .map(annotation -> annotation.sentenceId).distinct().sorted()
                .collect(GuavaCollectors.toImmutableList());
    }

    public static void printQueryToCSVFile(final ScoredQuery<QAStructureSurfaceForm> query,
                                           final ImmutableList<String> sentence,
                                           final ImmutableList<Integer> goldOptionIds,
                                           final int lineCounter,
                                           final boolean highlightPredicate,
                                           final CSVPrinter csvPrinter) throws IOException {
        // Print to CSV files.
        //   "query_id", "question_confidence", "question_uncertainty", "sent_id", "sentence", "pred_id", "pred_head",
        //   "question_key", "question", "answers", "_golden ", "choice_gold", "choice_gold_reason"
        final QuestionStructure questionStructure = query.getQAPairSurfaceForms().get(0).getQuestionStructures().get(0);
        final AnswerStructure answerStructure = query.getQAPairSurfaceForms().get(0).getAnswerStructures().get(0);
        int predicateIndex = query.isJeopardyStyle() ?
                answerStructure.argumentIndices.get(0) :
                questionStructure.predicateIndex;
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
        csvRow.add(query.isJeopardyStyle() ?
                answerStructure.toString(sentence) :
                questionStructure.toString(sentence)
        );
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

    public static void printQueryToCSVFileNew(final ScoredQuery<QAStructureSurfaceForm> query,
                                              final ImmutableList<String> sentence,
                                              final ImmutableList<Integer> goldOptionIds,
                                              final int lineCounter,
                                              final boolean highlightPredicate,
                                              final String goldReason,
                                              final CSVPrinter csvPrinter) throws IOException {
        // Print to CSV files.
        // "query_id", "sent_id", "sentence", "query_prompt", "options", "_golden ", "choice_gold", "choice_gold_reason",
        // "query_key", "jeopardy_style", "query_confidence", "query_uncertainty"
        final QuestionStructure questionStructure = query.getQAPairSurfaceForms().get(0).getQuestionStructures().get(0);
        final AnswerStructure answerStructure = query.getQAPairSurfaceForms().get(0).getAnswerStructures().get(0);
        int predicateIndex = query.isJeopardyStyle() ?
                answerStructure.argumentIndices.get(0) :
                questionStructure.predicateIndex;

        int sentenceId = query.getSentenceId();
        final String sentenceStr = TextGenerationHelper.renderHTMLSentenceString(sentence, predicateIndex,
                highlightPredicate);
        final ImmutableList<String> options = query.getOptions();
        List<String> csvRow = new ArrayList<>();
        csvRow.add(String.valueOf(lineCounter)); // Query id

        csvRow.add(String.valueOf(sentenceId));
        csvRow.add(String.valueOf(sentenceStr));
        csvRow.add(query.getPrompt());
        csvRow.add(options.stream().collect(Collectors.joining(answerDelimiter)));
        if (goldOptionIds == null) {
            csvRow.add(""); // _gold
            csvRow.add(""); // choice_gold
            csvRow.add(""); // choice_gold_reason
        } else {
            csvRow.add("TRUE");
            csvRow.add(goldOptionIds.stream().map(options::get).collect(Collectors.joining("\n")));
            csvRow.add(goldReason);
        }
        // Query key.
        csvRow.add(query.isJeopardyStyle() ? answerStructure.toString(sentence) : questionStructure.toString(sentence));
        csvRow.add(String.format("%d", query.isJeopardyStyle() ? 1 : 0));
        csvRow.add(String.format("%.3f", query.getPromptScore()));
        csvRow.add(String.format("%.3f", query.getOptionEntropy()));

        csvPrinter.printRecord(csvRow);
    }
}
