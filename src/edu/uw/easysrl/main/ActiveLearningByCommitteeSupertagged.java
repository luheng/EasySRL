package edu.uw.easysrl.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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

public class ActiveLearningByCommitteeSupertagged {
    /* public static String[] spielzeuge = {
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
    }; */

    public static void main(final String[] args) throws IOException, InterruptedException {
        // Read results from another parser ..
        List<Collection<ResolvedDependency>> bharatDeps = ActiveLearningHelper.readBharatParserDependencies(
                new File("/Users/luheng/Workspace/EasySRL/toy.txt.parg"));
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
                    ActiveLearningHelper.makeParser(
                            pipelineFolder.getAbsolutePath(), 0.0001, ParsingAlgorithm.CKY, 200000,
                                false /* joint */, Optional.empty(), commandLineOptions.getNbest()),
                    Util.deserialize(new File(pipelineFolder, "labelClassifier")), posTagger);
            // FIXME: it causes some bugs ...
            /* final SRLParser.BackoffSRLParser joint = new SRLParser.BackoffSRLParser(
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
                    InputReader.InputToParser parserInput = reader.readInput(buffer);
                    final List<CCGandSRLparse> parses = parser.parseSupertaggedSentence(parserInput);
                    easyCcgParses.add(parses.get(0));
                    sentences.add(parserInput.getInputWords());
                    //generateQuestions(parserInput.getInputWords(), parses, bharatDeps.get(sentenceIdx));
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

        // Compare parses.
        ActiveLearningHelper.compareDependencies(sentences, easyCcgParses, bharatDeps);
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
