package edu.uw.easysrl.syntax.training;

import com.google.common.io.Files;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.SRLParse;
import edu.uw.easysrl.corpora.qa.QACorpusReader;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.qasrl.PropBankAligner;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.evaluation.ResultsTable;
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

    private static final int numRandomSampleRuns = 10;

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

    private static void prepareCorpora(List<ParallelCorpusReader.Sentence> trainingPool,
                                       List<ParallelCorpusReader.Sentence> evalSentences,
                                       List<ParallelCorpusReader.Sentence> alignedPBSentences,
                                       List<QASentence> qaTrainingSentences,
                                       List<QASentence> alignedQASentences,
                                       List<Integer> trainingSentenceIds) throws IOException {
        List<QASentence> qaEvalSentences = new ArrayList<>();
        Iterator<QASentence> qaIter1 = QACorpusReader.getReader("newswire").readTrainingCorpus(),
                             qaIter2 = QACorpusReader.getReader("newswire").readEvaluationCorpus();
        Iterator<ParallelCorpusReader.Sentence> pbIter1 = ParallelCorpusReader.READER.readCorpus(false /* not dev */),
                                                pbIter2 = ParallelCorpusReader.READER.readCorpus(true  /* dev */);
        while (qaIter1.hasNext()) {
            qaTrainingSentences.add(qaIter1.next());
        }
        while (qaIter2.hasNext()) {
            qaEvalSentences.add(qaIter2.next());
        }
        while (pbIter1.hasNext()) {
            trainingPool.add(pbIter1.next());
        }
        while (pbIter2.hasNext()) {
            evalSentences.add(pbIter2.next());
        }
        Map<Integer, Integer> sentMap = PropBankAligner.getPropBankToQASentenceMapping(trainingPool,
                qaTrainingSentences);
        Map<Integer, Integer> evalSentMap = PropBankAligner.getPropBankToQASentenceMapping(trainingPool,
                qaEvalSentences);
        System.out.println("mapped sentences:\t" + sentMap.size() + "\t" + evalSentMap.size());
        for (int pbIdx = 0; pbIdx < trainingPool.size(); pbIdx ++) {
            if (!sentMap.containsKey(pbIdx) && !evalSentMap.containsKey(pbIdx)) {
                trainingSentenceIds.add(pbIdx);
            }
        }
        for (int pbIdx : evalSentMap.keySet()) {
            alignedPBSentences.add(trainingPool.get(pbIdx));
            alignedQASentences.add(qaEvalSentences.get(evalSentMap.get(pbIdx)));
        }
        System.out.println(String.format("Aligned %d sentences in dev set.", alignedPBSentences.size()));
    }

    private static List<ParallelCorpusReader.Sentence> subsample(final int numPropBankTrainingSentences,
                                                                 final List<ParallelCorpusReader.Sentence> trainingPool,
                                                                 final List<Integer> trainingSentenceIds) {
        List<ParallelCorpusReader.Sentence> trainingSentences = new ArrayList<>();
        trainingSentences.clear();
        Collections.shuffle(trainingSentenceIds, random);
        for (int pbIdx : trainingSentenceIds) {
            trainingSentences.add(trainingPool.get(pbIdx));
            if (trainingSentences.size() >= numPropBankTrainingSentences) {
                break;
            }
        }
        return trainingSentences;
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

        // Initialize hyperparameters
        final int minFeatureCount = TrainingUtils.parseIntegers(trainingSettings, "minimum_feature_frequency").get(0);
        final int maxChart = TrainingUtils.parseIntegers(trainingSettings, "max_chart_size").get(0);
        final double sigmaSquared = TrainingUtils.parseDoubles(trainingSettings, "sigma_squared").get(0);
        final double goldBeam = TrainingUtils.parseDoubles(trainingSettings, "beta_for_positive_charts").get(0);
        final double costFunctionWeight = TrainingUtils.parseDoubles(trainingSettings, "cost_function_weight").get(0);
        final double beta = TrainingUtils.parseDoubles(trainingSettings, "beta_for_training_charts").get(0);
        final double beam = TrainingUtils.parseDoubles(trainingSettings, "beta_for_decoding").get(0);
        final Double supertaggerWeight = 0.9;
        System.out.println(com.google.common.base.Objects.toStringHelper("Settings").add("DecodingBeam", beam)
                .add("MinFeatureCount", minFeatureCount).add("maxChart", maxChart)
                .add("sigmaSquared", sigmaSquared)
                .add("cost_function_weight", costFunctionWeight)
                .add("beta_for_positive_charts", goldBeam)
                .add("beta_for_training_charts", beta).toString());

        // Broilerplate code
        String absolutePath = Util.getHomeFolder().getAbsolutePath();
        final File modelFolder = new File(trainingSettings.getProperty("output_folder").replaceAll("~", absolutePath));
        modelFolder.mkdirs();
        Files.copy(propertiesFile, new File(modelFolder, "training.properties"));
        final File baseModel = new File(trainingSettings.getProperty("supertagging_model_folder").replaceAll("~", absolutePath));
        if (!baseModel.exists()) {
            throw new IllegalArgumentException("Supertagging model not found: " + baseModel.getAbsolutePath());
        }
        final File pipeline = new File(modelFolder, "pipeline");
        pipeline.mkdir();
        for (final File f : baseModel.listFiles()) {
            java.nio.file.Files.copy(f.toPath(), new File(pipeline, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        final TrainingDataParameters dataParameters = new TrainingDataParameters(beta, 70, ROOT_CATEGORIES, baseModel,
                maxChart, goldBeam);
        final FeatureSet allFeatures = new FeatureSet(new DenseLexicalFeature(pipeline),
                BilexicalFeature.getBilexicalFeatures(clusterings, 3),
                ArgumentSlotFeature.argumentSlotFeatures, Feature.unaryRules, PrepositionFeature.prepositionFeaures,
                Collections.emptyList(), Collections.emptyList());
        final TrainingParameters standard = new TrainingParameters(50, allFeatures, sigmaSquared, minFeatureCount,
                modelFolder, costFunctionWeight);

        // Initialize corpora
        List<QASentence> qaTrainingSentences = new ArrayList<>(), alignedQASentences = new ArrayList<>();
        List<ParallelCorpusReader.Sentence> trainingPool = new ArrayList<>(), evalSentences = new ArrayList<>(),
                                            alignedPBSentences = new ArrayList<>();
        List<Integer> trainingSentenceIds = new ArrayList<>();
        prepareCorpora(trainingPool, evalSentences, alignedPBSentences, qaTrainingSentences, alignedQASentences,
                trainingSentenceIds);

        ResultsTable allResults = new ResultsTable();
        for (int numPropBankTrainingSentences : new int[]{50, 100, 150}) {
            for (int r = 0; r < numRandomSampleRuns; r++) {
                List<ParallelCorpusReader.Sentence> trainingSentences = subsample(numPropBankTrainingSentences,
                        trainingPool, trainingSentenceIds);
                System.out.println(String.format("%d training, %d qa and (%d, %d) evaluation sentences.",
                        trainingSentences.size(), qaTrainingSentences.size(), evalSentences.size(),
                        alignedPBSentences.size()));
                final SSLTraining training = new SSLTraining(dataParameters, standard, trainingSentences, evalSentences,
                        alignedPBSentences, qaTrainingSentences, alignedQASentences);
                training.trainLocal();
                String resultKey = String.format("numTrainSents=%d", numPropBankTrainingSentences);
                allResults.addAll(resultKey, training.evaluate(beam, Optional.of(supertaggerWeight)));
            }
            // Print aggregated results every time :)
            System.out.println(allResults);
            allResults.printAggregated();
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
        /*
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
        */
        return weights;
    }

    private ResultsTable evaluate(final double testingSupertaggerBeam, final Optional<Double> supertaggerWeight)
            throws IOException {
        ResultsTable resultsTable = new ResultsTable();
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
        final Results pbDevResults = SRLEvaluation.evaluate(backoffParser, goldParses, maxSentenceLength);
        final ResultsTable alignedDev = SRLEvaluation.evaluate(backoffParser, alignedPBSentences, alignedQASentences,
                maxSentenceLength);
        resultsTable.add("pbDev", pbDevResults);
        resultsTable.addAll("alignedDev", alignedDev);
        System.out.println("Final result: F1=" + pbDevResults.getF1());
        System.out.println("Final result: F1=" + alignedDev.get("F1") + " on aligned PB-QA sentences");
        return resultsTable;
    }
}
