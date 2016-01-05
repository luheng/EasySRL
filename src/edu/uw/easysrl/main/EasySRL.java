package edu.uw.easysrl.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import uk.co.flamingpenguin.jewel.cli.ArgumentValidationException;
import uk.co.flamingpenguin.jewel.cli.CliFactory;
import uk.co.flamingpenguin.jewel.cli.Option;

import com.google.common.base.Stopwatch;

import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.semantics.lexicon.CompositeLexicon;
import edu.uw.easysrl.semantics.lexicon.Lexicon;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;
import edu.uw.easysrl.syntax.model.Model.ModelFactory;
import edu.uw.easysrl.syntax.model.SRLFactoredModel.SRLFactoredModelFactory;
import edu.uw.easysrl.syntax.model.SupertagFactoredModel.SupertagFactoredModelFactory;
import edu.uw.easysrl.syntax.model.feature.FeatureSet;
import edu.uw.easysrl.syntax.parser.Parser;
import edu.uw.easysrl.syntax.parser.ParserAStar;
import edu.uw.easysrl.syntax.parser.ParserCKY;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.BackoffSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.syntax.parser.SRLParser.JointSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.PipelineSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.SemanticParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.syntax.training.Training;
import edu.uw.easysrl.util.Util;

public class EasySRL {

	/**
	 * Command Line Interface
	 */
	public interface CommandLineArguments {
		@Option(shortName = "m", description = "Path to the parser model")
		String getModel();

		@Option(shortName = "f", defaultValue = "", description = "(Optional) Path to the input text file. Otherwise, the parser will read from stdin.")
		String getInputFile();

		@Option(shortName = "i", defaultValue = "tokenized", description = "(Optional) Input Format: one of \"tokenized\", \"POStagged\" (word|pos), or \"POSandNERtagged\" (word|pos|ner)")
		String getInputFormat();

		@Option(shortName = "o", description = "Output Format: one of \"logic\" \"srl\", \"ccgbank\", \"html\", \"dependencies\" or \"supertagged\"", defaultValue = "logic")
		String getOutputFormat();

		@Option(shortName = "a", description = "(Optional) Parsing algorithm: one of \"astar\" or \"cky\"", defaultValue = "astar")
		String getParsingAlgorithm();

		@Option(shortName = "l", defaultValue = "70", description = "(Optional) Maximum length of sentences in words. Defaults to 70.")
		int getMaxLength();

		@Option(shortName = "n", defaultValue = "1", description = "(Optional) Number of parses to return per sentence. Values >1 are only supported for A* parsing. Defaults to 1.")
		int getNbest();

		@Option(shortName = "r", defaultValue = { "S[dcl]", "S[wq]", "S[q]", "S[b]\\NP", "NP" }, description = "(Optional) List of valid categories for the root node of the parse. Defaults to: S[dcl] S[wq] S[q] NP S[b]\\NP")
		List<Category> getRootCategories();

		@Option(defaultValue = "0.01", description = "(Optional) Prunes lexical categories whose probability is less than this ratio of the best category. Decreasing this value will slightly improve accuracy, and give more varied n-best output, but decrease speed. Defaults to 0.01.")
		double getSupertaggerbeam();

		@Option(helpRequest = true, description = "Display this message", shortName = "h")
		boolean getHelp();

		// New stuff
		@Option(shortName = "p", defaultValue = { "false" }, description = "(Optional) Use the pipeline parser instead of joint.")
		boolean getUsePipeline();

		//@Option(shortName = "t", defaultValue = { "toy.txt.stagged" }, description = "Tagged input file because supertagger does not work on server ...")
		//String getTaggedInput();

		@Option(shortName = "b", defaultValue = "", description = "(Optional) Path to the input text file by Bharat parser.")
		String getBharatInputFile();
	}

	// Set of supported InputFormats
	public enum InputFormat {
		TOKENIZED, GOLD, SUPERTAGGED, POSTAGGED, POSANDNERTAGGED
	}

	// Set of supported OutputFormats
	public enum OutputFormat {
		CCGBANK(ParsePrinter.CCGBANK_PRINTER),
		HTML(ParsePrinter.HTML_PRINTER),
		SUPERTAGS(ParsePrinter.SUPERTAG_PRINTER),
		PROLOG(ParsePrinter.PROLOG_PRINTER),
		EXTENDED(ParsePrinter.EXTENDED_CCGBANK_PRINTER),
		DEPENDENCIES(new ParsePrinter.DependenciesPrinter()),
		SRL(ParsePrinter.SRL_PRINTER),
		LOGIC(ParsePrinter.LOGIC_PRINTER);

