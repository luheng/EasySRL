package edu.uw.easysrl.syntax.training;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.util.concurrent.AtomicDouble;
import com.sun.org.apache.xpath.internal.operations.Mult;
import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.DependencyStructure.ResolvedDependency;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.feature.ArgumentSlotFeature;
import edu.uw.easysrl.syntax.model.feature.BilexicalFeature;
import edu.uw.easysrl.syntax.model.feature.Feature;
import edu.uw.easysrl.syntax.model.feature.Feature.FeatureKey;
import edu.uw.easysrl.syntax.model.feature.PrepositionFeature;
import edu.uw.easysrl.syntax.parser.AbstractParser;
import edu.uw.easysrl.util.Util;

import java.io.IOException;
import java.util.*;

/**
 * Created by luheng on 10/28/15.
 */
public class TrainingFeatureHelper {
    private TrainingParameters trainingParameters;
    private TrainingDataParameters dataParameters;

    public TrainingFeatureHelper(TrainingParameters trainingParameters, TrainingDataParameters dataParameters) {
        this.trainingParameters = trainingParameters;
        this.dataParameters = dataParameters;
    }

    /**
     * Creates a map from (sufficiently frequent) features to integers
     */
    public Map<FeatureKey, Integer> makeKeyToIndexMap(
            final int minimumFeatureFrequency,
            final Set<FeatureKey> boundedFeatures) throws IOException {

        final Multiset<FeatureKey> keyCount = HashMultiset.create();
        final Multiset<FeatureKey> bilexicalKeyCount = HashMultiset.create();
        final Multiset<FeatureKey> binaryFeatureCount = HashMultiset.create();

        final Map<FeatureKey, Integer> result = new HashMap<>();

        // TODO: change this
        final Iterator<ParallelCorpusReader.Sentence> sentenceIt = ParallelCorpusReader.READER.readCorpus(false);

        while (sentenceIt.hasNext()) {
            final ParallelCorpusReader.Sentence sentence = sentenceIt.next();
            final List<DependencyStructure.ResolvedDependency> goldDeps = getGoldDeps(sentence);
            final List<Category> cats = sentence.getLexicalCategories();
            for (int i = 0; i < cats.size(); i++) {
                final Feature.FeatureKey key = trainingParameters.getFeatureSet().lexicalCategoryFeatures.getFeatureKey(
                        sentence.getInputWords(), i, cats.get(i));
                if (key != null) {
                    keyCount.add(key);
                }
            }
            for (final DependencyStructure.ResolvedDependency dep : goldDeps) {
                final SRLFrame.SRLLabel role = dep.getSemanticRole();
                // if (cutoffsDictionary.isFrequentWithAnySRLLabel(
                // dep.getCategory(), dep.getArgNumber())
                // && cutoffsDictionary.isFrequent(dep.getCategory(),
                // dep.getArgNumber(), dep.getSemanticRole())) {
                for (final ArgumentSlotFeature feature : trainingParameters.getFeatureSet().argumentSlotFeatures) {
                    final Feature.FeatureKey key = feature.getFeatureKey(sentence.getInputWords(), dep.getPredicateIndex(),
                            role, dep.getCategory(), dep.getArgNumber(), dep.getPreposition());
                    keyCount.add(key);
                }
                if (dep.getPreposition() != Preposition.NONE) {
                    for (final PrepositionFeature feature : trainingParameters.getFeatureSet().prepositionFeatures) {
                        final Feature.FeatureKey key = feature.getFeatureKey(sentence.getInputWords(), dep.getPredicateIndex(),
                                dep.getCategory(), dep.getPreposition(), dep.getArgNumber());
                        keyCount.add(key);
                    }
                }
                // }
                if (dep.getSemanticRole() != SRLFrame.NONE) {
                    for (final BilexicalFeature feature : trainingParameters.getFeatureSet().dependencyFeatures) {
                        final Feature.FeatureKey key = feature.getFeatureKey(sentence.getInputWords(), dep.getSemanticRole(),
                                dep.getPredicateIndex(), dep.getArgumentIndex());
                        bilexicalKeyCount.add(key);
                    }
                }

            }
            getFromDerivation(sentence.getCcgbankParse(), binaryFeatureCount, boundedFeatures,
                    sentence.getInputWords(), 0, sentence.getInputWords().size());
            for (final Feature.RootCategoryFeature rootFeature : trainingParameters.getFeatureSet().rootFeatures) {
                final Feature.FeatureKey key = rootFeature.getFeatureKey(sentence.getCcgbankParse().getCategory(),
                        sentence.getInputWords());
                boundedFeatures.add(key);
                keyCount.add(key);
            }
        }
        result.put(trainingParameters.getFeatureSet().lexicalCategoryFeatures.getDefault(), result.size());
        // This is never used, actually.
        //addFrequentFeatures(30 /* minimum feature frequency */, binaryFeatureCount, result, boundedFeatures, true);
        addFrequentFeatures(minimumFeatureFrequency, keyCount, result, boundedFeatures, false);
        addFrequentFeatures(minimumFeatureFrequency, bilexicalKeyCount, result, boundedFeatures, false);
        for (final Feature.BinaryFeature feature : trainingParameters.getFeatureSet().binaryFeatures) {
            boundedFeatures.add(feature.getDefault());
        }
        for (final Feature feature : trainingParameters.getFeatureSet().getAllFeatures()) {
            if (!result.containsKey(feature.getDefault())) {
                result.put(feature.getDefault(), result.size());
            }
        }
        System.out.println("Total features: " + result.size());
        return result;
    }

