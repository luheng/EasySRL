package edu.uw.easysrl.corpora;

import java.io.*;
import java.util.*;

import com.esotericsoftware.kryo.io.Input;
import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.BaseCcgParser;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.util.GuavaCollectors;
import edu.uw.easysrl.util.Util;

import java.util.stream.Collectors;

/**
 * Hacky reader..
 * Created by luheng on 5/23/16.
 */
public class BioinferCCGCorpus {
    public static final String BioMedDevFile = "./testfiles/biomed/GENIA1000.staggedGold";

    final ImmutableList<ImmutableList<InputReader.InputWord>> inputSentences;
    final ImmutableList<ImmutableList<String>> sentences, postags;
    final ImmutableList<ImmutableList<Category>> goldCategories;

    private BioinferCCGCorpus(final ImmutableList<ImmutableList<InputReader.InputWord>> inputSentences,
                              final ImmutableList<ImmutableList<String>> sentences,
                              final ImmutableList<ImmutableList<String>> postags,
                              final ImmutableList<ImmutableList<Category>> goldCategories) {
        this.inputSentences = inputSentences;
        this.sentences = sentences;
        this.postags = postags;
        this.goldCategories = goldCategories;
    }

    public ImmutableList<String> getSentence(int sentenceId) {
        return sentences.get(sentenceId);
    }

    public static Optional<BioinferCCGCorpus> readDev() {
        POSTagger postagger = POSTagger.getStanfordTagger(Util.getFile(BaseCcgParser.modelFolder + "/posTagger"));
        List<ImmutableList<InputReader.InputWord>> inputSentences = new ArrayList<>();
        List<ImmutableList<String>> sentences = new ArrayList<>(), postags = new ArrayList<>();
        List<ImmutableList<Category>> goldCategories = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(BioMedDevFile)));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] segments = line.split("\\s+");
                List<InputReader.InputWord> inputs = new ArrayList<>();
                List<String> words = new ArrayList<>(), pos = new ArrayList<>();
                List<Category> categories = new ArrayList<>();
                for (String seg : segments) {
                    String[] info = seg.split("\\|");
                    words.add(info[0]);
                    pos.add(info[1]);
                    categories.add(Category.valueOf(info[2]));
                    inputs.add(new InputReader.InputWord(info[0], "", ""));

                }
                if (words.size() > 0) {
                    postagger.tag(inputs);
                    inputSentences.add(ImmutableList.copyOf(inputs));
                    sentences.add(ImmutableList.copyOf(words));
                    postags.add(ImmutableList.copyOf(pos));
                    goldCategories.add(ImmutableList.copyOf(categories));
                }
            }

        } catch (IOException e) {
            return Optional.empty();
        }
        System.out.println(String.format("Read %d sentences from %s.", sentences.size(), BioMedDevFile));
        return Optional.of(new BioinferCCGCorpus(ImmutableList.copyOf(inputSentences), ImmutableList.copyOf(sentences),
                ImmutableList.copyOf(postags), ImmutableList.copyOf(goldCategories)));
    }

    public static void main(String[] args) {
        BioinferCCGCorpus corpus = readDev().get();

        Map<Integer, List<Parse>> allParses = new HashMap<>();
        final int nBest = 100;

        ImmutableList<ImmutableList<InputReader.InputWord>> sentences = corpus.sentences.stream()
                .map(InputReader.InputWord::listOf)
                .map(ImmutableList::copyOf)
                .collect(GuavaCollectors.toImmutableList());

        int numParsed = 0;
        double averageN = .0;
        Results oracleF1 = new Results(), baselineF1 = new Results();
        BaseCcgParser parser = new BaseCcgParser.AStarParser(BaseCcgParser.modelFolder, nBest),
                      backoffParser = new BaseCcgParser.AStarParser(BaseCcgParser.modelFolder, 1);

        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx ++) {
            //if (sentIdx < 813) { continue; }
            System.out.println(sentIdx + ", " + sentences.get(sentIdx).size());
            List<Parse> parses = ImmutableList.of(417, 815).contains(sentIdx) ?
                    ImmutableList.of(backoffParser.parse(sentences.get(sentIdx))) :
                    parser.parseNBest(sentences.get(sentIdx));
            if (parses == null) {
                System.err.println("Skipping sentence:\t" + sentIdx + "\t" + sentences.get(sentIdx).stream()
                        .map(w -> w.word).collect(Collectors.joining(" ")));
                continue;
            }
            averageN += parses.size();
            // Get results for every parse in the n-best list.
            /*
            List<Results> results = CcgEvaluation.evaluateNBest(parses, goldParses.get(sentIdx).dependencies);
            // Get oracle parse id.
            int oracleK = 0;
            for (int k = 1; k < parses.size(); k++) {
                if (results.get(k).getF1() > results.get(oracleK).getF1()) {
                    oracleK = k;
                }
            }
            if (allParses.size() % 100 == 0) {
                System.out.println("Parsed:\t" + allParses.size() + " sentences ...");
                System.out.println("Baseline:\n" + baselineF1);
                System.out.println("Oracle:\n" + oracleF1);
            }
            oracleF1.add(results.get(oracleK));
            baselineF1.add(results.get(0));
            */
            allParses.put(sentIdx, parses);
            numParsed ++;
        }

        String outputFileName = String.format("bioinfer.dev.%dbest.out", nBest);
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
        System.out.println("Average-N:\n" + averageN / allParses.size());
        System.out.println("saved to:\t" + outputFileName);

    }
}
