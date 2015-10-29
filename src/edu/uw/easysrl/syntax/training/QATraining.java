package edu.uw.easysrl.syntax.training;

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

/**
 * Created by luheng on 10/28/15.
 */
public class QATraining {

    public static final List<Category> ROOT_CATEGORIES = Arrays.asList(
            Category.valueOf("S[dcl]"),
            Category.valueOf("S[q]"),
            Category.valueOf("S[wq]"),
            Category.valueOf("NP"),
            Category.valueOf("S[qem]")// ,
            // Category.valueOf("S[b]\\NP")
    );

    private final Util.Logger trainingLogger;
    private final TrainingDataLoader.TrainingDataParameters dataParameters;
    private final TrainingParameters trainingParameters;
    private final CutoffsDictionary cutoffsDictionary;

    private QATraining(final TrainingDataLoader.TrainingDataParameters dataParameters,
                       final TrainingParameters parameters)
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

    private List<Optimization.TrainingExample> makeTrainingData(final boolean small) throws IOException {
        return new TrainingDataLoader(cutoffsDictionary, dataParameters, true).makeTrainingData(
                ParallelCorpusReader.READER.readCorpus(small), small);
    }

    private double[] trainLocal() throws IOException {
        final Set<Feature.FeatureKey> boundedFeatures = new HashSet<>();
        final Map<Feature.FeatureKey, Integer> featureToIndex = makeKeyToIndexMap(
                trainingParameters.getMinimumFeatureFrequency(), boundedFeatures);
        final List<Optimization.TrainingExample> data = makeTrainingData(false);
        final Optimization.LossFunction lossFunction = Optimization.getLossFunction(data, featureToIndex,
                trainingParameters, trainingLogger);

        final double[] weights = train(lossFunction, featureToIndex, boundedFeatures);
        return weights;
    }

