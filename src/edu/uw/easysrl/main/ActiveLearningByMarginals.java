package edu.uw.easysrl.main;

import java.io.File;
import java.io.IOException;
import java.util.*;

import edu.uw.easysrl.syntax.parser.*;
import uk.co.flamingpenguin.jewel.cli.ArgumentValidationException;
import uk.co.flamingpenguin.jewel.cli.CliFactory;


import edu.uw.easysrl.main.EasySRL.CommandLineArguments;
import edu.uw.easysrl.main.EasySRL.InputFormat;
import edu.uw.easysrl.main.EasySRL.ParsingAlgorithm;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.syntax.parser.SRLParser.PipelineSRLParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.util.Util;

public class ActiveLearningByMarginals {
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

    public static void main(final String[] args) throws IOException, InterruptedException {
        List<List<InputReader.InputWord>> sentences = new ArrayList<>();
        List<CCGandSRLparse> easyCcgParses = new ArrayList<>();

        // Read sentences to parse.
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
                    ActiveLearningHelper
                            .makeParser(pipelineFolder.getAbsolutePath(), 0.0001, ParsingAlgorithm.CKY, 200000,
                                    false /* joint */, Optional.empty(), commandLineOptions.getNbest()),
                    Util.deserialize(new File(pipelineFolder, "labelClassifier")), posTagger);
            final SRLParser.BackoffSRLParser joint = new SRLParser.BackoffSRLParser(
                    new SRLParser.JointSRLParser(
                            ActiveLearningHelper
                                    .makeParser(commandLineOptions, 20000, true, Optional.empty()), posTagger), pipeline);
            final SRLParser parser = pipeline;

            System.err.println("===Model loaded: parsing...===");

            // Go over sample sentences.
            for (String sentence : spielzeuge) {
                InputReader.InputToParser parserInput = reader.readInput(sentence);
                final List<CCGandSRLparse> parses = parser.parseTokens(parserInput.getInputWords());

                easyCcgParses.add(parses.get(0));
                sentences.add(parserInput.getInputWords());
                ActiveLearningHelper.generateQuestions(parserInput.getInputWords(), parses);
            }
        } catch (final ArgumentValidationException e) {
            System.err.println(e.getMessage());
            System.err.println(CliFactory.createCli(CommandLineArguments.class).getHelpMessage());
        }
    }


}
