package edu.uw.easysrl.syntax.training;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.feature.*;
import edu.uw.easysrl.syntax.model.feature.Feature.FeatureKey;
import edu.uw.easysrl.syntax.parser.AbstractParser;
import edu.uw.easysrl.util.Util;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class TrainingFeatureHelper {
    private final TrainingParameters trainingParameters;
    private final TrainingDataParameters dataParameters;

    public TrainingFeatureHelper(TrainingParameters trainingParameters, TrainingDataParameters dataParameters) {
        this.trainingParameters = trainingParameters;
        this.dataParameters = dataParameters;
    }

    public Map<FeatureKey, Integer> makeKeyToIndexMap(
            final int minimumFeatureFrequency,
            final Set<FeatureKey> boundedFeatures,
            List<ParallelCorpusReader.Sentence> trainingSentences) throws IOException {
        final Multiset<FeatureKey> keyCount = HashMultiset.create();
        final Multiset<FeatureKey> bilexicalKeyCount = HashMultiset.create();
        final Multiset<FeatureKey> binaryFeatureCount = HashMultiset.create();
        final Map<FeatureKey, Integer> result = new HashMap<>();
        final FeatureSet featureSet = trainingParameters.getFeatureSet();
        for (final ParallelCorpusReader.Sentence sentence : trainingSentences) {
            final List<ResolvedDependency> goldDeps = getGoldDeps(sentence);
            final List<Category> cats = sentence.getLexicalCategories();
            for (int i = 0; i < cats.size(); i++) {
                final Feature.FeatureKey key = featureSet.lexicalCategoryFeatures.getFeatureKey(
                        sentence.getInputWords(), i, cats.get(i));
                if (key != null) {
                    keyCount.add(key);
                }
            }
            for (final ResolvedDependency dep : goldDeps) {
                final SRLFrame.SRLLabel role = dep.getSemanticRole();
                for (final ArgumentSlotFeature feature : featureSet.argumentSlotFeatures) {
                    final Feature.FeatureKey key = feature.getFeatureKey(sentence.getInputWords(),
                            dep.getHead(), role, dep.getCategory(), dep.getArgNumber(), dep.getPreposition());
                    keyCount.add(key);
                }
                if (dep.getPreposition() != Preposition.NONE) {
                    for (final PrepositionFeature feature : featureSet.prepositionFeatures) {
                        final Feature.FeatureKey key = feature.getFeatureKey(sentence.getInputWords(),
                                dep.getHead(), dep.getCategory(), dep.getPreposition(), dep.getArgNumber());
                        keyCount.add(key);
                    }
                }
                if (dep.getSemanticRole() != SRLFrame.NONE) {
                    for (final BilexicalFeature feature : featureSet.dependencyFeatures) {
                        final Feature.FeatureKey key = feature.getFeatureKey(sentence.getInputWords(),
                                dep.getSemanticRole(), dep.getHead(), dep.getArgumentIndex());
                        bilexicalKeyCount.add(key);
                    }
                }
            }
            getFromDerivation(sentence.getCcgbankParse(), binaryFeatureCount, sentence.getInputWords(), 0,
                    sentence.getInputWords().size());
            for (final Feature.RootCategoryFeature rootFeature : featureSet.rootFeatures) {
                final Feature.FeatureKey key = rootFeature.getFeatureKey(sentence.getCcgbankParse().getCategory(),
                        sentence.getInputWords());
                boundedFeatures.add(key);
                keyCount.add(key);
            }
        }
        result.put(featureSet.lexicalCategoryFeatures.getDefault(), result.size());
        addFrequentFeatures(minimumFeatureFrequency, keyCount, result, boundedFeatures, false);
        addFrequentFeatures(minimumFeatureFrequency, bilexicalKeyCount, result, boundedFeatures, false);
        featureSet.binaryFeatures.forEach(feature -> boundedFeatures.add(feature.getDefault()));
        for (final Feature feature : featureSet.getAllFeatures()) {
            if (!result.containsKey(feature.getDefault())) {
                result.put(feature.getDefault(), result.size());
            }
            feature.resetDefaultIndex();
        }
        System.out.println("Total features: " + result.size());
        return result;
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
        final FeatureSet featureSet = trainingParameters.getFeatureSet();
        final Iterator<ParallelCorpusReader.Sentence> sentenceIt = ParallelCorpusReader.READER.readCorpus(false);
        while (sentenceIt.hasNext()) {
            final ParallelCorpusReader.Sentence sentence = sentenceIt.next();
            final List<ResolvedDependency> goldDeps = getGoldDeps(sentence);
            final List<Category> cats = sentence.getLexicalCategories();
            for (int i = 0; i < cats.size(); i++) {
                final Feature.FeatureKey key = featureSet.lexicalCategoryFeatures.getFeatureKey(
                        sentence.getInputWords(), i, cats.get(i));
                if (key != null) {
                    keyCount.add(key);
                }
            }
            for (final ResolvedDependency dep : goldDeps) {
                final SRLFrame.SRLLabel role = dep.getSemanticRole();
                // if (cutoffsDictionary.isFrequentWithAnySRLLabel(
                // dep.getCategory(), dep.getArgNumber())
                // && cutoffsDictionary.isFrequent(dep.getCategory(),
                // dep.getArgNumber(), dep.getSemanticRole())) {
                for (final ArgumentSlotFeature feature : featureSet.argumentSlotFeatures) {
                    final Feature.FeatureKey key = feature.getFeatureKey(sentence.getInputWords(), dep.getHead(),
                            role, dep.getCategory(), dep.getArgNumber(), dep.getPreposition());
                    keyCount.add(key);
                }
                if (dep.getPreposition() != Preposition.NONE) {
                    for (final PrepositionFeature feature : featureSet.prepositionFeatures) {
                        final Feature.FeatureKey key = feature.getFeatureKey(sentence.getInputWords(), dep.getHead(),
                                dep.getCategory(), dep.getPreposition(), dep.getArgNumber());
                        keyCount.add(key);
                    }
                }
                // }
                if (dep.getSemanticRole() != SRLFrame.NONE) {
                    for (final BilexicalFeature feature : featureSet.dependencyFeatures) {
                        final Feature.FeatureKey key = feature.getFeatureKey(sentence.getInputWords(), dep.getSemanticRole(),
                                dep.getHead(), dep.getArgumentIndex());
                        bilexicalKeyCount.add(key);
                    }
                }

            }
            getFromDerivation(sentence.getCcgbankParse(), binaryFeatureCount, sentence.getInputWords(), 0,
                    sentence.getInputWords().size());
            for (final Feature.RootCategoryFeature rootFeature : featureSet.rootFeatures) {
                final Feature.FeatureKey key = rootFeature.getFeatureKey(sentence.getCcgbankParse().getCategory(),
                        sentence.getInputWords());
                boundedFeatures.add(key);
                keyCount.add(key);
            }
        }
        result.put(featureSet.lexicalCategoryFeatures.getDefault(), result.size());
        addFrequentFeatures(30 /* min feature frequency */, binaryFeatureCount, result, boundedFeatures, true);
        addFrequentFeatures(minimumFeatureFrequency, keyCount, result, boundedFeatures, false);
        addFrequentFeatures(minimumFeatureFrequency, bilexicalKeyCount, result, boundedFeatures, false);
        featureSet.binaryFeatures.forEach(feature -> boundedFeatures.add(feature.getDefault()));
        for (final Feature feature : featureSet.getAllFeatures()) {
            if (!result.containsKey(feature.getDefault())) {
                result.put(feature.getDefault(), result.size());
            }
            feature.resetDefaultIndex();
        }
        System.out.println("Total features: " + result.size());
        return result;
    }

    /**
     * Creates a map from (sufficiently frequent) features to integers
     */
    public Map<FeatureKey, Integer> makeKeyToIndexMap(
            Iterator<QASentence> sentenceIt,
            final int minimumFeatureFrequency,
            final Set<FeatureKey> boundedFeatures) throws IOException {
        CCGHelper ccgHelper = new CCGHelper(dataParameters, true /* backoff */);
        FeatureSet featureSet = trainingParameters.getFeatureSet();
        final Multiset<FeatureKey> keyCount = HashMultiset.create();
        final Multiset<FeatureKey> bilexicalKeyCount = HashMultiset.create();
        final Map<FeatureKey, Integer> result = new HashMap<>();
        while (sentenceIt.hasNext()) {
            final QASentence sentence = sentenceIt.next();
            final CompressedChart smallChart = ccgHelper.parseSentence(sentence.getWords());
            if (smallChart == null) {
                continue;
            }
            List<Set<Category>> allCategories = ccgHelper.getAllCategories(sentence, smallChart);
            List<ResolvedDependency> goldDeps = getGoldDeps(sentence, smallChart, allCategories);
            for (int index = 0; index < allCategories.size(); index++) {
                for (Category category : allCategories.get(index)) {
                    final FeatureKey key = featureSet.lexicalCategoryFeatures.getFeatureKey(
                            sentence.getInputWords(), index, category);
                    if (key != null) {
                        keyCount.add(key);
                    }
                }
            }
            for (final ResolvedDependency dep : goldDeps) {
                final SRLFrame.SRLLabel role = dep.getSemanticRole();
                for (final ArgumentSlotFeature feature : featureSet.argumentSlotFeatures) {
                    final FeatureKey key = feature.getFeatureKey(
                            sentence.getInputWords(), dep.getHead(), role,
                            dep.getCategory(), dep.getArgNumber(), dep.getPreposition());
                    keyCount.add(key);
                }
                if (dep.getPreposition() != Preposition.NONE) {
                    for (final PrepositionFeature feature : featureSet.prepositionFeatures) {
                        final FeatureKey key = feature.getFeatureKey(
                                sentence.getInputWords(), dep.getHead(),
                                dep.getCategory(), dep.getPreposition(), dep.getArgNumber());
                        keyCount.add(key);
                    }
                }
                if (dep.getSemanticRole() != SRLFrame.NONE) {
                    for (final BilexicalFeature feature : featureSet.dependencyFeatures) {
                        final FeatureKey key = feature.getFeatureKey(
                                sentence.getInputWords(), dep.getSemanticRole(),
                                dep.getHead(), dep.getArgumentIndex());
                        bilexicalKeyCount.add(key);
                    }
                }
            }
            for (final Feature.RootCategoryFeature rootFeature : featureSet.rootFeatures) {
                for (CompressedChart.Key root : smallChart.getRoots()) {
                    Category rootCategory = root.category;
                    final FeatureKey key = rootFeature.getFeatureKey(rootCategory, sentence.getInputWords());
                    boundedFeatures.add(key);
                    keyCount.add(key);
                }
            }
        }
        result.put(featureSet.lexicalCategoryFeatures.getDefault(), result.size());
        addFrequentFeatures(minimumFeatureFrequency, keyCount, result, boundedFeatures, false);
        addFrequentFeatures(minimumFeatureFrequency, bilexicalKeyCount, result, boundedFeatures, false);
        featureSet.binaryFeatures.forEach(feature -> boundedFeatures.add(feature.getDefault()));
        for (final Feature feature : featureSet.getAllFeatures()) {
            if (!result.containsKey(feature.getDefault())) {
                result.put(feature.getDefault(), result.size());
            }
            feature.resetDefaultIndex();
        }
        System.out.println("Total features: " + result.size());
        return result;
    }

    private void getFromDerivation(final SyntaxTreeNode node, final Multiset<Feature.FeatureKey> binaryFeatureCount,
                                   final List<InputReader.InputWord> words, final int startIndex, final int endIndex) {
        if (node.getChildren().size() == 2) {
            final SyntaxTreeNode left = node.getChild(0);
            final SyntaxTreeNode right = node.getChild(1);
            for (final Combinator.RuleProduction rule :
                    Combinator.getRules(left.getCategory(), right.getCategory(), Combinator.STANDARD_COMBINATORS)) {
                if (rule.getCategory().equals(node.getCategory())) {
                    for (final Feature.BinaryFeature feature : trainingParameters.getFeatureSet().binaryFeatures) {
                        final Feature.FeatureKey featureKey = feature.getFeatureKey(node.getCategory(),
                                node.getRuleType(), left.getCategory(), left.getRuleType().getNormalFormClassForRule(),
                                0, right.getCategory(), right.getRuleType().getNormalFormClassForRule(), 0, null);
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
            getFromDerivation(child, binaryFeatureCount, words, start, end);
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
    private List<ResolvedDependency> getGoldDeps(final ParallelCorpusReader.Sentence sentence) {
        List<Category> goldCategories = sentence.getLexicalCategories();
        List<ResolvedDependency> goldDeps = new ArrayList<>();
        Set<CCGBankDependency> unlabelledDeps = new HashSet<>(sentence.getCCGBankDependencyParse().getDependencies());
        for (final Map.Entry<SRLDependency, CCGBankDependency> dep :
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
            goldDeps.add(new ResolvedDependency(
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
            goldDeps.add(new ResolvedDependency(
                        dep.getSentencePositionOfPredicate(), goldCategory, dep.getArgNumber(),
                        dep.getSentencePositionOfArgument(), SRLFrame.NONE, preposition));
        }
        return goldDeps;
    }

    private List<ResolvedDependency> getGoldDeps(
            final QASentence sentence,
            final CompressedChart smallChart,
            final List<Set<Category>> allCategories) {
        final List<ResolvedDependency> goldDeps = new ArrayList<>();
        for (ResolvedDependency dep : smallChart.getAllDependencies()) {
            List<QADependency> matchedQA = sentence.getDependencies().stream()
                    .filter(qa -> qa.unlabeledMatch(dep))
                    .collect(Collectors.toList());
            final Set<Category> predicateCategories = allCategories.get(dep.getHead());
            for (Category category : predicateCategories) {
                if (dep.getArgNumber() > category.getNumberOfArguments()) {
                    continue;
                }
                matchedQA.stream().forEach(qa ->
                        goldDeps.add(new ResolvedDependency(dep.getHead(), category, dep.getArgNumber(),
                                dep.getArgumentIndex(), qa.getLabel(), Preposition.fromString(qa.getPreposition()))));
                goldDeps.add(new ResolvedDependency(dep.getHead(), category, dep.getArgNumber(),
                        dep.getArgumentIndex(), SRLFrame.NONE, Preposition.NONE));
            }
        }
        return goldDeps;
    }
}
