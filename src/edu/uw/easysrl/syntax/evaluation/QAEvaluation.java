package edu.uw.easysrl.syntax.evaluation;

import com.google.common.base.Stopwatch;
import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.main.ParsePrinter;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.util.Util;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by luheng on 11/3/15.
 */
public class QAEvaluation {
    public static Results evaluate(final SRLParser parser,
                                   final Collection<QASentence> sentences,
                                   final int maxSentenceLength,
                                   boolean verbose) throws FileNotFoundException {
        final List<String> autoOutput = new ArrayList<>();
        final Results results = new Results();
        final Collection<List<String>> failedToParse = new ArrayList<>();
        final AtomicInteger shouldParse = new AtomicInteger();
        final AtomicInteger parsed = new AtomicInteger();
        final Collection<Runnable> jobs = new ArrayList<>();

        final boolean oneThread = true;
        final Stopwatch stopwatch = Stopwatch.createStarted();

        int id = 0;
        int numErrors = 0;

        for (final QASentence sentence : sentences) {
            id++;
            SRLParser.CCGandSRLparse parse;
            List<InputWord> words = InputWord.listOf(sentence.getWords());
            System.err.println(StringUtils.join(words, " "));
            try {
                parse = parser.parseTokens(words);
            } catch (RuntimeException e) {
                e.printStackTrace();
                numErrors ++;
                continue;
            }
            autoOutput.add(ParsePrinter.CCGBANK_PRINTER.print(parse != null ? parse.getCcgParse() : null, id));
            if (parse == null) {
                if (words.size() < maxSentenceLength) {
                    failedToParse.add(sentence.getWords());

                }
                results.add(new Results(0, 0, sentence.getDependencies().size()));
                continue;
            }
            parsed.getAndIncrement();
            results.add(evaluate(sentence, parse, verbose));
        }
        if (!oneThread) {
            Util.runJobsInParallel(jobs, Runtime.getRuntime().availableProcessors());
        }
        if (verbose) {
            for (final List<String> cov : failedToParse) {
                System.err.print("FAILED TO PARSE: ");
                for (final String word : cov) {
                    System.err.print(word + " ");
                }
                System.err.println();
            }
        }
        System.out.println(results);
        System.out.println("Coverage: " + Util.twoDP(100.0 * parsed.get() / shouldParse.get()));
        System.out.println("Time: " + stopwatch.elapsed(TimeUnit.SECONDS));
        System.out.println("Number of sentences that go runtime error:\t" + numErrors);
        return results;
    }

    private static Results evaluate(QASentence gold,
                                    final SRLParser.CCGandSRLparse parse,
                                    boolean verbose) {
        final Collection<DependencyStructure.ResolvedDependency> predictedDeps =
                new HashSet<>(parse.getDependencyParse());
        final Set<QADependency> goldDeps = new HashSet<>(gold.getDependencies());
        final Iterator<DependencyStructure.ResolvedDependency> depsIt = predictedDeps.iterator();
        // Remove non-SRL dependencies.
        while (depsIt.hasNext()) {
            final DependencyStructure.ResolvedDependency dep = depsIt.next();
            if (((dep.getSemanticRole() == SRLFrame.NONE)) || dep.getOffset() == 0 /* Unrealized arguments */) {
                depsIt.remove();
            }
        }
        if (verbose) {
            for (final SyntaxTreeNode.SyntaxTreeNodeLeaf leaf : parse.getCcgParse().getLeaves()) {
                System.err.print(leaf.getWord() + "|" + leaf.getCategory() + " ");
            }
            System.err.println();
        }
        int correctCount = 0;
        final int predictedCount = predictedDeps.size();
        final int goldCount = goldDeps.size();
        for (final QADependency goldDep : goldDeps) {
            if (goldDep.getAnswerPositions().size() == 0) {
                continue;
            }
            boolean found = false;
            for (final DependencyStructure.ResolvedDependency predictedDep : predictedDeps) {
                //if (goldDep.isCoreArgument()) {
                int predictedPredicate = predictedDep.getPredicateIndex();
                int predictedArgument = predictedDep.getArgumentIndex();
                int reversedPredicate = predictedDep.getArgumentIndex();
                int reversedArgument = predictedDep.getPredicateIndex();
                /*} else {
                    predictedPropbankPredicate = predictedDep.getArgumentIndex();
                    predictedPropbankArgument = predictedDep.getPredicateIndex();
                }*/
                // For adjuncts, the CCG functor is the Propbank argument
                boolean match = (goldDep.getPredicateIndex() == predictedPredicate
                        && (goldDep.getLabel() == predictedDep.getSemanticRole())
                        && goldDep.getAnswerPositions().contains(predictedArgument));
                boolean reversedMatch = (goldDep.getPredicateIndex() == reversedPredicate
                        && (goldDep.getLabel() == predictedDep.getSemanticRole())
                        && goldDep.getAnswerPositions().contains(reversedArgument));
                if (match || reversedMatch) {
                    predictedDeps.remove(predictedDep);
                    correctCount++;
                    found = true;
                    break;
                }
            }
            if (!found && verbose) {
                System.err.println("missing:" + goldDep.toString(gold.getWords()));
            }
        }
        if (verbose) {
            for (final DependencyStructure.ResolvedDependency wrong : predictedDeps) {
                System.err.println("wrong:  " + wrong.toString(gold.getWords()));
            }
        }
        return new Results(predictedCount, correctCount, goldCount);
    }
}
