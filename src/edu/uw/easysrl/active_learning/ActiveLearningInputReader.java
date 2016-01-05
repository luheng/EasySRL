package edu.uw.easysrl.active_learning;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.main.InputReader;


/**
 * Read gold input for active learning simulation.
 * Created by luheng on 1/4/16.
 */
public class ActiveLearningInputReader {

    public static void readDevPool(List<List<InputReader.InputWord>> sentences,
                                   List<CCGBankDependencies.DependencyParse> goldParses) {
        readGoldSentences(sentences, goldParses, true);
    }

    public static void readTrainingPool(List<List<InputReader.InputWord>> sentences,
                                        List<CCGBankDependencies.DependencyParse> goldParses) {
        readGoldSentences(sentences, goldParses, false);
    }

    private static void readGoldSentences(List<List<InputReader.InputWord>> sentences,
                                          List<CCGBankDependencies.DependencyParse> goldParses,
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
            goldParses.add(sentence.getCCGBankDependencyParse());
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
