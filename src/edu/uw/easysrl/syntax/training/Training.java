package edu.uw.easysrl.syntax.training;

import java.io.File;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import edu.uw.easysrl.syntax.model.feature.*;
import lbfgsb.DifferentiableFunction;

import com.google.common.base.Objects;
import com.google.common.collect.Iterators;
import com.google.common.io.Files;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.EasySRL.ParsingAlgorithm;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.evaluation.SRLEvaluation;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;
import edu.uw.easysrl.syntax.model.feature.Feature.FeatureKey;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.BackoffSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.JointSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.PipelineSRLParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.syntax.tagger.TagDict;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.syntax.training.Optimization.LossFunction;
import edu.uw.easysrl.syntax.training.Optimization.TrainingAlgorithm;
import edu.uw.easysrl.syntax.training.Optimization.TrainingExample;
import edu.uw.easysrl.util.Util;
import edu.uw.easysrl.util.Util.Logger;

public class Training {

	public static final List<Category> ROOT_CATEGORIES = Arrays.asList(Category.valueOf("S[dcl]"),
			Category.valueOf("S[q]"), Category.valueOf("S[wq]"), Category.valueOf("NP"), Category.valueOf("S[qem]"),
			Category.valueOf("S[b]\\NP")

			);

	private final Logger trainingLogger;

	public static void main(final String[] args) throws IOException, InterruptedException, NotBoundException {
		if (args.length == 0) {
			System.out.println("Please supply a file containing training settings");
			System.exit(0);
		}

		final File propertiesFile = new File(args[0]);
		final Properties trainingSettings = Util.loadProperties(propertiesFile);

		LocateRegistry.createRegistry(1099);

		final List<Clustering> clusterings = new ArrayList<>();

		// Dummy clustering (i.e. words)
		clusterings.add(null);
    /*
		for (final String file : trainingSettings.getProperty("clusterings").split(",")) {
			clusterings.add(new Clustering(new File(file), false));
		}
    */
		final boolean local = args.length == 1;

		for (final int minFeatureCount : parseIntegers(trainingSettings, "minimum_feature_frequency")) {
			for (final int maxChart : parseIntegers(trainingSettings, "max_chart_size")) {
				for (final double sigmaSquared : parseDoubles(trainingSettings, "sigma_squared")) {
					for (final double goldBeam : parseDoubles(trainingSettings, "beta_for_positive_charts")) {
						for (final double costFunctionWeight : parseDoubles(trainingSettings, "cost_function_weight")) {
							for (final double beta : parseDoubles(trainingSettings, "beta_for_training_charts")) {

								final File modelFolder = new File(trainingSettings.getProperty("output_folder")
										.replaceAll("~", Util.getHomeFolder().getAbsolutePath()));

								modelFolder.mkdirs();
								Files.copy(propertiesFile, new File(modelFolder, "training.properties"));

								// Pre-trained EasyCCG model
								final File baseModel = new File(trainingSettings.getProperty(
										"supertagging_model_folder").replaceAll("~",
												Util.getHomeFolder().getAbsolutePath()));

								if (!baseModel.exists()) {
									throw new IllegalArgumentException("Supertagging model not found: "
											+ baseModel.getAbsolutePath());
								}

								final File pipeline = new File(modelFolder, "pipeline");
								pipeline.mkdir();
								for (final File f : baseModel.listFiles()) {
									java.nio.file.Files.copy(f.toPath(), new File(pipeline, f.getName()).toPath(),
											StandardCopyOption.REPLACE_EXISTING);
								}
								final TrainingDataParameters dataParameters = new TrainingDataParameters(beta, 70,
										ROOT_CATEGORIES, baseModel, maxChart, goldBeam);

								// Features to use
								final FeatureSet allFeatures = new FeatureSet(new DenseLexicalFeature(pipeline, 0.0),
										BilexicalFeature.getBilexicalFeatures(clusterings, 3),
										ArgumentSlotFeature.argumentSlotFeatures, UnaryRuleFeature.unaryRules,
										PrepositionFeature.prepositionFeaures, Collections.emptyList(),
										Collections.emptyList());

								final TrainingParameters standard = new TrainingParameters(50, allFeatures,
										sigmaSquared, minFeatureCount, modelFolder, costFunctionWeight);

								final Training training = new Training(dataParameters, standard);

								if (local) {
									training.trainLocal();
								} else {
									training.trainDistributed();
								}

								for (final double beam : parseDoubles(trainingSettings, "beta_for_decoding")) {
									System.out.println(Objects.toStringHelper("Settings").add("DecodingBeam", beam)
											.add("MinFeatureCount", minFeatureCount).add("maxChart", maxChart)
											.add("sigmaSquared", sigmaSquared)
											.add("cost_function_weight", costFunctionWeight)
											.add("beta_for_positive_charts", goldBeam)
											.add("beta_for_training_charts", beta).toString());

									for (final Double supertaggerWeight : Arrays.asList(null, 0.5, 0.6, 0.7, 0.8, 0.9,
											1.0)) {
										training.evaluate(
												beam,
												supertaggerWeight == null ? Optional.empty() : Optional
														.of(supertaggerWeight));
									}
								}
							}
						}
					}
				}
			}
		}
	}

