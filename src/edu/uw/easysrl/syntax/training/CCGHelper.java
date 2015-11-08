package edu.uw.easysrl.syntax.training;

import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.AtomicDouble;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.parser.AbstractParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by luheng on 11/6/15.
 */
public class CCGHelper {
    private final TrainingDataParameters dataParameters;
    private final Multimap<Category, AbstractParser.UnaryRule> unaryRules;
    private final CKY parser;
    private final Tagger tagger;
    private final boolean backoff;
    //private final POSTagger posTagger;

    public CCGHelper(final TrainingDataParameters dataParameters, final boolean backoff) {
        this.dataParameters = dataParameters;
        this.backoff = backoff;
        try {
            unaryRules = AbstractParser.loadUnaryRules(new File(this.dataParameters.getExistingModel(), "unaryRules"));
            DependencyStructure.parseMarkedUpFile(new File(dataParameters.getExistingModel(), "markedup"));
            this.parser = new CKY(
                    dataParameters.getExistingModel(),
                    dataParameters.getMaxTrainingSentenceLength(),
                    dataParameters.getMaxChartSize());
            this.tagger = new TaggerEmbeddings(
                    dataParameters.getExistingModel(),
                    dataParameters.getSupertaggerBeam(),
                    50,
                    null /* cutoffs dictionary */);
            //this.posTagger = POSTagger.getStanfordTagger(new File(dataParameters.getExistingModel(), "posTagger"));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public CompressedChart parseSentence(final List<String> sentence) {
        final AtomicDouble beta = new AtomicDouble(dataParameters.getSupertaggerBeam());
        return parseSentence(sentence,
                new AtomicDouble(Math.max(dataParameters.getSupertaggerBeamForGoldCharts(), beta.doubleValue())),
                Training.ROOT_CATEGORIES);
    }

    private CompressedChart parseSentence(final List<String> sentence, AtomicDouble beta,
                                          final Collection<Category> rootCategories) {
        final CompressedChart compressed;
        final List<Collection<Category>> categories = new ArrayList<>();
        final List<List<TaggerEmbeddings.ScoredCategory>> tagsForSentence = tagger.tag(InputWord.listOf(sentence));
        for (final List<TaggerEmbeddings.ScoredCategory> tagsForWord : tagsForSentence) {
            final List<Category> tagsForWord2 = new ArrayList<>();
            final double threshold = beta.doubleValue() * Math.exp(tagsForWord.get(0).getScore());
            for (final TaggerEmbeddings.ScoredCategory leaf : tagsForWord) {
                if (Math.exp(leaf.getScore()) < threshold) {
                    break;
                }
                tagsForWord2.add(leaf.getCategory());
            }
            categories.add(tagsForWord2);
        }
        // Find set of all parses
        final CKY.ChartCell[][] chart = parser.parse(sentence, categories);
        if (chart == null) {
            if (beta.doubleValue() * 2 < 0.1 && backoff) {
                beta.set(beta.doubleValue() * 2);
                return parseSentence(sentence, beta, rootCategories);
            } else {
                return null;
            }
        }
        if (chart[0][chart.length - 1] == null || chart[0][chart.length - 1].getEntries().size() == 0) {
            return null;
        }
        compressed = CompressedChart.make(InputWord.listOf(sentence), chart, null /* cutoffsDictionary */, unaryRules,
                rootCategories);
        return compressed;
    }

    public List<Set<Category>> getAllCategories(QASentence sentence, CompressedChart smallChart) {
        Deque<CompressedChart.Key> cache = new ArrayDeque<>();
        List<Set<Category>> result = new ArrayList<>();
        for (int i = 0; i < sentence.getSentenceLength(); i++) {
            result.add(new HashSet<>());
        }
        cache.addAll(smallChart.getRoots());
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
                    e.printStackTrace();
                }
                cache.addAll(value.getChildren());
            }
        }
        return result;
    }

    public static boolean undirectDependencyMatch(DependencyStructure.ResolvedDependency ccgDep, QADependency qaDep) {
        return ((ccgDep.getPredicateIndex() == qaDep.getPredicateIndex() &&
                        qaDep.getAnswerPositions().contains(ccgDep.getArgumentIndex())) ||
                (ccgDep.getArgumentIndex() == qaDep.getPredicateIndex() &&
                        qaDep.getAnswerPositions().contains(ccgDep.getArgumentIndex())));
    }

}