    private double[] trainDistributed() throws IOException, NotBoundException {
        final Set<Feature.FeatureKey> boundedFeatures = new HashSet<>();
        final Map<Feature.FeatureKey, Integer> featureToIndex = makeKeyToIndexMap(
                trainingParameters.getMinimumFeatureFrequency(),  boundedFeatures);

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

    private Collection<RemoteTrainer> getTrainers(final Map<Feature.FeatureKey, Integer> featureToIndex)
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

    private double[] train(final DifferentiableFunction lossFunction, final Map<Feature.FeatureKey, Integer> featureToIndex,
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

    /**
     * Creates a map from (sufficiently frequent) features to integers
     */
    private Map<Feature.FeatureKey, Integer> makeKeyToIndexMap(
            final int minimumFeatureFrequency,
            final Set<Feature.FeatureKey> boundedFeatures) throws IOException {
        final Multiset<Feature.FeatureKey> keyCount = HashMultiset.create();
        final Multiset<Feature.FeatureKey> bilexicalKeyCount = HashMultiset.create();
        final Map<Feature.FeatureKey, Integer> result = new HashMap<>();
        final Multiset<Feature.FeatureKey> binaryFeatureCount = HashMultiset.create();
        final Iterator<ParallelCorpusReader.Sentence> sentenceIt = ParallelCorpusReader.READER.readCorpus(false);
        while (sentenceIt.hasNext()) {
            final ParallelCorpusReader.Sentence sentence = sentenceIt.next();
            final List<DependencyStructure.ResolvedDependency> goldDeps = getGoldDeps(sentence);

            final List<Category> cats = sentence.getLexicalCategories();

            for (int i = 0; i < cats.size(); i++) {

                final Feature.FeatureKey key = trainingParameters.getFeatureSet().lexicalCategoryFeatures.getFeatureKey(
                        sentence.getInputWords(), i, cats.get(i));
                if (key != null) {
                    keyCount.add(key);
                }
            }

            for (final DependencyStructure.ResolvedDependency dep : goldDeps) {
                final SRLFrame.SRLLabel role = dep.getSemanticRole();
                // if (cutoffsDictionary.isFrequentWithAnySRLLabel(
                // dep.getCategory(), dep.getArgNumber())
                // && cutoffsDictionary.isFrequent(dep.getCategory(),
                // dep.getArgNumber(), dep.getSemanticRole())) {
                for (final ArgumentSlotFeature feature : trainingParameters.getFeatureSet().argumentSlotFeatures) {
                    final Feature.FeatureKey key = feature.getFeatureKey(sentence.getInputWords(), dep.getPredicateIndex(),
                            role, dep.getCategory(), dep.getArgNumber(), dep.getPreposition());
                    keyCount.add(key);
                }

                if (dep.getPreposition() != Preposition.NONE) {
                    for (final PrepositionFeature feature : trainingParameters.getFeatureSet().prepositionFeatures) {
                        final Feature.FeatureKey key = feature.getFeatureKey(sentence.getInputWords(),
                                dep.getPredicateIndex(), dep.getCategory(), dep.getPreposition(), dep.getArgNumber());
                        keyCount.add(key);
                    }
                }
                // }

                if (dep.getSemanticRole() != SRLFrame.NONE) {
                    for (final BilexicalFeature feature : trainingParameters.getFeatureSet().dependencyFeatures) {
                        final Feature.FeatureKey key = feature.getFeatureKey(sentence.getInputWords(), dep.getSemanticRole(),
                                dep.getPredicateIndex(), dep.getArgumentIndex());
                        bilexicalKeyCount.add(key);
                    }
                }

            }

            getFromDerivation(sentence.getCcgbankParse(), binaryFeatureCount, boundedFeatures,
                    sentence.getInputWords(), 0, sentence.getInputWords().size());
            for (final Feature.RootCategoryFeature rootFeature : trainingParameters.getFeatureSet().rootFeatures) {
                final Feature.FeatureKey key = rootFeature.getFeatureKey(sentence.getCcgbankParse().getCategory(),
                        sentence.getInputWords());
                boundedFeatures.add(key);
                keyCount.add(key);
            }
        }

        result.put(trainingParameters.getFeatureSet().lexicalCategoryFeatures.getDefault(), result.size());

        addFrequentFeatures(// minimumFeatureFrequency,
                30,//
                binaryFeatureCount, result, boundedFeatures, true);
        addFrequentFeatures(minimumFeatureFrequency, keyCount, result, boundedFeatures, false);
        addFrequentFeatures(minimumFeatureFrequency, bilexicalKeyCount, result, boundedFeatures, false);

        for (final Feature.BinaryFeature feature : trainingParameters.getFeatureSet().binaryFeatures) {
            boundedFeatures.add(feature.getDefault());
        }

        for (final Feature feature : trainingParameters.getFeatureSet().getAllFeatures()) {
            if (!result.containsKey(feature.getDefault())) {
                result.put(feature.getDefault(), result.size());
            }
        }
        System.out.println("Total features: " + result.size());
        return result;
    }

    private void getFromDerivation(final SyntaxTreeNode node, final Multiset<Feature.FeatureKey> binaryFeatureCount,
                                   final Set<Feature.FeatureKey> boundedFeatures, final List<InputReader.InputWord> words, final int startIndex, final int endIndex) {
        if (node.getChildren().size() == 2) {
            final SyntaxTreeNode left = node.getChild(0);
            final SyntaxTreeNode right = node.getChild(1);

            for (final Combinator.RuleProduction rule : Combinator.getRules(left.getCategory(), right.getCategory(),
                    Combinator.STANDARD_COMBINATORS)) {
                if (rule.getCategory().equals(node.getCategory())) {
                    for (final Feature.BinaryFeature feature : trainingParameters.getFeatureSet().binaryFeatures) {
                        final Feature.FeatureKey featureKey = feature.getFeatureKey(node.getCategory(), node.getRuleType(),
                                left.getCategory(), left.getRuleType().getNormalFormClassForRule(), 0,
                                right.getCategory(), right.getRuleType().getNormalFormClassForRule(), 0, null);
                        binaryFeatureCount.add(featureKey);
                    }
                }
            }
        }

        if (node.getChildren().size() == 1) {
            for (final AbstractParser.UnaryRule rule : dataParameters.getUnaryRules().values()) {
                for (final Feature.UnaryRuleFeature feature : trainingParameters.getFeatureSet().unaryRuleFeatures) {
                    final Feature.FeatureKey key = feature.getFeatureKey(rule.getID(), words, startIndex, endIndex);
                    binaryFeatureCount.add(key);
                }

            }
            Util.debugHook();
        }

        int start = startIndex;
        for (final SyntaxTreeNode child : node.getChildren()) {
            final int end = start + child.getLength();
            getFromDerivation(child, binaryFeatureCount, boundedFeatures, words, start, end);
            start = end;
        }
    }

    private void addFrequentFeatures(final int minCount, final Multiset<Feature.FeatureKey> keyCount,
                                     final Map<Feature.FeatureKey, Integer> result,
                                     final Set<Feature.FeatureKey> boundedFeatures, final boolean upperBound0) {
        for (final com.google.common.collect.Multiset.Entry<Feature.FeatureKey> entry : keyCount.entrySet()) {
            if (entry.getCount() >= minCount) {
                result.put(entry.getElement(), result.size());
                if (upperBound0) {
                    boundedFeatures.add(entry.getElement());
                }
            }
        }
    }

    /**
     * Used for identifying features that occur in positive examples.
     *
     * Compares labels gold-standard CCGbank dependencies with SRL labels.
     */
    static List<DependencyStructure.ResolvedDependency> getGoldDeps(final ParallelCorpusReader.Sentence sentence) {
        final List<Category> goldCategories = sentence.getLexicalCategories();
        final List<DependencyStructure.ResolvedDependency> goldDeps = new ArrayList<>();
        final Set<CCGBankDependencies.CCGBankDependency> unlabelledDeps = new HashSet<>(sentence.getCCGBankDependencyParse()
                .getDependencies());
        for (final Map.Entry<SRLDependency, CCGBankDependencies.CCGBankDependency> dep : sentence.getCorrespondingCCGBankDependencies()
                .entrySet()) {
            final CCGBankDependencies.CCGBankDependency ccgbankDep = dep.getValue();
            if (ccgbankDep == null) {
                continue;
            }

            final Category goldCategory = goldCategories.get(ccgbankDep.getSentencePositionOfPredicate());
            if (ccgbankDep.getArgNumber() > goldCategory.getNumberOfArguments()) {
                // SRL_rebank categories are out of sync with Rebank deps
                continue;
            }

            goldDeps.add(new DependencyStructure.ResolvedDependency(ccgbankDep.getSentencePositionOfPredicate(), goldCategory, ccgbankDep
                    .getArgNumber(), ccgbankDep.getSentencePositionOfArgument(), dep.getKey().getLabel(), Preposition
                    .fromString(dep.getKey().getPreposition())));
            unlabelledDeps.remove(ccgbankDep);
        }

        for (final CCGBankDependencies.CCGBankDependency dep : unlabelledDeps) {
            final Category goldCategory = goldCategories.get(dep.getSentencePositionOfPredicate());
            if (dep.getArgNumber() > goldCategory.getNumberOfArguments()) {
                // SRL_rebank categories are out of sync with Rebank deps
                continue;
            }

            final Preposition preposition = Preposition.NONE;
            // if (dep.getCategory().getArgument(dep.getArgNumber()) ==
            // Category.PP) {
            // // If appropriate, figure out what the preposition should be.
            // preposition = Preposition.OTHER;
            //
            // for (final CCGBankDependency prepDep : unlabelledDeps) {
            // if (prepDep != dep
            // && prepDep.getSentencePositionOfArgument() == dep
            // .getSentencePositionOfArgument()
            // && Preposition.isPrepositionCategory(prepDep
            // .getCategory())) {
            // preposition = Preposition.fromString(dep
            // .getPredicateWord());
            // }
            // }
            // } else {
            // preposition = Preposition.NONE;
            // }

            goldDeps.add(new DependencyStructure.ResolvedDependency(dep.getSentencePositionOfPredicate(), goldCategory, dep.getArgNumber(),
                    dep.getSentencePositionOfArgument(), SRLFrame.NONE, preposition));
        }

        return goldDeps;
    }

    private void train(final DifferentiableFunction lossFunction, final Optimization.TrainingAlgorithm algorithm,
                       final double[] weights) throws IOException {
        trainingLogger.log("Starting Training");
        algorithm.train(lossFunction, weights);
        trainingLogger.log("Training Completed");
    }

    private void evaluate(final double testingSupertaggerBeam) throws IOException {
        final int maxSentenceLength = 70;
        final POSTagger posTagger = POSTagger
                .getStanfordTagger(new File(dataParameters.getExistingModel(), "posTagger"));

        final SRLParser parser = new SRLParser.JointSRLParser(EasySRL.makeParser(trainingParameters.getModelFolder()
                .getAbsolutePath(), testingSupertaggerBeam, EasySRL.ParsingAlgorithm.ASTAR, 20000, true), posTagger);

        final SRLParser backoff = new SRLParser.BackoffSRLParser(parser, new SRLParser.PipelineSRLParser(
                EasySRL.makeParser(dataParameters.getExistingModel().getAbsolutePath(),
                                   0.0001,
                                   EasySRL.ParsingAlgorithm.ASTAR, 100000, false),
                Util.deserialize(new File(dataParameters.getExistingModel(), "labelClassifier")), posTagger));
        final Results results = SRLEvaluation
                .evaluate(backoff, ParallelCorpusReader.getPropBank00(), maxSentenceLength);
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

                                final File pipeline = new File(modelFolder, "pipeline");
                                pipeline.mkdir();
                                for (final File f : baseModel.listFiles()) {
                                    java.nio.file.Files.copy(f.toPath(), new File(pipeline, f.getName()).toPath(),
                                            StandardCopyOption.REPLACE_EXISTING);
                                }
                                final TrainingDataLoader.TrainingDataParameters dataParameters =
                                        new TrainingDataLoader.TrainingDataParameters(
                                                beta, 70, ROOT_CATEGORIES, baseModel, maxChart, goldBeam);

                                // Features to use
                                final FeatureSet allFeatures = new FeatureSet(new DenseLexicalFeature(pipeline),
                                        BilexicalFeature.getBilexicalFeatures(clusterings, 3),
                                        ArgumentSlotFeature.argumentSlotFeatures, Feature.unaryRules,
                                        PrepositionFeature.prepositionFeaures, Collections.emptyList(),
                                        Collections.emptyList());

                                final TrainingParameters standard = new TrainingParameters(
                                        50, allFeatures,
                                        sigmaSquared, minFeatureCount, modelFolder, costFunctionWeight);

                                final QATraining training = new QATraining(dataParameters, standard);

                                if (local) {
                                    training.trainLocal();
                                } else {
                                    training.trainDistributed();
                                }

                                for (final double beam : TrainingUtils.parseDoubles(trainingSettings, "beta_for_decoding")) {
                                    System.out.println(com.google.common.base.Objects.toStringHelper("Settings")
                                            .add("DecodingBeam", beam)
                                            .add("MinFeatureCount", minFeatureCount).add("maxChart", maxChart)
                                            .add("sigmaSquared", sigmaSquared)
                                            .add("cost_function_weight", costFunctionWeight)
                                            .add("beta_for_positive_charts", goldBeam)
                                            .add("beta_for_training_charts", beta).toString());
                                    training.evaluate(beam);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