    // FIXME:
    private List<Set<Category>> getAllCategories(QASentence sentence, Collection<CompressedChart.Key> roots) {
        Deque<CompressedChart.Key> cache = new ArrayDeque<>();
        List<Set<Category>> result = new ArrayList<>();
        for (int i = 0; i < sentence.getSentenceLength(); i++) {
            result.add(new HashSet<>());
        }
        cache.addAll(roots);
        while (cache.size() > 0) {
            CompressedChart.Key key = cache.pop();
            if (key.getStartIndex() == key.getLastIndex()) {
                int index = key.getStartIndex();
                result.get(index).add(key.category);
            }
            for (CompressedChart.Value value : key.getChildren()) {
                try {
                    if (CompressedChart.CategoryValue.class.isInstance(value) &&
                            (value.getStartIndex() == value.getLastIndex())) {
                        int index = value.getStartIndex();
                        result.get(index).add(value.getCategory());
                    }
                } catch (UnsupportedOperationException e) {
                    // FIXME: how to avoid this?
                    //System.err.println("[value]:\tnull");
                }
                cache.addAll(value.getChildren());
            }
        }
        /*
        for (int index : result.keySet()) {
            System.out.print(index + "\t" + sentence.getWords().get(index) + "\t");
            for (Category cat : result.get(index)) {
                System.out.print(cat + "\t");
            }
            System.out.println();
        }
        */
        return result;
    }

