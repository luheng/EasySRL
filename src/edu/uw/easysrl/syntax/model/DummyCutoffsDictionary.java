package edu.uw.easysrl.syntax.model;

import edu.uw.easysrl.dependencies.QALabels;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;

import java.util.*;

/**
 * Created by luheng on 11/3/15.
 */
public class DummyCutoffsDictionary extends CutoffsDictionary {
    private static HashMap<String, SRLFrame.SRLLabel> qaLabels;

    public DummyCutoffsDictionary(final Collection<Category> lexicalCategories,
                                  final Map<String, Collection<Category>> tagDict,
                                  final int maxDependencyLength) {
        super(lexicalCategories, tagDict, maxDependencyLength);
    }

    @Override
    protected void make() {
    }

    // TODO: I wanted to put this in make(), but then I got a ConcurrentModificationException. Why?
    private void cacheQALabels() {
        qaLabels = new HashMap<>();
        for (SRLFrame.SRLLabel label : SRLFrame.getAllSrlLabels()) {
            if (QALabels.isQALabel(label)) {
                System.out.println("[qa label]\t" + label);
                qaLabels.put(label.toString(), label);
            }
        }
    }

    @Override
    public Collection<Category> getCategoriesForWord(final String word) {
        return lexicalCategories;
    }

    @Override
    public Collection<SRLFrame.SRLLabel> getRoles(final String word, final Category category,
                                                  final Preposition preposition,
                                                  final int argumentNumber) {
        if (qaLabels == null) {
            cacheQALabels();
        }
        return qaLabels.values();
    }

    @Override
    public boolean isFrequentWithAnySRLLabel(final Category category, final int argumentNumber) {
        // If this is a verb ...
        return category.isFunctionInto(Category.valueOf("S|NP")) || category.isFunctionInto(Category.valueOf("S|S"));
    }

    @Override
    public boolean isFrequent(final Category category, final int argumentNumber, final SRLFrame.SRLLabel label) {
        // If this is a verb ...
        if (qaLabels == null) {
            cacheQALabels();
        }
        return //qaLabels.containsKey(label.toString()) &&
                (category.isFunctionInto(Category.valueOf("S|NP")) || category.isFunctionInto(Category.valueOf("S|S")));
    }

    @Override
    public boolean isFrequent(final SRLFrame.SRLLabel label, final int offset) {
        if (qaLabels == null) {
            cacheQALabels();
        }
        return qaLabels.containsKey(label.toString());
    }

    @Override
    public Map<String, Collection<Category>> getTagDict() {
        return wordToCategory;
    }
}

