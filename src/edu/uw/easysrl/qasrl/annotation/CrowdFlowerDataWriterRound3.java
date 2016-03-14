package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.pomdp.POMDP;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * For analysis purposes for now.
 * Created by luheng on 2/9/16.
 */
public class CrowdFlowerDataWriterRound3 {
    static final int nBest = 100;
    static final int maxNumSentences = 300;
    static final int maxNumSentencesPerFile = 100;
    static final int numRandomSamples = 1; // 10;
    static final int randomSeed = 104743;

    static final int countEvery = 100;
    static final boolean skipPPQuestions = true;
    static final boolean skipPronounQuestions = false;
    private static boolean highlightPredicate = false;
    private static int minAgreementForTestQuestions = 4;

    // Option pruning.
    private static final int maxNumOptionsPerQuestion = 8;
    static {
        GroupedQuery.maxNumNonNAOptionsPerQuery = maxNumOptionsPerQuestion - 2;
    }
    // Query pruning parameters.
    private static QueryPruningParameters queryPruningParameters = new QueryPruningParameters(
            1,    /* top K */
            0.1,  /* min question confidence */
            0.05, /* min answer confidence */
            0.05  /* min attachment entropy */
    );

    // Fields for Crowdflower test questions.
    private static final String[] csvHeader = {
            "query_id", "question_confidence", "question_uncertainty", "sent_id", "sentence", "pred_id", "pred_head",
            "question_key", "question", "answers", "_golden ", "choice_gold", "choice_gold_reason"};

    private static final String answerDelimiter = " ### ";

    private static final String cfRound1AnnotationFilePath = "./Crowdflower_data/f878213.csv";

    private static final String csvOutputFilePrefix = "./Crowdflower_temp/crowdflower_dev_100best";

    // Sentences that happened to appear in instructions ...
    private static final int[] otherHeldOutSentences = { 1695, };

