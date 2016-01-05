package edu.uw.easysrl.active_learning;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Calls a parser. Input: List<InputWord>, Output: List<Category>, Set<ResolvedDependency>
 * Created by luheng on 1/5/16.
 */
public abstract class ActiveLearingBaseParser {

    public abstract void parse(List<InputReader.InputWord> sentence, List<Category> categories,
                               Collection<ResolvedDependency> dependencies);

    public static class EasyCCGParser extends ActiveLearingBaseParser {

        private static SRLParser parser = null;

        public static void initialzieParser(String modelFolderPath, int nBest)  {
            final File modelFolder = Util.getFile(modelFolderPath);
            if (!modelFolder.exists()) {
                throw new InputMismatchException("Couldn't load model from from: " + modelFolder);
            }
            final File pipelineFolder = new File(modelFolder, "/pipeline");
            System.err.println("====Starting loading model====");
            final POSTagger posTagger = POSTagger.getStanfordTagger(new File(pipelineFolder, "posTagger"));
            try {
                parser = new SRLParser.PipelineSRLParser(
                        ActiveLearningHelper.makeParser(pipelineFolder.getAbsolutePath(), 0.0001,
                                EasySRL.ParsingAlgorithm.CKY, 200000, false /* joint */, Optional.empty(), nBest),
                        Util.deserialize(new File(pipelineFolder, "labelClassifier")), posTagger);
            } catch (IOException e) {
            }
        }


        @Override
        public void parse(List<InputReader.InputWord> sentence, List<Category> categories,
                          Collection<ResolvedDependency> dependencies) {
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

    public static class BharatParser extends ActiveLearingBaseParser {
        @Override
        public void parse(List<InputReader.InputWord> sentence, List<Category> categories,
                          Collection<ResolvedDependency> dependencies) {
            // TODO
        }
    }
}
