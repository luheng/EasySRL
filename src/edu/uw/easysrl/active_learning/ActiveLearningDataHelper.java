package edu.uw.easysrl.active_learning;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;

/**
 * Read gold input for active learning simulation.
 * Created by luheng on 1/4/16.
 */
public class ActiveLearningDataHelper {

    public static void readDevPool(List<List<InputReader.InputWord>> sentences,
                                   List<List<Category>> goldCategories,
                                   List<Set<ResolvedDependency>> goldParses) {
        readGoldSentences(sentences, goldCategories, goldParses, true);
    }

    public static void readTrainingPool(List<List<InputReader.InputWord>> sentences,
                                        List<List<Category>> goldCategories,
                                        List<Set<ResolvedDependency>> goldParses) {
        readGoldSentences(sentences, goldCategories, goldParses, false);
    }

    private static void readGoldSentences(List<List<InputReader.InputWord>> sentences,
                                          List<List<Category>> goldCategories,
                                          List<Set<ResolvedDependency>> goldParses,
                                          boolean readDev) {
        Iterator<ParallelCorpusReader.Sentence> sentenceIterator;
        try {
            sentenceIterator = ParallelCorpusReader.READER.readCorpus(readDev);
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