		public final ParsePrinter printer;

		OutputFormat(final ParsePrinter printer) {
			this.printer = printer;
		}
	}

	public static void main(final String[] args) throws IOException, InterruptedException {
		try {
			final CommandLineArguments commandLineOptions = CliFactory.parseArguments(CommandLineArguments.class, args);
			final InputFormat input = InputFormat.valueOf(commandLineOptions.getInputFormat().toUpperCase());
			final File modelFolder = Util.getFile(commandLineOptions.getModel());

			if (!modelFolder.exists()) {
				throw new InputMismatchException("Couldn't load model from from: " + modelFolder);
			}

			final File pipelineFolder = new File(modelFolder, "/pipeline");
			System.err.println("====Starting loading model====");
			final POSTagger posTagger = POSTagger.getStanfordTagger(new File(pipelineFolder, "posTagger"));
			final PipelineSRLParser pipeline = new PipelineSRLParser(EasySRL.makeParser(
					pipelineFolder.getAbsolutePath(), 0.0001, ParsingAlgorithm.ASTAR, 200000, false, Optional.empty(),
					commandLineOptions.getNbest()), Util.deserialize(new File(pipelineFolder, "labelClassifier")),
					posTagger);

			final SRLParser parser2 = new BackoffSRLParser(new JointSRLParser(makeParser(commandLineOptions, 20000,
					true, Optional.empty()), posTagger), pipeline);

			final OutputFormat outputFormat = OutputFormat.valueOf(commandLineOptions.getOutputFormat().toUpperCase());
			final ParsePrinter printer = outputFormat.printer;
			//System.out.println("Use pipeline? " + commandLineOptions.getUsePipeline());

			final SRLParser parser;
			if (commandLineOptions.getUsePipeline()){
				System.err.println("Using pipeline parser.");
				parser = pipeline;
			} else if (printer.outputsLogic()) {
				// If we're outputing logic, load a lexicon
				final File lexiconFile = new File(modelFolder, "lexicon");
				final Lexicon lexicon = lexiconFile.exists() ? CompositeLexicon.makeDefault(lexiconFile)
						: CompositeLexicon.makeDefault();
				parser = new SemanticParser(parser2, lexicon);
			} else {
				parser = parser2;
			}

			final InputReader reader = InputReader.make(InputFormat.valueOf(commandLineOptions.getInputFormat()
					.toUpperCase()));
			if ((outputFormat == OutputFormat.PROLOG || outputFormat == OutputFormat.EXTENDED)
					&& input != InputFormat.POSANDNERTAGGED) {
				throw new Error("Must use \"-i POSandNERtagged\" for this output");
			}

			final boolean readingFromStdin;
			final Iterator<String> inputLines;
			if (commandLineOptions.getInputFile().isEmpty()) {
				// Read from STDIN
				inputLines = new Scanner(System.in, "UTF-8");
				readingFromStdin = true;
			} else {
				// Read from file
				inputLines = Util.readFile(Util.getFile(commandLineOptions.getInputFile())).iterator();
				readingFromStdin = false;
			}
			System.err.println("===Model loaded: parsing...===");

			final Stopwatch timer = Stopwatch.createStarted();
			final AtomicInteger parsedSentences = new AtomicInteger();
			final Results dependencyResults = new Results();
			final ExecutorService executorService = Executors.newFixedThreadPool(1// commandLineOptions.getThreads()
					);

			final BufferedWriter sysout = new BufferedWriter(new OutputStreamWriter(System.out));

			int id = 0;
			while (inputLines.hasNext()) {
				// Read each sentence, either from STDIN or a parse.
				final String line = inputLines instanceof Scanner ? ((Scanner) inputLines).nextLine().trim()
						: inputLines.next();
				if (!line.isEmpty() && !line.startsWith("#")) {
					id++;
					final int id2 = id;

					// Make a new ExecutorService job for each sentence to parse.
					executorService.execute(new Runnable() {
						@Override
						public void run() {

							final List<CCGandSRLparse> parses = parser.parseTokens(reader.readInput(line)
									.getInputWords());
							final String output = printer.printJointParses(parses, id2);

							synchronized (printer) {
								try {
									// It's a bit faster to buffer output than use
									// System.out.println() directly.
									sysout.write(output);
									sysout.newLine();

									if (readingFromStdin) {
										sysout.flush();
									}
								} catch (final IOException e) {
									throw new RuntimeException(e);
								}
							}
						}
					});
				}
			}
			executorService.shutdown();
			executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
			sysout.close();

			final DecimalFormat twoDP = new DecimalFormat("#.##");

			if (!dependencyResults.isEmpty()) {
				System.out.println("F1=" + dependencyResults.getF1());
			}

			System.err.println("Sentences parsed: " + parsedSentences.get());
			System.err.println("Speed: "
					+ twoDP.format(1000.0 * parsedSentences.get() / timer.elapsed(TimeUnit.MILLISECONDS))
					+ " sentences per second");

		} catch (final ArgumentValidationException e) {
			System.err.println(e.getMessage());
			System.err.println(CliFactory.createCli(CommandLineArguments.class).getHelpMessage());
		}
	}