	private static List<String> parseStrings(final Properties settings, final String field) {
		return Arrays.asList(settings.getProperty(field).split(";"));
	}

	private static List<Integer> parseIntegers(final Properties settings, final String field) {
		return parseStrings(settings, field).stream().map(x -> Integer.valueOf(x)).collect(Collectors.toList());
	}

	private static List<Double> parseDoubles(final Properties settings, final String field) {
		return parseStrings(settings, field).stream().map(x -> Double.valueOf(x)).collect(Collectors.toList());
	}

	private List<TrainingExample> makeTrainingData(final boolean small) throws IOException {
		return new TrainingDataLoader(cutoffsDictionary, dataParameters, true).makeTrainingData(
				ParallelCorpusReader.READER.readCorpus(small), small);
	}

	private double[] trainLocal() throws IOException {
		TrainingFeatureHelper featureHelper = new TrainingFeatureHelper(trainingParameters, dataParameters);
		final Set<FeatureKey> boundedFeatures = new HashSet<>();
		final Map<FeatureKey, Integer> featureToIndex = featureHelper.makeKeyToIndexMap(
				trainingParameters.getMinimumFeatureFrequency(), boundedFeatures);

		final List<TrainingExample> data = makeTrainingData(false);

		final LossFunction lossFunction = Optimization.getLossFunction(data, featureToIndex, trainingParameters,
				trainingLogger);

		final double[] weights = train(lossFunction, featureToIndex, boundedFeatures);

		return weights;
	}

	private double[] trainDistributed() throws IOException, NotBoundException {
		TrainingFeatureHelper featureHelper = new TrainingFeatureHelper(trainingParameters, dataParameters);
		final Set<FeatureKey> boundedFeatures = new HashSet<>();
		final Map<FeatureKey, Integer> featureToIndex = featureHelper.makeKeyToIndexMap(
				trainingParameters.getMinimumFeatureFrequency(), boundedFeatures);

		final Collection<RemoteTrainer> workers = getTrainers(featureToIndex);

		System.out.println("Training nodes: " + workers.size());

		final List<Runnable> tasks = new ArrayList<>();

		final int sentencesToLoad = Iterators.size(ParallelCorpusReader.READER.readCorpus(false));
		final int shardSize = sentencesToLoad / workers.size();

		int i = 0;
		for (final RemoteTrainer worker : workers) {
			final int start = i * shardSize;
			final int end = start + shardSize;
			tasks.add(new Runnable() {

				@Override
				public void run() {
					try {
						worker.loadData(start, end, dataParameters, trainingParameters.getModelFolder(), trainingLogger);
					} catch (final Throwable e) {
						throw new RuntimeException(e);
					}

				}
			});
			i++;

		}

		Util.runJobsInParallel(tasks, workers.size());

		final double[] weights = train(new DistributedLossFunction(workers, trainingParameters.getSigmaSquared()),
				featureToIndex, boundedFeatures);

		return weights;
	}

