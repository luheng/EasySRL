package edu.uw.easysrl.corpora;

import java.io.*;
import java.util.*;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.BaseCcgParser;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.util.Util;

import java.util.stream.Collectors;

/**
 * Hacky reader..
 * Created by luheng on 5/23/16.
 */
public class BioinferCCGCorpus {
    public static final String BioinferDevFile = "./testfiles/biomed/GENIA1000.staggedGold";
    public static final String BioinferTestFile = "./testfiles/biomed/gold.raw";

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

    public ImmutableList<ImmutableList<InputReader.InputWord>> getInputSentences() {
        return inputSentences;
    }

    public ImmutableList<InputReader.InputWord> getInputSentence(int sentenceId) {
        return inputSentences.get(sentenceId);
    }

    public static Optional<BioinferCCGCorpus> readDev() {
        POSTagger postagger = POSTagger.getStanfordTagger(Util.getFile(BaseCcgParser.modelFolder + "/posTagger"));
        List<ImmutableList<InputReader.InputWord>> inputSentences = new ArrayList<>();
        List<ImmutableList<String>> sentences = new ArrayList<>(), postags = new ArrayList<>();
        List<ImmutableList<Category>> goldCategories = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(BioinferDevFile)));
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
                    List<InputReader.InputWord> taggedInputs = postagger.tag(inputs);
                    //System.out.println(taggedInputs.stream().map(InputReader.InputWord::toString).collect(Collectors.joining(" ")));
                    inputSentences.add(ImmutableList.copyOf(taggedInputs));
                    sentences.add(ImmutableList.copyOf(words));
                    postags.add(ImmutableList.copyOf(pos));
                    goldCategories.add(ImmutableList.copyOf(categories));
                }
            }

        } catch (IOException e) {
            return Optional.empty();
        }
        System.out.println(String.format("Read %d sentences from %s.", sentences.size(), BioinferDevFile));
        return Optional.of(new BioinferCCGCorpus(ImmutableList.copyOf(inputSentences), ImmutableList.copyOf(sentences),
                ImmutableList.copyOf(postags), ImmutableList.copyOf(goldCategories)));
    }

    public static Optional<BioinferCCGCorpus> readTest() {
        POSTagger postagger = POSTagger.getStanfordTagger(Util.getFile(BaseCcgParser.modelFolder + "/posTagger"));
        List<ImmutableList<InputReader.InputWord>> inputSentences = new ArrayList<>();
        List<ImmutableList<String>> sentences = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(BioinferTestFile)));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] tokens = line.split("\\s+");
                List<InputReader.InputWord> inputs = new ArrayList<>();
                List<String> words = new ArrayList<>();
                for (String tok : tokens) {
                    words.add(tok);
                    inputs.add(new InputReader.InputWord(tok, "", ""));
                }
                if (words.size() > 0) {
                    List<InputReader.InputWord> taggedInputs = postagger.tag(inputs);
                    inputSentences.add(ImmutableList.copyOf(taggedInputs));
                    sentences.add(ImmutableList.copyOf(words));
                }
            }

        } catch (IOException e) {
            return Optional.empty();
        }
        System.out.println(String.format("Read %d sentences from %s.", sentences.size(), BioinferTestFile));
        return Optional.of(new BioinferCCGCorpus(ImmutableList.copyOf(inputSentences), ImmutableList.copyOf(sentences),
                ImmutableList.of(), ImmutableList.of()));
    }

    public static void main(String[] args) {
        BioinferCCGCorpus corpus = readTest().get();
        Map<Integer, List<Parse>> allParses = new HashMap<>();
        final int nBest = 100;
        int numParsed = 0;
        double averageN = .0;
        BaseCcgParser.AStarParser parser = new BaseCcgParser.AStarParser(BaseCcgParser.modelFolder, nBest,
                1e-6, 1e-6, 250000, 100);
        BaseCcgParser.AStarParser backoffParser = new BaseCcgParser.AStarParser(BaseCcgParser.modelFolder, 1,
                1e-6, 1e-6, 250000, 100);

        final ImmutableList<ImmutableList<InputReader.InputWord>> inputSentences = corpus.getInputSentences();
        for (int sentIdx = 0; sentIdx < inputSentences.size(); sentIdx ++) {
            System.out.println(sentIdx + ", " + inputSentences.get(sentIdx).size());
            /* List<Parse> parses = ImmutableList.of(417, 815, 953).contains(sentIdx) ?
                    ImmutableList.of(backoffParser.parse(inputSentences.get(sentIdx))) :
                    parser.parseNBest(inputSentences.get(sentIdx));*/
            List<Parse> parses = parser.parseNBest(sentIdx, inputSentences.get(sentIdx));
            if (parses == null) {
                System.err.println("Skipping sentence:\t" + sentIdx + "\t" + inputSentences.get(sentIdx).stream()
                        .map(w -> w.word).collect(Collectors.joining(" ")));
                Parse baseoffParse = backoffParser.parse(sentIdx, inputSentences.get(sentIdx));
                if (baseoffParse != null) {
                    parses = ImmutableList.of(baseoffParse);
                } else {
                    continue;
                }
            }
            averageN += parses.size();
            allParses.put(sentIdx, parses);
            numParsed ++;
        }
        String outputFileName = String.format("bioinfer.test.%dbest.out", nBest);
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outputFileName));
            oos.writeObject(allParses);
            oos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Parsed:\t" + numParsed + " sentences.");
        System.out.println("Average-N:\n" + averageN / allParses.size());
        System.out.println("saved to:\t" + outputFileName);
    }
}
