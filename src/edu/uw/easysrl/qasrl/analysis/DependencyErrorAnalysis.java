package edu.uw.easysrl.qasrl.analysis;

import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.NBestList;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.analysis.DependencyProfiler;
import edu.uw.easysrl.qasrl.analysis.ProfiledDependency;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingExperiment;
import edu.uw.easysrl.qasrl.experiments.OracleExperiment;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import static edu.uw.easysrl.util.GuavaCollectors.*;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static java.util.stream.Collectors.*;
import static java.lang.Math.toIntExact;

public class DependencyErrorAnalysis {

    @FunctionalInterface
    public static interface DependencyMatcher {
        public boolean match(ResolvedDependency dep1, ResolvedDependency dep2);
    }
    public static final DependencyMatcher unlabeledDirectedDepMatcher = (dep1, dep2) ->
        dep1.getHead() == dep2.getHead() && dep1.getArgument() == dep2.getArgument();
    public static final DependencyMatcher unlabeledUndirectedDepMatcher = (dep1, dep2) ->
        (dep1.getHead() == dep2.getHead() && dep1.getArgument() == dep2.getArgument()) ||
        (dep1.getHead() == dep2.getArgument() && dep1.getArgument() == dep2.getHead());
    public static final DependencyMatcher labeledDirectedDepMatcher = (dep1, dep2) ->
        dep1.getCategory().equals(dep2.getCategory()) && dep1.getArgNumber() == dep2.getArgNumber() &&
        dep1.getHead() == dep2.getHead() && dep1.getArgument() == dep2.getArgument();

    @FunctionalInterface
    public static interface DependencyClassifier {
        public boolean isMember(ResolvedDependency dep);
    }


    public static class SentenceMistakes<M extends Mistake> {
        private final ImmutableList<M> mistakes;

        public ImmutableList<M> getMistakes() { return mistakes; }

        public SentenceMistakes(ImmutableList<M> mistakes) {
            this.mistakes = mistakes;
        }

        public static <M extends Mistake> SentenceMistakesExtractor<M, SentenceMistakes<M>>
            extractorFromMistakeExtractor(MistakeExtractor<M> mistakeExtractor) {
            return (parse, goldParse) -> new SentenceMistakes(mistakeExtractor.extract(parse, goldParse));
        }

        public static SentenceMistakesExtractor<DependencyMistake, SentenceMistakes<DependencyMistake>> nounOrVerbAttachmentMistakeExtractor(DependencyMatcher depMatcher) {
            return (parse, goldParse) -> {
                ImmutableSet<ResolvedDependency> predictedNounOrVerbAttachments = parse.dependencies.stream()
                    .filter(dep -> isNounOrVerbAttachmentMistake(dep, goldParse))
                    .collect(toImmutableSet());
                ImmutableSet<ResolvedDependency> goldNounOrVerbAttachments = goldParse.dependencies.stream()
                    .filter(dep -> isNounOrVerbAttachmentMistake(dep, parse))
                    .collect(toImmutableSet());
                final Stream<DependencyMistake> falsePositives = falsePositives(predictedNounOrVerbAttachments, ImmutableSet.copyOf(goldParse.dependencies), depMatcher).stream();
                final Stream<DependencyMistake> falseNegatives = falseNegatives(ImmutableSet.copyOf(parse.dependencies), goldNounOrVerbAttachments, depMatcher).stream();
                final ImmutableList<DependencyMistake> mistakes = Stream.concat(falsePositives, falseNegatives)
                    .collect(toImmutableList());
                return new SentenceMistakes(mistakes);
            };
        }

        private static boolean isNounOrVerbAttachmentMistake(ResolvedDependency dep, Parse otherParse) {
            return (DependencyMistake.isVerbAdjunctDep(dep) &&
                    otherParse.dependencies.stream()
                    .anyMatch(otherDep -> otherDep.getHead() == dep.getHead() &&
                              DependencyMistake.isNounAdjunctDep(otherDep))) ||
                (DependencyMistake.isPPArgDep(dep) &&
                 otherParse.dependencies.stream()
                 .anyMatch(otherDep -> otherDep.getHead() == dep.getArgument() &&
                           DependencyMistake.isNounAdjunctDep(otherDep))) ||
                (DependencyMistake.isNounAdjunctDep(dep) &&
                 otherParse.dependencies.stream()
                 .anyMatch(otherDep -> (otherDep.getHead() == dep.getHead() &&
                                        DependencyMistake.isVerbAdjunctDep(otherDep)) ||
                           (otherDep.getArgument() == dep.getHead() &&
                            DependencyMistake.isPPArgDep(otherDep))));
        }
    }

    @FunctionalInterface
    public static interface SentenceMistakesExtractor<M extends Mistake, SM extends SentenceMistakes<M>> {
        public SM extract(Parse parse, Parse goldParse);
    }

    public static final class PrecisionRecallMistakes<M extends Mistake> extends SentenceMistakes<M> {
        private final Results stats;

        public Results getStats() { return stats; }
        // public double getPrecision() { return stats.getPrecision(); }
        // public double getRecall() { return stats.getRecall(); }
        // public double getF1() { return stats.getF1(); }

        public PrecisionRecallMistakes(ImmutableList<M> mistakes, Results stats) {
            super(mistakes);
            this.stats = stats;
        }

        public static SentenceMistakesExtractor<DependencyMistake, PrecisionRecallMistakes<DependencyMistake>>
            extractorForDependencyType(DependencyMatcher depMatcher, DependencyClassifier depClass) {
            return (parse, goldParse) -> {
                final ImmutableSet<ResolvedDependency> goldDepsOfType = goldParse.dependencies.stream()
                    .filter(depClass::isMember)
                    .collect(toImmutableSet());
                final ImmutableSet<ResolvedDependency> predictedDepsOfType = parse.dependencies.stream()
                    .filter(depClass::isMember)
                    .collect(toImmutableSet());
                final int numCorrect = toIntExact(goldDepsOfType.stream()
                                                       .filter(gDep -> parse.dependencies.stream().anyMatch(pDep -> depMatcher.match(gDep, pDep)))
                                                       .collect(counting()) +
                                                       predictedDepsOfType.stream()
                                                       .filter(pDep -> goldParse.dependencies.stream().anyMatch(gDep -> depMatcher.match(gDep, pDep)))
                                                       .collect(counting())) / 2;
                final Stream<DependencyMistake> falsePositives = falsePositives(predictedDepsOfType, ImmutableSet.copyOf(goldParse.dependencies), depMatcher).stream();
                final Stream<DependencyMistake> falseNegatives = falseNegatives(ImmutableSet.copyOf(parse.dependencies), goldDepsOfType, depMatcher).stream();
                final ImmutableList<DependencyMistake> mistakes = Stream.concat(falsePositives, falseNegatives)
                    .collect(toImmutableList());
                final Results stats = new Results(predictedDepsOfType.size(), numCorrect, goldDepsOfType.size());
                return new PrecisionRecallMistakes(mistakes, stats);
            };
        }

