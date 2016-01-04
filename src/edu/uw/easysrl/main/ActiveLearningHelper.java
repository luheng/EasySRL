package edu.uw.easysrl.main;

import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.QuestionSlot;
import edu.uw.easysrl.qasrl.qg.QuestionTemplate;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;
import edu.uw.easysrl.syntax.model.Model;
import edu.uw.easysrl.syntax.model.SRLFactoredModel;
import edu.uw.easysrl.syntax.model.SupertagFactoredModel;
import edu.uw.easysrl.syntax.model.feature.FeatureSet;
import edu.uw.easysrl.syntax.parser.Parser;
import edu.uw.easysrl.syntax.parser.ParserCKY2;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.util.Util;
import uk.co.flamingpenguin.jewel.cli.ArgumentValidationException;
import uk.co.flamingpenguin.jewel.cli.CliFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 1/3/16.
 */
public class ActiveLearningHelper {
    public static QuestionGenerator questionGenerator = new QuestionGenerator();

    public static Parser makeParser(final String modelFolder, final double supertaggerBeam,
                                     final EasySRL.ParsingAlgorithm parsingAlgorithm, final int maxChartSize,
                                     final boolean joint,
                                     final Optional<Double> supertaggerWeight, final int nbest) throws IOException {
        EasySRL.CommandLineArguments commandLineOptions;
        try {
            commandLineOptions = CliFactory.parseArguments(EasySRL.CommandLineArguments.class, new String[]{"-m",
                    modelFolder, "--supertaggerbeam", "" + supertaggerBeam, "-a", parsingAlgorithm.toString(),
                    "--nbest", "" + nbest});

        } catch (final ArgumentValidationException e) {
            throw new RuntimeException(e);
        }
        return makeParser(commandLineOptions, maxChartSize, joint, supertaggerWeight);
    }

    public static Parser makeParser(final EasySRL.CommandLineArguments commandLineOptions, final int maxChartSize,
                                     final boolean joint, final Optional<Double> supertaggerWeight) throws IOException {
        final File modelFolder = Util.getFile(commandLineOptions.getModel());
        Coindexation.parseMarkedUpFile(new File(modelFolder, "markedup"));
        final File cutoffsFile = new File(modelFolder, "cutoffs");
        final CutoffsDictionary cutoffs = cutoffsFile.exists() ? Util.deserialize(cutoffsFile) : null;

        Model.ModelFactory modelFactory;
        final EasySRL.ParsingAlgorithm algorithm = EasySRL.ParsingAlgorithm.valueOf(commandLineOptions.getParsingAlgorithm()
                .toUpperCase());
        double supertaggerBeam = commandLineOptions.getSupertaggerbeam();

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
            //modelFactory = new SupertagFactoredModel.SupertagFactoredModelFactory(
            //        new TaggerDummy(modelFolder, 0, 0, cutoffs));
            modelFactory = new SupertagFactoredModel.SupertagFactoredModelFactory(
                    Tagger.make(modelFolder, supertaggerBeam, 50, cutoffs));
        }

