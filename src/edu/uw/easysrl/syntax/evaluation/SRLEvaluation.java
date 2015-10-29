package edu.uw.easysrl.syntax.evaluation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Stopwatch;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.SRLParse;
import edu.uw.easysrl.dependencies.DependencyStructure.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.EasySRL.ParsingAlgorithm;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.main.ParsePrinter;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.BackoffSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.syntax.parser.SRLParser.JointSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.PipelineSRLParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.util.Util;

public class SRLEvaluation {

	public static Results evaluate(final SRLParser parser, final Collection<SRLParse> iterator,
			final int maxSentenceLength, boolean verbatim) throws FileNotFoundException {
		final List<String> autoOutput = new ArrayList<>();

		final Results results = new Results();
		int id = 0;

		final Collection<List<String>> failedToParse = new ArrayList<>();
		final AtomicInteger shouldParse = new AtomicInteger();
		final AtomicInteger parsed = new AtomicInteger();

		final Collection<Runnable> jobs = new ArrayList<>();

		final boolean oneThread = true;

		final Stopwatch stopwatch = Stopwatch.createStarted();

		for (final SRLParse srlParse : iterator) {
			id++;
			final CCGandSRLparse parse = parser.parseTokens(InputWord.listOf(srlParse.getWords()));
			autoOutput.add(ParsePrinter.CCGBANK_PRINTER.print(parse != null ? parse.getCcgParse() : null, id));
			if (parse == null) {
				if (srlParse.getWords().size() < maxSentenceLength) {
					failedToParse.add(srlParse.getWords());

				}
				results.add(new Results(0, 0, srlParse.getDependencies().size()));
				continue;
			}
			parsed.getAndIncrement();
			results.add(evaluate(srlParse, parse, verbatim));
		}

		if (!oneThread) {
			Util.runJobsInParallel(jobs, Runtime.getRuntime().availableProcessors());
		}
		if (verbatim) {
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
		return results;
	}

	private static Results evaluate(final SRLParse gold, final CCGandSRLparse parse, boolean verbatim) {
		final Collection<ResolvedDependency> predictedDeps = new HashSet<>(parse.getDependencyParse());
		final Set<SRLDependency> goldDeps = new HashSet<>(gold.getDependencies());
		final Iterator<ResolvedDependency> depsIt = predictedDeps.iterator();
		// Remove non-SRL dependencies.
		while (depsIt.hasNext()) {
			final ResolvedDependency dep = depsIt.next();
			if (((dep.getSemanticRole() == SRLFrame.NONE)) || dep.getOffset() == 0 /* Unrealized arguments */) {
				depsIt.remove();
			}
		}
		if (verbatim) {
			for (final SyntaxTreeNodeLeaf leaf : parse.getCcgParse().getLeaves()) {
				System.err.print(leaf.getWord() + "|" + leaf.getCategory() + " ");
			}
			System.err.println();
		}
		int correctCount = 0;
		final int predictedCount = predictedDeps.size();
		final int goldCount = goldDeps.size();
		for (final SRLDependency goldDep : goldDeps) {
			if (goldDep.getArgumentPositions().size() == 0) {
				continue;
			}
			boolean found = false;
			for (final ResolvedDependency predictedDep : predictedDeps) {
				int predictedPropbankPredicate;
				int predictedPropbankArgument;
				if (goldDep.isCoreArgument()) {
					predictedPropbankPredicate = predictedDep.getPredicateIndex();
					predictedPropbankArgument = predictedDep.getArgumentIndex();
				} else {
					predictedPropbankPredicate = predictedDep.getArgumentIndex();
					predictedPropbankArgument = predictedDep.getPredicateIndex();
				}
				// For adjuncts, the CCG functor is the Propbank argument
				if (goldDep.getPredicateIndex() == predictedPropbankPredicate
						&& (goldDep.getLabel() == predictedDep.getSemanticRole())
						&& goldDep.getArgumentPositions().contains(predictedPropbankArgument)) {
					predictedDeps.remove(predictedDep);
					correctCount++;
					found = true;
					break;
				}
			}
			if (!found && verbatim) {
				System.err.println("missing:" + goldDep.toString(gold.getWords()));
			}
		}
		if (verbatim) {
			for (final ResolvedDependency wrong : predictedDeps) {
				System.err.println("wrong:  " + wrong.toString(gold.getWords()));
			}
		}
		return new Results(predictedCount, correctCount, goldCount);
	}

	public static void main(final String[] args) throws IOException {
		final String folder = Util.getHomeFolder() + "/Downloads/model2";
		final String pipelineFolder = folder + "/pipeline";
		final POSTagger posTagger = POSTagger.getStanfordTagger(new File(pipelineFolder, "posTagger"));
		final PipelineSRLParser pipeline = new PipelineSRLParser(EasySRL.makeParser(pipelineFolder, 0.0001,
				ParsingAlgorithm.ASTAR, 200000, false), Util.deserialize(new File(pipelineFolder, "labelClassifier")),
				posTagger);

		final SRLParser jointAstar = new BackoffSRLParser(new JointSRLParser(EasySRL.makeParser(folder, 0.01,
				ParsingAlgorithm.ASTAR, 20000, true), posTagger), pipeline);

		final SRLParser jointCKY = new BackoffSRLParser(new JointSRLParser(EasySRL.makeParser(folder, 0.01,
				ParsingAlgorithm.CKY, 400000, true), posTagger), pipeline);

		final SRLParser jointAST = new BackoffSRLParser(new JointSRLParser(EasySRL.makeParser(folder, 0.1,
				ParsingAlgorithm.CKY, 400000, true), posTagger), new JointSRLParser(EasySRL.makeParser(folder, 0.01,
				ParsingAlgorithm.CKY, 400000, true), posTagger), pipeline);

		final SRLParser parser = new BackoffSRLParser(new JointSRLParser(EasySRL.makeParser(folder, 0.01,
				ParsingAlgorithm.ASTAR, 20000, true), posTagger), pipeline);

		evaluate(jointAstar,
				// BrownPropbankReader.readCorpus()//
				// ParallelCorpusReader.getPropBank00()
				ParallelCorpusReader.getPropBank23(), 70, true);
		// CCGBankEvaluation.evaluate(jointAstar, false);
	}
}
