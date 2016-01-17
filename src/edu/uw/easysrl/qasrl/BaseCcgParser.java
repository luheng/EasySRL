package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.*;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.evaluation.CCGBankEvaluation;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.CutoffsDictionaryInterface;
import edu.uw.easysrl.syntax.model.Model;
import edu.uw.easysrl.syntax.model.SupertagFactoredModel;
import edu.uw.easysrl.syntax.parser.*;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.util.Util;

import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Calls a parser. Input: List<InputWord>, Output: List<Category>, Set<ResolvedDependency>
 * Created by luheng on 1/5/16.
 */
public abstract class BaseCcgParser {

    private static final String[] dependencyFilter = {
            "be:(S[b]\\NP)/(S[pss]\\NP).1", "be:(S[b]\\NP)/(S[adj]\\NP).1", "been:(S[pt]\\NP)/(S[pss]\\NP).1",
            "have:(S[b]\\NP)/(S[pt]\\NP).1", "been:(S[pt]\\NP)/(S[ng]\\NP).1", "been:(S[pt]\\NP)/(S[adj]\\NP).1",
            "going:(S[ng]\\NP)/(S[to]\\NP).1", "have:(S[b]\\NP)/(S[to]\\NP).1", "be:(S[b]\\NP)/(S[ng]\\NP).1"
    };
    private static HashSet<String> badDependenciesSet;
    private static HashSet<String> frequentDependenciesSet;
    private static final int minDependencyCount = 10;
    static {
        initializeFilter();
    }

    private static void initializeFilter() {
        badDependenciesSet = new HashSet<>();
        badDependenciesSet.addAll(Arrays.asList(dependencyFilter));
        CountDictionary dependencyDict = new CountDictionary();
        List<List<InputReader.InputWord>> sentences = new ArrayList<>();
        List<Parse> goldParses = new ArrayList<>();
        DataLoader.readTrainingPool(sentences, goldParses);
        goldParses.forEach(parse -> parse.dependencies.forEach(dep ->
                dependencyDict.addString(dep.getCategory() + "." + dep.getArgNumber())));
        frequentDependenciesSet = IntStream.range(0, dependencyDict.size())
                .filter(i -> dependencyDict.getCount(i) >= minDependencyCount)
                .mapToObj(dependencyDict::getString)
                .collect(Collectors.toCollection(HashSet::new));
        System.out.println("Initialized frequent dependency set:\t" + frequentDependenciesSet.size());
    }

    protected static boolean acceptDependency(final List<InputReader.InputWord> sentence,
                                              final ResolvedDependency dependency) {
        int predIdx = dependency.getHead(), argIdx = dependency.getArgument();
        if (predIdx == argIdx) {
            return false;
        }
        String depStr1 = dependency.getCategory() + "." + dependency.getArgNumber();
        return frequentDependenciesSet.contains(depStr1) &&
                !badDependenciesSet.contains(sentence.get(predIdx).word + ":" + depStr1);
    }

    protected Parse getParse(final List<InputReader.InputWord> sentence, final SyntaxTreeNode ccgParse,
                             DependencyGenerator dependencyGenerator) {
        // TODO: get categories from syntax treenode
        List<Category> categories = ccgParse.getLeaves().stream().map(SyntaxTreeNode::getCategory)
                .collect(Collectors.toList());
        Set<UnlabelledDependency> unlabelledDeps = new HashSet<>();
        dependencyGenerator.generateDependencies(ccgParse, unlabelledDeps);
        Set<ResolvedDependency> dependencies = CCGBankEvaluation.convertDeps(sentence, unlabelledDeps).stream()
                .filter(dep -> acceptDependency(sentence, dep))
                .collect(Collectors.toSet());
        return new Parse(categories, dependencies);
    }

    protected Parse getParse(final List<InputReader.InputWord> sentence, final SRLParser.CCGandSRLparse parse) {
        // TODO: get categories from syntax treenode
        System.err.println(parse.getDependencyParse().size());
        List<Category> categories =
                parse.getCcgParse().getLeaves().stream().map(SyntaxTreeNode::getCategory)
                .collect(Collectors.toList());
        Set<ResolvedDependency> dependencies = parse.getDependencyParse().stream()
                .filter(dep -> acceptDependency(sentence, dep))
                .collect(Collectors.toSet());
        return new Parse(categories, dependencies);
    }

