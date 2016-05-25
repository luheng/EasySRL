package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.dependencies.*;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.corpora.CountDictionary;
import edu.uw.easysrl.syntax.evaluation.CCGBankEvaluation;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.*;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.util.GuavaCollectors;
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

    private static HashSet<String> frequentDependenciesSet;
    private static final int minDependencyCount = 10;
    static {
        initializeFilter();
    }

    public final static String modelFolder = "./model_tritrain_finetune/";

    private static void initializeFilter() {
        final CountDictionary dependencyDict = new CountDictionary();
        final ParseData parseData = ParseDataLoader.loadFromTrainingPool().get();
        parseData.getGoldParses()
                .forEach(parse -> parse.dependencies
                        .forEach(dep -> dependencyDict.addString(dep.getCategory() + "." + dep.getArgNumber())));
        frequentDependenciesSet = IntStream.range(0, dependencyDict.size())
                .filter(i -> dependencyDict.getCount(i) >= minDependencyCount)
                .mapToObj(dependencyDict::getString)
                .collect(Collectors.toCollection(HashSet::new));
        System.out.println("Initialized frequent dependency set:\t" + frequentDependenciesSet.size());
    }

    protected Parse getParse(final List<InputReader.InputWord> sentence, final Scored<SyntaxTreeNode> scoredParse,
                             DependencyGenerator dependencyGenerator) {
        SyntaxTreeNode ccgParse = scoredParse.getObject();
        List<Category> categories = ccgParse.getLeaves().stream().map(SyntaxTreeNode::getCategory)
                .collect(Collectors.toList());
        Set<UnlabelledDependency> unlabelledDeps = new HashSet<>();
        dependencyGenerator.generateDependencies(ccgParse, unlabelledDeps);
        Set<ResolvedDependency> dependencies = CCGBankEvaluation.convertDeps(sentence, unlabelledDeps)
                        .stream()
                        .filter(x -> x.getHead() != x.getArgument())
                        .filter(x -> frequentDependenciesSet.contains(x.getCategory() + "." +  x.getArgNumber()))
                        .collect(Collectors.toSet());
        return new Parse(scoredParse.getObject(), categories, dependencies, scoredParse.getScore());
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
        private Tagger batchTagger = null;
        private ImmutableList<List<List<Tagger.ScoredCategory>>> taggedSentences = null;

        public AStarParser(String modelFolderPath, int nBest)  {
            this(modelFolderPath, nBest, 1e-6, 1e-6, 1000000, 70);
        }

        public Parser getParser() {
            return parser;
        }

        public AStarParser(String modelFolderPath, int nBest, double supertaggerBeam, double nbestBeam,
                           int maxChartSize, int maxSentenceLength)  {
            final File modelFolder = Util.getFile(modelFolderPath);
            if (!modelFolder.exists()) {
                throw new InputMismatchException("Couldn't load model from from: " + modelFolder);
            }
            System.err.println("====Starting loading model====");
            parser = new ParserAStar.Builder(modelFolder)
                    .supertaggerBeam(supertaggerBeam)
                    .maxChartSize(maxChartSize)
                    .maximumSentenceLength(maxSentenceLength)
                    .nBest(nBest)
                    .nbestBeam(nbestBeam)
                    .build();
            try {
                dependencyGenerator = new DependencyGenerator(modelFolder);
            } catch (Exception e) {
                System.err.println("Parser initialization failed.");
                e.printStackTrace();
            }
            try {
                batchTagger = Tagger.make(modelFolder, supertaggerBeam, 50, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void cacheSupertags(ParseData corpus) {
            if (batchTagger != null) {
                System.err.println("Batch tagging " + corpus.getSentences().size() + " sentences ...");
                taggedSentences = batchTagger.tagBatch(corpus.getSentenceInputWords().parallelStream()
                        .map(s -> s.stream().collect(Collectors.toList())))
                        .collect(GuavaCollectors.toImmutableList());
            }
        }

        public void cacheSupertags(ImmutableList<ImmutableList<InputReader.InputWord>> inputSentences) {
            if (batchTagger != null) {
                System.err.println("Batch tagging " + inputSentences.size() + " sentences ...");
                taggedSentences = batchTagger.tagBatch(inputSentences.parallelStream()
                        .map(s -> s.stream().collect(Collectors.toList())))
                        .collect(GuavaCollectors.toImmutableList());
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

        @Override
        public Parse parse(int sentenceId, List<InputReader.InputWord> sentence) {
            final InputReader.InputToParser input = taggedSentences == null ?
                    new InputReader.InputToParser(sentence, null, null, false) :
                    new InputReader.InputToParser(sentence, null, taggedSentences.get(sentenceId), true);
            List<Scored<SyntaxTreeNode>> parses = parser.doParsing(input);
            if (parses == null || parses.size() == 0) {
                return null;
            }
            return getParse(sentence, parses.get(0), dependencyGenerator);
        }

        @Override
        public List<Parse> parseNBest(int sentenceId, List<InputReader.InputWord> sentence) {
            final InputReader.InputToParser input = taggedSentences == null ?
                    new InputReader.InputToParser(sentence, null, null, false) :
                    new InputReader.InputToParser(sentence, null, taggedSentences.get(sentenceId), true);
            List<Scored<SyntaxTreeNode>> parses = parser.doParsing(input);
            if (parses == null || parses.size() == 0) {
                return null;
            }
            return parses.stream().map(p -> getParse(sentence, p, dependencyGenerator)).collect(Collectors.toList());
        }
    }

    public static class ConstrainedCcgParser extends BaseCcgParser {
        private DependencyGenerator dependencyGenerator;
        private ConstrainedParserAStar parser;
        private Tagger batchTagger = null;
        private ImmutableList<List<List<Tagger.ScoredCategory>>> taggedSentences = null;
        private final double supertaggerBeam = 0.000001;
        private final int maxChartSize = 1000000;
        private final int maxSentenceLength = 70;

        public ConstrainedCcgParser(String modelFolderPath, int nBest) {
            final File modelFolder = Util.getFile(modelFolderPath);
            if (!modelFolder.exists()) {
                throw new InputMismatchException("Couldn't load model from from: " + modelFolder);
            }
            System.err.println("====Starting loading model====");
            parser = new ConstrainedParserAStar.Builder(modelFolder)
                    .supertaggerBeam(supertaggerBeam)
                    .nBest(nBest)
                    .maxChartSize(maxChartSize)
                    .maximumSentenceLength(maxSentenceLength)
                    .build();
            try {
                dependencyGenerator = new DependencyGenerator(modelFolder);
            } catch (Exception e) {
                System.err.println("Parser initialization failed.");
                e.printStackTrace();
            }
            try {
                batchTagger = Tagger.make(modelFolder, supertaggerBeam, 50, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void cacheSupertags(ParseData corpus) {
            if (batchTagger != null) {
                System.err.println("Batch tagging " + corpus.getSentences().size() + " sentences ...");
                taggedSentences = batchTagger.tagBatch(corpus.getSentenceInputWords().parallelStream()
                        .map(s -> s.stream().collect(Collectors.toList())))
                        .collect(GuavaCollectors.toImmutableList());
            }
        }

        // TODO: change this.
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
            return parseWithConstraint(sentenceId, sentence, new HashSet<>());
        }

        @Override
        public List<Parse> parseNBest(int sentenceId, List<InputReader.InputWord> sentence) {
            return parseNBestWithConstraint(sentenceId, sentence, new HashSet<>());
        }

        public Parse parseWithConstraint(int sentenceId, List<InputReader.InputWord> sentence,
                                         Set<Constraint> constraintSet) {
            if (sentence.size() > maxSentenceLength) {
                System.err.println("Skipping sentence of length " + sentence.size());
                return null;
            }
            final InputReader.InputToParser input = taggedSentences == null ?
                    new InputReader.InputToParser(sentence, null, null, false) :
                    new InputReader.InputToParser(sentence, null, taggedSentences.get(sentenceId), true);
            List<Scored<SyntaxTreeNode>> parses = parser.parseWithConstraints(input, constraintSet);
            return (parses == null || parses.size() == 0) ? null :
                    getParse(sentence, parses.get(0), dependencyGenerator);
        }

        public List<Parse> parseNBestWithConstraint(int sentenceId, List<InputReader.InputWord> sentence,
                                                    Set<Constraint> constraintSet) {
            if (sentence.size() > maxSentenceLength) {
                System.err.println("Skipping sentence of length " + sentence.size());
                return null;
            }
            final InputReader.InputToParser input = taggedSentences == null ?
                    new InputReader.InputToParser(sentence, null, null, false) :
                    new InputReader.InputToParser(sentence, null, taggedSentences.get(sentenceId), true);
            List<Scored<SyntaxTreeNode>> parses = parser.parseWithConstraints(input, constraintSet);
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
