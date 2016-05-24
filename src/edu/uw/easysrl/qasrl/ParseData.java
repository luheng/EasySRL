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


    ParseData(ImmutableList<ImmutableList<InputReader.InputWord>> sentenceInputWords, ImmutableList<Parse> goldParses) {
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
}
