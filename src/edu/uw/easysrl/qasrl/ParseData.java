package edu.uw.easysrl.qasrl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.cert.PKIXRevocationChecker;
import java.util.*;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import static edu.uw.easysrl.util.GuavaCollectors.*;

import edu.uw.easysrl.corpora.BioinferCCGCorpus;
import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.SRLParse;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.syntax.evaluation.CCGBankEvaluation;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.util.Util;
import scala.tools.cmd.Opt;

/**
 * Data structure to hold information about the sentences and gold parses.
 * This will make it easier to pass this data around and use it for evaluating QA Pairs.
 * It also comes with convenience methods to load the data, taken from DataLoader
 * (which is now deprecated).
 * Created by julianmichael on 3/17/2016.
 */
public final class ParseData {
    private final ImmutableList<ImmutableList<InputReader.InputWord>> sentenceInputWords;
    private final ImmutableList<ImmutableList<String>> sentences;
    private final ImmutableList<Parse> goldParses;

    public ImmutableList<ImmutableList<InputReader.InputWord>> getSentenceInputWords() {
        return sentenceInputWords;
    }

    public ImmutableList<ImmutableList<String>> getSentences() {
        return sentences;
    }

    public ImmutableList<Parse> getGoldParses() {
        return goldParses;
    }

    public static Optional<ParseData> loadFromDevPool() {
        return loadFromPropBank(true);
    }

    public static Optional<ParseData> loadFromTestPool() {
        POSTagger postagger = POSTagger.getStanfordTagger(Util.getFile(BaseCcgParser.modelFolder + "/posTagger"));
        List<List<InputReader.InputWord>> sentenceInputWords = new ArrayList<>();
        List<Parse> goldParses = new ArrayList<>();
        Iterator<Sentence> sentenceIterator;
        try {
            sentenceIterator = ParallelCorpusReader.READER.readCcgTestSet();
        } catch (IOException e) {
            System.out.println(String.format("Failed to read %d sentences.", sentenceInputWords.size()));
            return Optional.empty();
        }
        while (sentenceIterator.hasNext()) {
            Sentence sentence = sentenceIterator.next();
            List<InputReader.InputWord> taggedInput = postagger.tag(sentence.getInputWords());
            sentenceInputWords.add(taggedInput);
            Set<ResolvedDependency> goldDependencies = CCGBankEvaluation
                    .asResolvedDependencies(sentence.getCCGBankDependencyParse().getDependencies());
            goldParses.add(new Parse(sentence.getCcgbankParse(), sentence.getLexicalCategories(), goldDependencies));
        }
        System.out.println(String.format("Read %d sentences.", sentenceInputWords.size()));
        return Optional.of(makeParseData(sentenceInputWords, goldParses));
    }

    public static Optional<ParseData> loadFromBioinferDev() {
        POSTagger postagger = POSTagger.getStanfordTagger(Util.getFile(BaseCcgParser.modelFolder + "/posTagger"));
        List<List<InputReader.InputWord>> sentenceInputWords = new ArrayList<>();
        List<Parse> goldParses = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(BioinferCCGCorpus.BioinferDevFile)));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] segments = line.split("\\s+");
                List<InputReader.InputWord> inputs = new ArrayList<>();
                List<String> words = new ArrayList<>(); //, pos = new ArrayList<>();
                List<Category> categories = new ArrayList<>();
                for (String seg : segments) {
                    String[] info = seg.split("\\|");
                    words.add(info[0]);
                    // pos.add(info[1]);
                    categories.add(Category.valueOf(info[2]));
                    inputs.add(new InputReader.InputWord(info[0], "", ""));

                }
                if (words.size() > 0) {
                    sentenceInputWords.add(ImmutableList.copyOf(postagger.tag(inputs)));
                    goldParses.add(new Parse(words, categories));
                }
            }

        } catch (IOException e) {
            return Optional.empty();
        }

        System.out.println(String.format("Read %d sentences from %s.", sentenceInputWords.size(),
                BioinferCCGCorpus.BioinferDevFile));
        return Optional.of(makeParseData(sentenceInputWords, goldParses));
    }

    public static Optional<ParseData> loadFromTrainingPool() {
        return loadFromPropBank(false);
    }

    private static Optional<ParseData> devData = null;
    private static Optional<ParseData> loadFromPropBank(final boolean readDev) {
        if(readDev && devData != null) {
            return devData;
        }
        POSTagger postagger = POSTagger.getStanfordTagger(Util.getFile(BaseCcgParser.modelFolder + "/posTagger"));
        List<List<InputReader.InputWord>> sentenceInputWords = new ArrayList<>();
        List<Parse> goldParses = new ArrayList<>();
        Iterator<Sentence> sentenceIterator;
        try {
            sentenceIterator = ParallelCorpusReader.READER.readCcgCorpus(readDev);
        } catch (IOException e) {
            System.out.println(String.format("Failed to read %d sentences.", sentenceInputWords.size()));
            devData = Optional.empty();
            return devData;
        }
        while (sentenceIterator.hasNext()) {
            Sentence sentence = sentenceIterator.next();
            List<InputReader.InputWord> taggedInput = postagger.tag(sentence.getInputWords());
            sentenceInputWords.add(taggedInput);
            Set<ResolvedDependency> goldDependencies = CCGBankEvaluation
                    .asResolvedDependencies(sentence.getCCGBankDependencyParse().getDependencies());
            goldParses.add(new Parse(sentence.getCcgbankParse(), sentence.getLexicalCategories(), goldDependencies));
        }
        System.out.println(String.format("Read %d sentences.", sentenceInputWords.size()));
        Optional<ParseData> data = Optional.of(makeParseData(sentenceInputWords, goldParses));
        if(readDev) {
            devData = data;
        }
        return data;
    }

    private ParseData(ImmutableList<ImmutableList<InputReader.InputWord>> sentenceInputWords,
                     ImmutableList<Parse> goldParses) {
        this.sentenceInputWords = sentenceInputWords;
        this.goldParses = goldParses;
        this.sentences = sentenceInputWords
            .stream()
            .map(sentenceIWs -> sentenceIWs
                 .stream()
                 .map(iw -> iw.word)
                 .collect(toImmutableList()))
            .collect(toImmutableList());
    }

    private static ParseData makeParseData(List<List<InputReader.InputWord>> sentenceInputWords,
                                           List<Parse> goldParses) {
        ImmutableList<ImmutableList<InputReader.InputWord>> thisSentences = sentenceInputWords
            .stream()
            .map(ImmutableList::copyOf)
            .collect(toImmutableList());
        ImmutableList<Parse> thisGoldParses = goldParses
            .stream()
            .collect(toImmutableList());
        return new ParseData(thisSentences, thisGoldParses);
    }
}
