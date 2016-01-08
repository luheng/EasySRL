package edu.uw.easysrl.active_learning;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.util.Util;

import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calls a parser. Input: List<InputWord>, Output: List<Category>, Set<ResolvedDependency>
 * Created by luheng on 1/5/16.
 */
public abstract class BaseCcgParser {

    public abstract void parse(List<InputReader.InputWord> sentence, List<Category> categories,
                               Set<ResolvedDependency> dependencies);

    public static class EasyCCGParser extends BaseCcgParser {

        private SRLParser parser = null;

        public EasyCCGParser(String modelFolderPath, int nBest)  {
            final File modelFolder = Util.getFile(modelFolderPath);
            if (!modelFolder.exists()) {
                throw new InputMismatchException("Couldn't load model from from: " + modelFolder);
            }
            final File pipelineFolder = new File(modelFolder, "/pipeline");
            System.err.println("====Starting loading model====");
            final POSTagger posTagger = POSTagger.getStanfordTagger(new File(pipelineFolder, "posTagger"));
            try {
                parser = new SRLParser.PipelineSRLParser(
                        EasySRL.makeParser(pipelineFolder.getAbsolutePath(), 0.0001,
                                //EasySRL.ParsingAlgorithm.CKY,
                                EasySRL.ParsingAlgorithm.ASTAR,
                                200000, false /* joint */, Optional.empty(), nBest),
                        Util.deserialize(new File(pipelineFolder, "labelClassifier")), posTagger);
            } catch (IOException e) {
            }
        }


        @Override
        public void parse(List<InputReader.InputWord> sentence, List<Category> categories,
                          Set<ResolvedDependency> dependencies) {
            if (parser == null) {
                throw new RuntimeException("Parser uninitialized");
            }
            List<SRLParser.CCGandSRLparse> parses = parser.parseTokens(sentence);
            if (parses == null || parses.size() == 0) {
                return;
            }
            // 1-best here..
            assert categories != null && dependencies != null;
            for (int i = 0; i < sentence.size(); i++) {
                categories.add(parses.get(0).getLeaf(i).getCategory());
            }
            dependencies.addAll(parses.get(0).getDependencyParse());
        }
    }

    // TODO: debug
    public static class EasySRLParser extends BaseCcgParser {

        private SRLParser parser = null;

        public EasySRLParser(String modelFolderPath, int nBest)  {
            final File modelFolder = Util.getFile(modelFolderPath);
            if (!modelFolder.exists()) {
                throw new InputMismatchException("Couldn't load model from from: " + modelFolder);
            }
            final File pipelineFolder = new File(modelFolder, "/pipeline");
            System.err.println("====Starting loading model====");
            final POSTagger posTagger = POSTagger.getStanfordTagger(new File(pipelineFolder, "posTagger"));
            try {
                SRLParser pipelineParser = new SRLParser.PipelineSRLParser(
                        EasySRL.makeParser(pipelineFolder.getAbsolutePath(), 0.0001,
                                EasySRL.ParsingAlgorithm.ASTAR,
                                200000, false /* joint */, Optional.empty(), nBest),
                        Util.deserialize(new File(pipelineFolder, "labelClassifier")), posTagger);
                parser = new SRLParser.BackoffSRLParser(new SRLParser.JointSRLParser(
                                ActiveLearningHelper.makeParser(modelFolderPath, 0.0001,
                                        EasySRL.ParsingAlgorithm.ASTAR,
                                        200000, true /* joint */, Optional.empty(), nBest), posTagger), pipelineParser);
            } catch (IOException e) {
            }
        }

        @Override
        public void parse(List<InputReader.InputWord> sentence, List<Category> categories,
                          Set<ResolvedDependency> dependencies) {
            if (parser == null) {
                throw new RuntimeException("Parser uninitialized");
            }
            List<SRLParser.CCGandSRLparse> parses = parser.parseTokens(sentence);
            if (parses == null || parses.size() == 0) {
                return;
            }
            // 1-best here..
            assert categories != null && dependencies != null;
            for (int i = 0; i < sentence.size(); i++) {
                categories.add(parses.get(0).getLeaf(i).getCategory());
            }
            dependencies.addAll(parses.get(0).getDependencyParse());
        }
    }

