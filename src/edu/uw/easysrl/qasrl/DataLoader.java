package edu.uw.easysrl.qasrl;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.evaluation.CCGBankEvaluation;
import edu.uw.easysrl.syntax.grammar.Preposition;

/**
 * Read gold input for active learning simulation.
 * Created by luheng on 1/4/16.
 * Deprecated since 3/17/16. Use ParseData instead.
 */
@Deprecated
public class DataLoader {

    public static void readDevPool(List<List<InputReader.InputWord>> sentences, List<Parse> goldParses) {
        readFromPropBank(sentences, goldParses, true);
    }

    public static void readTrainingPool(List<List<InputReader.InputWord>> sentences, List<Parse> goldParses) {
        readFromPropBank(sentences, goldParses, false);
    }

    private static void readFromPropBank(List<List<InputReader.InputWord>> sentences, List<Parse> goldParses,
                                         boolean readDev) {
        Iterator<Sentence> sentenceIterator;
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
            // TODO: convert gold with CCGBankEvaluation.
            goldParses.add(new Parse(sentence.getCcgbankParse(), sentence.getLexicalCategories(), goldDependencies));
        }
        System.out.println(String.format("Read %d sentences to the training pool.", sentences.size()));
    }
}
