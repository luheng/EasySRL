package edu.uw.easysrl.syntax.model;

import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;

import java.util.*;

/**
 * Created by luheng on 11/3/15.
 */
public class DummyCutoffsDictionary extends CutoffsDictionary {
    public DummyCutoffsDictionary(final Collection<Category> lexicalCategories,
                                  final Map<String, Collection<Category>> tagDict,
                                  final int maxDependencyLength) {
        super(lexicalCategories, tagDict, maxDependencyLength);
    }

    public Collection<Category> getCategoriesForWord(final String word) {
        return lexicalCategories;
    }

    public Collection<SRLFrame.SRLLabel> getRoles(final String word, final Category category,
                                                  final Preposition preposition,
                                                  final int argumentNumber) {
        return SRLFrame.getAllSrlLabels();
    }

    public boolean isFrequentWithAnySRLLabel(final Category category, final int argumentNumber) {
        return true;
    }

    public boolean isFrequent(final Category category, final int argumentNumber, final SRLFrame.SRLLabel label) {
        return true;
    }

    public boolean isFrequent(final SRLFrame.SRLLabel label, final int offset) {
        return true;
    }

    public Map<String, Collection<Category>> getTagDict() {
        return wordToCategory;
    }
}

