package edu.uw.easysrl.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.QuestionSlot;
import edu.uw.easysrl.qasrl.qg.QuestionTemplate;
import edu.uw.easysrl.qasrl.qg.VerbHelper;
import edu.uw.easysrl.syntax.grammar.Category;
import uk.co.flamingpenguin.jewel.cli.ArgumentValidationException;
import uk.co.flamingpenguin.jewel.cli.CliFactory;

import com.google.common.base.Stopwatch;

import edu.uw.easysrl.main.EasySRL.CommandLineArguments;
import edu.uw.easysrl.main.EasySRL.InputFormat;
import edu.uw.easysrl.main.EasySRL.OutputFormat;
import edu.uw.easysrl.main.EasySRL.ParsingAlgorithm;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.syntax.parser.SRLParser.PipelineSRLParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.util.Util;

public class EasySRLSandbox {

    public static String[] spielzeuge = {
            "I saw a squirrel .",
            "I saw a squirrel with a nut yesterday .",
            "I saw a squirrel eating a nut yesterday .",
            "The squirrel refused to leave .",
            "I promised the squirrel to give it a nut tomorrow .",
            "They increased the rent from $100,000 to $200,000 .",
            "The squirrel increased the price from 1 nut to 2 nuts .",
            "I want the mug on the table that I usually drink from .",
            "I want the mug on the table that I usually drink water from .",
            //"Could you pass me the mug on the table that I usually drink from ?",
            "Pass me the mug on the table that I usually drink from !",
            "The man with mug was mugged by another man ."
    };

    private static QuestionGenerator questionGenerator;

    public static void main(final String[] args) throws IOException, InterruptedException {
        questionGenerator = new QuestionGenerator();
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
            final PipelineSRLParser pipeline = new PipelineSRLParser(
                        EasySRL.makeParser(pipelineFolder.getAbsolutePath(), 0.0001, ParsingAlgorithm.ASTAR, 200000,
                        false /* joint */, Optional.empty(),
                    commandLineOptions.getNbest()),
                    Util.deserialize(new File(pipelineFolder, "labelClassifier")), posTagger);
            final OutputFormat outputFormat = OutputFormat.valueOf(commandLineOptions.getOutputFormat().toUpperCase());
            final ParsePrinter printer = outputFormat.printer;
            final SRLParser parser = pipeline;

            final InputReader reader = InputReader.make(InputFormat.valueOf(commandLineOptions.getInputFormat().toUpperCase()));
            if ((outputFormat == OutputFormat.PROLOG || outputFormat == OutputFormat.EXTENDED)
                    && input != InputFormat.POSANDNERTAGGED) {
                throw new Error("Must use \"-i POSandNERtagged\" for this output");
            }
            System.err.println("===Model loaded: parsing...===");

            // Run over toy sentences.
            for (String rawSentence : spielzeuge) {
                List<InputReader.InputWord> words = reader.readInput(rawSentence).getInputWords();
                final List<CCGandSRLparse> parses = parser.parseTokens(words);
                generateQuestions(words, parses);
            }

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
        List<String> words = new ArrayList<>();
        List<Category> categories = new ArrayList<>();
        for (int i = 0; i < inputWords.size(); i++) {
            words.add(parses.get(0).getLeaf(i).getWord());
            categories.add(parses.get(0).getLeaf(i).getCategory());
        }

        words.forEach(w -> System.out.print(w + " ")); System.out.println();
        categories.forEach(cat -> System.out.print(cat + " ")); System.out.println();

        for (CCGandSRLparse parse : parses) {
            Collection<ResolvedDependency> dependencies = parse.getDependencyParse();
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

                // Print sentence and template.
                String ccgInfo = targetDependency.getCategory() + "_" + targetDependency.getArgNumber();
                System.out.println(ccgInfo);
                for (QuestionSlot slot : template.slots) {
                    String slotStr = (slot.argumentNumber == argumentNumber ?
                            String.format("{%s}", slot.toString(words)) : slot.toString(words));
                    System.out.print(slotStr + "\t");
                }
                System.out.println();
                // Print question.
                System.out.println(questionStr + "\t" + words.get(targetDependency.getArgumentIndex()));
                // Print target dependency.
                System.out.println(words.get(targetDependency.getHead()) + ":" + targetDependency.getSemanticRole() +
                        "\t" + words.get(targetDependency.getArgumentIndex()));
                System.out.println();
            }
        }
    }
}
