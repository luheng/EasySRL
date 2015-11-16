package edu.uw.easysrl.syntax.evaluation;

import com.google.common.base.Stopwatch;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.DependencyStructure.ResolvedDependency;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.main.ParsePrinter;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by luheng on 11/3/15.
 */
public class QAEvaluation {
    public static Results evaluate(final SRLParser parser, final Collection<QASentence> sentences,
                                   final int maxSentenceLength, File resultsFile) throws IOException {
        final EvaluationAndAnalysisHelper evaluationHelper = new EvaluationAndAnalysisHelper(resultsFile,
                true /* output to console */, false /* output to err */);
        // FIXME: what does it do?
        final List<String> autoOutput = new ArrayList<>();
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
            List<InputWord> words = InputWord.listOf(sentence.getWords());
            final List<CCGandSRLparse> parses = parser.parseTokens(words);
            evaluationHelper.processNewParse(sentence, parses);
            if (parses == null || parses.size() == 0) {
                if (words.size() < maxSentenceLength) {
                    failedToParse.add(sentence.getWords());
                }
                evaluationHelper.addResults(new Results(0, 0, sentence.getDependencies().size()));
            } else {
                final CCGandSRLparse parse = parses.get(0);
                autoOutput.add(ParsePrinter.CCGBANK_PRINTER.print(parse != null ? parse.getCcgParse() : null, id));
                parsed.getAndIncrement();
                evaluationHelper.addResults(evaluate(sentence, parse, evaluationHelper));
            }
        }
        if (!oneThread) {
            Util.runJobsInParallel(jobs, Runtime.getRuntime().availableProcessors());
        }
        for (final List<String> cov : failedToParse) {
            System.err.print("FAILED TO PARSE: ");
            for (final String word : cov) {
                System.err.print(word + " ");
            }
            System.err.println();
        }
        System.out.println(evaluationHelper.getResults());
        System.out.println("Coverage: " + Util.twoDP(100.0 * parsed.get() / shouldParse.get()));
        System.out.println("Time: " + stopwatch.elapsed(TimeUnit.SECONDS));
        System.out.println("Number of sentences that go runtime error:\t" + numErrors);
        return evaluationHelper.getResults();
    }

    // Doing labeled but undirected match.
    private static Results evaluate(QASentence gold, final SRLParser.CCGandSRLparse parse,
                                    EvaluationAndAnalysisHelper evaluationHelper) {
        //Collection<DependencyStructure.ResolvedDependency> predictedDeps
        // = new HashSet<>(parse.getDependencyParse());
        // final Iterator<DependencyStructure.ResolvedDependency> depsIt = predictedDeps.iterator();
        /*while (depsIt.hasNext()) {
            final DependencyStructure.ResolvedDependency dep = depsIt.next();
            if (((dep.getSemanticRole() == SRLFrame.NONE)) || dep.getOffset() == 0) {
                depsIt.remove();
            }
        }*/
        final Set<QADependency> goldDeps = new HashSet<>(gold.getDependencies());
        Collection<ResolvedDependency> predictedDeps = parse.getDependencyParse().stream()
                .filter(dep -> dep.getSemanticRole() != SRLFrame.NONE && dep.getOffset() != 0) // unrealized arguments
                .collect(Collectors.toCollection(HashSet::new));
        int correctCount = 0;
        final int predictedCount = predictedDeps.size();
        final int goldCount = goldDeps.size();
        for (final QADependency goldDep : goldDeps) {
            if (goldDep.getAnswerPositions().size() == 0) {
                System.err.println("Skipping dependency: empty answer.");
                continue;
            }
            boolean found = false;
            for (final ResolvedDependency predictedDep : predictedDeps) {
                if (match(goldDep, predictedDep)) {
                    predictedDeps.remove(predictedDep);
                    correctCount++;
                    found = true;
                    evaluationHelper.processMatchedDependency(gold, goldDep, predictedDep);
                    break;
                }
            }
            if (!found) {
                evaluationHelper.processMissingDependency(gold, goldDep);
            }
        }
        predictedDeps.forEach(wrong -> evaluationHelper.processWrongDependency(gold, wrong));
        return new Results(predictedCount, correctCount, goldCount);
    }

    private static boolean match(QADependency goldDep, ResolvedDependency predictedDep) {
        int predictedPredicate = predictedDep.getPredicateIndex();
        int predictedArgument = predictedDep.getArgumentIndex();
        int reversedPredicate = predictedDep.getArgumentIndex();
        int reversedArgument = predictedDep.getPredicateIndex();
        boolean match = goldDep.getPredicateIndex() == predictedPredicate
                && goldDep.getAnswerPositions().contains(predictedArgument);
        boolean reversedMatch = goldDep.getPredicateIndex() == reversedPredicate
                && goldDep.getAnswerPositions().contains(reversedArgument);
        boolean labelMatch = goldDep.getLabel() == predictedDep.getSemanticRole();
        return (match || reversedMatch) && labelMatch;
    }
}
