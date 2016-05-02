package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.HashMap;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.Parse;

public final class PredicateCache {

    public Noun getNoun(int index) {
        return (Noun) getPredication(index, Category.valueOf("NP"));
    }

    public Verb getVerb(int index) {
        return (Verb) getPredication(index, Category.valueOf("S\\NP"));
    }

    public Predication getPredication(int index, Category category) {
        if(!preds.containsKey(index)) {
            preds.put(index, new HashMap<>());
        }
        Map<Category, Predication> predsForIndex = preds.get(index);
        if(predsForIndex.containsKey(category)) {
            return predsForIndex.get(category);
        } else {
            Predication result = Predication.getFromParse(index, category, this, parse);
            predsForIndex.put(category, result);
            return result;
        }
    }

    public PredicateCache(Parse parse) {
        this.parse = parse;
        // nouns = new HashMap<Integer, Noun>();
        // verbs = new HashMap<Integer, Verb>();
        preds = new HashMap<>();
    }

    private final Parse parse;
    // note: pred category of any value pred equals the key you got it with
    private final Map<Integer, Map<Category, Predication>> preds;
    // private final Map<Integer, Noun> nouns;
    // private final Map<Integer, Verb> verbs;
}