    /**
     * Creates a map from (sufficiently frequent) features to integers
     */
    public Map<FeatureKey, Integer> makeKeyToIndexMap(
            Iterator<QASentence> sentenceIt,
            final int minimumFeatureFrequency,
            final Set<FeatureKey> boundedFeatures,
            QATrainingDataLoader ccgHelper) throws IOException {

        final Multiset<FeatureKey> keyCount = HashMultiset.create();
        final Multiset<FeatureKey> bilexicalKeyCount = HashMultiset.create();
        final Multiset<FeatureKey> binaryFeatureCount = HashMultiset.create();

        final Map<FeatureKey, Integer> result = new HashMap<>();

        while (sentenceIt.hasNext()) {
            final QASentence sentence = sentenceIt.next();
            final AtomicDouble beta = new AtomicDouble(dataParameters.getSupertaggerBeam());
            final CompressedChart smallChart = ccgHelper.parseSentence(
                    sentence.getWords(),
                    new AtomicDouble(Math.max(dataParameters.getSupertaggerBeamForGoldCharts(),
                            beta.doubleValue())),
                    Training.ROOT_CATEGORIES);
            if (smallChart == null) {
                continue;
            }
            List<Set<Category>> allCategories = getAllCategories(sentence, smallChart.getRoots());
            List<ResolvedDependency> goldDeps = getGoldDeps(sentence, smallChart, allCategories);

            for (int index = 0; index < allCategories.size(); index++) {
                for (Category category : allCategories.get(index)) {
                    final FeatureKey key = trainingParameters.getFeatureSet().lexicalCategoryFeatures.getFeatureKey(
                            sentence.getInputWords(), index, category);
                    if (key != null) {
                        keyCount.add(key);
                    }
                }
            }
            for (final DependencyStructure.ResolvedDependency dep : goldDeps) {
                final SRLFrame.SRLLabel role = dep.getSemanticRole();
                for (final ArgumentSlotFeature feature : trainingParameters.getFeatureSet().argumentSlotFeatures) {
                    final FeatureKey key = feature.getFeatureKey(
                            sentence.getInputWords(), dep.getPredicateIndex(), role,
                            dep.getCategory(), dep.getArgNumber(), dep.getPreposition());
                    keyCount.add(key);
                }
                if (dep.getPreposition() != Preposition.NONE) {
                    for (final PrepositionFeature feature : trainingParameters.getFeatureSet().prepositionFeatures) {
                        final FeatureKey key = feature.getFeatureKey(
                                sentence.getInputWords(), dep.getPredicateIndex(),
                                dep.getCategory(), dep.getPreposition(), dep.getArgNumber());
                        keyCount.add(key);
                    }
                }
                if (dep.getSemanticRole() != SRLFrame.NONE) {
                    for (final BilexicalFeature feature : trainingParameters.getFeatureSet().dependencyFeatures) {
                        final FeatureKey key = feature.getFeatureKey(
                                sentence.getInputWords(), dep.getSemanticRole(),
                                dep.getPredicateIndex(), dep.getArgumentIndex());
                        bilexicalKeyCount.add(key);
                    }
                }
            }
            /*getFromDerivation(
                    sentence.getCcgbankParse(),
                    binaryFeatureCount,
                    boundedFeatures,
                    sentence.getInputWords(),
                    0,
                    sentence.getInputWords().size());*/
            for (final Feature.RootCategoryFeature rootFeature : trainingParameters.getFeatureSet().rootFeatures) {
                for (CompressedChart.Key root : smallChart.getRoots()) {
                    Category rootCategory = root.category;
                    System.out.println(rootCategory);
                    final FeatureKey key = rootFeature.getFeatureKey(rootCategory, sentence.getInputWords());
                    boundedFeatures.add(key);
                    keyCount.add(key);
                }
            }
        }
        result.put(trainingParameters.getFeatureSet().lexicalCategoryFeatures.getDefault(), result.size());
        addFrequentFeatures(minimumFeatureFrequency, keyCount, result, boundedFeatures, false);
        addFrequentFeatures(minimumFeatureFrequency, bilexicalKeyCount, result, boundedFeatures, false);
        for (final Feature.BinaryFeature feature : trainingParameters.getFeatureSet().binaryFeatures) {
            boundedFeatures.add(feature.getDefault());
        }
        for (final Feature feature : trainingParameters.getFeatureSet().getAllFeatures()) {
            if (!result.containsKey(feature.getDefault())) {
                result.put(feature.getDefault(), result.size());
            }
        }
        System.out.println("Total features: " + result.size());
        return result;
    }

