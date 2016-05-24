package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.sun.xml.internal.rngom.parse.host.Base;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader;

import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.util.GuavaCollectors;
import edu.uw.easysrl.util.Util;
import uk.co.flamingpenguin.jewel.cli.CliFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generate n-best parses and save them to file.
 * Created by luheng on 1/30/16.
 */
public class ParseFileGenerator {
    static final boolean generateDev = false;
    static final boolean includeGoldInTest = true;
    static final int nBest = 100;
    static ImmutableSet<Integer> skipDevSentences = ImmutableSet.of(1244, 1839);

    public static void main(String[] args) {
        System.err.println(generateDev ? "Generating for CCG Dev set." : "Generating for CCG Test set.");
        if (!generateDev && includeGoldInTest) {
            System.err.println("Warning: reading gold parses for test!!!");
        }
        Map<Integer, List<Parse>> allParses = new HashMap<>();
        ParseData dev, test;
        if (generateDev) {
            dev = ParseDataLoader.loadFromDevPool().get();
        } else {
            test = ParseDataLoader.loadFromTestPool(includeGoldInTest).get();
        }

        ImmutableList<ImmutableList<InputReader.InputWord>> sentences =
                generateDev ? dev.getSentenceInputWords() : test.getSentenceInputWords();
        ImmutableList<Parse> goldParses =
                generateDev ?  dev.getGoldParses() : test.getGoldParses();


        int numParsed = 0;
        double averageN = .0;
        Results oracleF1 = new Results(), baselineF1 = new Results();
        BaseCcgParser.AStarParser parser = new BaseCcgParser.AStarParser(BaseCcgParser.modelFolder, nBest,
                              1e-6, 1e-6, 1000000, 70);
        BaseCcgParser.AStarParser backoffParser = new BaseCcgParser.AStarParser(BaseCcgParser.modelFolder, 10,
                              1e-6, 1e-3, 1000000, 70);
        parser.cacheSupertags(generateDev ? dev : test);

        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx ++) {
            System.out.println(sentIdx + ", " + sentences.get(sentIdx).size());
            List<Parse> parses = generateDev && skipDevSentences.contains(sentIdx) ?
                    ImmutableList.of(backoffParser.parse(sentences.get(sentIdx))) :
                    parser.parseNBest(sentences.get(sentIdx));
            if (parses == null) {
                System.err.println("Backing-off:\t" + sentIdx + "\t" + sentences.get(sentIdx).stream()
                        .map(w -> w.word).collect(Collectors.joining(" ")));
                parses = backoffParser.parseNBest(sentences.get(sentIdx));
            }
            averageN += parses.size();
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
            if (allParses.size() % 100 == 0) {
                System.out.println("Parsed:\t" + allParses.size() + " sentences ...");
                System.out.println("Baseline:\n" + baselineF1);
                System.out.println("Oracle:\n" + oracleF1);
                System.out.println("Average-N:\n" + averageN / allParses.size());
            }
            oracleF1.add(results.get(oracleK));
            baselineF1.add(results.get(0));
            numParsed ++;
        }

        String outputFileName = generateDev ?
                String.format("parses.tagged.dev.%dbest.new.out", nBest) :
                    includeGoldInTest ?
                            String.format("parses.tagged.test.gold.%dbest.new.out", nBest) :
                            String.format("parses.tagged.test.nogold.%dbest.new.out", nBest);
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outputFileName));
            oos.writeObject(allParses);
            oos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Parsed:\t" + numParsed + " sentences.");
        System.out.println("baseline accuracy:\n" + baselineF1);
        System.out.println("oracle accuracy:\n" + oracleF1);
        System.out.println("saved to:\t" + outputFileName);
    }
}
