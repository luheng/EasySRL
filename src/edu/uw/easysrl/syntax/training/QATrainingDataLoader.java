package edu.uw.easysrl.syntax.training;

import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.AtomicDouble;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;
import edu.uw.easysrl.syntax.parser.AbstractParser;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.syntax.tagger.Tagger.ScoredCategory;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.syntax.training.CKY.ChartCell;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by luheng on 11/2/15.
 */
public class QATrainingDataLoader {
    private final CutoffsDictionary cutoffsDictionary;
    private final TrainingDataParameters dataParameters;
    private final Multimap<Category, UnaryRule> unaryRules;
    private final CKY parser;
    private final Tagger tagger;
    private final boolean backoff;
    private final POSTagger posTagger;

    public QATrainingDataLoader(final CutoffsDictionary cutoffsDictionary,
                                final TrainingDataParameters dataParameters,
                                final boolean backoff) {
        this.cutoffsDictionary = cutoffsDictionary;
        this.dataParameters = dataParameters;
        this.backoff = backoff;
        try {
            unaryRules = AbstractParser.loadUnaryRules(new File(this.dataParameters.getExistingModel(), "unaryRules"));
            DependencyStructure.parseMarkedUpFile(new File(dataParameters.getExistingModel(), "markedup"));
            // Build set of possible parses
            this.parser = new CKY(
                    dataParameters.getExistingModel(),
                    dataParameters.getMaxTrainingSentenceLength(),
                    dataParameters.getMaxChartSize());
            this.tagger = Tagger.make(
                    dataParameters.getExistingModel(),
                    dataParameters.getSupertaggerBeam(),
                    50,
                    cutoffsDictionary // null
            );
            this.posTagger = POSTagger.getStanfordTagger(new File(dataParameters.getExistingModel(), "posTagger"));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Optimization.TrainingExample> makeTrainingData(
            final Iterator<QASentence> sentences,
            final boolean singleThread) throws IOException {
        final ExecutorService executor = Executors.newFixedThreadPool(singleThread ? 1 : Runtime.getRuntime()
                .availableProcessors());
        final List<Optimization.TrainingExample> result = new ArrayList<>();
        while (sentences.hasNext()) {
            final QASentence sentence = sentences.next();
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        final Optimization.TrainingExample trainingExample = makeTrainingExample(sentence);
                        if (trainingExample != null) {
                            synchronized (result) {
                                result.add(trainingExample);
                                if (result.size() % 100 == 0) {
                                    System.out.println(String.format("Processed %d samples.\r", result.size()));
                                }
                            }
                        }
                    } catch (final Throwable t) {
                        t.printStackTrace();
                    }
                }
            });
        }
        executor.shutdown(); // always reclaim resources
        try {
            executor.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private Optimization.TrainingExample makeTrainingExample(final QASentence sentence) {
        if (sentence.getSentenceLength() > dataParameters.getMaxTrainingSentenceLength()) {
            return null;
        }
        try {
            // Build a complete chart for the training sentence.
            final AtomicDouble beta = new AtomicDouble(dataParameters.getSupertaggerBeam());
            final CompressedChart completeChart = parseSentence(sentence.getWords(), beta, Training.ROOT_CATEGORIES);
            if (completeChart == null) {
                // Unable to parse sentence
                return null;
            }
            // Build a smaller chart, which will be used for identifying positive examples.
            final CompressedChart smallChart = parseSentence(sentence.getWords(),
                    // Make sure the value of the beam is at least the value used for parsing the training charts.
                    // Otherwise, the positive chart can be a superset of the complete chart.
                    new AtomicDouble(Math.max(dataParameters.getSupertaggerBeamForGoldCharts(), beta.doubleValue())),
                    Training.ROOT_CATEGORIES);
            if (smallChart == null) {
                // Unable to parse sentence with restrictive supertagger beam.
                // TODO I guess we could try backing off here.
                return null;
            }
            // Now find the parses which are maximally consistent with the Q/A Pairs.
            final CompressedChart goldChart = new QAGoldChartFinder(smallChart).goldChart(sentence, cutoffsDictionary);
            if (goldChart == null) {
                // No matched dependencies, so we can't learn against this chart.
                return null;
            }
            final Optimization.TrainingExample ex = new Optimization.TrainingExample(completeChart, goldChart,
                    posTagger.tag(sentence.getInputWords()), cutoffsDictionary);
            return ex;
        } catch (final Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Build a chart for the sentence using the specified supertagger beam. If the chart exceeds the maximum size, beta
     * is doubled and the parser will re-try. When the function returns, beta will contain the value of the beam used
     * for the returned chart.
     */
    public CompressedChart parseSentence(final List<String> sentence, final AtomicDouble beta,
                                         final Collection<Category> rootCategories) {
        final CompressedChart compressed;
        final List<Collection<Category>> categories = new ArrayList<>();
        final List<List<ScoredCategory>> tagsForSentence = tagger.tag(InputWord.listOf(sentence));
        for (final List<ScoredCategory> tagsForWord : tagsForSentence) {
            final List<Category> tagsForWord2 = new ArrayList<>();
            final double threshold = beta.doubleValue() * Math.exp(tagsForWord.get(0).getScore());
            for (final ScoredCategory leaf : tagsForWord) {
                if (Math.exp(leaf.getScore()) < threshold) {
                    break;
                }
                tagsForWord2.add(leaf.getCategory());
            }
            categories.add(tagsForWord2);
        }
        // Find set of all parses
        final ChartCell[][] chart = parser.parse(sentence, categories);
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
        compressed = CompressedChart.make(InputWord.listOf(sentence), chart, cutoffsDictionary, unaryRules,
                rootCategories);
        return compressed;
    }
}