    private void getFromDerivation(final SyntaxTreeNode node, final Multiset<Feature.FeatureKey> binaryFeatureCount,
                                   final Set<Feature.FeatureKey> boundedFeatures,
                                   final List<InputReader.InputWord> words, final int startIndex, final int endIndex) {
        if (node.getChildren().size() == 2) {
            final SyntaxTreeNode left = node.getChild(0);
            final SyntaxTreeNode right = node.getChild(1);
            for (final Combinator.RuleProduction rule :
                    Combinator.getRules(left.getCategory(), right.getCategory(), Combinator.STANDARD_COMBINATORS)) {
                if (rule.getCategory().equals(node.getCategory())) {
                    for (final Feature.BinaryFeature feature : trainingParameters.getFeatureSet().binaryFeatures) {
                        final Feature.FeatureKey featureKey = feature.getFeatureKey(node.getCategory(), node.getRuleType(),
                                left.getCategory(), left.getRuleType().getNormalFormClassForRule(), 0,
                                right.getCategory(), right.getRuleType().getNormalFormClassForRule(), 0, null);
                        binaryFeatureCount.add(featureKey);
                    }
                }
            }
        }
        if (node.getChildren().size() == 1) {
            for (final AbstractParser.UnaryRule rule : dataParameters.getUnaryRules().values()) {
                for (final Feature.UnaryRuleFeature feature : trainingParameters.getFeatureSet().unaryRuleFeatures) {
                    final Feature.FeatureKey key = feature.getFeatureKey(rule.getID(), words, startIndex, endIndex);
                    binaryFeatureCount.add(key);
                }
            }
            Util.debugHook();
        }
        int start = startIndex;
        for (final SyntaxTreeNode child : node.getChildren()) {
            final int end = start + child.getLength();
            getFromDerivation(child, binaryFeatureCount, boundedFeatures, words, start, end);
            start = end;
        }
    }

    private void addFrequentFeatures(final int minCount, final Multiset<Feature.FeatureKey> keyCount,
                                     final Map<Feature.FeatureKey, Integer> result,
                                     final Set<Feature.FeatureKey> boundedFeatures, final boolean upperBound0) {
        for (final com.google.common.collect.Multiset.Entry<Feature.FeatureKey> entry : keyCount.entrySet()) {
            if (entry.getCount() >= minCount) {
                result.put(entry.getElement(), result.size());
                if (upperBound0) {
                    boundedFeatures.add(entry.getElement());
                }
            }
        }
    }

    /**
     * Used for identifying features that occur in positive examples.
     * Compares labels gold-standard CCGbank dependencies with SRL labels.
     */
    static List<DependencyStructure.ResolvedDependency> getGoldDeps(final ParallelCorpusReader.Sentence sentence) {
        final List<Category> goldCategories = sentence.getLexicalCategories();
        final List<DependencyStructure.ResolvedDependency> goldDeps = new ArrayList<>();
        final Set<CCGBankDependencies.CCGBankDependency> unlabelledDeps =
                new HashSet<>(sentence.getCCGBankDependencyParse().getDependencies());
        for (final Map.Entry<SRLDependency, CCGBankDependencies.CCGBankDependency> dep :
                sentence.getCorrespondingCCGBankDependencies().entrySet()) {
            final CCGBankDependencies.CCGBankDependency ccgbankDep = dep.getValue();
            if (ccgbankDep == null) {
                continue;
            }
            final Category goldCategory = goldCategories.get(ccgbankDep.getSentencePositionOfPredicate());
            if (ccgbankDep.getArgNumber() > goldCategory.getNumberOfArguments()) {
                // SRL_rebank categories are out of sync with Rebank deps
                continue;
            }
            goldDeps.add(new DependencyStructure.ResolvedDependency(
                    ccgbankDep.getSentencePositionOfPredicate(), goldCategory, ccgbankDep
                    .getArgNumber(), ccgbankDep.getSentencePositionOfArgument(), dep.getKey().getLabel(), Preposition
                    .fromString(dep.getKey().getPreposition())));
            unlabelledDeps.remove(ccgbankDep);
        }

        for (final CCGBankDependencies.CCGBankDependency dep : unlabelledDeps) {
            final Category goldCategory = goldCategories.get(dep.getSentencePositionOfPredicate());
            if (dep.getArgNumber() > goldCategory.getNumberOfArguments()) {
                // SRL_rebank categories are out of sync with Rebank deps
                continue;
            }

            Preposition preposition = Preposition.NONE;
            /*
            if (dep.getCategory().getArgument(dep.getArgNumber()) == Category.PP) {
                // If appropriate, figure out what the preposition should be.
                preposition = Preposition.OTHER;
                for (final CCGBankDependency prepDep : unlabelledDeps) {
                    if (prepDep != dep &&
                            prepDep.getSentencePositionOfArgument() == dep.getSentencePositionOfArgument() &&
                            Preposition.isPrepositionCategory(prepDep.getCategory())) {
                        preposition = Preposition.fromString(dep.getPredicateWord());
                    }
                }
            } else {
                preposition = Preposition.NONE;
            }
            */
            goldDeps.add(new DependencyStructure.ResolvedDependency(
                        dep.getSentencePositionOfPredicate(), goldCategory, dep.getArgNumber(),
                        dep.getSentencePositionOfArgument(), SRLFrame.NONE, preposition));
        }
        return goldDeps;
    }

