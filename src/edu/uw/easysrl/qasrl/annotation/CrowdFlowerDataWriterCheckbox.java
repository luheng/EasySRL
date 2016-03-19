package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.RawQuestionAnswerPair;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Group query by sentences.
 * Created by luheng on 2/9/16.
 */
public class CrowdFlowerDataWriterCheckbox {
    static final int nBest = 100;
    static final int maxNumSentences = 100;
    // static final int maxNumQueries = 2000;

    // TODO: hold out sentences we used in examples and test questions.
    static final int maxNumSentencesPerFile = 20;

    static final int numRandomSamples = 10;
    static final int randomSeed = 4;

    static final int countEvery = 100;

    private static final double minQuestionConfidence = 0.1;
    private static final double minAnswerEntropy = 0.1;

    private static boolean highlightPredicate = false;
    private static boolean skipBinaryQueries = true;


    private static final int maxNumOptionsPerQuestion = 6;
    private static final int maxNumOptionsPerTestQuestion = 6;
    static {
        GroupedQuery.maxNumNonNAOptionsPerQuery = maxNumOptionsPerQuestion - 2;
    }

    // Fields for Crowdflower test questions.
    private static final String[] csvHeader = {
            "query_id", "question_confidence", "question_uncertainty", "sent_id", "sentence", "pred_id", "pred_head",
            "question_key", "question", "answers", "_golden ", "choice_gold", "choice_gold_reason"};

    private static final String answerDelimiter = " ### ";

    private static final String csvOutputFilePrefix = "_cb_crowdflower_dev_100best";

    // Sentences that happened to appear in instructions ...
    private static final int[] otherHeldOutSentences = { 1695, };

    public static void main(String[] args) throws IOException {
        if(args.length > 0 && args[0].equals("single")) {
            runSingleAnswerQueries();
        } else {
            runMultiAnswerQueries();
        }
    }