        public static SentenceMistakesExtractor<DependencyMistake, PrecisionRecallMistakes<DependencyMistake>>
            extractorForMistakeType(DependencyMatcher depMatcher, MistakeClassifier mistakeClass) {
            return (parse, goldParse) -> {
                final ImmutableSet<ResolvedDependency> goldDepsOfType = goldParse.dependencies.stream()
                    .filter(gDep -> mistakeClass.isMember(gDep, parse))
                    .collect(toImmutableSet());
                final ImmutableSet<ResolvedDependency> predictedDepsOfType = parse.dependencies.stream()
                    .filter(pDep -> mistakeClass.isMember(pDep, goldParse))
                    .collect(toImmutableSet());
                final int numCorrect = toIntExact(goldDepsOfType.stream()
                                                  .filter(gDep -> parse.dependencies.stream().anyMatch(pDep -> depMatcher.match(gDep, pDep)))
                                                  .collect(counting()) +
                                                  predictedDepsOfType.stream()
                                                  .filter(pDep -> goldParse.dependencies.stream().anyMatch(gDep -> depMatcher.match(gDep, pDep)))
                                                  .collect(counting())) / 2;
                final Stream<DependencyMistake> falsePositives = falsePositives(predictedDepsOfType, ImmutableSet.copyOf(goldParse.dependencies), depMatcher).stream();
                final Stream<DependencyMistake> falseNegatives = falseNegatives(ImmutableSet.copyOf(parse.dependencies), goldDepsOfType, depMatcher).stream();
                final ImmutableList<DependencyMistake> mistakes = Stream.concat(falsePositives, falseNegatives)
                    .collect(toImmutableList());
                final Results stats = new Results(predictedDepsOfType.size(), numCorrect, goldDepsOfType.size());
                return new PrecisionRecallMistakes(mistakes, stats);
            };
        }

    }

    @FunctionalInterface
    public static interface MistakeClassifier {
        public boolean isMember(ResolvedDependency dep, Parse otherParse);
    }

    public static abstract class Mistake {
        public Mistake() {}
    }

    @FunctionalInterface
    public static interface MistakeExtractor<M extends Mistake> {
        public ImmutableList<M> extract(Parse parse, Parse goldParse);
    }

    public static final class SupertagMistake extends Mistake {
        private final Category goldSupertag;
        private final Category predictedSupertag;
        private final int index;
        private final String word;

        public Category getGoldSupertag() { return goldSupertag; }
        public Category getPredictedSupertag() { return predictedSupertag; }
        public int getIndexInSentence() { return index; }
        public String getWord() { return word; }

        public SupertagMistake(Category goldSupertag, Category predictedSupertag, int index, String word) {
            this.goldSupertag = goldSupertag;
            this.predictedSupertag = predictedSupertag;
            this.index = index;
            this.word = word;
        }

        public static final MistakeExtractor<SupertagMistake> exactExtractor = (parse, goldParse) -> IntStream
            .range(0, parse.categories.size())
            .filter(i -> !parse.categories.get(i).equals(goldParse.categories.get(i)))
            .mapToObj(i -> new SupertagMistake(goldParse.categories.get(i), parse.categories.get(i),
                                               i, parse.syntaxTree.getLeaves().get(i).getWord()))
            .collect(toImmutableList());

        // don't already have easy way of dropping features, so I won't bother for now. TODO maybe try this
        // public static final MistakeExtractor<SupertagMistake> featureStrippingExtractor = (parse, goldParse) -> IntStream
        //     .range(0, parse.categories.size())
        //     .filter(i -> !parse.categories.get(i).matches(goldParse.categories.get(i)))
        //     .mapToObj(i -> new SupertagMistake(goldParse.categories.get(i), parse.categories.get(i),
        //                                        i, parse.syntaxTree.getLeaves().get(i).getWord()))
        //     .collect(toImmutableList());
    }

    public static final class DependencyMistake extends Mistake {
        private final ResolvedDependency dependency;
        private boolean isFalsePositive;

        public ResolvedDependency getDependency() { return dependency; }
        public boolean isFalsePositive() { return isFalsePositive; }
        public boolean isFalseNegative() { return !isFalsePositive; }

        public DependencyMistake(ResolvedDependency dependency, boolean isFalsePositive) {
            this.dependency = dependency;
            this.isFalsePositive = isFalsePositive;
        }

        private static boolean isPPAttachment(ResolvedDependency dep) {
            return isPPArgDep(dep) || isVerbAdjunctDep(dep) || isNounAdjunctDep(dep);
        }

        private static boolean isVerbNPArgDep(ResolvedDependency dep) {
            return (dep.getCategory().isFunctionInto(Category.valueOf("S\\NP")) &&
                    dep.getCategory().getArgument(dep.getArgNumber()).matches(Category.NP));
        }

        private static boolean isPPArgDep(ResolvedDependency dep) {
            return (dep.getCategory().isFunctionInto(Category.valueOf("S\\NP")) &&
                    dep.getCategory().getArgument(dep.getArgNumber()).matches(Category.PP));
        }

        private static boolean isVerbAdjunctDep(ResolvedDependency dep) {
            return (dep.getCategory().isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)")) &&
                    dep.getArgNumber() == 2);
        }

        private static boolean isNounAdjunctDep(ResolvedDependency dep) {
            return (dep.getCategory().isFunctionInto(Category.valueOf("NP\\NP")) &&
                    dep.getArgNumber() == 1);
        }

        // tells us whether we COULD make such a mistake; whether we actually do is decided by depMatcher
        // places where we got the label (PP arg, noun mod, verb mod) RIGHT, but we may have gotten the attachment wrong
        private static boolean isPPAttachmentChoiceMistake(ResolvedDependency dep, Parse otherParse) {
            Function<DependencyClassifier, Boolean> isMistake = (depClass -> {
                    boolean otherHasSuchADep = otherParse.dependencies.stream()
                    .anyMatch(otherDep -> otherDep.getHead() == dep.getHead() &&
                              depClass.isMember(otherDep));
                    return depClass.isMember(dep) && otherHasSuchADep;
                });
            return isMistake.apply(DependencyMistake::isPPArgDep) ||
                isMistake.apply(DependencyMistake::isVerbAdjunctDep) ||
                isMistake.apply(DependencyMistake::isNounAdjunctDep);
        }

