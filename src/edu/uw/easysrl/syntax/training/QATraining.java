package edu.uw.easysrl.syntax.training;

import com.google.common.io.Files;
import edu.uw.easysrl.corpora.qa.QACorpusReader;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.syntax.evaluation.QAEvaluation;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;
import edu.uw.easysrl.syntax.model.DummyCutoffsDictionary;
import edu.uw.easysrl.syntax.model.feature.*;
import edu.uw.easysrl.syntax.model.feature.Feature.FeatureKey;
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

/**
 * Created by luheng on 11/2/15.
 * Training with QA-SRL data. (yeah)
 */
public class QATraining {
    public static final List<Category> ROOT_CATEGORIES = NewTraining.ROOT_CATEGORIES;
    private final Util.Logger trainingLogger;
    private final TrainingDataParameters dataParameters;
    private final TrainingParameters trainingParameters;
    private final CutoffsDictionary cutoffsDictionary;

    private QATraining(final TrainingDataParameters dataParameters,
                       final TrainingParameters parameters) throws IOException {
        this.dataParameters = dataParameters;
        this.trainingParameters = parameters;
        this.trainingLogger = new Util.Logger(trainingParameters.getLogFile());
        final List<Category> lexicalCategoriesList = TaggerEmbeddings.loadCategories(
                new File(dataParameters.getExistingModel(), "categories"));
        this.cutoffsDictionary = new DummyCutoffsDictionary(
                lexicalCategoriesList,
                TagDict.readDict(dataParameters.getExistingModel(), new HashSet<>(lexicalCategoriesList)),
                trainingParameters.getMaxDependencyLength());
    }

    private double[] trainLocal() throws IOException {
        QATrainingDataLoader dataLoader = new QATrainingDataLoader(cutoffsDictionary, dataParameters,
                true /* backoff */);
        final Set<Feature.FeatureKey> boundedFeatures = new HashSet<>();
        TrainingFeatureHelper featureHelper = new TrainingFeatureHelper(trainingParameters, dataParameters);
        final Map<FeatureKey, Integer> featureToIndex = featureHelper
                .makeKeyToIndexMap(QACorpusReader.READER.readCorpus(false),
                                   trainingParameters.getMinimumFeatureFrequency(),
                                   boundedFeatures);
        System.out.println("Number of features:\t" + featureToIndex.size());
        boolean small = true;
        final List<Optimization.TrainingExample> data =
                dataLoader.makeTrainingData(QACorpusReader.READER.readCorpus(small), small);
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
        trainingLogger.log("Starting Training");
        Optimization.TrainingAlgorithm algorithm = Optimization.makeLBFGS(featureToIndex, boundedFeatures);
        algorithm.train(lossFunction, weights);
        trainingLogger.log("Training Completed");

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

    private void evaluate(final double testingSupertaggerBeam, final Optional<Double> supertaggerWeight)
            throws IOException{
        final int maxSentenceLength = 70;
        final POSTagger posTagger = POSTagger.getStanfordTagger(new File(dataParameters.getExistingModel(), "posTagger"));
        final SRLParser parser = new SRLParser.JointSRLParser(
                EasySRL.makeParser(trainingParameters.getModelFolder().getAbsolutePath(),
                                   testingSupertaggerBeam,
                                   EasySRL.ParsingAlgorithm.ASTAR,
                                   20000, /* max chart size */
                                   true, /* joint */
                                   supertaggerWeight,
                                   1 /* nbest */), posTagger);
        final SRLParser backoff = new SRLParser.BackoffSRLParser(parser, new SRLParser.PipelineSRLParser(
                EasySRL.makeParser(dataParameters.getExistingModel().getAbsolutePath(), 0.0001,
                        EasySRL.ParsingAlgorithm.ASTAR, 100000, false, Optional.empty(), 1),
                Util.deserialize(new File(dataParameters.getExistingModel(), "labelClassifier")), posTagger));

        final Results results = QAEvaluation.evaluate(
                backoff, QACorpusReader.READER.getDepCorpus(), maxSentenceLength, trainingParameters.getResultsFile());
        System.out.println("Final result: F1=" + results.getF1());
    }

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

        QADependency.directedMatch = TrainingUtils.parseIntegers(trainingSettings, "directed_match").get(0) == 1 ?
                true : false;
        System.out.println("Labeled match:\t" + QADependency.directedMatch);

        // FIXME: make it look better?
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
                                final File pipeline = new File(modelFolder, "pipeline");
                                pipeline.mkdir();
                                for (final File f : baseModel.listFiles()) {
                                    java.nio.file.Files.copy(f.toPath(), new File(pipeline, f.getName()).toPath(),
                                            StandardCopyOption.REPLACE_EXISTING);
                                }
                                final TrainingDataParameters dataParameters = new TrainingDataParameters(
                                                beta, 70, ROOT_CATEGORIES, baseModel, maxChart, goldBeam);
                                // Features to use
                                final FeatureSet allFeatures = new FeatureSet(
                                        new DenseLexicalFeature(pipeline),
                                        BilexicalFeature.getBilexicalFeatures(clusterings, 3),
                                        ArgumentSlotFeature.argumentSlotFeatures,
                                        Feature.unaryRules,
                                        PrepositionFeature.prepositionFeaures,
                                        Collections.emptyList(), /* root features */
                                        Collections.emptyList()  /* binary features */);
                                final TrainingParameters standard = new TrainingParameters(
                                        50 /* max dependency length */, allFeatures,
                                        sigmaSquared, minFeatureCount, modelFolder, costFunctionWeight);

                                final QATraining training = new QATraining(dataParameters, standard);
                                training.trainLocal();

                                for (final double beam : TrainingUtils.parseDoubles(trainingSettings, "beta_for_decoding")) {
                                    System.out.println(com.google.common.base.Objects.toStringHelper("Settings")
                                            .add("DecodingBeam", beam)
                                            .add("MinFeatureCount", minFeatureCount).add("maxChart", maxChart)
                                            .add("sigmaSquared", sigmaSquared)
                                            .add("cost_function_weight", costFunctionWeight)
                                            .add("beta_for_positive_charts", goldBeam)
                                            .add("beta_for_training_charts", beta).toString());
                                    for (final Double supertaggerWeight : Arrays.asList(null, 0.5, 0.6, 0.7, 0.8, 0.9,
                                            1.0)) {
                                        training.evaluate(beam, supertaggerWeight == null ? Optional.empty() :
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
}