    public static void runMultiAnswerQueries() throws IOException {
        // this isn't actually going to do any reranking yet
        ActiveLearningMultiResponse baseLearner = new ActiveLearningMultiResponse(nBest);
        List<double[]>  avgNumQueries = new ArrayList<>(),
                avgOptionsPerQuery = new ArrayList<>(),
                avgNumBinaryQueries = new ArrayList<>(),
                oneBestF1 = new ArrayList<>(),
                rerankF1 = new ArrayList<>(),
                oracleF1 = new ArrayList<>(),
                gainF1 = new ArrayList<>();
        MultiResponseSimulator responseSimulator = new MultiResponseSimulator(baseLearner.goldParses);
        Random random = new Random(randomSeed);
        int[] ids = { 90, 99, 156, 192,
                199, 217, 224, 268,
                294, 397, 444, 469,
                563, 705, 762, 992,
                1016, 1078, 1105, 1124,
                1199, 1232, 1261, 1304,
                1305, 1489, 1495, 1516,
                1564, 1674, 1695 };
        int[] dataIds = {
                11, 27, 50, 53,
                55, 78, 86, 105,
                149, 188, 191, 229,
                241, 242, 248, 255,
                290, 309, 359, 393,
                425, 430, 433, 443,
                465, 492, 532, 553,
                554, 568, 656, 658,
                708, 709, 714, 735,
                754, 763, 766, 825,
                896, 935, 946, 1017,
                1036, 1050, 1051, 1053,
                1060, 1063, 1109, 1116,
                1150, 1159, 1200, 1274,
                1315, 1328, 1367, 1370,
                1378, 1384, 1388, 1410,
                1419, 1423, 1482, 1490,
                1615, 1667, 1669, 1693,
                1712, 1747, 1864, 1886,
                1907
        };
        List<Integer> sentenceIds = new ArrayList<Integer>(ids.length);
        for(int i : ids) {
            sentenceIds.add(i);
        }

        int numSentences = sentenceIds.size();

        for (int i = 1; i < sentenceIds.size(); i++) {
            if (i % countEvery == 0) {
                avgOptionsPerQuery.add(new double[numRandomSamples]);
                avgNumQueries.add(new double[numRandomSamples]);
                avgNumBinaryQueries.add(new double[numRandomSamples]);
                oneBestF1.add(new double[numRandomSamples]);
                rerankF1.add(new double[numRandomSamples]);
                oracleF1.add(new double[numRandomSamples]);
                gainF1.add(new double[numRandomSamples]);
            }
        }

        assert maxNumSentences % maxNumSentencesPerFile == 0;
        CSVPrinter csvPrinter = null;

        ActiveLearningMultiResponse learner = new ActiveLearningMultiResponse(baseLearner);
        Collections.shuffle(sentenceIds, random);
        int lineCounter = 0;
        int fileCounter = 0;
        int queryCounter = 0;
        int[] queryHist = new int[30];
        int queryHistOverflow = 0;
        int optionCounter = 0;
        int goldOptionCounter = 0;
        int[] goldOptionHist = new int[30];
        int goldOptionHistOverflow = 0;
        Map<RawQuestionAnswerPair.QuestionType, int[]> questionTypeHists = new HashMap<>();
        final int numQueriesPerOptionCount = 1;
        List<Integer> annotatedSentences = new ArrayList<>();

        // Initialize CSV printer.
        csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(String.format("%s_test.csv", csvOutputFilePrefix))),
                CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) csvHeader);

        // Write test questions.
        // TODO get test questions from somewhere
        List<Integer> testSentenceIds = new LinkedList<>();
        int numTestQuestions = testSentenceIds.size();
        for (Integer sentenceId : testSentenceIds) {
            List<String> sentence = baseLearner.getSentenceById(sentenceId);
            List<Parse> parses = baseLearner.allParses.get(sentenceId);
            QueryGeneratorBothWays queryGenerator = new QueryGeneratorBothWays(sentenceId, sentence, parses);
            List<MultiQuery> queries = queryGenerator.getAllMaximalQueries();
            for (MultiQuery query : queries) {
                final Set<String> goldAnswers = responseSimulator.respondToQuery(query);
                printMultiQueryToCSVFile(sentence, query, Optional.of(goldAnswers), 10000 + numTestQuestions /* lineCounter */,
                        csvPrinter);
                numTestQuestions ++;
            }
        }
        System.out.println("Wrote " + numTestQuestions + " test questions to file.");
        csvPrinter.close();

        // open a new file
        csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter))),
                CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) csvHeader);
        fileCounter ++;
        // Process other questions
        for (int sentenceId : sentenceIds) {
            // Print queries to the .csv file
            final List<String> sentence = learner.getSentenceById(sentenceId);
            List<Parse> parses = baseLearner.allParses.get(sentenceId);
            QueryGeneratorBothWays queryGenerator = new QueryGeneratorBothWays(sentenceId, sentence, parses);
            List<MultiQuery> queries = queryGenerator
                    .getAllMaximalQueries()
                    .stream()
                    .filter(q -> !q.isJeopardyStyle())
                    .collect(Collectors.toList());
            // make sure we're randomly sampling queries
            Collections.shuffle(queries, random);
            Map<Integer, Integer> optionCountsForSentence = new HashMap<>();
            for (MultiQuery query : queries) {
                final int numOptions = query.options.size();
                if(!optionCountsForSentence.containsKey(numOptions)) {
                    optionCountsForSentence.put(numOptions, 0);
                }
                final int queryCountForOptionCount = optionCountsForSentence.get(numOptions);
                if (queryCountForOptionCount >= numQueriesPerOptionCount) {
                    continue;
                }
                optionCountsForSentence.put(numOptions, queryCountForOptionCount + 1);

                printMultiQueryToCSVFile(sentence, query, Optional.empty(), lineCounter, csvPrinter);
                lineCounter ++;

                // print and log fun stuff
                System.out.println("SID=" + sentenceId + "\t" + learner.getSentenceScore(sentenceId));
                System.out.println(TextGenerationHelper.renderString(sentence));
                System.out.println(query.toStringWithResponse(responseSimulator));
                Set<String> gold = responseSimulator.respondToQuery(query);
                // learner.respondToQuery(query, gold);
                goldOptionCounter += gold.size();
                optionCounter += numOptions;
                if(numOptions >= queryHist.length) {
                    queryHistOverflow++;
                    goldOptionHistOverflow++;
                } else {
                    queryHist[numOptions]++;
                    goldOptionHist[numOptions] += gold.size();
                    for(RawQuestionAnswerPair.QuestionType qType : query.getQuestionTypes()) {
                        if(!questionTypeHists.containsKey(qType)) {
                            questionTypeHists.put(qType, new int[30]);
                        }
                        questionTypeHists.get(qType)[numOptions]++;
                    }
                }
                queryCounter++;
                // annotatedSentences.add(sentenceId);
                // if (annotatedSentences.size() % countEvery == 0) {
                //     int k = annotatedSentences.size() / countEvery - 1;
                //     avgNumQueries.get(k)[r] = queryCounter;
                //     avgNumBinaryQueries.get(k)[r] = numBinaryQueries;
                //     avgOptionsPerQuery.get(k)[r] = 1.0 * optionCounter / queryCounter;
                //     oneBestF1.get(k)[r] = 100.0 * learner.getOneBestF1(annotatedSentences).getF1();
                //     rerankF1.get(k)[r]  = 100.0 * learner.getRerankedF1(annotatedSentences).getF1();
                //     oracleF1.get(k)[r]  = 100.0 * learner.getOracleF1(annotatedSentences).getF1();
                //     gainF1.get(k)[r]    = rerankF1.get(k)[r] - oneBestF1.get(k)[r];
                // }
            }
        }

        System.out.println(String.format("Sentences: %d", numSentences));
        System.out.println(String.format("Queries: %d", queryCounter));
        System.out.println(String.format("Average queries per sentence: %f", (1.0 * queryCounter)/numSentences));
        System.out.println(String.format("Average options per query: %f", (1.0 * optionCounter)/queryCounter));
        System.out.println(String.format("Proportion of correct options: %f", (1.0 * goldOptionCounter)/optionCounter));
        for(int i = 0; i < queryHist.length; i++) {
            if(queryHist[i] != 0) {
                System.out.println(String.format("Number of questions with %d options: %d (%.2f gold)", i, queryHist[i], (1.0 * goldOptionHist[i])/(i * queryHist[i])));
                for(RawQuestionAnswerPair.QuestionType qType : questionTypeHists.keySet()) {
                    if(questionTypeHists.get(qType)[i] != 0) {
                        System.out.println(String.format("\tOf type %s: %.2f", qType, (1.0 * questionTypeHists.get(qType)[i])/queryHist[i]));
                    }
                }
            }
        }
        if(queryHistOverflow > 0) {
            System.out.println(String.format("Number of questions with %d or more options: %d (%d gold options)",
                    queryHist.length,
                    queryHistOverflow,
                    goldOptionHistOverflow));
        }


        // print final interesting stats
        // for (int k = 0; k < avgNumQueries.size(); k++) {
        //     System.out.println(String.format("On %d sentences:", (k + 1) * 100));
        //     System.out.println(String.format("Avg. number of queries:\t%.3f (%.3f)",
        //             getAverage(avgNumQueries.get(k)),
        //             getStd(avgNumQueries.get(k))));
        //     System.out.println(String.format("Avg. number of binary queries:\t%.3f (%.3f)",
        //             getAverage(avgNumBinaryQueries.get(k)),
        //             getStd(avgNumBinaryQueries.get(k))));
        //     System.out.println(String.format("Avg. number of options per query:\t%.3f (%.3f)",
        //             getAverage(avgOptionsPerQuery.get(k)),
        //             getStd(avgOptionsPerQuery.get(k))));
        //     System.out.println(String.format("Avg. 1-best F1:\t%.3f%%\t%.3f%%",
        //             getAverage(oneBestF1.get(k)),
        //             getStd(oneBestF1.get(k))));
        //     System.out.println(String.format("Avg. rerank F1:\t%.3f%%\t%.3f%%",
        //             getAverage(rerankF1.get(k)),
        //             getStd(rerankF1.get(k))));
        //     System.out.println(String.format("Avg. oracle F1:\t%.3f%%\t%.3f%%",
        //             getAverage(oracleF1.get(k)),
        //             getStd(oracleF1.get(k))));
        //     System.out.println(String.format("Avg. F1 gain  :\t%.3f%%\t%.3f%%",
        //             getAverage(gainF1.get(k)),
        //             getStd(gainF1.get(k))));
        // }
    }

    public static void runSingleAnswerQueries() throws IOException {
        List<AlignedAnnotation> pilotAnnotations = AlignedAnnotation.getAllAlignedAnnotationsFromPilotStudy();
        Set<Integer> heldOutSentences = pilotAnnotations.stream().map(a -> a.sentenceId).collect(Collectors.toSet());
        List<AlignedAnnotation> agreedAnnotations = pilotAnnotations.stream()
                .filter(annot -> {
                    int numJudgements = annot.getNumAnnotated();
                    int numOptions = annot.answerDist.length;
                    return numOptions > 3 && /* numOptions <= maxNumOptionsPerTestQuestion && */
                            numJudgements >= 3 && annot.answerDist[annot.goldAnswerId] == numJudgements;
                }).collect(Collectors.toList());
        for (int sid : otherHeldOutSentences) {
            heldOutSentences.add(sid);
        }
        System.out.println("Number of held-out sentences:\t" + heldOutSentences.size());
        System.out.println("Number of high-agreement annotations:\t" + agreedAnnotations.size());

        ActiveLearningBySentence baseLearner = new ActiveLearningBySentence(nBest);
        List<double[]>  avgNumQueries = new ArrayList<>(),
                avgOptionsPerQuery = new ArrayList<>(),
                avgNumBinaryQueries = new ArrayList<>(),
                oneBestF1 = new ArrayList<>(),
                rerankF1 = new ArrayList<>(),
                oracleF1 = new ArrayList<>(),
                gainF1 = new ArrayList<>();
        ResponseSimulator responseSimulator = new ResponseSimulatorGold(baseLearner.goldParses, new QuestionGenerator(),
                true /* Allow label match */);
        Random random = new Random(randomSeed);
        List<Integer> sentenceIds = baseLearner.getAllSentenceIds().stream()
                .filter(sid -> !heldOutSentences.contains(sid))
                .collect(Collectors.toList());

        for (int i = 1; i < sentenceIds.size(); i++) {
            if (i % countEvery == 0) {
                avgOptionsPerQuery.add(new double[numRandomSamples]);
                avgNumQueries.add(new double[numRandomSamples]);
                avgNumBinaryQueries.add(new double[numRandomSamples]);
                oneBestF1.add(new double[numRandomSamples]);
                rerankF1.add(new double[numRandomSamples]);
                oracleF1.add(new double[numRandomSamples]);
                gainF1.add(new double[numRandomSamples]);
            }
        }

        assert maxNumSentences % maxNumSentencesPerFile == 0;
        CSVPrinter csvPrinter = null;

        for (int r = 0; r < numRandomSamples; r++) {
            ActiveLearningBySentence learner = new ActiveLearningBySentence(baseLearner);
            Collections.shuffle(sentenceIds, random);
            int lineCounter = 0;
            int fileCounter = 0;
            int queryCounter = 0;
            int optionCounter = 0;
            // Queries that has only one option except for N/A.
            int numBinaryQueries = 0;
            List<Integer> annotatedSentences = new ArrayList<>();

            if (r == 0) {
                // Initialize CSV printer.
                csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                        String.format("%s_test.csv", csvOutputFilePrefix))),
                        CSVFormat.EXCEL.withRecordSeparator("\n"));
                csvPrinter.printRecord((Object[]) csvHeader);

                // Write test questions.
                int numTestQuestions = 0;
                for (AlignedAnnotation test : agreedAnnotations) {
                    int sentenceId = test.sentenceId;
                    String goldAnswer = test.answerOptions.get(test.goldAnswerId).replace(" # ", " _AND_ ");
                    List<GroupedQuery> queries = learner.getQueriesBySentenceId(sentenceId);
                    for (GroupedQuery query : queries) {
                        // TODO: remove later
                        //System.err.println(query.getDebuggingInfo(responseSimulator.answerQuestion(query)));

                        // Match predicate ID, question key and gold answer span.
                        // TODO XXX predicateId is no longer populated in AlignedAnnotation;
                        // instead we match based on getAnnotationKey(); but this needs to reconcile with queries;
                        // so instead we should have a method on Annotation/AlignedAnnotation determines matching for given queries.
                        if (false) {
                            // query.getPredicateIndex() == test.predicateId &&
                            //     query.getQuestion().equalsIgnoreCase(test.question)) {
                            int goldAnswerId = -1;
                            for (int i = 0; i < query.getAnswerOptions().size(); i++) {
                                GroupedQuery.AnswerOption ao = query.getAnswerOptions().get(i);
                                if (ao.getAnswer().equalsIgnoreCase(goldAnswer) ||
                                        (GroupedQuery.BadQuestionOption.class.isInstance(ao) &&
                                                goldAnswer.startsWith("Question is not"))) {
                                    goldAnswerId = i;
                                    break;
                                }
                            }
                            if (goldAnswerId >= 0) {
                                printQueryToCSVFile(query, goldAnswerId, 10000 + numTestQuestions /* lineCounter */,
                                        csvPrinter);
                                numTestQuestions ++;
                            } else {
                                System.err.println(test.toString() + "\n---\n"
                                        + query.getDebuggingInfo(responseSimulator.answerQuestion(query)));
                            }
                        }
                    }
                }
                System.out.println("Wrote " + numTestQuestions + " test questions to file.");
                csvPrinter.close();
            }

            // Process other questions.
            for (int sentenceId : sentenceIds) {
                final List<String> sentence = learner.getSentenceById(sentenceId);
                List<GroupedQuery> queries = learner.getQueriesBySentenceId(sentenceId).stream()
                        .filter(query -> query.answerEntropy > minAnswerEntropy
                                && query.questionConfidence > minQuestionConfidence
                                && (!skipBinaryQueries || query.attachmentUncertainty > 1e-6))
                        .collect(Collectors.toList());
                // Print query to .csv file.
                if (r == 0 && annotatedSentences.size() < maxNumSentences) {
                    int numSentences = annotatedSentences.size();
                    if (numSentences <= maxNumSentences && numSentences % maxNumSentencesPerFile == 0) {
                        if (lineCounter > 0) {
                            csvPrinter.close();
                        }
                        if (numSentences < maxNumSentences) {
                            csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                                    String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter))),
                                    CSVFormat.EXCEL.withRecordSeparator("\n"));
                            csvPrinter.printRecord((Object[]) csvHeader);
                            fileCounter ++;
                        }
                    }
                    for (GroupedQuery query : queries) {
                        printQueryToCSVFile(query, -1 /* gold option id */, lineCounter, csvPrinter);
                        lineCounter ++;
                    }
                }
                for (GroupedQuery query : queries) {
                    Response gold = responseSimulator.answerQuestion(query);
                    if (r == 0 && lineCounter < 100) {
                        System.out.println("SID=" + sentenceId + "\t" + learner.getSentenceScore(sentenceId));
                        System.out.println(sentence.stream().collect(Collectors.joining(" ")));
                        System.out.println(query.getDebuggingInfo(gold));
                    }
                    learner.respondToQuery(query, gold);
                    optionCounter += query.getAnswerOptions().size();
                    if (query.attachmentUncertainty < 1e-6) {
                        numBinaryQueries ++;
                    }
                }
                queryCounter += queries.size();
                annotatedSentences.add(sentenceId);
                if (annotatedSentences.size() % countEvery == 0) {
                    int k = annotatedSentences.size() / countEvery - 1;
                    avgNumQueries.get(k)[r] = queryCounter;
                    avgNumBinaryQueries.get(k)[r] = numBinaryQueries;
                    avgOptionsPerQuery.get(k)[r] = 1.0 * optionCounter / queryCounter;
                    oneBestF1.get(k)[r] = 100.0 * learner.getOneBestF1(annotatedSentences).getF1();
                    rerankF1.get(k)[r]  = 100.0 * learner.getRerankedF1(annotatedSentences).getF1();
                    oracleF1.get(k)[r]  = 100.0 * learner.getOracleF1(annotatedSentences).getF1();
                    gainF1.get(k)[r]    = rerankF1.get(k)[r] - oneBestF1.get(k)[r];
                }
            }
        }
        for (int k = 0; k < avgNumQueries.size(); k++) {
            System.out.println(String.format("On %d sentences:", (k + 1) * 100));
            System.out.println(String.format("Avg. number of queries:\t%.3f (%.3f)",
                    getAverage(avgNumQueries.get(k)),
                    getStd(avgNumQueries.get(k))));
            System.out.println(String.format("Avg. number of binary queries:\t%.3f (%.3f)",
                    getAverage(avgNumBinaryQueries.get(k)),
                    getStd(avgNumBinaryQueries.get(k))));
            System.out.println(String.format("Avg. number of options per query:\t%.3f (%.3f)",
                    getAverage(avgOptionsPerQuery.get(k)),
                    getStd(avgOptionsPerQuery.get(k))));
            System.out.println(String.format("Avg. 1-best F1:\t%.3f%%\t%.3f%%",
                    getAverage(oneBestF1.get(k)),
                    getStd(oneBestF1.get(k))));
            System.out.println(String.format("Avg. rerank F1:\t%.3f%%\t%.3f%%",
                    getAverage(rerankF1.get(k)),
                    getStd(rerankF1.get(k))));
            System.out.println(String.format("Avg. oracle F1:\t%.3f%%\t%.3f%%",
                    getAverage(oracleF1.get(k)),
                    getStd(oracleF1.get(k))));
            System.out.println(String.format("Avg. F1 gain  :\t%.3f%%\t%.3f%%",
                    getAverage(gainF1.get(k)),
                    getStd(gainF1.get(k))));
        }
    }

    /**
     *
     */
    private static void printMultiQueryToCSVFile(final List<String> sentence,
                                                 final MultiQuery query,
                                                 Optional<Set<String>> goldAnswers,
                                                 int lineCounter,
                                                 final CSVPrinter csvPrinter) throws IOException {
        // Print to CSV files.
        // "query_id", "question_confidence", "question_uncertainty", "sent_id", "sentence", "pred_id", "pred_head",
        // "question_key", "question", "answers", "_golden ", "choice_gold", "choice_gold_reason"
        int sentenceId = query.sentenceId;
        final String sentenceStr = TextGenerationHelper.renderHTMLSentenceString(sentence, -1, highlightPredicate);
        List<String> csvRow = new ArrayList<>();
        csvRow.add(String.valueOf(lineCounter));
        csvRow.add(String.format("%.3f", 1.0)); // TODO remove; questionConfidence
        csvRow.add(String.format("%.3f", 1.0)); // TODO remove; attachmentUncertainty
        csvRow.add(String.valueOf(sentenceId));
        csvRow.add(String.valueOf(sentenceStr));
        csvRow.add(String.valueOf(-1)); // TODO remove; predicateIndex
        csvRow.add("<pred>"); // TODO remove; word at predicateIndex
        csvRow.add("<qkey>"); // TODO remove; question key
        csvRow.add(query.prompt);
        csvRow.add(query.options.stream().collect(Collectors.joining(answerDelimiter)));
        if (goldAnswers.isPresent()) {
            csvRow.add("TRUE");
            csvRow.add(goldAnswers.get().stream().collect(Collectors.joining(answerDelimiter)));
            csvRow.add("Based on gold responses.");
        } else {
            csvRow.add(""); // _gold
            csvRow.add(""); // choice_gold
            csvRow.add(""); // choice_gold_reason
        }
        csvPrinter.printRecord(csvRow);
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
                .map(ao -> ao.getAnswer()) //.replace(" &&& ", " <strong> & </strong> "))
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