    public static class BharatParser extends BaseCcgParser {

        Map<String, Integer> sentences;
        List<Set<ResolvedDependency>> parsed;

        public BharatParser(String parsedFile)  {
            BufferedReader reader;
            String line;
            sentences = new HashMap<>();
            parsed = new ArrayList<>();
            try {
                int sentenceIdx = 0;
                reader = new BufferedReader(new FileReader(parsedFile));
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#") || line.trim().isEmpty()) {
                        continue;
                    }
                    String sentStr = "";
                    if (line.startsWith("<c> ")) {
                        String[] info = line.trim().split("\\s+");
                        for (int i = 1; i < info.length; i++) {
                            sentStr += (i > 0 ?  " " : "") + info[i].split("|")[0];
                        }
                        assert sentenceIdx == parsed.size() - 1;
                        sentences.put(sentStr, sentenceIdx);
                        parsed.set(sentenceIdx, propagatePrepositions(parsed.get(sentenceIdx)));
                        sentenceIdx++;
                        continue;
                    }
                    if (sentenceIdx == parsed.size()) {
                        parsed.add(new HashSet<>());
                    }
                    // Parse the f--king line:
                    // saw_2(S[dcl]\NP)/NP1 I_1 0
                    // in_29((S\NP)\(S\NP))/NP3 hours_33 0
                    // Getting: in ((S\NP)\(S\NP))/NP hours
                    String[] stringsInfo = line.trim().split("[_\\d]+");
                    // Getting: _ 29 3 33 0
                    String[] indicesInfo = line.trim().split("[^\\d]+");
                    int predIdx = Integer.parseInt(indicesInfo[1]) - 1;
                    Category category = Category.valueOf(stringsInfo[1].trim());
                    int argNum = Integer.parseInt(indicesInfo[2]);
                    int argIdx = Integer.parseInt(indicesInfo[indicesInfo.length - 2]) - 1;
                    Preposition preposition = Preposition.NONE;
                    if (category.getArgument(argNum).equals(Category.PP)) {
                        preposition = Preposition.fromString(stringsInfo[2].trim());
                    }
                    parsed.get(sentenceIdx).add(new ResolvedDependency(predIdx, category, argNum, argIdx, SRLFrame.NONE,
                            preposition));
                }
            } catch (IOException e) {
            }
        }

        private static Set<ResolvedDependency> propagatePrepositions(Set<ResolvedDependency> dependencies) {
            Set<ResolvedDependency> result = new HashSet<>();
            for (ResolvedDependency dep : dependencies) {
                Category argCat = dep.getCategory().getArgument(dep.getArgNumber());
                if (!argCat.equals(Category.PP) && !argCat.equals(Category.valueOf("S[to]\\NP"))) {
                    result.add(dep);
                    continue;
                }
                for (ResolvedDependency d2 : dependencies) {
                    if (d2.getCategory().isFunctionInto(argCat) && d2.getHead() == dep.getArgument()) {
                        result.add(new ResolvedDependency(dep.getHead(), dep.getCategory(), dep.getArgNumber(),
                                d2.getArgument(), SRLFrame.NONE, dep.getPreposition()));
                        break;
                    }
                }
            }
            return result;
        }

        @Override
        public void parse(List<InputReader.InputWord> sentence, List<Category> categories,
                          Set<ResolvedDependency> dependencies) {
            String sentStr = StringUtils.join(sentence.stream().map(w->w.word).collect(Collectors.toList()));
            if (!sentences.containsKey(sentStr)) {
                System.err.println("unable to parse:\t" + sentStr);
                return;
            }
            dependencies.addAll(parsed.get(sentences.get(sentStr)));
            for (int i = 0; i < sentence.size(); i++) {
                categories.add(null);
            }
            for (ResolvedDependency dep : dependencies) {
                categories.set(dep.getHead(), dep.getCategory());
                int argId = dep.getArgument();
                Category argCat = dep.getCategory().getArgument(dep.getArgNumber());
                // Simple heuristic that writes N over NP.
                if (categories.get(argId) == null ||
                        categories.get(argId).toString().length() > argCat.toString().length()) {
                    categories.set(argId, argCat);
                }
            }
        }
    }
}
