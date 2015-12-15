package edu.uw.easysrl.syntax.training;

import com.google.common.io.Files;
import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.SRLParse;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.QuestionSlot;
import edu.uw.easysrl.qasrl.qg.QuestionTemplate;
import edu.uw.easysrl.qasrl.qg.VerbHelper;
import edu.uw.easysrl.syntax.evaluation.ActiveLearningEvaluation;
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
import java.util.stream.Collectors;

/**
 * Active Learning for CCG/SRL, or maybe CCG only.
 * Created by luheng on 12/14/15.
 */
public class ALTraining {
    public static final List<Category> ROOT_CATEGORIES = Arrays.asList(
            Category.valueOf("S[dcl]"),
            Category.valueOf("S[q]"),
            Category.valueOf("S[wq]"),
            Category.valueOf("NP"),
            Category.valueOf("S[qem]"),
            Category.valueOf("S[b]\\NP")
    );

    private static final Random random = new Random(123456);

    private final TrainingDataParameters dataParameters;
    private final TrainingParameters trainingParameters;
    private final CutoffsDictionary cutoffsDictionary;
    private final Util.Logger trainingLogger;

    private final List<ParallelCorpusReader.Sentence> trainingSentences, evalSentences, alignedPBSentences;
    private final List<QASentence> qaTrainingSentences, alignedQASentences;

    private final QuestionGenerator questionGenerator;
    private static final int nBest = 20;
    private static final int numPropBankTrainingSentences = 100;

    private ALTraining(
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

        this.questionGenerator = new QuestionGenerator();
    }