	public static Parser makeParser(final File modelFolder, final Optional<Double> supertaggerWeight)
			throws IOException {
		CommandLineArguments commandLineOptions;
		try {
			// Meh.
			String rootCats = "";
			for (final Category cat : Training.ROOT_CATEGORIES) {
				rootCats = rootCats + cat + " ";
			}
			commandLineOptions = CliFactory.parseArguments(CommandLineArguments.class,
					new String[] { "-m", modelFolder.toString() });
		} catch (final ArgumentValidationException e) {
			throw new RuntimeException(e);
		}

		return makeParser(commandLineOptions,
						  	commandLineOptions.getParsingAlgorithm().equals("astar") ? 20000 : 400000,
							true /* joint */,
							supertaggerWeight);

	}

	public enum ParsingAlgorithm {
		ASTAR, CKY
	}

	public static Parser makeParser(final String modelFolder, final double supertaggerBeam,
			final ParsingAlgorithm parsingAlgorithm, final int maxChartSize, final boolean joint,
			final Optional<Double> supertaggerWeight, final int nbest) throws IOException {
		CommandLineArguments commandLineOptions;
		try {
			commandLineOptions = CliFactory.parseArguments(CommandLineArguments.class, new String[] { "-m",
					modelFolder, "--supertaggerbeam", "" + supertaggerBeam, "-a", parsingAlgorithm.toString(),
					"--nbest", "" + nbest });

		} catch (final ArgumentValidationException e) {
			throw new RuntimeException(e);
		}
		return makeParser(commandLineOptions, maxChartSize, joint, supertaggerWeight);
	}

	private static Parser makeParser(final CommandLineArguments commandLineOptions, final int maxChartSize,
			final boolean joint, final Optional<Double> supertaggerWeight) throws IOException {
		final File modelFolder = Util.getFile(commandLineOptions.getModel());
		Coindexation.parseMarkedUpFile(new File(modelFolder, "markedup"));
		final File cutoffsFile = new File(modelFolder, "cutoffs");
		final CutoffsDictionary cutoffs = cutoffsFile.exists() ? Util.deserialize(cutoffsFile) : null;

		final ParsingAlgorithm algorithm = ParsingAlgorithm.valueOf(commandLineOptions.getParsingAlgorithm()
				.toUpperCase());
		double supertaggerBeam = commandLineOptions.getSupertaggerbeam();
		final int nBest = commandLineOptions.getNbest();
		int maxSentenceLength = commandLineOptions.getMaxLength();
		InputFormat inputFormat = InputFormat.valueOf(commandLineOptions.getInputFormat().toUpperCase());
		List<Category> rootCategories = commandLineOptions.getRootCategories();

		ModelFactory modelFactory;
		if (joint) {
			final double[] weights = Util.deserialize(new File(modelFolder, "weights"));
			if (supertaggerWeight.isPresent()) {
				weights[0] = supertaggerWeight.get();
			}
			modelFactory = new SRLFactoredModelFactory(weights,
					((FeatureSet) Util.deserialize(new File(modelFolder, "features")))
							.setSupertaggingFeature(new File(modelFolder, "/pipeline"), supertaggerBeam),
					TaggerEmbeddings.loadCategories(new File(modelFolder, "categories")), cutoffs,
					Util.deserialize(new File(modelFolder, "featureToIndex")));
		} else {
			modelFactory = new SupertagFactoredModelFactory(Tagger.make(modelFolder, supertaggerBeam, 50, cutoffs));
		}
		return algorithm == ParsingAlgorithm.CKY ?
			new ParserCKY(modelFactory, maxSentenceLength, nBest, inputFormat, rootCategories, modelFolder, maxChartSize) :
			new ParserAStar(modelFactory, maxSentenceLength, nBest, inputFormat, rootCategories, modelFolder, maxChartSize);
	}

}
