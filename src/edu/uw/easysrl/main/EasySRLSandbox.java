package edu.uw.easysrl.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.QuestionSlot;
import edu.uw.easysrl.qasrl.qg.QuestionTemplate;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;
import edu.uw.easysrl.syntax.model.Model;
import edu.uw.easysrl.syntax.model.SRLFactoredModel;
import edu.uw.easysrl.syntax.model.SupertagFactoredModel;
import edu.uw.easysrl.syntax.model.feature.FeatureSet;
import edu.uw.easysrl.syntax.parser.Parser;
import edu.uw.easysrl.syntax.parser.ParserAStar;
import edu.uw.easysrl.syntax.parser.ParserCKY;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.syntax.tagger.TaggerDummy;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.syntax.training.Training;
import sun.security.krb5.internal.ASRep;
import uk.co.flamingpenguin.jewel.cli.ArgumentValidationException;
import uk.co.flamingpenguin.jewel.cli.CliFactory;


import edu.uw.easysrl.main.EasySRL.CommandLineArguments;
import edu.uw.easysrl.main.EasySRL.InputFormat;
import edu.uw.easysrl.main.EasySRL.ParsingAlgorithm;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.syntax.parser.SRLParser.PipelineSRLParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.util.Util;

public class EasySRLSandbox {

    /*
    public static String[] spielzeuge = {
            "I saw a squirrel .",
            "I saw a squirrel with a nut yesterday .",
            "I saw a squirrel eating a nut yesterday .",
            "The squirrel refused to leave .",
            "I promised the squirrel to give it a nut tomorrow .",
            "They increased the rent from $100,000 to $200,000 .",
            "The squirrel increased the demand from 1 nut to 2 nuts .",
            "I want the mug on the table that I usually drink from .",
            "I want the mug on the table that I usually drink water from .",
            "Could you pass me the mug on the table that I usually drink from ?",
            "Pass me the mug on the table that I usually drink from !",
            "The man with mug was mugged by another man ."
    };
    */

    // TODO: run the same sentences over the pipeline model to see if there's any difference.
    private static QuestionGenerator questionGenerator;

    public static void main(final String[] args) throws IOException, InterruptedException {
        questionGenerator = new QuestionGenerator();
        try {
            final CommandLineArguments commandLineOptions = CliFactory.parseArguments(CommandLineArguments.class, args);
            final File modelFolder = Util.getFile(commandLineOptions.getModel());
            final InputReader reader = InputReader.make(InputFormat.valueOf(commandLineOptions.getInputFormat()
                    .toUpperCase()));

            if (!modelFolder.exists()) {
                throw new InputMismatchException("Couldn't load model from from: " + modelFolder);
            }

            final File pipelineFolder = new File(modelFolder, "/pipeline");
            System.err.println("====Starting loading model====");
            final POSTagger posTagger = POSTagger.getStanfordTagger(new File(pipelineFolder, "posTagger"));

            // Use CKY here, because we want marginal scores ..
            final PipelineSRLParser pipeline = new PipelineSRLParser(
                        makeParser(pipelineFolder.getAbsolutePath(), 0.0001, ParsingAlgorithm.CKY, 200000,
                                false /* joint */, Optional.empty(), commandLineOptions.getNbest()),
                    Util.deserialize(new File(pipelineFolder, "labelClassifier")), posTagger);
            // FIXME: it causes some bugs ...
            /*
            final SRLParser.BackoffSRLParser joint = new SRLParser.BackoffSRLParser(
                    new SRLParser.JointSRLParser(makeParser(commandLineOptions, 20000, true, Optional.empty()), posTagger),
                    pipeline);*/

            final SRLParser parser = pipeline;

            System.err.println("===Model loaded: parsing...===");

            // Read sentences.
            BufferedReader fileReader = new BufferedReader(new FileReader(new File(commandLineOptions.getInputFile())));
            String buffer = "";
            String line;
            while ((line = fileReader.readLine()) != null) {
                if (line.trim().isEmpty() && !buffer.isEmpty()) {
                    // System.out.println(buffer + "\n\n\n");
                    InputReader.InputToParser parserInput = reader.readInput(buffer);
                    final List<CCGandSRLparse> parses = parser.parseSupertaggedSentence(parserInput);
                    generateQuestions(parserInput.getInputWords(), parses);
                    buffer = "";
                } else {
                    buffer += (buffer.isEmpty() ? "" : "\n") + line.trim();
                }
            }
            fileReader.close();
        } catch (final ArgumentValidationException e) {
            System.err.println(e.getMessage());
            System.err.println(CliFactory.createCli(CommandLineArguments.class).getHelpMessage());
        }
    }