    public abstract Parse parse(List<InputReader.InputWord> sentence);

    public abstract List<Parse> parseNBest(List<InputReader.InputWord> sentence);

    public static class EasyCCGParser extends BaseCcgParser {
        private DependencyGenerator dependencyGenerator;
        private POSTagger posTagger;
        private Parser parser;
        private final double supertaggerBeam = 0.000001;
        private final int maxChartSize = 20000;
        private final int maxSentenceLength = 70;

        public EasyCCGParser(String modelFolderPath, int nBest)  {
            final File modelFolder = Util.getFile(modelFolderPath);
            if (!modelFolder.exists()) {
                throw new InputMismatchException("Couldn't load model from from: " + modelFolder);
            }
            System.err.println("====Starting loading model====");
            try {
                Coindexation.parseMarkedUpFile(new File(modelFolder, "markedup"));
                final File cutoffsFile = new File(modelFolder, "cutoffs");
                final CutoffsDictionaryInterface cutoffs = cutoffsFile.exists() ? Util.deserialize(cutoffsFile) : null;
                Model.ModelFactory modelFactory = new SupertagFactoredModel.SupertagFactoredModelFactory(
                        Tagger.make(modelFolder, supertaggerBeam, 50, cutoffs), nBest > 1);
                List<Category> rootCategories = new ArrayList<>();
                String[] rootCats = { "S[dcl]", "S[wq]", "S[q]", "S[b]\\NP", "NP" };
                for (String cat : rootCats) {
                    rootCategories.add(Category.valueOf(cat));
                }
                parser = new ParserAStar(modelFactory, maxSentenceLength, nBest, rootCategories, modelFolder,
                        maxChartSize);
                posTagger = POSTagger.getStanfordTagger(new File(modelFolder, "posTagger"));
                dependencyGenerator = new DependencyGenerator(parser.getUnaryRules());
            } catch (Exception e) {
                System.err.println("Parser initialization failed.");
            }
        }

        @Override
        public Parse parse(List<InputReader.InputWord> sentence) {
            List<Util.Scored<SyntaxTreeNode>> parses = parser.doParsing(
                    posTagger.tag(new InputReader.InputToParser(sentence, null, null, false)));
            if (parses == null || parses.size() == 0) {
                return null;
            }
            return getParse(sentence, parses.get(0).getObject(), dependencyGenerator);
        }

        @Override
        public List<Parse> parseNBest(List<InputReader.InputWord> sentence) {
            List<Util.Scored<SyntaxTreeNode>> parses = parser.doParsing(
                    posTagger.tag(new InputReader.InputToParser(sentence, null, null, false)));
            if (parses == null || parses.size() == 0) {
                return null;
            }
            return parses.stream().map(p -> getParse(sentence, p.getObject(), dependencyGenerator))
                    .collect(Collectors.toList());
        }
    }

    public static class PipelineCCGParser extends BaseCcgParser {
        private SRLParser parser;
        private final double supertaggerBeam = 0.000001;
        private final int maxChartSize = 20000;
        private final int maxSentenceLength = 70;

        public PipelineCCGParser(String modelFolderPath, int nBest)  {
            final File modelFolder = Util.getFile(modelFolderPath);
            if (!modelFolder.exists()) {
                throw new InputMismatchException("Couldn't load model from from: " + modelFolder);
            }
            System.err.println("====Starting loading model====");
            try {
                final POSTagger posTagger = POSTagger.getStanfordTagger(new File(modelFolder, "posTagger"));
                parser = new SRLParser.PipelineSRLParser(
                        EasySRL.makeParser(modelFolder.getAbsolutePath(), supertaggerBeam,
                                EasySRL.ParsingAlgorithm.ASTAR,
                                maxChartSize,
                                false /* joint */,
                                Optional.empty(),
                                nBest, maxSentenceLength), CCGBankEvaluation.dummyLabelClassifier, posTagger);
            } catch (Exception e) {
                System.err.println("Parser initialization failed.");
            }
        }

        @Override
        public Parse parse(List<InputReader.InputWord> sentence) {
            List<SRLParser.CCGandSRLparse> parses = parser.parseTokens(sentence);
            if (parses == null || parses.size() == 0) {
                return null;
            }
            return getParse(sentence, parses.get(0));
        }

