package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader;

import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.syntax.evaluation.Results;
import uk.co.flamingpenguin.jewel.cli.CliFactory;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generate n-best parses and save them to file.
 * Created by luheng on 1/30/16.
 */
public class ParseFileGenerator {
    static final int nBest = 100;

    public static void main(String[] args) {
        Map<Integer, List<Parse>> allParses = new HashMap<>();
        final ParseData dev = ParseData.loadFromDevPool().get();
        //final ParseData test = ParseData.loadFromTestPool().get();

        ImmutableList<ImmutableList<InputReader.InputWord>> sentences = dev.getSentenceInputWords();
        ImmutableList<Parse> goldParses = dev.getGoldParses();

        int numParsed = 0;
        double averageN = .0;
        Results oracleF1 = new Results(), baselineF1 = new Results();
        BaseCcgParser parser = new BaseCcgParser.AStarParser(BaseCcgParser.modelFolder, nBest);

        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx ++) {
            List<Parse> parses = parser.parseNBest(sentences.get(sentIdx));
            if (parses == null) {
                System.err.println("Skipping sentence:\t" + sentIdx + "\t" + sentences.get(sentIdx).stream()
                        .map(w -> w.word).collect(Collectors.joining(" ")));
                continue;
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

        String outputFileName = String.format("parses.dev.%dbest.out", nBest);
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