    /**
     * QA stuff ...
     */
/*
    private void getFromDerivation(final CompressedChart.Key key,
                                   final Multiset<Feature.FeatureKey> binaryFeatureCount,
                                   final Set<Feature.FeatureKey> boundedFeatures,
                                   final List<InputReader.InputWord> words, final int startIndex, final int endIndex) {
        if (key.getChildren().size() == 2) {
            final SyntaxTreeNode left = key.
            final SyntaxTreeNode right = node.getChild(1);
            for (final Combinator.RuleProduction rule :
                    Combinator.getRules(left.getCategory(), right.getCategory(), Combinator.STANDARD_COMBINATORS)) {
                if (rule.getCategory().equals(key.category)) {
                    for (final Feature.BinaryFeature feature : trainingParameters.getFeatureSet().binaryFeatures) {
                        final Feature.FeatureKey featureKey = feature.getFeatureKey(
                                node.getCategory(), node.getRuleType(),
                                left.getCategory(), left.getRuleType().getNormalFormClassForRule(), 0,
                                right.getCategory(), right.getRuleType().getNormalFormClassForRule(), 0, null);
                        binaryFeatureCount.add(featureKey);
                    }
                }
            }
        }
        if (node.getChildren().size() == 1) {
            for (final AbstractParser.UnaryRule rule : dataParameters.getUnaryRules().values()) {
                for (final Feature.UnaryRuleFeature feature : trainingParameters.getFeatureSet().unaryRuleFeatures) {
                    final Feature.FeatureKey key = feature.getFeatureKey(rule.getID(), words, startIndex, endIndex);
                    binaryFeatureCount.add(key);
                }
            }
            Util.debugHook();
        }
        int start = startIndex;
        for (final SyntaxTreeNode child : node.getChildren()) {
            final int end = start + child.getLength();
            getFromDerivation(child, binaryFeatureCount, boundedFeatures, words, start, end);
            start = end;
        }
    }
*/

    static List<DependencyStructure.ResolvedDependency> getGoldDeps(
            final QASentence sentence,
            final CompressedChart smallChart,
            final List<Set<Category>> allCategories) {
        final List<DependencyStructure.ResolvedDependency> goldDeps = new ArrayList<>();
        Set<ResolvedDependency> ccgDependencies = smallChart.getAllDependencies();
        Collection<QADependency> qaDependencies = sentence.getDependencies();
        for (ResolvedDependency dep : ccgDependencies) {
            List<QADependency> matchedQA = new ArrayList<>();
            for (QADependency qa : qaDependencies) {
                if (qa.getPredicateIndex() == dep.getPredicateIndex() &&
                        qa.getAnswerPositions().contains(dep.getArgumentIndex())) {
                    matchedQA.add(qa);
                }
            }
            final Set<Category> predCategories = allCategories.get(dep.getPredicateIndex());
            for (Category category : predCategories) {
                if (dep.getArgNumber() > category.getNumberOfArguments()) {
                    continue;
                }
                for (QADependency qa : matchedQA) {
                    goldDeps.add(new ResolvedDependency(
                            dep.getPredicateIndex(),
                            category,
                            dep.getArgNumber(),
                            dep.getArgumentIndex(),
                            qa.getLabel(),
                            Preposition.fromString(qa.getPreposition())));
                }
                final Preposition preposition = Preposition.NONE;
                goldDeps.add(new ResolvedDependency(
                        dep.getPredicateIndex(),
                        category,
                        dep.getArgNumber(),
                        dep.getArgumentIndex(),
                        SRLFrame.NONE,
                        preposition));
            }
        }
        return goldDeps;
    }
}