    private static void generateQuestions(List<InputReader.InputWord> inputWords, List<CCGandSRLparse> parses) {
        if (parses == null || parses.size() == 0) {
            System.out.println("Unable to parse.");
            return;
        }
        int sentenceLength = inputWords.size();
        List<String> words = new ArrayList<>();
        List<Category> categories = new ArrayList<>();
        for (int i = 0; i < sentenceLength; i++) {
            words.add(parses.get(0).getLeaf(i).getWord());
            categories.add(parses.get(0).getLeaf(i).getCategory());
        }

        words.forEach(w -> System.out.print(w + " ")); System.out.println();
        categories.forEach(cat -> System.out.print(cat + " ")); System.out.println("\n");

        // TODO: get some scores for the dependencies
        System.out.println(parses.size());
        for (CCGandSRLparse parse : parses) {
            Collection<ResolvedDependency> dependencies = parse.getDependencyParse();
            for (int predicateId = 0; predicateId < sentenceLength; predicateId++) {
                String predicateWord = words.get(predicateId);
                Category predicateCategory = categories.get(predicateId);
                Collection<ResolvedDependency> deps = parse.getOrderedDependenciesAtPredicateIndex(predicateId);
                if (deps == null || deps.size() == 0) {
                    continue;
                }
                /*
                System.out.println("\t" + predicateWord + "\t" + predicateCategory);
                System.out.println("# deps found for " + predicateWord + " : " + deps.size());
                if (questionGenerator.filterPredicate(predicateWord, predicateCategory)) {
                    System.out.println("Skip this predicate.");
                    continue;
                }
                */
                for (ResolvedDependency targetDependency : deps) {
                    if (targetDependency == null) {
                        continue;
                    }
                    int predicateIndex = targetDependency.getHead();
                    int argumentNumber = targetDependency.getArgNumber();
                    // Get template.
                    QuestionTemplate template = questionGenerator.getTemplate(predicateIndex, words, categories,
                            dependencies);
                    if (template == null) {
                        // System.out.println("Cannot generate template for " + targetDependency.toString(words));
                        continue;
                    }
                    // Get question.
                    List<String> question = questionGenerator.generateQuestionFromTemplate(template, argumentNumber);
                    if (question == null) {
                        System.out.println("Cannot generate question for " + template.toString());
                        continue;
                    }
                    String questionStr = StringUtils.join(question) + " ?";

                    // Print sentence and template.
                    String ccgInfo = targetDependency.getCategory() + "_" + targetDependency.getArgNumber();
                    System.out.println("\t" + ccgInfo);
                    System.out.print("\t");
                    for (QuestionSlot slot : template.slots) {
                        String slotStr = (slot.argumentNumber == argumentNumber ?
                                String.format("{%s}", slot.toString(words)) : slot.toString(words));
                        System.out.print(slotStr + "\t");
                    }
                    System.out.println();
                    // Print question.
                    System.out.println("\t" + questionStr + "\t" + words.get(targetDependency.getArgumentIndex()));
                    // Print target dependency.
                    System.out.println("\t" + words.get(targetDependency.getHead()) + ":" + targetDependency.getSemanticRole() +
                            "\t" + words.get(targetDependency.getArgumentIndex()));
                    System.out.println();
                }
            }
            break;
        }
    }

    private static Parser makeParser(final String modelFolder, final double supertaggerBeam,
                                     final ParsingAlgorithm parsingAlgorithm, final int maxChartSize,
                                     final boolean joint,
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

        Model.ModelFactory modelFactory;
        final ParsingAlgorithm algorithm = ParsingAlgorithm.valueOf(commandLineOptions.getParsingAlgorithm()
                .toUpperCase());

        if (joint) {
            final double[] weights = Util.deserialize(new File(modelFolder, "weights"));
            if (supertaggerWeight.isPresent()) {
                weights[0] = supertaggerWeight.get();
            }
            modelFactory = new SRLFactoredModel.SRLFactoredModelFactory(weights,
                    ((FeatureSet) Util.deserialize(new File(modelFolder, "features")))
                            .setSupertaggingFeature(new File(modelFolder, "/pipeline"),
                                    commandLineOptions.getSupertaggerbeam()),
                    TaggerEmbeddings.loadCategories(new File(modelFolder, "categories")), cutoffs,
                    Util.deserialize(new File(modelFolder, "featureToIndex")));
        } else {
            modelFactory = new SupertagFactoredModel.SupertagFactoredModelFactory(
                    new TaggerDummy(modelFolder, 0, 0, cutoffs));
        }

        final Parser parser;
        final int nBest = commandLineOptions.getNbest();
        if (algorithm == ParsingAlgorithm.CKY) {
            parser = new ParserCKY(modelFactory, commandLineOptions.getMaxLength(), nBest,
                    InputFormat.valueOf(commandLineOptions.getInputFormat().toUpperCase()),
                    commandLineOptions.getRootCategories(), modelFolder, maxChartSize);
        } else {
            parser = new ParserAStar(modelFactory, commandLineOptions.getMaxLength(), nBest,
                    InputFormat.valueOf(commandLineOptions.getInputFormat().toUpperCase()),
                    commandLineOptions.getRootCategories(), modelFolder, maxChartSize);
        }
        return parser;
    }

    /*
    public static void readTaggedInput(File taggedInputFile, List<List<String>> sentences,
                                       List<List<List<Tagger.ScoredCategory>>> sentenceTags) {
        BufferedReader reader;
        String line;
        assert sentences != null && sentenceTags != null;
        try {
            int sentenceIdx = 0;
            reader = new BufferedReader(new FileReader(taggedInputFile));
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    sentenceIdx ++;
                    continue;
                }
                if (sentenceIdx == sentences.size()) {
                    sentences.add(new ArrayList<>());
                    sentenceTags.add(new ArrayList<>());
                }
                String[] info = line.trim().split("\\s+");
                List<Tagger.ScoredCategory> scoredCategories = new ArrayList<>();
                int numCategories = Integer.parseInt(info[2]);
                for (int i = 0; i < numCategories; i++) {
                    int j = i * 2 + 3;
                    scoredCategories.add(new Tagger.ScoredCategory(
                            Category.valueOf(info[j]), Double.parseDouble(info[j + 1])));
                }
                sentences.get(sentenceIdx).add(info[0]);
                sentenceTags.get(sentenceIdx).add(scoredCategories);
            }
            reader.close();
        } catch (IOException e) {
        }
        assert sentences.size() == sentenceTags.size();
        System.out.println("Read " + sentences.size() + " sentences from " + taggedInputFile.getName());
    }
    */
}
