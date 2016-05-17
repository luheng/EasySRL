package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.*;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.corpora.CountDictionary;
import edu.uw.easysrl.syntax.evaluation.CCGBankEvaluation;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.ConstrainedParsingModel.*;
import edu.uw.easysrl.syntax.model.ConstrainedSupertagFactoredModel.ConstrainedSupertagModelFactory;
import edu.uw.easysrl.syntax.model.Model;
import edu.uw.easysrl.syntax.model.SupertagFactoredModel;
import edu.uw.easysrl.syntax.parser.*;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.util.Util;
import edu.uw.easysrl.util.Util.Scored;

import edu.stanford.nlp.util.StringUtils;

import java.io.*;
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

    // For convenience.
    public final static String modelFolder = "./model_tritrain_big/";
    public final static List<Category> rootCategories =  Arrays.asList(
            Category.valueOf("S[dcl]"), Category.valueOf("S[wq]"), Category.valueOf("S[q]"),
            Category.valueOf("S[b]\\NP"), Category.valueOf("NP"));

    private static void initializeFilter() {
        badDependenciesSet = new HashSet<>();
        badDependenciesSet.addAll(Arrays.asList(dependencyFilter));
        final CountDictionary dependencyDict = new CountDictionary();
        final ParseData parseData = ParseData.loadFromTrainingPool().get();
        parseData.getGoldParses()
                .forEach(parse -> parse.dependencies
                        .forEach(dep -> dependencyDict.addString(dep.getCategory() + "." + dep.getArgNumber())));
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

    protected Parse getParse(final List<InputReader.InputWord> sentence, final Scored<SyntaxTreeNode> scoredParse,
                             DependencyGenerator dependencyGenerator) {
        SyntaxTreeNode ccgParse = scoredParse.getObject();
        List<Category> categories = ccgParse.getLeaves().stream().map(SyntaxTreeNode::getCategory)
                .collect(Collectors.toList());
        Set<UnlabelledDependency> unlabelledDeps = new HashSet<>();
        dependencyGenerator.generateDependencies(ccgParse, unlabelledDeps);
        Set<ResolvedDependency> dependencies = new HashSet<>();
        // Convert and filter dependencies for evaluation.
        unlabelledDeps.forEach(dep -> {
            int predIdx = dep.getHead();
            Category category = dep.getCategory();
            int argNum = dep.getArgNumber();
            String depStr = category + "." + dep.getArgNumber();
            if (frequentDependenciesSet.contains(depStr) &&
                    !badDependenciesSet.contains(sentence.get(predIdx).word + ":" + depStr)) {
                dep.getArguments().stream()
                        .filter(argIdx -> predIdx != argIdx)
                        .forEach(argIdx2 -> dependencies.add(new ResolvedDependency(
                                predIdx, category, argNum, argIdx2, SRLFrame.NONE, Preposition.NONE)));
            }
        });
        return new Parse(scoredParse.getObject(), categories, dependencies, scoredParse.getScore());
    }

    protected Parse getParse(final List<InputReader.InputWord> sentence, final SRLParser.CCGandSRLparse parse) {
        System.err.println(parse.getDependencyParse().size());
        List<Category> categories =
                parse.getCcgParse().getLeaves().stream().map(SyntaxTreeNode::getCategory)
                        .collect(Collectors.toList());
        Set<ResolvedDependency> dependencies = parse.getDependencyParse().stream()
                .filter(dep -> acceptDependency(sentence, dep))
                .collect(Collectors.toSet());
        return new Parse(parse.getCcgParse(), categories, dependencies);
    }

    public abstract Parse parse(List<InputReader.InputWord> sentence);

    public Parse parse(int sentenceId, List<InputReader.InputWord> sentence) {
        return parse(sentence);
    }

    public abstract List<Parse> parseNBest(List<InputReader.InputWord> sentence);

    public List<Parse> parseNBest(int sentenceId, List<InputReader.InputWord> sentence) {
        return parseNBest(sentence);
    }

    public static class AStarParser extends BaseCcgParser {
        private DependencyGenerator dependencyGenerator;
        private Parser parser;
        private final double supertaggerBeam = 0.000001;
        //private final double supertaggerBeam = 0.0001;
        private final int maxChartSize = 1000000;
        private final int maxSentenceLength = 70;

        public AStarParser(String modelFolderPath, List<Category> rootCategories, int nBest)  {
            final File modelFolder = Util.getFile(modelFolderPath);
            if (!modelFolder.exists()) {
                throw new InputMismatchException("Couldn't load model from from: " + modelFolder);
            }
            System.err.println("====Starting loading model====");
            try {
                Coindexation.parseMarkedUpFile(new File(modelFolder, "markedup"));
                Model.ModelFactory modelFactory = new SupertagFactoredModel.SupertagFactoredModelFactory(
                        Tagger.make(modelFolder, supertaggerBeam, 50, null /* cutoffs */), nBest > 1);
                parser = new ParserAStar(modelFactory, maxSentenceLength, nBest, rootCategories, modelFolder,
                        maxChartSize);
                dependencyGenerator = new DependencyGenerator(parser.getUnaryRules());
            } catch (Exception e) {
                System.err.println("Parser initialization failed.");
                e.printStackTrace();
            }
        }

        @Override
        public Parse parse(List<InputReader.InputWord> sentence) {
            List<Scored<SyntaxTreeNode>> parses = parser.doParsing(
                    new InputReader.InputToParser(sentence, null, null, false));
            if (parses == null || parses.size() == 0) {
                return null;
            }
            return getParse(sentence, parses.get(0), dependencyGenerator);
        }

        @Override
        public List<Parse> parseNBest(List<InputReader.InputWord> sentence) {
            List<Scored<SyntaxTreeNode>> parses = parser.doParsing(
                    new InputReader.InputToParser(sentence, null, null, false));
            if (parses == null || parses.size() == 0) {
                return null;
            }
            return parses.stream().map(p -> getParse(sentence, p, dependencyGenerator)).collect(Collectors.toList());
        }
    }

    public static class ConstrainedCcgParser extends BaseCcgParser {
        private DependencyGenerator dependencyGenerator;
        private ConstrainedParserAStar parser;
        private final double supertaggerBeam = 0.000001;
        private final int maxChartSize = 100000;
        private final int maxSentenceLength = 70;

        public ConstrainedCcgParser(String modelFolderPath, List<Category> rootCategories, int maxTagsPerWord,
                                    int nBest)  {
            final File modelFolder = Util.getFile(modelFolderPath);
            if (!modelFolder.exists()) {
                throw new InputMismatchException("Couldn't load model from from: " + modelFolder);
            }
            System.err.println("====Starting loading model====");
            try {
                Coindexation.parseMarkedUpFile(new File(modelFolder, "markedup"));
                ConstrainedSupertagModelFactory modelFactory = new ConstrainedSupertagModelFactory(
                        Tagger.make(modelFolder, supertaggerBeam, maxTagsPerWord /* default  50 */, null /* cutoffs */),
                        true /* include deps */);
                parser = new ConstrainedParserAStar(modelFactory, maxSentenceLength, nBest, rootCategories, modelFolder,
                        maxChartSize);
                dependencyGenerator = new DependencyGenerator(parser.getUnaryRules());
            } catch (Exception e) {
                System.err.println("Parser initialization failed.");
                e.printStackTrace();
            }
        }

        @Override
        public Parse parse(List<InputReader.InputWord> sentence) {
            return parseWithConstraint(sentence, new HashSet<>());
        }

        @Override
        public List<Parse> parseNBest(List<InputReader.InputWord> sentence) {
            return parseNBestWithConstraint(sentence, new HashSet<>());
        }

        public Parse parseWithConstraint(List<InputReader.InputWord> sentence, Set<Constraint> constraintSet) {
            if (sentence.size() > maxSentenceLength) {
                System.err.println("Skipping sentence of length " + sentence.size());
                return null;
            }
            List<Scored<SyntaxTreeNode>> parses = parser.parseAstarWithConstraints(
                    new InputReader.InputToParser(sentence, null, null, false), constraintSet, dependencyGenerator);
            return (parses == null || parses.size() == 0) ? null :
                    getParse(sentence, parses.get(0), dependencyGenerator);
        }

        public List<Parse> parseNBestWithConstraint(List<InputReader.InputWord> sentence, Set<Constraint> constraintSet) {
            if (sentence.size() > maxSentenceLength) {
                System.err.println("Skipping sentence of length " + sentence.size());
                return null;
            }
            List<Scored<SyntaxTreeNode>> parses = parser.parseAstarWithConstraints(
                    new InputReader.InputToParser(sentence, null, null, false), constraintSet, dependencyGenerator);
            return (parses == null || parses.size() == 0) ? null :
                    parses.stream().map(p -> getParse(sentence, p, dependencyGenerator)).collect(Collectors.toList());
        }
    }

    public static class ConstrainedCcgParser2 extends BaseCcgParser {
        private DependencyGenerator dependencyGenerator;
        private ConstrainedParserAStar parser;
        private final double supertaggerBeam = 0.000001;
        private final int maxChartSize = 1000000;
        private final int maxSentenceLength = 70;

        public ConstrainedCcgParser2(String modelFolderPath, List<Category> rootCategories, int maxTagsPerWord,
                                     int nBest) {
            final File modelFolder = Util.getFile(modelFolderPath);
            if (!modelFolder.exists()) {
                throw new InputMismatchException("Couldn't load model from from: " + modelFolder);
            }
            System.err.println("====Starting loading model====");
            try {
                Coindexation.parseMarkedUpFile(new File(modelFolder, "markedup"));
                ConstrainedParsingModelFactory modelFactory = new ConstrainedParsingModelFactory(
                        Tagger.make(modelFolder, supertaggerBeam, maxTagsPerWord /* default  50 */, null /* cutoffs */));
                parser = new ConstrainedParserAStar(modelFactory, maxSentenceLength, nBest, rootCategories, modelFolder,
                        maxChartSize);
                dependencyGenerator = new DependencyGenerator(parser.getUnaryRules());
            } catch (Exception e) {
                System.err.println("Parser initialization failed.");
                e.printStackTrace();
            }
        }

        @Override
        public Parse parse(List<InputReader.InputWord> sentence) {
            return parseWithConstraint(sentence, new HashSet<>());
        }

        @Override
        public List<Parse> parseNBest(List<InputReader.InputWord> sentence) {
            return parseNBestWithConstraint(sentence, new HashSet<>());
        }

        public Parse parseWithConstraint(List<InputReader.InputWord> sentence, Set<Constraint> constraintSet) {
            if (sentence.size() > maxSentenceLength) {
                System.err.println("Skipping sentence of length " + sentence.size());
                return null;
            }
            List<Scored<SyntaxTreeNode>> parses = parser.parseAstarWithConstraints(
                    new InputReader.InputToParser(sentence, null, null, false), constraintSet);
            return (parses == null || parses.size() == 0) ? null :
                    getParse(sentence, parses.get(0), dependencyGenerator);
        }

        public List<Parse> parseNBestWithConstraint(List<InputReader.InputWord> sentence, Set<Constraint> constraintSet) {
            if (sentence.size() > maxSentenceLength) {
                System.err.println("Skipping sentence of length " + sentence.size());
                return null;
            }
            List<Scored<SyntaxTreeNode>> parses = parser.parseAstarWithConstraints(
                    new InputReader.InputToParser(sentence, null, null, false), constraintSet);
            return (parses == null || parses.size() == 0) ? null :
                    parses.stream().map(p -> getParse(sentence, p, dependencyGenerator)).collect(Collectors.toList());
        }
    }

    /**
     * Reads pre-parsed n-best list from file.
     *
     * NOTE: this isn't needed if all you want is an n-best list from a file... see NBestList.loadNBestListsFromFile
     * I personally like that way better so I deprecated this.
     * but feel free to un-deprecate it if you think it's still good. --julian
     */
    public static class MockParser extends BaseCcgParser {
        private Map<Integer, List<Parse>> allParses;

        public MockParser(String parsesFilePath, int nBest)  {
            allParses = new HashMap<>();
            Map<Integer, List<Parse>> readParses;
            try {
                ObjectInputStream inputStream = new ObjectInputStream(new BufferedInputStream(
                        new FileInputStream(parsesFilePath)));
                readParses = (Map<Integer, List<Parse>>) inputStream.readObject();
            }  catch(Exception e){
                e.printStackTrace();
                return;
            }
            readParses.forEach((sentIdx, parses) ->
                    allParses.put(sentIdx, (parses.size() <= nBest) ? parses : parses.subList(0, nBest)));
        }

        @Override
        public Parse parse(List<InputReader.InputWord> sentence) {
            return null;
        }

        @Override
        public List<Parse> parseNBest(List<InputReader.InputWord> sentence) {
            return null;
        }

        @Override
        public Parse parse(int sentenceId, List<InputReader.InputWord> sentence) {
            return allParses.containsKey(sentenceId) ? allParses.get(sentenceId).get(0) : null;
        }

        @Override
        public List<Parse> parseNBest(int sentenceId, List<InputReader.InputWord> sentence) {
            return allParses.containsKey(sentenceId) ? allParses.get(sentenceId) : null;
        }
    }

    /**
     * Not used.
     */
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
            return new Parse(null, categories, dependencies);
        }

        @Override
        public List<Parse> parseNBest(List<InputReader.InputWord> sentence) {
            throw new RuntimeException("unsupported");
        }
    }
}
