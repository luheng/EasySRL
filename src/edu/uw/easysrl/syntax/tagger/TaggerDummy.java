package edu.uw.easysrl.syntax.tagger;

import com.google.common.collect.Ordering;
import edu.stanford.nlp.util.StringUtils;
import edu.uw.deeptagger.DeepTagger;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;
import edu.uw.easysrl.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 12/21/15.
 */
public class TaggerDummy extends Tagger {

    private Map<String, List<List<ScoredCategory>>> taggedSentences;

    public TaggerDummy(final File modelFolder, final double beta, final int maxTagsPerWord,
                       final CutoffsDictionary cutoffs) throws IOException {
        super(cutoffs, beta, TaggerEmbeddings.loadCategories(new File(modelFolder, "categories")), maxTagsPerWord);
        taggedSentences = new HashMap<>();
    }

    @Override
    public List<List<ScoredCategory>> tag(final List<InputReader.InputWord> words) {
        throw new RuntimeException("this is not in my job description");
        /*
        final List<String> input = words.stream().map(x -> x.word).collect(Collectors.toList());
        String sentenceKey = getSentenceKey(input);

        List<List<ScoredCategory>> tagged = taggedSentences.get(sentenceKey);
        if (tagged == null) {
            throw new RuntimeException("sentence is not tagged.");
        }
        return tagged;
        */
    }

    @Override
    public Map<Category, Double> getCategoryScores(final List<InputReader.InputWord> sentence, final int wordIndex,
                                                   final double weight, final Collection<Category> categories) {
        throw new RuntimeException("TODO");
    }

    private String getSentenceKey(List<String> words) {
        return StringUtils.join(words, " ");
    }
}