    public static void main(final String[] args) throws IOException, InterruptedException, NotBoundException {
        if (args.length == 0) {
            System.out.println("Please supply a file containing training settings");
            System.exit(0);
        }
        final File propertiesFile = new File(args[0]);
        final Properties trainingSettings = Util.loadProperties(propertiesFile);
        LocateRegistry.createRegistry(1099);

        // Initialize hyperparameters
        final int minFeatureCount = TrainingUtils.parseIntegers(trainingSettings, "minimum_feature_frequency").get(0);
        final int maxChart = TrainingUtils.parseIntegers(trainingSettings, "max_chart_size").get(0);
        final double sigmaSquared = TrainingUtils.parseDoubles(trainingSettings, "sigma_squared").get(0);
        final double goldBeam = TrainingUtils.parseDoubles(trainingSettings, "beta_for_positive_charts").get(0);
        final double costFunctionWeight = TrainingUtils.parseDoubles(trainingSettings, "cost_function_weight").get(0);
        final double beta = TrainingUtils.parseDoubles(trainingSettings, "beta_for_training_charts").get(0);
        final double beam = TrainingUtils.parseDoubles(trainingSettings, "beta_for_decoding").get(0);
        final Double supertaggerWeight = 0.9;
        System.out.println(com.google.common.base.Objects.toStringHelper("Settings")
                .add("DecodingBeam", beam)
                .add("MinFeatureCount", minFeatureCount)
                .add("maxChart", maxChart)
                .add("sigmaSquared", sigmaSquared)
                .add("cost_function_weight", costFunctionWeight)
                .add("beta_for_positive_charts", goldBeam)
                .add("beta_for_training_charts", beta).toString());

        ////////////////// BROILERPLATE ///////////////////////
        final List<Clustering> clusterings = new ArrayList<>();
        clusterings.add(null);

        String absolutePath = Util.getHomeFolder().getAbsolutePath();
        String tempFolder = trainingSettings.getProperty("output_folder");
        // String.format("ntr%d_r%d/", numPropBankTrainingSentences, r);
        final File modelFolder = new File(tempFolder.replaceAll("~", absolutePath));
        modelFolder.mkdirs();
        Files.copy(propertiesFile, new File(modelFolder, "training.properties"));
        final File baseModel = new File(trainingSettings.getProperty("supertagging_model_folder")
                .replaceAll("~", absolutePath));
        if (!baseModel.exists()) {
            throw new IllegalArgumentException("Supertagging model not found: " + baseModel.getAbsolutePath());
        }
        final File pipeline = new File(modelFolder, "pipeline");
        pipeline.mkdir();
        for (final File f : baseModel.listFiles()) {
            java.nio.file.Files.copy(f.toPath(), new File(pipeline, f.getName()).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        final TrainingDataParameters dataParameters = new TrainingDataParameters(beta, 70, ROOT_CATEGORIES,
                baseModel, maxChart, goldBeam);
        final FeatureSet allFeatures = new FeatureSet(
                new DenseLexicalFeature(pipeline, 0.0),
                BilexicalFeature.getBilexicalFeatures(clusterings, 3),
                ArgumentSlotFeature.argumentSlotFeatures,
                UnaryRuleFeature.unaryRules,
                PrepositionFeature.prepositionFeaures,
                Collections.emptyList(),
                Collections.emptyList());
        final TrainingParameters standard = new TrainingParameters(50, allFeatures, sigmaSquared,
                minFeatureCount, modelFolder, costFunctionWeight);

        // Initialize corpora
        List<QASentence> qaTrainingSentences = new ArrayList<>(), alignedQASentences = new ArrayList<>();
        List<ParallelCorpusReader.Sentence> trainingPool = new ArrayList<>(), evalSentences = new ArrayList<>(),
                alignedPBSentences = new ArrayList<>();
        List<Integer> trainingSentenceIds = new ArrayList<>();
        SemiSupervisedLearningHelper.prepareCorpora(trainingPool, evalSentences, alignedPBSentences,
                qaTrainingSentences, alignedQASentences, trainingSentenceIds);
        ResultsTable allResults = new ResultsTable();
        //for (int numPropBankTrainingSentences : new int[]{50, 100, 150, 200, 300, 500, 750, 1000, 1500, 2000}) {

        List<ParallelCorpusReader.Sentence> trainingSentences = SemiSupervisedLearningHelper.subsample(
                numPropBankTrainingSentences, trainingPool, trainingSentenceIds, random);
        System.out.println(String.format("%d training, %d qa and (%d, %d) evaluation sentences.",
                trainingSentences.size(), qaTrainingSentences.size(), evalSentences.size(),
                alignedPBSentences.size()));

        final ALTraining training = new ALTraining(dataParameters, standard, trainingSentences, evalSentences,
                alignedPBSentences, qaTrainingSentences, alignedQASentences);
        training.trainLocal();
        String resultKey = String.format("numTrainSents=%d", numPropBankTrainingSentences);
        allResults.addAll(resultKey, training.evaluate(beam, Optional.of(supertaggerWeight)));

        // Print aggregated results every time :)
        System.out.println(allResults);
        allResults.printAggregated();
    }

    private double[] trainLocal() throws IOException {
        TrainingFeatureHelper featureHelper = new TrainingFeatureHelper(trainingParameters, dataParameters);
        Set<Feature.FeatureKey> boundedFeatures = new HashSet<>();
        Map<Feature.FeatureKey, Integer> featureToIndex = featureHelper.makeKeyToIndexMap(
                trainingParameters.getMinimumFeatureFrequency(), boundedFeatures, trainingSentences);
        List<Optimization.TrainingExample> data =
                new TrainingDataLoader(cutoffsDictionary, dataParameters, true /* backoff? */)
                        .makeTrainingData(trainingSentences.iterator(), true /* single thread */);
        Optimization.LossFunction lossFunction = Optimization.getLossFunction(data, featureToIndex, trainingParameters,
                trainingLogger);

        final double[] weights = new double[featureToIndex.size()];

        trainingParameters.getModelFolder().mkdirs();
        trainingLogger.log("Starting Training");
        Optimization.makeLBFGS(featureToIndex, boundedFeatures).train(lossFunction, weights);
        trainingLogger.log("Training Completed");

        // Save model. Have to do this because the evaluation method reads from it :(
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

    private ResultsTable evaluate(final double testingSupertaggerBeam, final Optional<Double> supertaggerWeight)
            throws IOException {
        ResultsTable resultsTable = new ResultsTable();
        final int maxSentenceLength = 70;
        final POSTagger posTagger = POSTagger
                .getStanfordTagger(new File(dataParameters.getExistingModel(), "posTagger"));
        // TODO: see how often we fallback to a pipeline parser.
        final SRLParser parser = new SRLParser.JointSRLParser(
                EasySRL.makeParser(trainingParameters.getModelFolder().getAbsolutePath(), testingSupertaggerBeam,
                        EasySRL.ParsingAlgorithm.ASTAR, 20000, true, supertaggerWeight, nBest),
                posTagger);
        final SRLParser backoffParser = new SRLParser.BackoffSRLParser(parser, new SRLParser.PipelineSRLParser(
                EasySRL.makeParser(dataParameters.getExistingModel().getAbsolutePath(), 0.0001,
                        EasySRL.ParsingAlgorithm.ASTAR, 100000, false, Optional.empty(), nBest),
                Util.deserialize(new File(dataParameters.getExistingModel(), "labelClassifier")),
                posTagger));
        Collection<SRLParse> goldParses = evalSentences.stream()
                .map(sentence -> sentence.getSrlParse()).collect(Collectors.toSet());

        // Some old evaluation.
        final ResultsTable alignedDev = SRLEvaluation.evaluate(backoffParser, alignedPBSentences, alignedQASentences,
                maxSentenceLength);
        resultsTable.addAll("alignedDev", alignedDev);
        System.out.println("Final result: F1=" + alignedDev.get("F1").get(0) + " on aligned PB-QA sentences");

        Results nbestOracle = new Results(),   // upperbound on recall from n-best list.
                nbestQGOracle = new Results(); // upperbound on recall where we can generate questions.

        for (int sentIdx = 0; sentIdx < alignedPBSentences.size(); sentIdx ++) {
            // For reference/evaluation only.
            ParallelCorpusReader.Sentence pbSentence = alignedPBSentences.get(sentIdx);
            QASentence qaSentence = alignedQASentences.get(sentIdx);
            SRLParse goldParse = pbSentence.getSrlParse();

            // TODO: remove duplicates in n-best dependencies
            List<String> words = pbSentence.getWords();
            final List<SRLParser.CCGandSRLparse> nbestParses = parser.parseTokens(
                    InputReader.InputWord.listOf(goldParse.getWords()));

            if (nbestParses == null || nbestParses.size() == 0) {
                System.err.println("Failed to parse: " + pbSentence.toString());
                continue;
            }

            List<List<Category>> nbestCategories = new ArrayList<>();
            Map<String, RerankingInfo> questionToDeps = new HashMap<>();
            List<ResolvedDependency> allNBestDependencies = new ArrayList<>();
            List<ResolvedDependency> allQGDependencies = new ArrayList<>();

            for (int nb = 0; nb < nbestParses.size(); nb++) {
                final SRLParser.CCGandSRLparse parse = nbestParses.get(nb);
                List<Category> categories = new ArrayList<>();
                for (int i = 0; i < words.size(); i++) {
                    categories.add(parse.getLeaf(i).getCategory());
                }
                nbestCategories.add(categories);
                Collection<ResolvedDependency> dependencies = parse.getDependencyParse();

                allNBestDependencies.addAll(dependencies);
                for (ResolvedDependency targetDependency : dependencies) {
                    int predicateIndex = targetDependency.getHead();
                    int argumentNumber = targetDependency.getArgNumber();
                    // Skip copula verb.
                    if (VerbHelper.isCopulaVerb(words.get(predicateIndex))) {
                        continue;
                    }
                    // Get template.
                    QuestionTemplate template = questionGenerator.getTemplate(predicateIndex, words, categories,
                            dependencies);
                    if (template == null) {
                        continue;
                    }
                    // Get question.
                    List<String> question = questionGenerator.generateQuestionFromTemplate(template, argumentNumber);
                    if (question == null) {
                        continue;
                    }
                    String questionStr = StringUtils.join(question) + " ?";
                    if (!questionToDeps.containsKey(questionStr)) {
                        questionToDeps.put(questionStr, new RerankingInfo(nb, targetDependency, template));
                    }
                    allQGDependencies.add(targetDependency);
                }
            }

            System.out.println(String.format("All NBest dependencies: %d. All NBest QG dependencies: %d.",
                    allNBestDependencies.size(), allQGDependencies.size()));
            nbestOracle.add(ActiveLearningEvaluation.evaluate(goldParse.getDependencies(), allNBestDependencies));
            nbestQGOracle.add(ActiveLearningEvaluation.evaluate(goldParse.getDependencies(), allQGDependencies));

            for (String questionStr : questionToDeps.keySet()) {
                RerankingInfo rrDep = questionToDeps.get(questionStr);
                ResolvedDependency targetDependency = rrDep.targetDependency;
                QuestionTemplate template = rrDep.template;
                int argumentNumber = targetDependency.getArgNumber();

                SRLDependency matchedGold = matchGoldDependency(targetDependency, goldParse.getDependencies());
                QADependency matchedQA = matchQADependency(targetDependency, qaSentence.getDependencies());

                // Print sentence and template.
                String ccgInfo = targetDependency.getCategory() + "_" + targetDependency.getArgNumber();
                System.out.println(StringUtils.join(words));
                System.out.println(String.format("#%d\t", rrDep.nBest) + ccgInfo);
                for (QuestionSlot slot : template.slots) {
                    String slotStr = (slot.argumentNumber == argumentNumber ?
                            String.format("{%s}", slot.toString(words)) : slot.toString(words));
                    System.out.print(slotStr + "\t");
                }
                System.out.println();
                // Print question
                System.out.println(questionStr + "\t" + words.get(targetDependency.getArgumentIndex()));
                // Print matching information.
                System.out.println(matchedGold == null ? "[no-gold]" : matchedGold.toString(words));
                // FIXME: are those indices not aligned? Weird.
                System.out.println(matchedQA == null ? "[no-qa]" : matchedQA.toString(qaSentence.getWords()));
                System.out.println();
            }
        }

        System.out.println("Final result: F1=" + alignedDev.get("F1").get(0) + " on aligned PB-QA sentences");
        System.out.println("N-Best oracle:\n" + nbestOracle.toString());
        System.out.println("N-Best oracle with QG:\n" + nbestQGOracle.toString());
        return resultsTable;
    }

    private static class RerankingInfo {
        int nBest;
        ResolvedDependency targetDependency;
        QuestionTemplate template;

        RerankingInfo(int nBest, ResolvedDependency targetDependency, QuestionTemplate template) {
            this.nBest = nBest;
            this.targetDependency = targetDependency;
            this.template = template;
        }
    }

    private static SRLDependency matchGoldDependency(ResolvedDependency targetDependency,
                                                     Collection<SRLDependency> goldDependencies) {
        for (final SRLDependency goldDep : goldDependencies) {
            if (goldDep.getArgumentPositions().size() == 0) {
                continue;
            }
            int predicateIndex, argumentIndex;
            if (goldDep.isCoreArgument()) {
                predicateIndex = targetDependency.getHead();
                argumentIndex = targetDependency.getArgumentIndex();
            } else {
                predicateIndex = targetDependency.getArgumentIndex();
                argumentIndex = targetDependency.getHead();
            }
            if (goldDep.getPredicateIndex() == predicateIndex
                    // && (goldDep.getLabel() == targetDependency.getSemanticRole())
                    && goldDep.getArgumentPositions().contains(argumentIndex)) {
                return goldDep;
            }
        }
        return null;
    }

    private static QADependency matchQADependency(ResolvedDependency targetDependency,
                                                  Collection<QADependency> qaDependencies) {
        for (final QADependency qaDep : qaDependencies) {
            if (qaDep.unlabeledMatch(targetDependency)) {
                return qaDep;
            }
        }
        return null;
    }
}
