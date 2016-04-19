package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.HashMap;

public final class PredicateCache {

    public Noun getNoun(int index) {
        if(nouns.hasKey(index)) {
            return nouns.get(index);
        } else {
            Noun result = Noun.getFromParse(index, parse);
            nouns.put(index, result);
            return result;
        }
    }

    public Verb getVerb(int index) {
        if(verbs.hasKey(index)) {
            return verbs.get(index);
        } else {
            // assuming things don't cycle
            Verb result = Verb.getFromParse(index, this, parse);
            verbs.put(index, result);
            return result;
        }
    }

    public PredicateCache(Parse parse) {
        this.parse = parse;
        nouns = new HashMap<Integer, Noun>();
        verbs = new HashMap<Integer, Verb>();
    }

    private final Parse parse;
    private final Map<Integer, Noun> nouns;
    private final Map<Integer, Verb> verbs;
}
