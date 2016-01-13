package edu.uw.easysrl.active_learning;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import com.google.common.collect.Table;
import edu.uw.easysrl.corpora.CCGBankParseReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.corpora.SRLParse;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.util.Util;

/**
 * Read gold input for active learning simulation.
 * Created by luheng on 1/4/16.
 */
public class ActiveLearningDataHelper {

    public static void readDevPool(List<List<InputReader.InputWord>> sentences,
                                   List<List<Category>> goldCategories,
                                   List<Set<ResolvedDependency>> goldParses) {
        readFromPropBank(sentences, goldCategories, goldParses, true);
    }

    public static void readTrainingPool(List<List<InputReader.InputWord>> sentences,
                                        List<List<Category>> goldCategories,
                                        List<Set<ResolvedDependency>> goldParses) {
        readFromPropBank(sentences, goldCategories, goldParses, false);
    }

    private static void readFromPropBank(List<List<InputReader.InputWord>> sentences,
                                         List<List<Category>> goldCategories,
                                         List<Set<ResolvedDependency>> goldParses,
                                         boolean readDev) {
        Iterator<ParallelCorpusReader.Sentence> sentenceIterator;
        try {
            sentenceIterator = ParallelCorpusReader.READER.readCcgCorpus(readDev);
        } catch (IOException e) {
            return;
        }
        while (sentenceIterator.hasNext()) {
            Sentence sentence = sentenceIterator.next();
            sentences.add(sentence.getInputWords());
            Set<ResolvedDependency> goldDependencies =
                    sentence.getCCGBankDependencyParse().getDependencies().stream().map(
                            dep -> new ResolvedDependency(
                                    dep.getSentencePositionOfPredicate(),
                                    dep.getCategory(),
                                    dep.getArgNumber(),
                                    dep.getSentencePositionOfArgument(),
                                    SRLFrame.NONE,
                                    Preposition.NONE)
                    ).collect(Collectors.toSet());
            goldParses.add(goldDependencies);
            goldCategories.add(sentence.getLexicalCategories());
        }
        System.out.println(String.format("Read %d sentences to the training pool.", sentences.size()));
    }

    /*
    private static void readFromCCGBank(List<List<InputReader.InputWord>> sentences,
                                        List<List<Category>> goldCategories,
                                        List<Set<ResolvedDependency>> goldParses,
                                        boolean readDev) {
        List<SyntaxTreeNode> ccgParses;
        Table<String, Integer, SRLParse> srlParses;
        try {
            ccgParses = CCGBankParseReader.loadCorpus(ParallelCorpusReader.CCGREBANK, readDev);

        } catch (IOException e) {
            return;
        }
        for (SyntaxTreeNode parse : ccgParses) {
            List<ResolvedDependency> deps = parse.getAllLabelledDependencies();
            goldCategories.add(
                    IntStrea
            );
            goldParses.add(new HashSet(deps));
            sentences.add(parse.getAllLabelledDependencies());
        }
    }
    */

    public static void main(String[] args) {
        List<List<InputReader.InputWord>> sentences = new ArrayList<>();
        List<List<Category>> goldCategories = new ArrayList<>();
        List<Set<ResolvedDependency>> goldParses = new ArrayList<>();

        readDevPool(sentences, goldCategories, goldParses);

        String outputFile = "propbank.dev.txt";
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputFile)));
            for (List<InputReader.InputWord> sentence : sentences) {
                for (int i = 0; i < sentence.size(); i++) {
                    writer.write(sentence.get(i).word + (i == sentence.size() - 1 ? "\n" : " "));
                }
            }
            writer.close();
        } catch (IOException e) {

        }
    }
}

/*
assert sentences != null && goldParses != null;
final InputReader reader = InputReader.make(InputFormat.valueOf("GOLD"));
final Iterator<String> inputLines;

try {
    inputLines = Util.readFile(Util.getFile(inputFile)).iterator();
    while (inputLines.hasNext()) {
        // Read each sentence, either from STDIN or a parse.
        final String line = inputLines.next();
        if (!line.isEmpty() && !line.startsWith("#")) {
            InputReader.InputToParser input = reader.readTrainingPool(line);
            sentences.add(input.getInputWords());
        }
    }
} catch (IOException e) {
}
*/
