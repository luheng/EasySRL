package edu.uw.easysrl.syntax.training;

import com.google.common.io.Files;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.SRLParse;
import edu.uw.easysrl.corpora.qa.QACorpusReader;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.qasrl.PropBankAligner;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.evaluation.SRLEvaluation;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;
import edu.uw.easysrl.syntax.model.feature.*;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.syntax.tagger.TagDict;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.util.TrainingUtils;
import edu.uw.easysrl.util.Util;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
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
    public static final List<Category> ROOT_CATEGORIES = Arrays.asList(
            Category.valueOf("S[dcl]"),
            Category.valueOf("S[q]"),
            Category.valueOf("S[wq]"),
            Category.valueOf("NP"),
            Category.valueOf("S[qem]"),
            Category.valueOf("S[b]\\NP")
    );

    private final TrainingDataParameters dataParameters;
    private final TrainingParameters trainingParameters;
    private final CutoffsDictionary cutoffsDictionary;
    private final Util.Logger trainingLogger;

    private final List<ParallelCorpusReader.Sentence> trainingSentences, evalSentences, alignedPBSentences;
    private final List<QASentence> qaTrainingSentences, alignedQASentences;

    private static final Random random = new Random(12345);

    private SSLTraining(
            final TrainingDataParameters dataParameters,
            final TrainingParameters parameters,
            final List<ParallelCorpusReader.Sentence> trainingSentences,
            final List<ParallelCorpusReader.Sentence> evalSentences,
            final List<ParallelCorpusReader.Sentence> alignedPBSentences,
            final List<QASentence> qaTraining,
            final List<QASentence> alignedQASentences) throws IOException {
        super();
        this.dataParameters = dataParameters;
        this.trainingParameters = parameters;
        this.trainingLogger = new Util.Logger(trainingParameters.getLogFile());
        this.trainingSentences = trainingSentences;
        this.evalSentences = evalSentences;
        this.alignedPBSentences = alignedPBSentences;
        this.qaTrainingSentences = qaTraining;
        this.alignedQASentences = alignedQASentences;
        final List<Category> lexicalCategoriesList = TaggerEmbeddings.loadCategories(new File(dataParameters
                .getExistingModel(), "categories"));
        this.cutoffsDictionary = new CutoffsDictionary(
                lexicalCategoriesList,
                TagDict.readDict(dataParameters.getExistingModel(), new HashSet<>(lexicalCategoriesList)),
                trainingParameters.getMaxDependencyLength(),
                trainingSentences);
    }

    private static void prepareCorpora(int numPropBankTrainingSentences,
                                       List<ParallelCorpusReader.Sentence> trainingSentences,
                                       List<ParallelCorpusReader.Sentence> evalSentences,
                                       List<ParallelCorpusReader.Sentence> alignedPBSentences,
                                       List<QASentence> qaTrainingSentences,
                                       List<QASentence> alignedQASentences) throws IOException {
        List<ParallelCorpusReader.Sentence> trainingPool = new ArrayList<>();
        Iterator<QASentence> qaIter = QACorpusReader.getReader("newswire").readTrainingCorpus();
        while (qaIter.hasNext()) {
            qaTrainingSentences.add(qaIter.next());
        }
        qaIter = QACorpusReader.getReader("newswire").readEvaluationCorpus();
        List<QASentence> qaEvalSentences = new ArrayList<>();
        while (qaIter.hasNext()) {
            qaEvalSentences.add(qaIter.next());
        }
        Iterator<ParallelCorpusReader.Sentence> pbIter = ParallelCorpusReader.READER.readCorpus(false /* not dev */);
        while (pbIter.hasNext()) {
            trainingPool.add(pbIter.next());
        }
        Iterator<ParallelCorpusReader.Sentence> pbIter2 = ParallelCorpusReader.READER.readCorpus(true /* dev */);
        while (pbIter2.hasNext()) {
            evalSentences.add(pbIter2.next());
        }
        Map<Integer, Integer> sentMap = PropBankAligner.getPropBankToQASentenceMapping(trainingPool,
                qaTrainingSentences);
        Map<Integer, Integer> evalSentMap = PropBankAligner.getPropBankToQASentenceMapping(trainingPool,
                qaEvalSentences);
        System.out.println("mapped sentences:\t" + sentMap.size() + "\t" + evalSentMap.size());
        List<Integer> sentIds = new ArrayList<>();
        for (int pbIdx = 0; pbIdx < trainingPool.size(); pbIdx ++) {
            if (!sentMap.containsKey(pbIdx) && !evalSentMap.containsKey(pbIdx)) {
                sentIds.add(pbIdx);
            }
        }
        Collections.shuffle(sentIds, random);
        for (int pbIdx : sentIds) {
            trainingSentences.add(trainingPool.get(pbIdx));
            if (trainingSentences.size() >= numPropBankTrainingSentences) {
                break;
            }
        }
        for (int pbIdx : evalSentMap.keySet()) {
            alignedPBSentences.add(trainingPool.get(pbIdx));
            alignedQASentences.add(qaEvalSentences.get(evalSentMap.get(pbIdx)));
        }
        System.out.println(String.format("Aligned %d sentences in dev set.", alignedPBSentences.size()));
        System.out.println(String.format("%d training, %d qa and %d evaluation sentences.", trainingSentences.size(),
                qaTrainingSentences.size(), evalSentences.size()));
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

        List<QASentence> qaTrainingSentences = new ArrayList<>(), alignedQASentences = new ArrayList<>();
        List<ParallelCorpusReader.Sentence> trainingSentences = new ArrayList<>(), evalSentences = new ArrayList<>(),
                                            alignedPBSentences = new ArrayList<>();
        //for (int numPropBankTrainingSentences : new int[]{50, 100, 200, 500, 1000, 2000, 5000}) {
        //for (int numPropBankTrainingSentences : new int[]{300, 400, 1500, 7000, 10000, 15000, 20000}) {
        for (int numPropBankTrainingSentences : new int[]{50, }) {
            prepareCorpora(numPropBankTrainingSentences, trainingSentences, evalSentences, alignedPBSentences,
                           qaTrainingSentences, alignedQASentences);
            // Grid search of hyper-parameters.
            for (final int minFeatureCount : TrainingUtils.parseIntegers(trainingSettings, "minimum_feature_frequency")) {
                for (final int maxChart : TrainingUtils.parseIntegers(trainingSettings, "max_chart_size")) {
                    for (final double sigmaSquared : TrainingUtils.parseDoubles(trainingSettings, "sigma_squared")) {
                        for (final double goldBeam : TrainingUtils.parseDoubles(trainingSettings, "beta_for_positive_charts")) {
                            for (final double costFunctionWeight : TrainingUtils.parseDoubles(trainingSettings, "cost_function_weight")) {
                                for (final double beta : TrainingUtils.parseDoubles(trainingSettings, "beta_for_training_charts")) {
                                    // Setting an output folder, whatever.
                                    final File modelFolder = new File(trainingSettings.getProperty("output_folder")
                                            .replaceAll("~", Util.getHomeFolder().getAbsolutePath()));
                                    modelFolder.mkdirs();
                                    Files.copy(propertiesFile, new File(modelFolder, "training.properties"));
                                    // Pre-trained EasyCCG model.
                                    final File baseModel = new File(trainingSettings.getProperty(
                                            "supertagging_model_folder").replaceAll("~",
                                            Util.getHomeFolder().getAbsolutePath()));
                                    if (!baseModel.exists()) {
                                        throw new IllegalArgumentException("Supertagging model not found: "
                                                + baseModel.getAbsolutePath());
                                    }
                                    // What for?
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
                                    // Initialize training.
                                    // TODO: split training, eval and qa training
                                    final SSLTraining training = new SSLTraining(
                                            dataParameters, standard,
                                            trainingSentences, evalSentences, alignedPBSentences, qaTrainingSentences,
                                            alignedQASentences);
                                    training.trainLocal();
                                    for (final double beam : TrainingUtils.parseDoubles(trainingSettings, "beta_for_decoding")) {
                                        System.out.println(com.google.common.base.Objects.toStringHelper("Settings").add("DecodingBeam", beam)
                                                .add("MinFeatureCount", minFeatureCount).add("maxChart", maxChart)
                                                .add("sigmaSquared", sigmaSquared)
                                                .add("cost_function_weight", costFunctionWeight)
                                                .add("beta_for_positive_charts", goldBeam)
                                                .add("beta_for_training_charts", beta).toString());
                                        //for (final Double supertaggerWeight :Arrays.asList(null, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0)) {
                                        Double supertaggerWeight = 0.9;
                                        training.evaluate(beam, Optional.of(supertaggerWeight));
                                        //supertaggerWeight == null ? Optional.empty() :
                                        //}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private double[] trainLocal() throws IOException {
        TrainingFeatureHelper featureHelper = new TrainingFeatureHelper(trainingParameters, dataParameters);
        final Set<Feature.FeatureKey> boundedFeatures = new HashSet<>();
        // TODO: only pass a small number of sentences.
        // TODO: fix cutoff dictionary.
        final Map<Feature.FeatureKey, Integer> featureToIndex = featureHelper.makeKeyToIndexMap(
                trainingParameters.getMinimumFeatureFrequency(), boundedFeatures, trainingSentences);
        final List<Optimization.TrainingExample> data =
                new TrainingDataLoader(cutoffsDictionary, dataParameters, true /* backoff? */)
                        .makeTrainingData(trainingSentences.iterator(), true /* single thread */);
        final Optimization.LossFunction lossFunction = Optimization.getLossFunction(data, featureToIndex,
                trainingParameters, trainingLogger);

        final double[] weights = new double[featureToIndex.size()];


        trainingParameters.getModelFolder().mkdirs();
        trainingLogger.log("Starting Training");
        Optimization.makeLBFGS(featureToIndex, boundedFeatures).train(lossFunction, weights);
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
            throws IOException {
        final int maxSentenceLength = 70;
        final POSTagger posTagger = POSTagger
                .getStanfordTagger(new File(dataParameters.getExistingModel(), "posTagger"));
        final SRLParser parser = new SRLParser.JointSRLParser(EasySRL.makeParser(trainingParameters.getModelFolder()
                .getAbsolutePath(), testingSupertaggerBeam, EasySRL.ParsingAlgorithm.ASTAR, 20000, true,
                supertaggerWeight, 1), posTagger);
        final SRLParser backoffParser = new SRLParser.BackoffSRLParser(
                parser, new SRLParser.PipelineSRLParser(EasySRL.makeParser(
                    dataParameters.getExistingModel().getAbsolutePath(),
                    0.0001, EasySRL.ParsingAlgorithm.ASTAR, 100000, false, Optional.empty(), 1),
                Util.deserialize(new File(dataParameters.getExistingModel(), "labelClassifier")), posTagger));
        Collection<SRLParse> goldParses = evalSentences.stream()
                .map(sentence -> sentence.getSrlParse()).collect(Collectors.toSet());
        final Results results = SRLEvaluation.evaluate(backoffParser, goldParses, maxSentenceLength);
        System.out.println("Final result: F1=" + results.getF1());
        final Results results2 = SRLEvaluation.evaluate(backoffParser, alignedPBSentences, alignedQASentences, maxSentenceLength);
        System.out.println("Final result: F1=" + results2.getF1() + " on aligned PB-QA sentences");

    }
}