    public static void main(String[] args) throws IOException {
        POMDP learner = new POMDP(nBest, 10000 /* horizon */, 0.0 /* money penalty */);
        ResponseSimulatorGold goldSimulator = new ResponseSimulatorGold(learner.goldParses, new QuestionGenerator(),
                true /* match by label */);
        learner.setQueryPruningParameters(queryPruningParameters);
        Set<Integer> heldOutSentences = new HashSet<>();
        // Print test questions.
        try {
            printTestQuestions(learner, goldSimulator, heldOutSentences);
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<double[]> avgNumQueries = new ArrayList<>(),
                avgOptionsPerQuery = new ArrayList<>(),
                avgNumGoldNAQueries = new ArrayList<>(),
                avgNumOverlappingAnswers = new ArrayList<>(),
                avgNumUnlistedAnswers = new ArrayList<>(),
                avgNumPronounQueries = new ArrayList<>(),
                avgNumGoldPronounQueries = new ArrayList<>(),
                avgNumMultiArgQueries = new ArrayList<>(),
                avgNumGoldMultiArgQueries = new ArrayList<>(),
                oneBestF1 = new ArrayList<>(),
                rerankF1 = new ArrayList<>(),
                oracleF1 = new ArrayList<>(),
                gainF1 = new ArrayList<>();
        Random random = new Random(randomSeed);
        List<Integer> sentenceIds = learner.allParses.keySet().stream()
                .filter(sid -> !heldOutSentences.contains(sid))
                .collect(Collectors.toList());
        for (int i = 1; i < sentenceIds.size(); i++) {
            if (i % countEvery == 0) {
                avgOptionsPerQuery.add(new double[numRandomSamples]);
                avgNumQueries.add(new double[numRandomSamples]);
                avgNumGoldNAQueries.add(new double[numRandomSamples]);
                avgNumUnlistedAnswers.add(new double[numRandomSamples]);
                avgNumOverlappingAnswers.add(new double[numRandomSamples]);
                avgNumPronounQueries.add(new double[numRandomSamples]);
                avgNumGoldPronounQueries.add(new double[numRandomSamples]);
                avgNumMultiArgQueries.add(new double[numRandomSamples]);
                avgNumGoldMultiArgQueries.add(new double[numRandomSamples]);
                oneBestF1.add(new double[numRandomSamples]);
                rerankF1.add(new double[numRandomSamples]);
                oracleF1.add(new double[numRandomSamples]);
                gainF1.add(new double[numRandomSamples]);
            }
        }
        assert maxNumSentences % maxNumSentencesPerFile == 0;
        CSVPrinter csvPrinter = null;
        for (int r = 0; r < numRandomSamples; r++) {
            Collections.shuffle(sentenceIds, random);
            int lineCounter = 0;
            int fileCounter = 0;
            int queryCounter = 0;
            int optionCounter = 0;
            // Queries that has only one option except for N/A.
            int numNAQueries = 0,
                    numUnlistedAnswers = 0,
                    numOverlappingAnswers = 0,
                    numPronounQueries = 0,
                    numGoldPronounQueries = 0,
                    numMultiArgQueries = 0,
                    numGoldMultiArgQueries =0;
            List<Integer> annotatedSentences = new ArrayList<>();
            Results rerank = new Results(),
                    oracle = new Results(),
                    oneBest = new Results();
            // Process questions.
            for (int sentenceId : sentenceIds) {
                List<GroupedQuery> queries = getQueriesForSentence(learner, sentenceId);
                // Print query to .csv file.
                if (r == 0 && annotatedSentences.size() < maxNumSentences) {
                    int numSentences = annotatedSentences.size();
                    if (numSentences <= maxNumSentences && numSentences % maxNumSentencesPerFile == 0) {
                        // Close previous CSV file.
                        if (lineCounter > 0) {
                            csvPrinter.close();
                        }
                        // Create a new CSV file.
                        if (numSentences < maxNumSentences) {
                            csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                                    String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter))),
                                    CSVFormat.EXCEL.withRecordSeparator("\n"));
                            csvPrinter.printRecord((Object[]) csvHeader);
                            fileCounter ++;
                        }
                    }
                    // Write query to file.
                    for (GroupedQuery query : queries) {
                        printQueryToCSVFile(query, -1 /* gold option id */, lineCounter, csvPrinter);
                        lineCounter ++;
                    }
                }
                // Simulation and count.
                for (GroupedQuery query : queries) {
                    List<GroupedQuery.AnswerOption> options = query.getAnswerOptions();
                    int numOptions = options.size();
                    int oracleId = learner.getOracleParseId(sentenceId);
                    Response oracleResponse = new Response();
                    Response goldResponse = goldSimulator.answerQuestion(query);
                    GroupedQuery.AnswerOption goldOption = options.get(goldResponse.chosenOptions.get(0));

                    boolean oracleIsNA = false;
                    boolean goldIsNA = GroupedQuery.BadQuestionOption.class.isInstance(goldOption);
                    boolean goldIsUnlistedAnswer = GroupedQuery.NoAnswerOption.class.isInstance(goldOption);
                    boolean hasOverlappingAnswers = false;
                    for (int i = 0; i < numOptions; i++) {
                        GroupedQuery.AnswerOption option = options.get(i);
                        if (option.getParseIds().contains(oracleId)) {
                            oracleResponse.add(i);
                            if (GroupedQuery.BadQuestionOption.class.isInstance(option)) {
                                oracleIsNA = true;
                            }
                        }
                        for (int j = 0; j < numOptions; j++) {
                            GroupedQuery.AnswerOption option2 = options.get(j);
                            if (i != j && option2.getAnswer().contains(option.getAnswer())) {
                                hasOverlappingAnswers = true;
                            }
                        }
                    }
                    if (goldIsNA && oracleIsNA) {
                        numNAQueries++;
                    }
                    if (goldIsUnlistedAnswer) {
                        numUnlistedAnswers ++;
                    }
                    if (hasOverlappingAnswers) {
                        numOverlappingAnswers ++;
                    }
                    if (QualityControl.queryContainsPronoun(query)) {
                        numPronounQueries ++;
                        if (QualityControl.optionContainsPronoun(goldOption)) {
                            numGoldPronounQueries ++;
                        }
                    }
                    if (QualityControl.queryContainsMultiArg(query)) {
                        numMultiArgQueries ++;
                        if (QualityControl.optionContainsMultiArg(goldOption)) {
                            numGoldMultiArgQueries ++;
                        }
                    }
                    if (r == 0 && lineCounter < 500) {
                        if (QualityControl.queryContainsMultiArg(query) && !QualityControl.optionContainsMultiArg(goldOption)) {
                            System.out.println("OracleID=" + learner.getOracleParseId(sentenceId));
                            //System.out.println(query.getDebuggingInfo(oracleResponse));
                            System.out.println(query.getDebuggingInfo(goldResponse) + goldResponse.debugInfo);
                        }
                    }
                    learner.receiveObservationForQuery(query, oracleResponse);
                    //learner.receiveObservationForQuery(query, goldResponse);
                    optionCounter += query.getAnswerOptions().size();
                }
                queryCounter += queries.size();
                annotatedSentences.add(sentenceId);
                rerank.add(learner.getRerankedF1(sentenceId));
                oracle.add(learner.getOracleF1(sentenceId));
                oneBest.add(learner.getOneBestF1(sentenceId));
                // Compute stats.
                if (annotatedSentences.size() % countEvery == 0) {
                    int k = annotatedSentences.size() / countEvery - 1;
                    avgNumQueries.get(k)[r]             = queryCounter;
                    avgNumGoldNAQueries.get(k)[r]       = numNAQueries;
                    avgNumUnlistedAnswers.get(k)[r]     = numUnlistedAnswers;
                    avgNumOverlappingAnswers.get(k)[r]  = numOverlappingAnswers;
                    avgNumPronounQueries.get(k)[r]      = numPronounQueries;
                    avgNumGoldPronounQueries.get(k)[r]  = numGoldPronounQueries;
                    avgNumMultiArgQueries.get(k)[r]     = numMultiArgQueries;
                    avgNumGoldMultiArgQueries.get(k)[r] = numGoldMultiArgQueries;
                    avgOptionsPerQuery.get(k)[r]        = 1.0 * optionCounter / queryCounter;
                    oneBestF1.get(k)[r] = 100.0 * oneBest.getF1();
                    rerankF1.get(k)[r]  = 100.0 * rerank.getF1();
                    oracleF1.get(k)[r]  = 100.0 * oracle.getF1();
                    gainF1.get(k)[r]    = rerankF1.get(k)[r] - oneBestF1.get(k)[r];
                }
            }
        }
        // Print aggregated results.
        for (int k = 0; k < avgNumQueries.size(); k++) {
            System.out.println(String.format("On %d sentences:", (k + 1) * 100));
            System.out.println(String.format("Avg. number of queries:\t%.3f (%.3f)",
                    getAverage(avgNumQueries.get(k)), getStd(avgNumQueries.get(k))));
            System.out.println(String.format("Avg. number of N/A queries:\t%.3f (%.3f)",
                    getAverage(avgNumGoldNAQueries.get(k)), getStd(avgNumGoldNAQueries.get(k))));
            System.out.println(String.format("Avg. number of queries with gold unlisted answers:\t%.3f (%.3f)",
                    getAverage(avgNumUnlistedAnswers.get(k)), getStd(avgNumUnlistedAnswers.get(k))));
            System.out.println(String.format("Avg. number of queries with overlapping answers:\t%.3f (%.3f)",
                    getAverage(avgNumOverlappingAnswers.get(k)), getStd(avgNumOverlappingAnswers.get(k))));
            System.out.println(String.format("Percentage of N/A queries:\t%.3f%%",
                    100.0 * getAverage(avgNumGoldNAQueries.get(k)) / getAverage(avgNumQueries.get(k))));
            System.out.println(String.format("Avg. number of options per query:\t%.3f (%.3f)",
                    getAverage(avgOptionsPerQuery.get(k)), getStd(avgOptionsPerQuery.get(k))));

            double avgQ =  getAverage(avgNumQueries.get(k));
            double avgP1 = getAverage(avgNumPronounQueries.get(k)),
                   avgP2 = getAverage(avgNumGoldPronounQueries.get(k)),
                   avgM1 = getAverage(avgNumMultiArgQueries.get(k)),
                   avgM2 = getAverage(avgNumGoldMultiArgQueries.get(k));
            System.out.println(String.format("Avg. number of pronoun queries:\t%.3f (%.3f)\t%.3f%%",
                    avgP1, getStd(avgNumPronounQueries.get(k)),          100.0 * avgP1 / avgQ));
            System.out.println(String.format("Avg. number of gold pronoun queries:\t%.3f (%.3f)\t%.3f%%",
                    avgP2, getStd(avgNumGoldPronounQueries.get(k)),      100.0 * avgP2 / avgQ));
            System.out.println(String.format("Avg. number of multi-arg queries:\t%.3f (%.3f)\t%.3f%%",
                    avgM1, getStd(avgNumMultiArgQueries.get(k)),         100.0 * avgM1 / avgQ));
            System.out.println(String.format("Avg. number of gold mult-arg queries:\t%.3f (%.3f)\t%.3f%%",
                    avgM2, getAverage(avgNumGoldMultiArgQueries.get(k)), 100.0 * avgM2 / avgQ));

            System.out.println(String.format("Avg. 1-best F1:\t%.3f%%\t%.3f%%",
                    getAverage(oneBestF1.get(k)), getStd(oneBestF1.get(k))));
            System.out.println(String.format("Avg. rerank F1:\t%.3f%%\t%.3f%%",
                    getAverage(rerankF1.get(k)), getStd(rerankF1.get(k))));
            System.out.println(String.format("Avg. oracle F1:\t%.3f%%\t%.3f%%",
                    getAverage(oracleF1.get(k)), getStd(oracleF1.get(k))));
            System.out.println(String.format("Avg. F1 gain  :\t%.3f%%\t%.3f%%",
                    getAverage(gainF1.get(k)), getStd(gainF1.get(k))));
        }
    }

    private static void printTestQuestions(POMDP learner, ResponseSimulator goldSimulator,
                                           Set<Integer> heldOutSentences) throws IOException {
        // Load test questions from pilot study.
        List<AlignedAnnotation> pilotAnnotations = AlignedAnnotation.getAllAlignedAnnotationsFromPilotStudy();
        // Load test questions from previous annotation.
        List<AlignedAnnotation> cfRound1Annotations = CrowdFlowerDataReader.readAggregatedAnnotationFromFile(
                cfRound1AnnotationFilePath, false /* check box */);
        pilotAnnotations.stream().forEach(a -> heldOutSentences.add(a.sentenceId));
        cfRound1Annotations.stream().forEach(a -> heldOutSentences.add(a.sentenceId));
        for (int sid : otherHeldOutSentences) {
            heldOutSentences.add(sid);
        }
        // Extract high-agreement questions from pilot study.
        List<AlignedAnnotation> agreedAnnotations = new ArrayList<>();
        pilotAnnotations.stream()
                .filter(a -> {
                    int numJudgements = a.getNumAnnotated();
                    int numOptions = a.answerDist.length;
                    return numOptions > 3 && numJudgements >= 3 && a.answerDist[a.goldAnswerId] == numJudgements;
                })
                .forEach(agreedAnnotations::add);
        cfRound1Annotations.stream()
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
        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                String.format("%s_test.csv", csvOutputFilePrefix))), CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) csvHeader);
        // Write test questions.
        int numTestQuestions = 0;
        for (AlignedAnnotation test : agreedAnnotations) {
            int sentenceId = test.sentenceId;
            String agreedAnswer = "";
            if (test.goldAnswerId >= 0) {
                // Inconsistency of answer delimiter..
                agreedAnswer = test.answerOptions.get(test.goldAnswerId)
                        .replace(" # ", QuestionAnswerPair.answerDelimiter);
            } else {
                for (int i = 0; i < test.answerOptions.size(); i++) {
                    if (test.answerDist[i] >= minAgreementForTestQuestions) {
                        agreedAnswer = test.answerOptions.get(i);
                        break;
                    }
                }
            }
            for (GroupedQuery query : getQueriesForSentence(learner, sentenceId)) {
                if (query.getPredicateIndex() == test.predicateId &&
                        query.getQuestion().equalsIgnoreCase(test.question)) {
                    Response gold = goldSimulator.answerQuestion(query);
                    int goldAnswerId = gold.chosenOptions.get(0);
                    GroupedQuery.AnswerOption goldOption = query.getAnswerOptions().get(goldAnswerId);
                    boolean agreedMatchesGold = goldOption.getAnswer().equalsIgnoreCase(agreedAnswer) ||
                            (GroupedQuery.BadQuestionOption.class.isInstance(goldOption) &&
                                    agreedAnswer.startsWith("Question is not"));
                    if (agreedMatchesGold) {
                        printQueryToCSVFile(query, goldAnswerId, 10000 + numTestQuestions /* lineCounter */,
                                csvPrinter);
                        numTestQuestions ++;
                    } else {
                        System.err.println(test.toString() + "---\n" + query.getDebuggingInfo(gold) + gold.debugInfo);
                    }
                }
            }
        }
        System.out.println("Wrote " + numTestQuestions + " test questions to file.");
        csvPrinter.close();
    }

    private static List<GroupedQuery> getQueriesForSentence(POMDP learner, int sentenceId) {
        learner.initializeForSentence(sentenceId);
        return learner.getQueryPool().stream()
                .filter(q ->
                        !(skipPPQuestions && QualityControl.propositionalCategories.contains(q.getCategory())) &&
                        !(skipPronounQuestions && QualityControl.queryContainsPronoun(q)))
                .collect(Collectors.toList());
    }

    /**
     *
     * @param query
     * @param goldAnswerId: -1 if gold is unknown (means this line is not a test question).
     * @param lineCounter
     * @param csvPrinter
     */
    private static void printQueryToCSVFile(final GroupedQuery query, int goldAnswerId, int lineCounter,
                                            final CSVPrinter csvPrinter) throws IOException {
        // Print to CSV files.
        // "query_id", "question_confidence", "question_uncertainty", "sent_id", "sentence", "pred_id", "pred_head",
        // "question_key", "question", "answers", "_golden ", "choice_gold", "choice_gold_reason"
        int predicateIndex = query.getPredicateIndex();
        int sentenceId = query.getSentenceId();
        final List<String> sentence = query.getSentence();
        final String sentenceStr = TextGenerationHelper.renderHTMLSentenceString(sentence, predicateIndex,
                highlightPredicate);
        final List<String> answerStrings = query.getAnswerOptions().stream()
                .map(GroupedQuery.AnswerOption::getAnswer) //.replace(" &&& ", " <strong> & </strong> "))
                .collect(Collectors.toList());
        List<String> csvRow = new ArrayList<>();
        csvRow.add(String.valueOf(lineCounter));
        csvRow.add(String.format("%.3f", query.questionConfidence));
        csvRow.add(String.format("%.3f", query.attachmentUncertainty));
        csvRow.add(String.valueOf(sentenceId));
        csvRow.add(String.valueOf(sentenceStr));
        csvRow.add(String.valueOf(predicateIndex));
        csvRow.add(sentence.get(predicateIndex));
        csvRow.add(query.getQuestionKey());
        csvRow.add(query.getQuestion());
        csvRow.add(answerStrings.stream().collect(Collectors.joining(answerDelimiter)));
        if (goldAnswerId < 0) {
            csvRow.add(""); // _gold
            csvRow.add(""); // choice_gold
            csvRow.add(""); // choice_gold_reason
        } else {
            csvRow.add("TRUE");
            csvRow.add(answerStrings.get(goldAnswerId));
            csvRow.add("Based on high-agreement of workers.");
        }
        csvPrinter.printRecord(csvRow);
    }

    private static double getAverage(final double[] arr) {
        double sum = .0;
        for (double a : arr) sum += a;
        return sum / arr.length;
    }

    private static double getStd(final double[] arr) {
        double std = .0;
        double mean = getAverage(arr);
        for (double a : arr) std += (a - mean) * (a - mean);
        return Math.sqrt(std / arr.length);
    }
}