package edu.uw.easysrl.syntax.training;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.dependencies.DependencyStructure;
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
    private TrainingDataLoader.TrainingDataParameters dataParameters;

    public TrainingFeatureHelper(TrainingParameters trainingParameters,
                                 TrainingDataLoader.TrainingDataParameters dataParameters) {
        this.trainingParameters = trainingParameters;
        this.dataParameters = dataParameters;
    }
    /**
     * Creates a map from (sufficiently frequent) features to integers
     */
    public Map<Feature.FeatureKey, Integer> makeKeyToIndexMap(
            final int minimumFeatureFrequency, final Set<Feature.FeatureKey> boundedFeatures) throws IOException {
        final Multiset<Feature.FeatureKey> keyCount = HashMultiset.create();
        final Multiset<Feature.FeatureKey> bilexicalKeyCount = HashMultiset.create();
        final Map<Feature.FeatureKey, Integer> result = new HashMap<>();
        final Multiset<Feature.FeatureKey> binaryFeatureCount = HashMultiset.create();
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

    private void getFromDerivation(final SyntaxTreeNode node, final Multiset<Feature.FeatureKey> binaryFeatureCount,
                                   final Set<Feature.FeatureKey> boundedFeatures,
                                   final List<InputReader.InputWord> words, final int startIndex, final int endIndex) {
        if (node.getChildren().size() == 2) {
            final SyntaxTreeNode left = node.getChild(0);
            final SyntaxTreeNode right = node.getChild(1);

            for (final Combinator.RuleProduction rule : Combinator.getRules(left.getCategory(), right.getCategory(),
                    Combinator.STANDARD_COMBINATORS)) {
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

            final Preposition preposition = Preposition.NONE;
            // if (dep.getCategory().getArgument(dep.getArgNumber()) ==
            // Category.PP) {
            // // If appropriate, figure out what the preposition should be.
            // preposition = Preposition.OTHER;
            //
            // for (final CCGBankDependency prepDep : unlabelledDeps) {
            // if (prepDep != dep
            // && prepDep.getSentencePositionOfArgument() == dep
            // .getSentencePositionOfArgument()
            // && Preposition.isPrepositionCategory(prepDep
            // .getCategory())) {
            // preposition = Preposition.fromString(dep
            // .getPredicateWord());
            // }
            // }
            // } else {
            // preposition = Preposition.NONE;
            // }

            goldDeps.add(new DependencyStructure.ResolvedDependency(
                    dep.getSentencePositionOfPredicate(), goldCategory, dep.getArgNumber(),
                    dep.getSentencePositionOfArgument(), SRLFrame.NONE, preposition));
        }

        return goldDeps;
    }

}
