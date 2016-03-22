package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.ResponseSimulator;
import edu.uw.easysrl.qasrl.ResponseSimulatorGold;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryGenerator;
import edu.uw.easysrl.qasrl.query.QueryGenerators;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Automatically generate test questions for Crowdflower Experiments.
 * Created by luheng on 3/22/16.
 */
public class CrowdFlowerTestQuestionGenerator {

    /**
     * This only works for non-jeopardy style.
     * @param heldOutSentences
     * @param parseData
     * @param nbestLists
     * @param queryPruningParameters
     * @param goldSimulator
     * @param cfAnnotationFiles
     * @param testQuestionFilePath
     * @param isCheckboxVersion
     * @param highlightPredicates
     * @throws IOException
     */
    public static void printTestQuestions(Set<Integer> heldOutSentences, final ParseData parseData,
                                          final Map<Integer, NBestList> nbestLists,
                                          final QueryPruningParameters queryPruningParameters,
                                          final ResponseSimulatorGold goldSimulator,
                                          final String[] cfAnnotationFiles, final String testQuestionFilePath,
                                          final boolean isCheckboxVersion,
                                          final boolean highlightPredicates) throws IOException {
        // Load test questions from pilot study.
        List<AlignedAnnotation> pilotAnnotations = AlignedAnnotation.getAllAlignedAnnotationsFromPilotStudy();
        // Load test questions from previous annotation.
        List<AlignedAnnotation> cfAnnotations = new ArrayList<>();
        for (String cfAnnotationFile : cfAnnotationFiles) {
            cfAnnotations.addAll(CrowdFlowerDataReader.readAggregatedAnnotationFromFile(cfAnnotationFile));
        }
        pilotAnnotations.stream().forEach(a -> heldOutSentences.add(a.sentenceId));
        cfAnnotations.stream().forEach(a -> heldOutSentences.add(a.sentenceId));

        // Extract high-agreement questions from pilot study.
        List<AlignedAnnotation> agreedAnnotations = new ArrayList<>();
        pilotAnnotations.stream()
                .filter(a -> {
                    int numJudgements = a.getNumAnnotated();
                    int numOptions = a.answerDist.length;
                    return numOptions > 3 && numJudgements >= 3 && a.answerDist[a.goldAnswerIds.get(0)] == numJudgements;
                })
                .forEach(agreedAnnotations::add);
        cfAnnotations.stream()
                .filter(a -> {
                    boolean highAgreement = false;
                    for (int i = 0; i < a.answerDist.length; i++) {
                        highAgreement |= (a.answerDist[i] >= 4);
                    }
                    return highAgreement;
                })
                .forEach(agreedAnnotations::add);
        System.out.println("Number of held-out sentences:\t" + heldOutSentences.size());
        System.out.println("Number of high-agreement annotations:\t" + agreedAnnotations.size());

        // Initialize CSV printer.
        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(testQuestionFilePath)),
                CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeader);
        // Write test questions.
        int numTestQuestions = 0;
        for (AlignedAnnotation test : agreedAnnotations) {
            final int sentenceId = test.sentenceId;
            final ImmutableList<String> sentence = parseData.getSentences().get(sentenceId);
            List<String> agreedAnswers = new ArrayList<>();
            if (test.goldAnswerIds != null) {
                // Inconsistency of answer delimiter..
                agreedAnswers.add(test.answerOptions.get(test.goldAnswerIds.get(0))
                        .replace(" # ", QAPairAggregatorUtils.answerDelimiter));
            } else {
                for (int i = 0; i < test.answerOptions.size(); i++) {
                    if (test.answerDist[i] >= 4) {
                        // Handle inconsistency of bad question option strings.
                        agreedAnswers.add(test.answerOptions.get(i).startsWith("Question is not") ?
                                QueryGenerators.kBadQuestionOptionString :
                                test.answerOptions.get(i));
                        break;
                    }
                }
            }
            String agreedAnswerStr = agreedAnswers.stream()
                    .collect(Collectors.joining(QAPairAggregatorUtils.answerDelimiter));

            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList = isCheckboxVersion ?
                    ExperimentUtils.generateAllCheckboxQueries(sentenceId, sentence, nbestLists.get(sentenceId),
                            queryPruningParameters) :
                    ExperimentUtils.generateAllRadioButtonQueries(sentenceId, sentence, nbestLists.get(sentenceId),
                            queryPruningParameters);
            for (ScoredQuery<QAStructureSurfaceForm> query : queryList) {
                if (query.getPredicateId().getAsInt() == test.predicateId &&
                        query.getPrompt().equalsIgnoreCase(test.question)) {
                    final ImmutableList<String> options = query.getOptions();
                    final ImmutableList<Integer> goldOptionIds = goldSimulator.respondToQuery(query);
                    final ImmutableList<String> goldAnswers = goldOptionIds.stream().map(options::get)
                            .collect(GuavaCollectors.toImmutableList());
                    final String goldAnswerStr = goldAnswers.stream()
                            .collect(Collectors.joining(QAPairAggregatorUtils.answerDelimiter));
                    if (goldAnswerStr.equalsIgnoreCase(agreedAnswerStr)) {
                        CrowdFlowerDataUtils.printQueryToCSVFile(query,
                                sentence,
                                goldOptionIds,
                                10000 + numTestQuestions /* lineCounter */,
                                highlightPredicates,
                                csvPrinter);
                        numTestQuestions ++;
                    } else {
                        System.err.println(test.toString() + "---\n" + query.toString(sentence) + "---\n" + goldAnswerStr);
                    }
                }
            }
        }
        System.out.println("Wrote " + numTestQuestions + " test questions to file.");
        csvPrinter.close();
    }

}