        final int nBest = 1; // commandLineOptions.getNbest();
        return new ParserCKY2(modelFactory, commandLineOptions.getMaxLength(), nBest,
                EasySRL.InputFormat.valueOf(commandLineOptions.getInputFormat().toUpperCase()),
                commandLineOptions.getRootCategories(), modelFolder, maxChartSize);
    }


    public static List<Collection<ResolvedDependency>> readBharatParserDependencies(File dependenciesFile) {
        BufferedReader reader;
        String line;
        List<Collection<ResolvedDependency>> dependencies = new ArrayList<>();
        try {
            int sentenceIdx = 0;
            reader = new BufferedReader(new FileReader(dependenciesFile));
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.startsWith("<c> ")) {
                    continue;
                }
                if (line.trim().isEmpty()) {
                    if (sentenceIdx < dependencies.size()) {
                        sentenceIdx++;
                    }
                    continue;
                }
                if (sentenceIdx == dependencies.size()) {
                    dependencies.add(new HashSet<>());
                }
                // Parse the f--king line:
                // saw_2(S[dcl]\NP)/NP1 I_1 0
                // in_29((S\NP)\(S\NP))/NP3 hours_33 0
                // in ((S\NP)\(S\NP))/NP hours
                String[] stringsInfo = line.trim().split("[_\\d]+");
                // _ 29 3 33 0
                String[] indicesInfo = line.trim().split("[^\\d]+");

                int predIdx = Integer.parseInt(indicesInfo[1]) - 1;
                Category predCateogory = Category.valueOf(stringsInfo[1]);
                int argNum = Integer.parseInt(indicesInfo[2]);
                int argIdx = Integer.parseInt(indicesInfo[indicesInfo.length - 2]) - 1;

                //System.out.println(line);
                //System.out.println(predIdx + "\t" + predCateogory + "\t" + argNum + "\t" + argIdx);

                dependencies.get(sentenceIdx).add(new ResolvedDependency(predIdx, predCateogory, argNum, argIdx,
                        SRLFrame.NONE, Preposition.NONE));
            }
        } catch (IOException e) {
            return null;
        }
        return dependencies;
    }

    public static void compareDependencies(List<List<InputReader.InputWord>> sentences,
                                            List<SRLParser.CCGandSRLparse> easyCcgParses,
                                            List<Collection<ResolvedDependency>> bharatDeps) {
        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx++) {
            List<InputReader.InputWord> sentence = sentences.get(sentIdx);
            List<String> words = sentence.stream().map(w -> w.word).collect(Collectors.toList());
            List<ResolvedDependency> parse1 = new ArrayList<>(easyCcgParses.get(sentIdx).getDependencyParse());
            List<ResolvedDependency> parse2 = new ArrayList<>(bharatDeps.get(sentIdx));

            int[] matched1 = new int[parse1.size()];
            int[] matched2 = new int[parse2.size()];
            Arrays.fill(matched1, -1);
            Arrays.fill(matched2, -1);

            for (int i1 = 0; i1 < parse1.size(); i1++) {
                for (int i2 = 0; i2 < parse2.size(); i2++) {
                    ResolvedDependency d1 = parse1.get(i1);
                    ResolvedDependency d2 = parse2.get(i2);
                    if (d1.getHead() == d2.getHead() && d1.getArgumentIndex() == d2.getArgumentIndex()) {
                        matched1[i1] = i2;
                        matched2[i2] = i1;
                    }
                }
            }

            System.out.println("\n" + StringUtils.join(words));

            System.out.println("[UNMATCHED DEPENDENCIES]");
            for (int i1 = 0; i1 < parse1.size(); i1++) {
                if (matched1[i1] < 0) {
                    ResolvedDependency d1 = parse1.get(i1);
                    System.out.println("easyccg\t" + d1.getCategory() + "\t" + d1.toString(words));
                }
            }
            for (int i2 = 0; i2 < parse2.size(); i2++) {
                if (matched2[i2] < 0) {
                    ResolvedDependency d2 = parse2.get(i2);
                    System.out.println("bharat\t" + d2.getCategory() + "\t" + d2.toString(words));
                }
            }
            System.out.println("[MATCHED DEPENDENCIES]");
            for (int i1 = 0; i1 < parse1.size(); i1++) {
                if (matched1[i1] >= 0) {
                    ResolvedDependency d1 = parse1.get(i1), d2 = parse2.get(matched1[i1]);
                    System.out.print("\t\t" + d1.getCategory() + "\t" + d1.toString(words));
                    System.out.println("\t\t" + d2.getCategory() + "\t" + d2.toString(words));
                }
            }
        }
    }

    public static void generateQuestions(List<InputReader.InputWord> inputWords, List<SRLParser.CCGandSRLparse> parses) {
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
        for (SRLParser.CCGandSRLparse parse : parses) {
            Collection<ResolvedDependency> dependencies = parse.getDependencyParse();
            for (int predicateId = 0; predicateId < sentenceLength; predicateId++) {
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
                        System.out.println("Cannot generate template for " + targetDependency.toString(words));
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
}