	private Collection<RemoteTrainer> getTrainers(final Map<FeatureKey, Integer> featureToIndex)
			throws NotBoundException, IOException {
		final File modelFolder = trainingParameters.getModelFolder();

		modelFolder.mkdirs();
		// Much quicker to transfer settings with files than over RMI
		Util.serialize(trainingParameters, new File(modelFolder, "parameters"));
		Util.serialize(cutoffsDictionary, new File(modelFolder, "cutoffs"));
		Util.serialize(featureToIndex, trainingParameters.getFeatureToIndexFile());

		final List<String> servers = new ArrayList<>();
		for (final String line : Util.readFile(new File("servers.txt"))) {
			servers.add(line);
		}

		return RemoteTrainer.getTrainers(servers);
	}

	private double[] train(final DifferentiableFunction lossFunction, final Map<FeatureKey, Integer> featureToIndex,
			final Set<FeatureKey> boundedFeatures) throws IOException {
		trainingParameters.getModelFolder().mkdirs();

		final double[] weights = new double[featureToIndex.size()];

		// Do training
		train(lossFunction, Optimization.makeLBFGS(featureToIndex, boundedFeatures), weights);

		// Save model
		Util.serialize(weights, trainingParameters.getWeightsFile());
		Util.serialize(trainingParameters.getFeatureSet(), trainingParameters.getFeaturesFile());
		Util.serialize(featureToIndex, trainingParameters.getFeatureToIndexFile());

		final File modelFolder = trainingParameters.getModelFolder();
		modelFolder.mkdirs();
		Files.copy(new File(dataParameters.getExistingModel(), "categories"), new File(modelFolder, "categories"));
		Util.serialize(trainingParameters.getFeatureSet(), new File(modelFolder, "features"));
		Util.serialize(cutoffsDictionary, new File(modelFolder, "cutoffs"));
		Files.copy(new File(dataParameters.getExistingModel(), "unaryRules"), new File(modelFolder, "unaryRules"));
		Files.copy(new File(dataParameters.getExistingModel(), "markedup"), new File(modelFolder, "markedup"));
		Files.copy(new File(dataParameters.getExistingModel(), "seenRules"), new File(modelFolder, "seenRules"));

		return weights;
	}

	private void train(final DifferentiableFunction lossFunction, final TrainingAlgorithm algorithm,
			final double[] weights) throws IOException {
		trainingLogger.log("Starting Training");
		algorithm.train(lossFunction, weights);
		trainingLogger.log("Training Completed");
	}

	private final TrainingDataParameters dataParameters;
	private final TrainingParameters trainingParameters;
	private final CutoffsDictionary cutoffsDictionary;

	private Training(final TrainingDataParameters dataParameters, final TrainingParameters parameters)
			throws IOException {
		super();
		this.dataParameters = dataParameters;
		this.trainingParameters = parameters;
		this.trainingLogger = new Logger(trainingParameters.getLogFile());

		final List<Category> lexicalCategoriesList = TaggerEmbeddings.loadCategories(new File(dataParameters
				.getExistingModel(), "categories"));
		this.cutoffsDictionary = new CutoffsDictionary(lexicalCategoriesList, TagDict.readDict(
				dataParameters.getExistingModel(), new HashSet<>(lexicalCategoriesList)),
				trainingParameters.getMaxDependencyLength());

	}

	private void evaluate(final double testingSupertaggerBeam, final Optional<Double> supertaggerWeight)
			throws IOException {
		final int maxSentenceLength = 70;
		final POSTagger posTagger = POSTagger
				.getStanfordTagger(new File(dataParameters.getExistingModel(), "posTagger"));

		final SRLParser parser = new JointSRLParser(EasySRL.makeParser(trainingParameters.getModelFolder()
				.getAbsolutePath(), testingSupertaggerBeam, ParsingAlgorithm.ASTAR, 20000, true, supertaggerWeight, 1),
				posTagger);

		final SRLParser backoff = new BackoffSRLParser(parser, new PipelineSRLParser(EasySRL.makeParser(dataParameters
				.getExistingModel().getAbsolutePath(), 0.0001, ParsingAlgorithm.ASTAR, 100000, false, Optional.empty(),
				1), Util.deserialize(new File(dataParameters.getExistingModel(), "labelClassifier")), posTagger));

		final Results results = SRLEvaluation.evaluate(backoff, ParallelCorpusReader.getPropBank00(), maxSentenceLength);

		System.out.println("Final result: F1=" + results.getF1());
	}
}