        @Override
        public List<Parse> parseNBest(List<InputReader.InputWord> sentence) {
            List<SRLParser.CCGandSRLparse> parses = parser.parseTokens(sentence);
            if (parses == null || parses.size() == 0) {
                return null;
            }
            return parses.stream().map(p -> getParse(sentence, p)).collect(Collectors.toList());
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
                            sentStr += (i > 1 ?  " " : "") + info[i].split("\\|")[0];
                        }
                        if (sentenceIdx == parsed.size() - 1) {
                            sentences.put(sentStr, sentenceIdx);
                            parsed.set(sentenceIdx, propagatePrepositions(parsed.get(sentenceIdx)));
                            sentenceIdx++;
                        }
                        continue;
                    }
                    if (sentenceIdx == parsed.size()) {
                        parsed.add(new HashSet<>());
                    }
                    // Parse the f--king line:
                    // saw_2(S[dcl]\NP)/NP1 I_1 0
                    // in_29((S\NP)\(S\NP))/NP3 hours_33 0
                    // Getting: in ((S\NP)\(S\NP))/NP hours
                    // String[] stringsInfo = line.trim().split("[_\\d]+");
                    // Getting: _ 29 3 33 0
                    // String[] indicesInfo = line.trim().split("[^\\d]+");
                    // saw_2(S[dcl]\NP)/NP1
                    String chunk1 = line.trim().split("\\s+")[0];
                    String[] stringsInfo = chunk1.split("[_\\d]+");
                    String[] indicesInfo = chunk1.split("[^\\d]+");
                    Category category = Category.valueOf(stringsInfo[stringsInfo.length - 1].trim());
                    int predIdx = Integer.parseInt(indicesInfo[indicesInfo.length - 2]) - 1;
                    int argNum = Integer.parseInt(indicesInfo[indicesInfo.length - 1]);
                    // I_1
                    String chunk2 = line.trim().split("\\s+")[1];
                    int argIdx = Integer.parseInt(chunk2.substring(chunk2.lastIndexOf('_') + 1)) - 1;
                  //  System.out.println(predIdx + "\t" + category + "\t" + argNum + "\t" + argIdx);
                    parsed.get(sentenceIdx).add(new ResolvedDependency(predIdx, category, argNum, argIdx, SRLFrame.NONE,
                            Preposition.NONE));
                }
            } catch (IOException e) {
            }
            System.out.println(String.format("Read %d pre-parsed sentences", sentences.size()));
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
                                d2.getArgument(), // this is changed ...
                                dep.getSemanticRole(), dep.getPreposition()));
                        break;
                    }
                }
            }
            return result;
        }

        @Override
        public Parse parse(List<InputReader.InputWord> sentence) {
            String sentStr = StringUtils.join(sentence.stream().map(w->w.word).collect(Collectors.toList()));
            if (!sentences.containsKey(sentStr)) {
                System.err.println("unable to parse:\t" + sentStr);
                return null;
            }

            List<Category> categories = new ArrayList<>();
            Set<ResolvedDependency> dependencies = new HashSet<>();
            for (int i = 0; i < sentence.size(); i++) {
                categories.add(null);
            }
            for (ResolvedDependency dep : parsed.get(sentences.get(sentStr))) {
                /*if (dep.getHead() >= sentence.size() || dep.getArgument() >= sentence.size()) {
                    continue;
                }*/
                dependencies.add(dep);
                if (dep.getHead() < categories.size()) {
                    categories.set(dep.getHead(), dep.getCategory());
                }
                int argId = dep.getArgument();
                Category argCat = dep.getCategory().getArgument(dep.getArgNumber());
                // Simple heuristic that writes N over NP.
                if (categories.get(argId) == null ||
                        categories.get(argId).toString().length() > argCat.toString().length()) {
                    categories.set(argId, argCat);
                }
            }
            for (int i = 0; i < sentence.size(); i++) {
                // This of course doesn't work. i.e. auxiliary verb and so.
                if (categories.get(i) == null) {
                    categories.set(i, Category.valueOf("N"));
                }
            }
            return new Parse(categories, dependencies);
        }

        @Override
        public List<Parse> parseNBest(List<InputReader.InputWord> sentence) {
            throw new RuntimeException("unsupported");
        }
    }
}