        // places where we got the attachment of a verb mod / PP arg RIGHT, but may have swapped argument/adjunct
        private static boolean isArgumentAdjunctMistake(ResolvedDependency dep, Parse otherParse) {
            return (isPPArgDep(dep) || isVerbAdjunctDep(dep)) && otherParse.dependencies.stream()
                .anyMatch(otherDep ->
                          ((isPPArgDep(dep) && isPPArgDep(otherDep)) &&
                           otherDep.getHead() == dep.getHead() && otherDep.getArgument() == dep.getArgument()) ||
                          ((isPPArgDep(dep) && isVerbAdjunctDep(otherDep)) &&
                           otherDep.getHead() == dep.getArgument() && otherDep.getArgument() == dep.getHead()) ||
                          ((isVerbAdjunctDep(dep) && isPPArgDep(otherDep)) &&
                           otherDep.getHead() == dep.getArgument() && otherDep.getArgument() == dep.getHead()) ||
                          ((isVerbAdjunctDep(dep) && isVerbAdjunctDep(otherDep)) &&
                           otherDep.getHead() == dep.getHead() && otherDep.getArgument() == dep.getArgument()));
        }

    }

    private static class MistakeLogger<M extends Mistake> {
        private ImmutableMap<Integer, SentenceMistakes<M>> mistakesBySentence;
        private ImmutableList<M> allMistakes;
        private String label;

        protected String getLabel() { return label; }

        public ImmutableMap<Integer, SentenceMistakes<M>> getMistakesBySentence() { return mistakesBySentence; }
        public ImmutableList<M> getAllMistakes() { return allMistakes; }

        public MistakeLogger(ImmutableMap<Integer, Parse> parses, SentenceMistakesExtractor<M, SentenceMistakes<M>> extractor, String label) {
            this.mistakesBySentence = parses.entrySet().stream()
                .collect(toImmutableMap(e -> e.getKey(), e -> extractor.extract(e.getValue(), goldParses.get(e.getKey()))));
            this.allMistakes = this.mistakesBySentence.entrySet().stream()
                .flatMap(e -> e.getValue().getMistakes().stream())
                .collect(toImmutableList());
            this.label = label;
        }

        public String log() {
            return String.format("Mistakes (%s): %d (%.2f per sentence)",
                                 label, allMistakes.size(), ( (double) allMistakes.size()) / mistakesBySentence.entrySet().size());
        }

        public String log(int referenceNumMistakes, String referenceLabel) {
            return String.format("Mistakes (%s): %d (%.2f per sentence, %.2f%% %s)",
                                 label, allMistakes.size(), ( (double) allMistakes.size()) / mistakesBySentence.entrySet().size(),
                                 (100.0 * allMistakes.size()) / referenceNumMistakes, referenceLabel);
        }
    }

    private static class PrecisionRecallMistakeLogger<M extends Mistake> {
        private ImmutableMap<Integer, PrecisionRecallMistakes<M>> mistakesBySentence;
        private ImmutableList<M> allMistakes;
        private String label;
        private Results meanStats;

        protected String getLabel() { return label; }

        public ImmutableMap<Integer, PrecisionRecallMistakes<M>> getMistakesBySentence() { return mistakesBySentence; }
        public ImmutableList<M> getAllMistakes() { return allMistakes; }
        public Results getStats() { return meanStats; }

        public PrecisionRecallMistakeLogger(ImmutableMap<Integer, Parse> parses, SentenceMistakesExtractor<M, PrecisionRecallMistakes<M>> extractor, String label) {
            this.mistakesBySentence = parses.entrySet().stream()
                .collect(toImmutableMap(e -> e.getKey(), e -> extractor.extract(e.getValue(), goldParses.get(e.getKey()))));
            this.allMistakes = this.mistakesBySentence.entrySet().stream()
                .flatMap(e -> e.getValue().getMistakes().stream())
                .collect(toImmutableList());
            this.label = label;
            this.meanStats = new Results();
            this.mistakesBySentence.entrySet().stream().forEach(e -> meanStats.add(e.getValue().getStats()));
        }

        public String log() {
            return String.format("Mistakes (%s): %d (%.2f per sentence; precision: %.2f, recall: %.2f, f1: %.2f)",
                                 label, allMistakes.size(), ( (double) allMistakes.size()) / mistakesBySentence.entrySet().size(),
                                 100.0 * meanStats.getPrecision(), 100.0 * meanStats.getRecall(), 100.0 * meanStats.getF1());
        }

        public String log(int referenceNumMistakes, String referenceLabel) {
            return String.format("Mistakes (%s): %d (%.2f per sentence, %.2f%% %s; precision: %.2f, recall: %.2f, f1: %.2f)",
                                 label, allMistakes.size(), ( (double) allMistakes.size()) / mistakesBySentence.entrySet().size(),
                                 (100.0 * allMistakes.size()) / referenceNumMistakes, referenceLabel,
                                 100.0 * meanStats.getPrecision(), 100.0 * meanStats.getRecall(), 100.0 * meanStats.getF1());
        }
    }

    private static ImmutableList<DependencyMistake> falsePositives(ImmutableSet<ResolvedDependency> deps, ImmutableSet<ResolvedDependency> goldDeps, DependencyMatcher depMatcher) {
        return deps.stream()
            .filter(dep -> goldDeps.stream().noneMatch(goldDep -> depMatcher.match(dep, goldDep)))
            .map(dep -> new DependencyMistake(dep, true))
            .collect(toImmutableList());
    }

    private static ImmutableList<DependencyMistake> falseNegatives(ImmutableSet<ResolvedDependency> deps, ImmutableSet<ResolvedDependency> goldDeps, DependencyMatcher depMatcher) {
        return goldDeps.stream()
            .filter(goldDep -> deps.stream().noneMatch(dep -> depMatcher.match(dep, goldDep)))
            .map(dep -> new DependencyMistake(dep, false))
            .collect(toImmutableList());
    }

    private static final ParseData parseData = ParseData.loadFromDevPool().get();
    private static final ImmutableList<Parse> goldParses = parseData.getGoldParses();

    private static void runSupertagAnalysis(ImmutableMap<Integer, Parse> parses) {
        final MistakeLogger<SupertagMistake> supertagMistakes =
            new MistakeLogger(parses,
                              SentenceMistakes.extractorFromMistakeExtractor(SupertagMistake.exactExtractor),
                              "supertag");
        System.out.println(supertagMistakes.log());

        final Table<Category, Category, Integer> supertagMistakeCounts = HashBasedTable.create();
        for(SupertagMistake mistake : supertagMistakes.getAllMistakes()) {
            final Category goldTag = mistake.getGoldSupertag();
            final Category predictedTag = mistake.getPredictedSupertag();
            if(!supertagMistakeCounts.contains(goldTag, predictedTag)) {
                supertagMistakeCounts.put(goldTag, predictedTag, 1);
            } else {
                int currentCount = supertagMistakeCounts.get(goldTag, predictedTag);
                supertagMistakeCounts.put(goldTag, predictedTag, currentCount + 1);
            }
        }

        final ImmutableSet<Category> mistakenSupertags = supertagMistakes.getAllMistakes().stream()
            .flatMap(m -> Stream.of(m.getGoldSupertag(), m.getPredictedSupertag()))
            .collect(toImmutableSet());
        final ImmutableMap<Category, Long> numMistakesBySupertag = mistakenSupertags.stream()
            .collect(toImmutableMap(tag -> tag, tag -> supertagMistakes.getAllMistakes().stream()
                                    .filter(m -> tag.equals(m.getGoldSupertag()) || tag.equals(m.getPredictedSupertag()))
                                    .collect(counting())));
        final ImmutableList<Category> sortedSupertags = mistakenSupertags.stream()
            .sorted((tag1, tag2) -> numMistakesBySupertag.get(tag2).compareTo(numMistakesBySupertag.get(tag1)))
            .collect(toImmutableList());

        int nMostProblematicSupertags = 20;
        System.out.println();
        System.out.println(nMostProblematicSupertags + " supertags with the most tagging errors:");
        for(Category tag : sortedSupertags.subList(0, nMostProblematicSupertags)) {
            System.out.println(tag + ": " + numMistakesBySupertag.get(tag));
        }

        final ImmutableList<Table.Cell<Category, Category, Integer>> sortedSupertagCells = supertagMistakeCounts.cellSet().stream()
            .sorted((cell1, cell2) -> cell2.getValue().compareTo(cell1.getValue()))
            .collect(toImmutableList());
        int nMostCommonSupertaggingErrors = 20;
        System.out.println();
        System.out.println(nMostCommonSupertaggingErrors + " most common supertagging errors (gold --> predicted):");
        for(Table.Cell<Category, Category, Integer> cell : sortedSupertagCells.subList(0, nMostCommonSupertaggingErrors)) {
            System.out.println(cell.getRowKey() + " --> " + cell.getColumnKey() + ": " + cell.getValue());
        }
    }

    private static void logDependencyMistakes(PrecisionRecallMistakeLogger mistakes, StringBuilder csvStringBuilder,
                                              String fullRunLabel, String specificLabel,
                                              Optional<PrecisionRecallMistakeLogger> refMistakes) {
        if(refMistakes.isPresent()) {
            System.out.println(mistakes.log(refMistakes.get().getAllMistakes().size(), "of " + refMistakes.get().getLabel() + " mistakes"));
        } else {
            System.out.println(mistakes.log());
        }
        csvStringBuilder.append(String.format("%s,%s,%.2f,%.2f,%.2f\n",
                                              fullRunLabel, specificLabel,
                                              100.0 * mistakes.getStats().getPrecision(),
                                              100.0 * mistakes.getStats().getRecall(),
                                              100.0 * mistakes.getStats().getF1()));
    }

    private static void runDependencyAnalysis(ImmutableMap<Integer, Parse> parses, DependencyMatcher depMatcher, String depMatchingLabel,
                                              String runLabel, StringBuilder csvStringBuilder) {
        String fullRunLabel = runLabel + "," + depMatchingLabel;
        final PrecisionRecallMistakeLogger depMistakes =
            new PrecisionRecallMistakeLogger(parses,
                                             PrecisionRecallMistakes.extractorForDependencyType(depMatcher, dep -> true),
                                             depMatchingLabel);
        logDependencyMistakes(depMistakes, csvStringBuilder, fullRunLabel, "all dependency scoring", Optional.empty());

        final PrecisionRecallMistakeLogger coreNPArgMistakes =
            new PrecisionRecallMistakeLogger(parses,
                                             PrecisionRecallMistakes.extractorForDependencyType(depMatcher, DependencyMistake::isVerbNPArgDep),
                                             depMatchingLabel + " core-np-arg");
        logDependencyMistakes(coreNPArgMistakes, csvStringBuilder, fullRunLabel, "core NP arg scoring", Optional.of(depMistakes));

        final PrecisionRecallMistakeLogger ppArgMistakes =
            new PrecisionRecallMistakeLogger(parses,
                                             PrecisionRecallMistakes.extractorForDependencyType(depMatcher, DependencyMistake::isPPArgDep),
                                             depMatchingLabel + " pp-arg");
        logDependencyMistakes(ppArgMistakes, csvStringBuilder, fullRunLabel, "pp arg scoring", Optional.of(depMistakes));

        final PrecisionRecallMistakeLogger ppAttachmentMistakes =
            new PrecisionRecallMistakeLogger(parses,
                                             PrecisionRecallMistakes.extractorForDependencyType(depMatcher, DependencyMistake::isPPAttachment),
                                             depMatchingLabel + " pp-attachment");
        logDependencyMistakes(ppAttachmentMistakes, csvStringBuilder, fullRunLabel, "pp attachment scoring", Optional.of(depMistakes));

        final PrecisionRecallMistakeLogger ppAttachmentChoiceMistakes =
            new PrecisionRecallMistakeLogger(parses,
                                             PrecisionRecallMistakes.extractorForMistakeType(depMatcher, DependencyMistake::isPPAttachmentChoiceMistake),
                                             depMatchingLabel + " just-pp-attachment-choice");
        logDependencyMistakes(ppAttachmentChoiceMistakes, csvStringBuilder, fullRunLabel, "pp attachment scoring", Optional.of(ppAttachmentMistakes));

        final PrecisionRecallMistakeLogger argumentAdjunctMistakes =
            new PrecisionRecallMistakeLogger(parses,
                                             PrecisionRecallMistakes.extractorForMistakeType(depMatcher, DependencyMistake::isArgumentAdjunctMistake),
                                             depMatchingLabel + " argument-adjunct");
        logDependencyMistakes(argumentAdjunctMistakes, csvStringBuilder, fullRunLabel, "argument adjunct distinction only scoring", Optional.of(ppAttachmentMistakes));

        final MistakeLogger nounOrVerbAttachmentMistakes =
            new MistakeLogger(parses,
                              SentenceMistakes.nounOrVerbAttachmentMistakeExtractor(depMatcher),
                              depMatchingLabel + " noun-verb");
        System.out.println("\t" + nounOrVerbAttachmentMistakes.log(ppAttachmentMistakes.getAllMistakes().size(),
                                                            "of " + depMatchingLabel + " PP attachment mistakes"));

        final PrecisionRecallMistakeLogger coreNPOrPPAttachmentMistakes =
            new PrecisionRecallMistakeLogger(parses,
                                             PrecisionRecallMistakes.extractorForDependencyType(depMatcher, dep ->
                                                                                                DependencyMistake.isVerbNPArgDep(dep) ||
                                                                                                DependencyMistake.isPPAttachment(dep)),
                                             depMatchingLabel + " core-np+pp-attach");
        logDependencyMistakes(coreNPOrPPAttachmentMistakes, csvStringBuilder, fullRunLabel, "core NP arg or pp attachment scoring", Optional.of(depMistakes));

        final PrecisionRecallMistakeLogger mysteriousMistakes =
            new PrecisionRecallMistakeLogger(parses,
                              PrecisionRecallMistakes.extractorForDependencyType(depMatcher, dep ->
                                                                                 !DependencyMistake.isVerbNPArgDep(dep) &&
                                                                                 !DependencyMistake.isPPAttachment(dep)),
                              depMatchingLabel + " mysterious");
        logDependencyMistakes(mysteriousMistakes, csvStringBuilder, fullRunLabel, "non core NP arg or pp attachment scoring", Optional.of(depMistakes));
    }

    public static void runFullAnalysis(ImmutableMap<Integer, Parse> parses,
                                       String runLabel,
                                       StringBuilder csvStringBuilder) {
        runSupertagAnalysis(parses);

        System.out.println();
        runDependencyAnalysis(parses, labeledDirectedDepMatcher, "labeled directed", runLabel, csvStringBuilder);

        System.out.println();
        runDependencyAnalysis(parses, unlabeledDirectedDepMatcher, "unlabeled directed", runLabel, csvStringBuilder);

        System.out.println();
        runDependencyAnalysis(parses, unlabeledUndirectedDepMatcher, "unlabeled undirected", runLabel, csvStringBuilder);
    }

    private static ImmutableMap<Integer, Parse>
        reparsedFromAnnotationSignal(HITLParser hitlParser,
                                     Function<Integer, ImmutableList<ScoredQuery<QAStructureSurfaceForm>>> queriesForSentence,
                                     ImmutableMap<Integer, List<AlignedAnnotation>> annotations) {
        return annotations.entrySet().stream().collect(toImmutableMap(e -> e.getKey(), e -> {
                    final int sentenceId = e.getKey();
                    final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = queriesForSentence.apply(sentenceId);
                    final ImmutableSet<Constraint> annotationConstraints = queries.stream()
                        .flatMap(query -> {
                                AlignedAnnotation annotation = ExperimentUtils.getAlignedAnnotation(query, annotations.get(sentenceId));
                                if(annotation == null) {
                                    return Stream.of();
                                }
                                return hitlParser.getConstraints(query, annotation).stream();
                            })
                        .collect(toImmutableSet());
                    return hitlParser.getReparsed(sentenceId, annotationConstraints);
                }));
    }

    private static ImmutableMap<Integer, Parse>
        reparsedFromAnnotationSignalGold(HITLParser hitlParser,
                                         Function<Integer, ImmutableList<ScoredQuery<QAStructureSurfaceForm>>> queriesForSentence,
                                         ImmutableMap<Integer, List<AlignedAnnotation>> annotations) {
        return annotations.entrySet().stream().collect(toImmutableMap(e -> e.getKey(), e -> {
                    final int sentenceId = e.getKey();
                    final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = queriesForSentence.apply(sentenceId);
                    final ImmutableSet<Constraint> annotationConstraints = queries.stream()
                        .flatMap(query -> {
                                AlignedAnnotation annotation = ExperimentUtils.getAlignedAnnotation(query, annotations.get(sentenceId));
                                if(annotation == null) {
                                    return Stream.of();
                                }
                                final ImmutableList<Integer> goldOptions = hitlParser.getGoldOptions(query);
                                return hitlParser.getOracleConstraints(query, goldOptions).stream();
                            })
                        .collect(toImmutableSet());
                    return hitlParser.getReparsed(sentenceId, annotationConstraints);
                }));
    }

    private static ImmutableMap<Integer, Parse>
        reparsedFromGoldQueryResponses(HITLParser hitlParser,
                                       Function<Integer, ImmutableList<ScoredQuery<QAStructureSurfaceForm>>> queriesForSentence,
                                       ImmutableMap<Integer, List<AlignedAnnotation>> annotations) {
        return annotations.entrySet().stream().collect(toImmutableMap(e -> e.getKey(), e -> {
                    final int sentenceId = e.getKey();
                    final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = queriesForSentence.apply(sentenceId);
                    final ImmutableSet<Constraint> annotationConstraints = queries.stream()
                        .flatMap(query -> {
                                final ImmutableList<Integer> goldOptions = hitlParser.getGoldOptions(query);
                                return hitlParser.getOracleConstraints(query, goldOptions).stream();
                            })
                        .collect(toImmutableSet());
                    return hitlParser.getReparsed(sentenceId, annotationConstraints);
                }));
    }

    private static ImmutableMap<Integer, Parse>
        reparsedWithGoldDepConstraints(HITLParser hitlParser,
                                       ImmutableSet<Integer> targetSentenceIds,
                                       DependencyProfiler dependencyProfiler,
                                       Predicate<ProfiledDependency> isDepDesired) {
        return targetSentenceIds.stream()
            .collect(toImmutableMap(i -> i, sentenceId -> {
                        final Parse goldParse = goldParses.get(sentenceId);
                        final Stream<ProfiledDependency> chosenDeps = goldParse.dependencies.stream()
                        .map(d -> dependencyProfiler.getProfiledDependency(sentenceId, d, 1.0))
                        .filter(d -> isDepDesired.test(d));
                        final Set<Constraint> constraints = OracleExperiment.getAttachmentConstraints(chosenDeps);
                        return hitlParser.getReparsed(sentenceId, constraints);
                    }));
    }

    public static void runIndependentAnalyses() {
        final StringBuilder csvStringBuilder = new StringBuilder();
        final ImmutableMap<Integer, NBestList> originalOneBestLists = NBestList.loadNBestListsFromFile("parses.100best.out", 1).get();
        final ImmutableMap<Integer, Parse> parsesOriginal = originalOneBestLists.entrySet().stream()
            .collect(toImmutableMap(e -> e.getKey(), e -> e.getValue().getParse(0)));

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("=========== ORIGINAL ONE BEST: ALL DEV SET ============");
        System.out.println("=======================================================");
        runFullAnalysis(parsesOriginal,
                        "",
                        new StringBuilder()); // don't need to compare

        final String[] coreArgAnnotationFiles = {
            "./Crowdflower_data/f893900.csv"                 // Round3-pronouns: checkbox, core only, pronouns.
        };
        final String[] cleftedQuestionAnnotationFiles = {
            "./Crowdflower_data/f897179.csv"                 // Round2-3: NP clefting questions.
        };
        final String[] annotationFiles = {
            "./Crowdflower_data/f893900.csv",                // Round3-pronouns: checkbox, core only, pronouns.
            "./Crowdflower_data/f897179.csv"                 // Round2-3: NP clefting questions.
        };
        final ImmutableMap<Integer, List<AlignedAnnotation>> coreArgAnnotations = ImmutableMap
            .copyOf(ExperimentUtils.loadCrowdflowerAnnotation(coreArgAnnotationFiles));
        final ImmutableMap<Integer, List<AlignedAnnotation>> cleftedQuestionAnnotations = ImmutableMap
            .copyOf(ExperimentUtils.loadCrowdflowerAnnotation(cleftedQuestionAnnotationFiles));
        final ImmutableMap<Integer, List<AlignedAnnotation>> annotations = ImmutableMap
            .copyOf(ExperimentUtils.loadCrowdflowerAnnotation(annotationFiles));
        assert annotations != null;
        System.err.println("\nSuccessfully read annotation files.");
        System.err.println("Number of annotated sentences: " + annotations.keySet().size());
        System.err.println("number of total annotations: " + annotations.keySet().stream().mapToInt(id -> annotations.get(id).size()).sum());
        ImmutableList<ResolvedDependency> allGoldDeps = annotations.keySet().stream()
            .flatMap(id -> goldParses.get(id).dependencies.stream())
            .collect(toImmutableList());
        int totalNumDeps = allGoldDeps.size();
        System.err.println("Number of dependencies in annotated sentences: " + allGoldDeps.size());
        long numNPArgDeps = allGoldDeps.stream()
            .filter(DependencyMistake::isVerbNPArgDep)
            .collect(counting());
        System.err.println(String.format("Number of core NP arg dependencies in annotated sentences: %d (%.2f%%)", numNPArgDeps, (100.0 * numNPArgDeps) / totalNumDeps));
        long numPPArgDeps = allGoldDeps.stream()
            .filter(DependencyMistake::isPPArgDep)
            .collect(counting());
        System.err.println(String.format("Number of core PP arg dependencies in annotated sentences: %d (%.2f%%)", numPPArgDeps, (100.0 * numPPArgDeps) / totalNumDeps));
        long numVerbAdjunctDeps = allGoldDeps.stream()
            .filter(DependencyMistake::isVerbAdjunctDep)
            .collect(counting());
        System.err.println(String.format("Number of verb adjunct dependencies in annotated sentences: %d (%.2f%%)", numVerbAdjunctDeps, (100.0 * numVerbAdjunctDeps) / totalNumDeps));
        long numNounAdjunctDeps = allGoldDeps.stream()
            .filter(DependencyMistake::isNounAdjunctDep)
            .collect(counting());
        System.err.println(String.format("Number of noun adjunct dependencies in annotated sentences: %d (%.2f%%)", numNounAdjunctDeps, (100.0 * numNounAdjunctDeps) / totalNumDeps));

        // these should be the same as in ReparsingExperiment
        final QueryPruningParameters queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipPPQuestions = false;
        queryPruningParameters.skipSAdjQuestions = true;

        final HITLParsingParameters reparsingParameters = new HITLParsingParameters();
        reparsingParameters.jeopardyQuestionMinAgreement = 1;
        reparsingParameters.positiveConstraintMinAgreement = 5;
        reparsingParameters.negativeConstraintMaxAgreement = 1;
        reparsingParameters.skipPronounEvidence = false;
        reparsingParameters.jeopardyQuestionWeight = 1.0;
        reparsingParameters.attachmentPenaltyWeight = 1.0;
        reparsingParameters.supertagPenaltyWeight = 1.0;

        final HITLParser hitlParser = new HITLParser(100);
        hitlParser.setQueryPruningParameters(queryPruningParameters);
        hitlParser.setReparsingParameters(reparsingParameters);

        final ImmutableMap<Integer, Parse> parsesOriginalForAnnotated = parsesOriginal.entrySet().stream()
            .filter(e -> annotations.containsKey(e.getKey()))
            .collect(toImmutableMap(e -> e.getKey(), e -> e.getValue()));

        int totalNumQueries = 0;
        int totalNumQueriesWithoutMatch = 0;
        for(int sentenceId : cleftedQuestionAnnotations.keySet()) {
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> allQueries = hitlParser.getCleftedQuestionsForSentence(sentenceId);
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queriesWithoutMatch = allQueries.stream()
                .filter(query -> ExperimentUtils.getAlignedAnnotation(query, cleftedQuestionAnnotations.get(sentenceId)) == null)
                .collect(toImmutableList());
            totalNumQueries += allQueries.size();
            totalNumQueriesWithoutMatch += queriesWithoutMatch.size();
            System.out.println();
            System.out.println(String.format("SID = %d\n" +
                                             "Number of generated queries: %d\n" +
                                             "Number of annotations: %d\n" +
                                             "Number of queries without a match: %d (%.2f%%)",
                                             sentenceId,
                                             allQueries.size(),
                                             annotations.get(sentenceId).size(),
                                             queriesWithoutMatch.size(),
                                             (100.0 * queriesWithoutMatch.size()) / allQueries.size()));
            System.out.println("Queries without a match:");
            for(ScoredQuery<QAStructureSurfaceForm> query : queriesWithoutMatch) {
                ImmutableList<Integer> goldOptions = hitlParser.getGoldOptions(query);
                System.out.println(query.toString(parseData.getSentences().get(sentenceId),
                                                  'G', goldOptions));
            }
        }
        System.out.println();
        System.out.println(String.format("All sentences:\n" +
                                         "Number of generated queries: %d\n" +
                                         "Number of annotations: %d\n" +
                                         "Number of queries without a match: %d (%.2f%%)",
                                         totalNumQueries,
                                         annotations.keySet().stream().mapToInt(id -> annotations.get(id).size()).sum(),
                                         totalNumQueriesWithoutMatch,
                                         (100.0 * totalNumQueriesWithoutMatch) / totalNumQueries));

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("==== ORIGINAL ONE BEST: SENTENCES WITH ANNOTATIONS ====");
        System.out.println("=======================================================");
        runFullAnalysis(parsesOriginalForAnnotated,
                        "baseline,core arg signal",
                        csvStringBuilder);
        runFullAnalysis(parsesOriginalForAnnotated,
                        "baseline,adjunct signal",
                        csvStringBuilder);
        runFullAnalysis(parsesOriginalForAnnotated,
                        "baseline,core arg + adjunct signal",
                        csvStringBuilder);

        final ImmutableSet<Integer> annotatedSentenceIds = annotations.entrySet().stream()
            .map(e -> e.getKey())
            .collect(toImmutableSet());

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("== REPARSED ONE BEST: JUST CORE ARG QUESTIONS =========");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromAnnotationSignal(hitlParser,
                                                     sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                     .addAll(hitlParser.getPronounCoreArgQueriesForSentence(sentenceId))
                                                     .build(),
                                                     annotations),
                        "human annotated Qs,core arg signal",
                        csvStringBuilder);

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("== REPARSED ONE BEST: JUST CLEFTED QUESTIONS ==========");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromAnnotationSignal(hitlParser,
                                                     sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                     .addAll(hitlParser.getCleftedQuestionsForSentence(sentenceId))
                                                     .build(),
                                                     annotations),
                        "human annotated Qs,adjunct signal",
                        csvStringBuilder);

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("== REPARSED ONE BEST: CORE ARG AND CLEFTED QUESTIONS ==");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromAnnotationSignal(hitlParser,
                                                     sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                     .addAll(hitlParser.getPronounCoreArgQueriesForSentence(sentenceId))
                                                     .addAll(hitlParser.getCleftedQuestionsForSentence(sentenceId))
                                                     .build(),
                                                     annotations),
                        "human annotated Qs,core arg + adjunct signal",
                        csvStringBuilder);

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("== REPARSED: JUST CORE ARG Qs W/GOLD SIGNAL ON ANNO. ==");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromAnnotationSignalGold(hitlParser,
                                                         sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                         .addAll(hitlParser.getPronounCoreArgQueriesForSentence(sentenceId))
                                                         .addAll(hitlParser.getCleftedQuestionsForSentence(sentenceId))
                                                         .build(),
                                                         annotations),
                        "gold annotated Qs,core arg signal",
                        csvStringBuilder);

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("== REPARSED: JUST CLEFTED  Qs W/GOLD SIGNAL ON ANNO. ==");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromAnnotationSignalGold(hitlParser,
                                                         sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                         .addAll(hitlParser.getCleftedQuestionsForSentence(sentenceId))
                                                         .build(),
                                                         annotations),
                        "gold annotated Qs,adjunct signal",
                        csvStringBuilder);

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("== REPARSED: CORE ARG/CLEFTED W/GOLD SIGNAL ON ANNO. ==");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromAnnotationSignalGold(hitlParser,
                                                         sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                         .addAll(hitlParser.getPronounCoreArgQueriesForSentence(sentenceId))
                                                         .addAll(hitlParser.getCleftedQuestionsForSentence(sentenceId))
                                                         .build(),
                                                         annotations),
                        "gold annotated Qs,core arg + adjunct signal",
                        csvStringBuilder);

        System.err.println("Number of filtered core arg queries: " +
                           annotations.keySet().stream().mapToInt(sentenceId -> hitlParser.getPronounCoreArgQueriesForSentence(sentenceId).size()).sum());
        System.err.println("Number of gold constraints from filtered core arg queries: " +
                           annotations.keySet().stream().mapToInt(sentenceId -> {
                                   final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = hitlParser.getPronounCoreArgQueriesForSentence(sentenceId);
                                   final ImmutableSet<Constraint> annotationConstraints = queries.stream()
                                       .flatMap(query -> {
                                               final ImmutableList<Integer> goldOptions = hitlParser.getGoldOptions(query);
                                               return hitlParser.getOracleConstraints(query, goldOptions).stream();
                                           })
                                       .collect(toImmutableSet());
                                   return annotationConstraints.size();
                                       }).sum());
        System.err.println("Number of filtered clefted question queries: " +
                           annotations.keySet().stream().mapToInt(sentenceId -> hitlParser.getCleftedQuestionsForSentence(sentenceId).size()).sum());
        System.err.println("Number of gold constraints from filtered clefted question queries: " +
                           annotations.keySet().stream().mapToInt(sentenceId -> {
                                   final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = hitlParser.getCleftedQuestionsForSentence(sentenceId);
                                   final ImmutableSet<Constraint> annotationConstraints = queries.stream()
                                       .flatMap(query -> {
                                               final ImmutableList<Integer> goldOptions = hitlParser.getGoldOptions(query);
                                               return hitlParser.getOracleConstraints(query, goldOptions).stream();
                                           })
                                       .collect(toImmutableSet());
                                   return annotationConstraints.size();
                                       }).sum());

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("== REPARSED: JUST CORE ARG Qs W/GOLD SIGNAL FILTERED ==");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromGoldQueryResponses(hitlParser,
                                                       sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                       .addAll(hitlParser.getPronounCoreArgQueriesForSentence(sentenceId))
                                                       .build(),
                                                       annotations),
                        "gold all filtered Qs,core arg signal",
                        csvStringBuilder);

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("== REPARSED: JUST CLEFTED Qs  W/GOLD SIGNAL FILTERED ==");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromGoldQueryResponses(hitlParser,
                                                       sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                       .addAll(hitlParser.getCleftedQuestionsForSentence(sentenceId))
                                                       .build(),
                                                       annotations),
                        "gold all filtered Qs,adjunct signal",
                        csvStringBuilder);

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("===== REPARSED: ALL QUERIES W/GOLD SIGNAL FILTERED ====");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromGoldQueryResponses(hitlParser,
                                                       sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                       .addAll(hitlParser.getCleftedQuestionsForSentence(sentenceId))
                                                       .addAll(hitlParser.getPronounCoreArgQueriesForSentence(sentenceId))
                                                       .build(),
                                                       annotations),
                        "gold all filtered Qs,core arg + adjunct signal",
                        csvStringBuilder);

        // hooray for messing with the fields of this structure, which is already inside the HITLParser...
        queryPruningParameters.minPromptConfidence = 0.0;
        queryPruningParameters.minOptionConfidence = 0.0;
        queryPruningParameters.minOptionEntropy = 0.0;
        queryPruningParameters.maxNumOptionsPerQuery = 50;
        queryPruningParameters.skipBinaryQueries = false;
        queryPruningParameters.skipSAdjQuestions = false;
        queryPruningParameters.skipPPQuestions = false;
        queryPruningParameters.skipQueriesWithPronounOptions = false;

        System.err.println("Number of unfiltered core arg queries: " +
                           annotations.keySet().stream().mapToInt(sentenceId -> hitlParser.getPronounCoreArgQueriesForSentence(sentenceId).size()).sum() + "; " +
                           annotations.keySet().stream().map(sentenceId -> hitlParser.getPronounCoreArgQueriesForSentence(sentenceId).size()).collect(toImmutableList()));
        System.err.println("Number of gold constraints from unfiltered core arg queries: " +
                           annotations.keySet().stream().mapToInt(sentenceId -> {
                                   final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = hitlParser.getPronounCoreArgQueriesForSentence(sentenceId);
                                   final ImmutableSet<Constraint> annotationConstraints = queries.stream()
                                       .flatMap(query -> {
                                               final ImmutableList<Integer> goldOptions = hitlParser.getGoldOptions(query);
                                               return hitlParser.getOracleConstraints(query, goldOptions).stream();
                                           })
                                       .collect(toImmutableSet());
                                   return annotationConstraints.size();
                                       }).sum());
        System.err.println("Number of unfiltered clefted question queries: " +
                           annotations.keySet().stream().mapToInt(sentenceId -> hitlParser.getCleftedQuestionsForSentence(sentenceId).size()).sum() + "; " +
                           annotations.keySet().stream().map(sentenceId -> hitlParser.getCleftedQuestionsForSentence(sentenceId).size()).collect(toImmutableList()));
        System.err.println("Number of gold constraints from unfiltered clefted question queries: " +
                           annotations.keySet().stream().mapToInt(sentenceId -> {
                                   final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = hitlParser.getCleftedQuestionsForSentence(sentenceId);
                                   final ImmutableSet<Constraint> annotationConstraints = queries.stream()
                                       .flatMap(query -> {
                                               final ImmutableList<Integer> goldOptions = hitlParser.getGoldOptions(query);
                                               return hitlParser.getOracleConstraints(query, goldOptions).stream();
                                           })
                                       .collect(toImmutableSet());
                                   return annotationConstraints.size();
                                       }).sum());

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("= REPARSED: JUST CORE ARG Qs W/GOLD SIGNAL UNFILTERED =");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromGoldQueryResponses(hitlParser,
                                                       sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                       .addAll(hitlParser.getPronounCoreArgQueriesForSentence(sentenceId))
                                                       .build(),
                                                       annotations),
                        "gold all unfiltered Qs,core arg signal",
                        csvStringBuilder);

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("= REPARSED: JUST CLEFTED Qs  W/GOLD SIGNAL UNFILTERED =");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromGoldQueryResponses(hitlParser,
                                                       sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                       .addAll(hitlParser.getCleftedQuestionsForSentence(sentenceId))
                                                       .build(),
                                                       annotations),
                        "gold all unfiltered Qs,adjunct signal",
                        csvStringBuilder);

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("==== REPARSED: ALL QUERIES W/GOLD SIGNAL UNFILTERED ===");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromGoldQueryResponses(hitlParser,
                                                       sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                       .addAll(hitlParser.getCleftedQuestionsForSentence(sentenceId))
                                                       .addAll(hitlParser.getPronounCoreArgQueriesForSentence(sentenceId))
                                                       .build(),
                                                       annotations),
                        "gold all unfiltered Qs,core arg + adjunct signal",
                        csvStringBuilder);

        final DependencyProfiler dependencyProfiler =
            new DependencyProfiler(hitlParser.getParseData(),
                                   hitlParser.getAllSentenceIds().stream()
                                   .collect(toMap(Function.identity(), hitlParser::getNBestList)));

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("== REPARSED ONE BEST: ALL GOLD CORE ARG CONSTRAINTS ===");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedWithGoldDepConstraints(hitlParser,
                                                       annotatedSentenceIds,
                                                       dependencyProfiler,
                                                       d -> dependencyProfiler.dependencyIsCore(d.dependency, false)),
                        "gold constraints,core arg signal",
                        csvStringBuilder);


        System.out.println();
        System.out.println("=======================================================");
        System.out.println("=== REPARSED ONE BEST: ALL GOLD ADJUNCT CONSTRAINTS ===");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedWithGoldDepConstraints(hitlParser,
                                                       annotatedSentenceIds,
                                                       dependencyProfiler,
                                                       d -> !dependencyProfiler.dependencyIsCore(d.dependency, false) &&
                                                       dependencyProfiler.dependencyIsAdjunct(d.dependency, false)),
                        "gold constraints,adjunct signal",
                        csvStringBuilder);

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("== REPARSED ONE BEST: GOLD CORE & ADJUNCT CONSTRAINTS =");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedWithGoldDepConstraints(hitlParser,
                                                       annotatedSentenceIds,
                                                       dependencyProfiler,
                                                       d -> dependencyProfiler.dependencyIsCore(d.dependency, false) ||
                                                       dependencyProfiler.dependencyIsAdjunct(d.dependency, false)),
                        "gold constraints,core arg + adjunct signal",
                        csvStringBuilder);

        System.out.println("CSV data:");
        System.out.println("reparsing strategy,signal type,dependency matching,scoring method,precision,recall,f1");
        System.out.println(csvStringBuilder.toString());
    }

    public static void runDifferentialAnalyses() {

    }

    public static void main(String[] args) {
        runIndependentAnalyses();
    }
}
