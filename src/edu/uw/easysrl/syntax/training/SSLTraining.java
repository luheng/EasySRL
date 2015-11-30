package edu.uw.easysrl.syntax.training;

import com.google.common.base.*;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;
import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.evaluation.SRLEvaluation;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;
import edu.uw.easysrl.syntax.model.feature.*;
import edu.uw.easysrl.syntax.parser.AbstractParser;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.syntax.tagger.TagDict;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.util.TrainingUtils;
import edu.uw.easysrl.util.Util;
import lbfgsb.DifferentiableFunction;

import java.io.File;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A crippled version of PropBank training.
 * List<Sentence> training,
 * List<QASentence> qaTraining,
 * List<Sentence> eval
 * Created by luheng on 11/30/15.
 */
public class SSLTraining {
    public static final List<Category> ROOT_CATEGORIES = Arrays.asList(Category.valueOf("S[dcl]"),
            Category.valueOf("S[q]"), Category.valueOf("S[wq]"), Category.valueOf("NP"), Category.valueOf("S[qem]"),
            Category.valueOf("S[b]\\NP")
    );

    private final TrainingDataParameters dataParameters;
    private final TrainingParameters trainingParameters;
    private final CutoffsDictionary cutoffsDictionary;
    private final Util.Logger trainingLogger;

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

        final boolean local = args.length == 1;

        for (final int minFeatureCount : TrainingUtils.parseIntegers(trainingSettings, "minimum_feature_frequency")) {
            for (final int maxChart : TrainingUtils.parseIntegers(trainingSettings, "max_chart_size")) {
                for (final double sigmaSquared : TrainingUtils.parseDoubles(trainingSettings, "sigma_squared")) {
                    for (final double goldBeam : TrainingUtils.parseDoubles(trainingSettings, "beta_for_positive_charts")) {
                        for (final double costFunctionWeight : TrainingUtils.parseDoubles(trainingSettings, "cost_function_weight")) {
                            for (final double beta : TrainingUtils.parseDoubles(trainingSettings, "beta_for_training_charts")) {

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
                                final FeatureSet allFeatures = new FeatureSet(new DenseLexicalFeature(pipeline),
                                        BilexicalFeature.getBilexicalFeatures(clusterings, 3),
                                        ArgumentSlotFeature.argumentSlotFeatures, Feature.unaryRules,
                                        PrepositionFeature.prepositionFeaures, Collections.emptyList(),
                                        Collections.emptyList());
                                final TrainingParameters standard = new TrainingParameters(50, allFeatures,
                                        sigmaSquared, minFeatureCount, modelFolder, costFunctionWeight);

                                final SSLTraining training = new SSLTraining(dataParameters, standard);
                                training.trainLocal();
                                for (final double beam : TrainingUtils.parseDoubles(trainingSettings, "beta_for_decoding")) {
                                    System.out.println(com.google.common.base.Objects.toStringHelper("Settings").add("DecodingBeam", beam)
                                            .add("MinFeatureCount", minFeatureCount).add("maxChart", maxChart)
                                            .add("sigmaSquared", sigmaSquared)
                                            .add("cost_function_weight", costFunctionWeight)
                                            .add("beta_for_positive_charts", goldBeam)
                                            .add("beta_for_training_charts", beta).toString());
                                    for (final Double supertaggerWeight : Arrays.asList(null, 0.5, 0.6, 0.7, 0.8, 0.9,
                                            1.0)) {
                                        training.evaluate(
                                                beam,
                                                supertaggerWeight == null ? Optional.empty() :
                                                        Optional.of(supertaggerWeight));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private List<Optimization.TrainingExample> makeTrainingData(final boolean small) throws IOException {
        return new TrainingDataLoader(cutoffsDictionary, dataParameters, true).makeTrainingData(
                ParallelCorpusReader.READER.readCorpus(small), small);
    }

    private double[] trainLocal() throws IOException {
        TrainingFeatureHelper featureHelper = new TrainingFeatureHelper(trainingParameters, dataParameters);
        final Set<Feature.FeatureKey> boundedFeatures = new HashSet<>();
        final Map<Feature.FeatureKey, Integer> featureToIndex = featureHelper.makeKeyToIndexMap(
                trainingParameters.getMinimumFeatureFrequency(), boundedFeatures);
        final List<Optimization.TrainingExample> data = makeTrainingData(false);
        final Optimization.LossFunction lossFunction = Optimization.getLossFunction(data, featureToIndex,
                trainingParameters, trainingLogger);
        final double[] weights = train(lossFunction, featureToIndex, boundedFeatures);
        return weights;
    }

    private double[] train(final DifferentiableFunction lossFunction,
                           final Map<Feature.FeatureKey, Integer> featureToIndex,
                           final Set<Feature.FeatureKey> boundedFeatures) throws IOException {
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

    private void train(final DifferentiableFunction lossFunction, final Optimization.TrainingAlgorithm algorithm,
                       final double[] weights) throws IOException {
        trainingLogger.log("Starting Training");
        algorithm.train(lossFunction, weights);
        trainingLogger.log("Training Completed");
    }

    private SSLTraining(final TrainingDataParameters dataParameters, final TrainingParameters parameters)
            throws IOException {
        super();
        this.dataParameters = dataParameters;
        this.trainingParameters = parameters;
        this.trainingLogger = new Util.Logger(trainingParameters.getLogFile());

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
        final SRLParser parser = new SRLParser.JointSRLParser(EasySRL.makeParser(trainingParameters.getModelFolder()
                .getAbsolutePath(), testingSupertaggerBeam, EasySRL.ParsingAlgorithm.ASTAR, 20000, true, supertaggerWeight, 1),
                posTagger);
        final SRLParser backoff = new SRLParser.BackoffSRLParser(parser, new SRLParser.PipelineSRLParser(EasySRL.makeParser(dataParameters
                        .getExistingModel().getAbsolutePath(), 0.0001, EasySRL.ParsingAlgorithm.ASTAR, 100000, false, Optional.empty(),
                1), Util.deserialize(new File(dataParameters.getExistingModel(), "labelClassifier")), posTagger));
        final Results results = SRLEvaluation.evaluate(backoff, ParallelCorpusReader.getPropBank00(), maxSentenceLength);
        System.out.println("Final result: F1=" + results.getF1());
    }
}
