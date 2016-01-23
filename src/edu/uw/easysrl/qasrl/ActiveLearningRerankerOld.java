package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import uk.co.flamingpenguin.jewel.cli.CliFactory;

import java.util.*;
import java.util.stream.Collectors;


/***
 * BACKUP VERSION DONT CHANGE!!!!!
 *
 */
/**
 * Active Learning experiments (n-best reranking).
 * Created by luheng on 1/5/16.
 */
@Deprecated
public class ActiveLearningRerankerOld {
    List<List<InputWord>> sentences;
    List<Parse> goldParses;
    BaseCcgParser parser;
    QuestionGenerator questionGenerator;
    ResponseSimulator responseSimulator;
    int nBest;
    Map<String, Double> allResults;

    double minAnswerEntropy = 0.0;
    boolean collapseQueries = false;

    public static void main(String[] args) {
        EasySRL.CommandLineArguments commandLineOptions;
        try {
            commandLineOptions = CliFactory.parseArguments(EasySRL.CommandLineArguments.class, args);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        List<List<InputWord>> sentences = new ArrayList<>();
        List<Parse> goldParses = new ArrayList<>();
        DataLoader.readDevPool(sentences, goldParses);

        String modelFolder = commandLineOptions.getModel();
        List<Category> rootCategories = commandLineOptions.getRootCategories();
        QuestionGenerator questionGenerator = new QuestionGenerator();
        ResponseSimulator responseSimulator = new ResponseSimulatorGold(questionGenerator);

        /************** manual parameter tuning ... ***********/
        //final int[] nBestList = new int[] { 3, 5, 10, 20, 50, 100, 250, 500, 1000 };
        final int[] nBestList = new int[] { 100 };
        final double minAnswerEntropy = 0.0;
        final boolean collapseQueries = false;
        final boolean verbose = false;

        List<Map<String, Double>> allResults = new ArrayList<>();
        for (int nBest : nBestList) {
            BaseCcgParser parser = new BaseCcgParser.AStarParser(modelFolder, rootCategories, nBest);

            ActiveLearningRerankerOld learner = new ActiveLearningRerankerOld(sentences, goldParses, parser,
                                                                        questionGenerator, responseSimulator, nBest);
            learner.minAnswerEntropy = minAnswerEntropy;
            learner.collapseQueries = collapseQueries;
            learner.run(verbose);
            allResults.add(learner.allResults);
        }
        /*********** output results **********/
        List<String> resultKeys = new ArrayList<>(allResults.get(0).keySet());
        Collections.sort(resultKeys);
        System.out.print("\nnbest");
        for (int i = 0; i < nBestList.length; i++) {
            System.out.print("\t" + nBestList[i]);
        }
        resultKeys.forEach(rk -> {
            System.out.print("\n" + rk);
            for (int i = 0; i < nBestList.length; i++) {
                System.out.print(String.format("\t%.6f",allResults.get(i).get(rk)));
            }
        });
        System.out.println();
    }


    public ActiveLearningRerankerOld(List<List<InputWord>> sentences, List<Parse> goldParses, BaseCcgParser parser,
                                     QuestionGenerator questionGenerator, ResponseSimulator responseSimulator, int nBest) {
        System.out.println(String.format("\n========== ReRanker Active Learning with %d-Best List ==========", nBest));
        this.sentences = sentences;
        this.goldParses = goldParses;
        this.parser = parser;
        this.questionGenerator = questionGenerator;
        this.responseSimulator = responseSimulator;
        this.nBest = nBest;
    }


    public void run(boolean verbose) {
        allResults = new HashMap<>();

        Results oneBest = new Results();
        Results reRanked = new Results();
        Results oracle = new Results();
        Accuracy oneBestAcc = new Accuracy();
        Accuracy reRankedAcc = new Accuracy();
        Accuracy oracleAcc = new Accuracy();

        int numSentencesParsed = 0;
        int avgBestK = 0, avgOracleK = 0;
        // Effect query: a query whose response boosts the score of a non-top parse but not the top one.
        int numQueries = 0, numEffectiveQueries = 0;

        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx ++) {
            List<InputWord> sentence = sentences.get(sentIdx);
            List<String> words = sentence.stream().map(w->w.word).collect(Collectors.toList());
            Parse goldParse = goldParses.get(sentIdx);
            // TODO: get parse scores.
            /****************** Base n-best Parser ***************/
            List<Parse> parses = parser.parseNBest(sentences.get(sentIdx));
            if (parses == null) {
                continue;
            }
            numSentencesParsed ++;

            /****************** Generate and Filter Queries ******************/
            List<QueryOld> queryList = QueryGenerator.generateQueries(words, parses, questionGenerator, collapseQueries,
                    minAnswerEntropy);

            /******************* Response simulator ************/
            // TODO: If the response gives N/A, shall we down vote all parses?
            List<Response> responseList = queryList.stream()
                    .map(q -> responseSimulator.answerQuestion(q, words, goldParse))
                    .collect(Collectors.toList());

            /******************* ReRanker ******************/
            double[] votes = parses.stream().mapToDouble(p->0.0).toArray();
            for (int i = 0; i < queryList.size(); i++) {
                QueryOld query = queryList.get(i);
                Response response = responseList.get(i);
                int minK = parses.size();
                for (int answerId : response.answerIds) {
                    if (query.answerToParses.containsKey(answerId)) {
                        for (int k : query.answerToParses.get(answerId)) {
                            votes[k] += 1.0;
                            if (k < minK) {
                                minK = k;
                            }
                        }
                    }
                }
                ++ numQueries;
                if (minK > 0 && minK < parses.size()) {
                    ++ numEffectiveQueries;
                }
            }

            /******************* Evaluate *******************/
            List<Results> results = CcgEvaluation.evaluate(parses, goldParse.dependencies);
            int bestK = 0, oracleK = 0;
            for (int k = 1; k < parses.size(); k++) {
                if (votes[k] > votes[bestK]) {
                    bestK = k;
                }
                if (results.get(k).getF1() > results.get(oracleK).getF1()) {
                    oracleK = k;
                }
            }
            avgBestK += bestK;
            avgOracleK += oracleK;
            oneBest.add(results.get(0));
            reRanked.add(results.get(bestK));
            oracle.add(results.get(oracleK));
            oneBestAcc.add(CcgEvaluation.evaluateTags(parses.get(0).categories, goldParse.categories));
            reRankedAcc.add(CcgEvaluation.evaluateTags(parses.get(bestK).categories, goldParse.categories));
            oracleAcc.add(CcgEvaluation.evaluateTags(parses.get(oracleK).categories, goldParse.categories));

            /*************** Print Debugging Info *************/
            if (verbose && queryList.size() > 0) {
                DebugPrinter.printQueryListInfo(sentIdx, words, parses, queryList, responseList);
            }
        }
        System.out.println("\n1-best:\navg-k = 1.0\n" + oneBestAcc + "\n" + oneBest);
        System.out.println("re-ranked:\navg-k = " + 1.0 * avgBestK / numSentencesParsed + "\n" + reRankedAcc + "\n" + reRanked);
        System.out.println("oracle:\navg-k = " + 1.0 * avgOracleK / numSentencesParsed + "\n"+ oracleAcc + "\n" + oracle);
        System.out.println("Number of queries = " + numQueries);
        System.out.println("Number of effective queries = " + numEffectiveQueries);
        System.out.println("Effective ratio = " + 1.0 * numEffectiveQueries / numQueries);
        double baselineF1 = oneBest.getF1();
        double rerankF1 = reRanked.getF1();
        double avgGain = (rerankF1 - baselineF1) / numQueries * 100;
        System.out.println("Avg. F1 gain = " + avgGain);

        allResults.put("1-best-acc", oneBestAcc.getAccuracy() * 100);
        allResults.put("1-best-f1", oneBest.getF1() * 100);
        allResults.put("rerank-acc", reRankedAcc.getAccuracy() * 100);
        allResults.put("rerank-f1", reRanked.getF1() * 100);
        allResults.put("oracle-acc", oracleAcc.getAccuracy() * 100);
        allResults.put("oracle-f1", oracle.getF1() * 100);
        allResults.put("num-queries", (double) numQueries);
        allResults.put("num-eff-queries", (double) numEffectiveQueries);
        allResults.put("num-queries", (double) numQueries);
        allResults.put("eff-ratio", 100.0 * numEffectiveQueries / numQueries);
        allResults.put("avg-gain", avgGain);
    }


}
