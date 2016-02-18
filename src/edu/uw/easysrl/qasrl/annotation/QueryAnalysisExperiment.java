package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Look at all the queries, trying to find a better data structure of the GroupedQuery.
 * Created by luheng on 2/17/16.
 */
public class QueryAnalysisExperiment {
    public static List<List<InputReader.InputWord>> sentences;
    public static List<Parse> goldParses;
    static BaseCcgParser parser;
    static QuestionGenerator questionGenerator;

    // All queries.
    private static List<GroupedQuery> queryPool;
    private static PriorityQueue<GroupedQuery> queryQueue;
    private static Map<Integer, List<Parse>> allParses;
    private static Map<Integer, List<Results>> allResults;
    private static Map<Integer, Integer> oracleParseIds;

    final static String modelFolder = "./model_tritrain_big/";
    final static List<Category> rootCategories =  Arrays.asList(
            Category.valueOf("S[dcl]"), Category.valueOf("S[wq]"), Category.valueOf("S[q]"),
            Category.valueOf("S[b]\\NP"), Category.valueOf("NP"));

    static final int nBest = 50;

    public static void main(String[] args) {
       sentences = new ArrayList<>();
        goldParses = new ArrayList<>();
        DataLoader.readDevPool(sentences, goldParses);
        questionGenerator = new QuestionGenerator();
        String preparsedFile = "";
        if (nBest <= 10) {
            preparsedFile = "parses.10best.out";
        } else if (nBest <= 50) {
            preparsedFile = "parses.50best.out";
        } else if (nBest <= 100) {
            preparsedFile = "parses.100best.out";
        } else if (nBest <= 1000) {
            preparsedFile = "parses.1000best.out";
        }
        // TODO: check existence of parse files.
        parser = preparsedFile.isEmpty() ?
                new BaseCcgParser.AStarParser(modelFolder, rootCategories, nBest) :
                new BaseCcgParser.MockParser(preparsedFile, nBest);

        initializeParses();
        initializeQueries();
    }

    private static void initializeParses() {
        allParses = new HashMap<>();
        allResults = new HashMap<>();
        oracleParseIds = new HashMap<>();
        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx ++) {
            List<Parse> parses = parser.parseNBest(sentIdx, sentences.get(sentIdx));
            if (parses == null) {
                continue;
            }
            // Get results for every parse in the n-best list.
            List<Results> results = CcgEvaluation.evaluateNBest(parses, goldParses.get(sentIdx).dependencies);
            // Get oracle parse id.
            int oracleK = 0;
            for (int k = 1; k < parses.size(); k++) {
                if (results.get(k).getF1() > results.get(oracleK).getF1()) {
                    oracleK = k;
                }
            }
            allParses.put(sentIdx, parses);
            allResults.put(sentIdx, results);
            oracleParseIds.put(sentIdx, oracleK);
            if (allParses.size() % 500 == 0) {
                System.out.println("Parsed:\t" + allParses.size() + " sentences ...");
            }
        }
    }

    private static void initializeQueries() {
        for (int sentIdx : allParses.keySet()) {
            List<String> words = sentences.get(sentIdx).stream().map(w -> w.word).collect(Collectors.toList());
            List<Parse> parses = allParses.get(sentIdx);
            QueryGeneratorAnalysis.generateQueries(sentIdx, words, parses, questionGenerator);
        }
    }
}
