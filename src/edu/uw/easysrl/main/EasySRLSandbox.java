package edu.uw.easysrl.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
          "I saw a squirrel.",
          "I saw a squirrel with a nut yesterday.",
          "They increased the rent from $100,000 to $200,000.",
          "The squirrel increased the price from 1 nut to 2 nuts.",
          "I want the mug on the table that I usually drink from.",
          "Could you pass me the mug on the table that I usually drink from?",
          "Pass me the mug on the table that I usually drink from!"
    };

    public static void main(final String[] args) throws IOException, InterruptedException {
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
            int id = 0;
            for (String rawSentence : spielzeuge) {
                id++;
                final int id2 = id; // what is this for?
                final List<CCGandSRLparse> parses = parser.parseTokens(reader.readInput(rawSentence).getInputWords());
                final String output = printer.printJointParses(parses, id2);
                System.out.println(output);
            }

        } catch (final ArgumentValidationException e) {
            System.err.println(e.getMessage());
            System.err.println(CliFactory.createCli(CommandLineArguments.class).getHelpMessage());
        }
    }
}
