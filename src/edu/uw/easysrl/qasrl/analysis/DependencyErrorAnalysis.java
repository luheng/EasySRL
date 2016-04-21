package edu.uw.easysrl.qasrl.analysis;

import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.NBestList;

import static edu.uw.easysrl.util.GuavaCollectors.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.analysis.DependencyProfiler;
import edu.uw.easysrl.qasrl.analysis.ProfiledDependency;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingExperiment;
import edu.uw.easysrl.qasrl.experiments.OracleExperiment;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static java.util.stream.Collectors.*;


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

    public static final class SingleDependencyMistake extends Mistake {
        private final ResolvedDependency dependency;
        private final boolean isFalsePositive; // false means it's a false negative

        public ResolvedDependency getDependency() { return dependency; }
        public boolean isFalsePositive() { return isFalsePositive; }
        public boolean isFalseNegative() { return !isFalsePositive; }

        public SingleDependencyMistake(ResolvedDependency dependency, boolean isFalsePositive) {
            this.dependency = dependency;
            this.isFalsePositive = isFalsePositive;
        }

        public static MistakeExtractor<SingleDependencyMistake> extractor(DependencyMatcher depMatcher) {
            return (parse, goldParse) -> {
                final Stream<SingleDependencyMistake> fp = falsePositives(parse, goldParse, depMatcher).stream()
                    .map(dep -> new SingleDependencyMistake(dep, true));
                final Stream<SingleDependencyMistake> fn = falseNegatives(parse, goldParse, depMatcher).stream()
                    .map(dep -> new SingleDependencyMistake(dep, false));
                return Stream.concat(fp, fn)
                    .collect(toImmutableList());
            };
        }

        @FunctionalInterface
        public static interface DependencyClassifier {
            public boolean isMember(ResolvedDependency dep);
        }

        public static MistakeExtractor<SingleDependencyMistake>
            extractorForDependencyType(DependencyMatcher depMatcher,
                                       DependencyClassifier depClass) {
            return (parse, goldParse) -> {
                final Stream<SingleDependencyMistake> fp = falsePositives(parse, goldParse, depMatcher).stream()
                    .filter(dep -> depClass.isMember(dep))
                    .map(dep -> new SingleDependencyMistake(dep, true));
                final Stream<SingleDependencyMistake> fn = falseNegatives(parse, goldParse, depMatcher).stream()
                    .filter(dep -> depClass.isMember(dep))
                    .map(dep -> new SingleDependencyMistake(dep, false));
                return Stream.concat(fp, fn)
                    .collect(toImmutableList());
            };
        }

        @FunctionalInterface
        public static interface MistakeClassifier {
            public boolean isMember(ResolvedDependency dep, Parse otherParse);
        }

        public static MistakeExtractor<SingleDependencyMistake>
            extractorForDependencyType(DependencyMatcher depMatcher,
                                       MistakeClassifier mistakeClass) {
            return (parse, goldParse) -> {
                final Stream<SingleDependencyMistake> fp = falsePositives(parse, goldParse, depMatcher).stream()
                    .filter(dep -> mistakeClass.isMember(dep, goldParse))
                    .map(dep -> new SingleDependencyMistake(dep, true));
                final Stream<SingleDependencyMistake> fn = falseNegatives(parse, goldParse, depMatcher).stream()
                    .filter(dep -> mistakeClass.isMember(dep, parse))
                    .map(dep -> new SingleDependencyMistake(dep, false));
                return Stream.concat(fp, fn)
                    .collect(toImmutableList());
            };
        }

        public static MistakeExtractor<SingleDependencyMistake> coreNPArgExtractor(DependencyMatcher depMatcher) {
            return extractorForDependencyType(depMatcher, SingleDependencyMistake::isVerbNPArgDep);
        }

        public static MistakeExtractor<SingleDependencyMistake> ppAttachmentExtractor(DependencyMatcher depMatcher) {
            return extractorForDependencyType(depMatcher, SingleDependencyMistake::isPPAttachment);
        }

        public static MistakeExtractor<SingleDependencyMistake> argumentAdjunctExtractor(DependencyMatcher depMatcher) {
            return extractorForDependencyType(depMatcher, SingleDependencyMistake::isArgumentAdjunctMistake);
        }

        public static MistakeExtractor<SingleDependencyMistake> nounOrVerbAttachmentExtractor(DependencyMatcher depMatcher) {
            return extractorForDependencyType(depMatcher, SingleDependencyMistake::isNounOrVerbAttachmentMistake);
        }

        public static MistakeExtractor<SingleDependencyMistake> ppAttachmentChoiceExtractor(DependencyMatcher depMatcher) {
            return extractorForDependencyType(depMatcher, SingleDependencyMistake::isPPAttachmentChoiceMistake);
        }

        public static MistakeExtractor<SingleDependencyMistake> mysteryExtractor(DependencyMatcher depMatcher) {
            return extractorForDependencyType(depMatcher, (dep) -> !isVerbNPArgDep(dep) && !isPPAttachment(dep));
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

        private static boolean isPPAttachmentChoiceMistake(ResolvedDependency dep, Parse otherParse) {
            Function<DependencyClassifier, Boolean> isMistake = (depClass -> {
                    boolean otherHasSuchADep = otherParse.dependencies.stream()
                    .anyMatch(otherDep -> otherDep.getHead() == dep.getHead() &&
                              depClass.isMember(otherDep));
                    boolean otherHasNotThisDep = otherParse.dependencies.stream()
                    .noneMatch(otherDep -> otherDep.getHead() == dep.getHead() &&
                               otherDep.getArgument() == dep.getArgument() &&
                               depClass.isMember(otherDep));
                    return depClass.isMember(dep) && otherHasSuchADep && otherHasNotThisDep;
                });
            return isMistake.apply(SingleDependencyMistake::isPPArgDep) ||
                isMistake.apply(SingleDependencyMistake::isVerbAdjunctDep) ||
                isMistake.apply(SingleDependencyMistake::isNounAdjunctDep);
        }

        private static boolean isArgumentAdjunctMistake(ResolvedDependency dep, Parse otherParse) {
            return (isPPArgDep(dep) &&
                    otherParse.dependencies.stream()
                    .anyMatch(otherDep -> otherDep.getHead() == dep.getArgument() &&
                              otherDep.getArgument() == dep.getHead() &&
                              isVerbAdjunctDep(otherDep))) ||
                (isVerbAdjunctDep(dep) &&
                 otherParse.dependencies.stream()
                 .anyMatch(otherDep -> otherDep.getHead() == dep.getArgument() &&
                           otherDep.getArgument() == dep.getHead() &&
                           isPPArgDep(otherDep)));
        }

        private static boolean isNounOrVerbAttachmentMistake(ResolvedDependency dep, Parse otherParse) {
            return (isVerbAdjunctDep(dep) &&
                    otherParse.dependencies.stream()
                    .anyMatch(otherDep -> otherDep.getHead() == dep.getHead() &&
                              isNounAdjunctDep(otherDep))) ||
                (isPPArgDep(dep) &&
                 otherParse.dependencies.stream()
                 .anyMatch(otherDep -> otherDep.getHead() == dep.getArgument() &&
                           isNounAdjunctDep(otherDep))) ||
                (isNounAdjunctDep(dep) &&
                 otherParse.dependencies.stream()
                 .anyMatch(otherDep -> (otherDep.getHead() == dep.getHead() &&
                                        isVerbAdjunctDep(otherDep)) ||
                           (otherDep.getArgument() == dep.getHead() &&
                            isPPArgDep(otherDep))));
        }
    }

    private static class MistakeLogger<M extends Mistake> {
        private ImmutableMap<Integer, ImmutableList<M>> mistakesBySentence;
        private ImmutableList<M> allMistakes;
        private String label;

        protected String getLabel() { return label; }

        public ImmutableMap<Integer, ImmutableList<M>> getMistakesBySentence() { return mistakesBySentence; }
        public ImmutableList<M> getAllMistakes() { return allMistakes; }

        public MistakeLogger(ImmutableMap<Integer, Parse> parses, MistakeExtractor<M> extractor, String label) {
            this.mistakesBySentence = parses.entrySet().stream()
                .collect(toImmutableMap(e -> e.getKey(), e -> extractor.extract(e.getValue(), goldParses.get(e.getKey()))));
            this.allMistakes = this.mistakesBySentence.entrySet().stream()
                .flatMap(e -> e.getValue().stream())
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

    private static final class DependencyMistakeLogger extends MistakeLogger<SingleDependencyMistake> {
        private final ImmutableMap<Integer, Double> precisionBySentence, recallBySentence, f1BySentence;
        private final double meanPrecision, meanRecall, meanF1;

        public DependencyMistakeLogger(ImmutableMap<Integer, Parse> parses,
                                       MistakeExtractor<SingleDependencyMistake> extractor,
                                       String label) {
            super(parses, extractor, label);
            this.precisionBySentence = null;
            this.recallBySentence = null;
            this.f1BySentence = null;
            this.meanPrecision = 0.0;
            this.meanRecall = 0.0;
            this.meanF1 = 0.0;
            // this.precisionBySentence = parses.entrySet().stream()
            //     .collect(toImmutableMap(e -> e.getKey(), e -> {
            //                 int sentenceId = e.getValue();
            //                 Parse parse = 
            //             }))
        }
    }


    private static ImmutableList<ResolvedDependency> falsePositives(Parse parse, Parse goldParse, DependencyMatcher depMatcher) {
        return parse.dependencies.stream()
            .filter(dep -> goldParse.dependencies.stream().noneMatch(goldDep -> depMatcher.match(dep, goldDep)))
            .collect(toImmutableList());
    }

    private static ImmutableList<ResolvedDependency> falseNegatives(Parse parse, Parse goldParse, DependencyMatcher depMatcher) {
        return goldParse.dependencies.stream()
            .filter(goldDep -> parse.dependencies.stream().noneMatch(dep -> depMatcher.match(dep, goldDep)))
            .collect(toImmutableList());
    }

    private static final ParseData parseData = ParseData.loadFromDevPool().get();
    private static final ImmutableList<Parse> goldParses = parseData.getGoldParses();

    private static void runSupertagAnalysis(ImmutableMap<Integer, Parse> parses) {
        final MistakeLogger<SupertagMistake> supertagMistakes = new MistakeLogger(parses, SupertagMistake.exactExtractor, "supertag");
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

    private static void runDependencyAnalysis(ImmutableMap<Integer, Parse> parses, DependencyMatcher depMatcher, String depMatchingLabel) {
        final MistakeLogger depMistakes =
            new MistakeLogger(parses,
                              SingleDependencyMistake.extractor(depMatcher),
                              depMatchingLabel);
        System.out.println(depMistakes.log());

        final MistakeLogger coreNPArgMistakes =
            new MistakeLogger(parses,
                              SingleDependencyMistake.coreNPArgExtractor(depMatcher),
                              depMatchingLabel + " core-np-arg");
        System.out.println(coreNPArgMistakes.log(depMistakes.getAllMistakes().size(),
                                                 "of " + depMatchingLabel + " mistakes"));

        final MistakeLogger ppAttachmentMistakes =
            new MistakeLogger(parses,
                              SingleDependencyMistake.ppAttachmentExtractor(depMatcher),
                              depMatchingLabel + " pp-attachment");
        System.out.println(ppAttachmentMistakes.log(depMistakes.getAllMistakes().size(),
                                                    "of " + depMatchingLabel + " mistakes"));

        final MistakeLogger ppAttachmentChoiceMistakes =
            new MistakeLogger(parses,
                              SingleDependencyMistake.ppAttachmentChoiceExtractor(depMatcher),
                              depMatchingLabel + " just-pp-attachment-choice");
        System.out.println("\t" + ppAttachmentChoiceMistakes.log(ppAttachmentMistakes.getAllMistakes().size(),
                                                                 "of " + depMatchingLabel + " PP attachment mistakes"));

        final MistakeLogger argumentAdjunctMistakes =
            new MistakeLogger(parses,
                              SingleDependencyMistake.argumentAdjunctExtractor(depMatcher),
                              depMatchingLabel + " argument-adjunct");
        System.out.println("\t"+ argumentAdjunctMistakes.log(ppAttachmentMistakes.getAllMistakes().size(),
                                                       "of " + depMatchingLabel + " PP attachment mistakes"));

        final MistakeLogger nounOrVerbAttachmentMistakes =
            new MistakeLogger(parses,
                              SingleDependencyMistake.nounOrVerbAttachmentExtractor(depMatcher),
                              depMatchingLabel + " noun-verb");
        System.out.println("\t" + nounOrVerbAttachmentMistakes.log(ppAttachmentMistakes.getAllMistakes().size(),
                                                            "of " + depMatchingLabel + " PP attachment mistakes"));

        final MistakeLogger mysteriousMistakes =
            new MistakeLogger(parses,
                              SingleDependencyMistake.mysteryExtractor(depMatcher),
                              depMatchingLabel + " mysterious");
        System.out.println(mysteriousMistakes.log(depMistakes.getAllMistakes().size(),
                                                  "of " + depMatchingLabel + " mistakes"));
    }

    public static void runFullAnalysis(ImmutableMap<Integer, Parse> parses) {
        runSupertagAnalysis(parses);

        System.out.println();
        runDependencyAnalysis(parses, unlabeledDirectedDepMatcher, "unlabeled directed");

        System.out.println();
        runDependencyAnalysis(parses, unlabeledUndirectedDepMatcher, "unlabeled undirected");

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
                                AlignedAnnotation annotation = ExperimentUtils.getAlignedAnnotation(query, annotations.get(sentenceId));
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

    public static void main(String[] args) {

        // TODO TODO TODO : log F1 score with each as well for some context.

        final ImmutableMap<Integer, NBestList> originalOneBestLists = NBestList.loadNBestListsFromFile("parses.100best.out", 1).get();
        final ImmutableMap<Integer, Parse> parsesOriginal = originalOneBestLists.entrySet().stream()
            .collect(toImmutableMap(e -> e.getKey(), e -> e.getValue().getParse(0)));
        System.out.println();
        System.out.println("=======================================================");
        System.out.println("=========== ORIGINAL ONE BEST: ALL DEV SET ============");
        System.out.println("=======================================================");
        runFullAnalysis(parsesOriginal);

        final String[] annotationFiles = {
            "./Crowdflower_data/f893900.csv",                // Round3-pronouns: checkbox, core only, pronouns.
            "./Crowdflower_data/f897179.csv"                 // Round2-3: NP clefting questions.
        };
        final ImmutableMap<Integer, List<AlignedAnnotation>> annotations = ImmutableMap
            .copyOf(ExperimentUtils.loadCrowdflowerAnnotation(annotationFiles));
        assert annotations != null;
        System.out.println("\nSuccessfully read annotation files.\n");

        final HITLParser hitlParser = ReparsingExperiment.makeHITLParser();

        final ImmutableMap<Integer, Parse> parsesOriginalForAnnotated = parsesOriginal.entrySet().stream()
            .filter(e -> annotations.containsKey(e.getKey()))
            .collect(toImmutableMap(e -> e.getKey(), e -> e.getValue()));

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("==== ORIGINAL ONE BEST: SENTENCES WITH ANNOTATIONS ====");
        System.out.println("=======================================================");
        runFullAnalysis(parsesOriginalForAnnotated);

        final ImmutableSet<Integer> annotatedSentenceIds = annotations.entrySet().stream()
            .map(e -> e.getKey())
            .collect(toImmutableSet());

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("== REPARSED ONE BEST: CORE ARG AND CLEFTED QUESTIONS ==");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromAnnotationSignal(hitlParser,
                                                     sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                     .addAll(hitlParser.getPronounCoreArgQueriesForSentence(sentenceId))
                                                     .addAll(hitlParser.getCleftedQuestionsForSentence(sentenceId))
                                                     .build(),
                                                     annotations));

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("== REPARSED: CORE ARG/CLEFTED W/GOLD SIGNAL ON ANNO. ==");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromAnnotationSignalGold(hitlParser,
                                                         sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                         .addAll(hitlParser.getPronounCoreArgQueriesForSentence(sentenceId))
                                                         .addAll(hitlParser.getCleftedQuestionsForSentence(sentenceId))
                                                         .build(),
                                                         annotations));

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("=== REPARSED: CORE ARG/CLEFTED W/GOLD SIGNAL ALL Q's ==");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedFromGoldQueryResponses(hitlParser,
                                                       sentenceId -> new ImmutableList.Builder<ScoredQuery<QAStructureSurfaceForm>>()
                                                       .addAll(hitlParser.getPronounCoreArgQueriesForSentence(sentenceId))
                                                       .addAll(hitlParser.getCleftedQuestionsForSentence(sentenceId))
                                                       .build(),
                                                       annotations));

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
                                                       d -> dependencyProfiler.dependencyIsCore(d.dependency, false)));


        System.out.println();
        System.out.println("=======================================================");
        System.out.println("=== REPARSED ONE BEST: ALL GOLD ADJUNCT CONSTRAINTS ===");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedWithGoldDepConstraints(hitlParser,
                                                       annotatedSentenceIds,
                                                       dependencyProfiler,
                                                       d -> !dependencyProfiler.dependencyIsCore(d.dependency, false) &&
                                                       dependencyProfiler.dependencyIsAdjunct(d.dependency, false)));

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("== REPARSED ONE BEST: GOLD CORE & ADJUNCT CONSTRAINTS =");
        System.out.println("=======================================================");
        runFullAnalysis(reparsedWithGoldDepConstraints(hitlParser,
                                                       annotatedSentenceIds,
                                                       dependencyProfiler,
                                                       d -> dependencyProfiler.dependencyIsCore(d.dependency, false) ||
                                                       dependencyProfiler.dependencyIsAdjunct(d.dependency, false)));
    }
}
