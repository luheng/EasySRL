package edu.uw.easysrl.qasrl.qg.util;

import edu.uw.easysrl.qasrl.Parse;
import com.google.common.collect.HashBasedTable;

public final class PredicateCache {

    public Noun getNoun(int index) {
        return (Noun) getPredication(index, Predication.Type.NOUN);
    }

    public Verb getVerb(int index) {
        return (Verb) getPredication(index, Predication.Type.VERB);
    }

    public Predication getPredication(int index, Predication.Type predType) {
        if(preds.contains(index, predType)) {
            return preds.get(index, predType);
        } else {
            final Predication result;
            switch(predType) {
            case VERB: result = Verb.getFromParse(index, this, parse); break;
            case NOUN: result = Noun.getFromParse(index, parse); break;
            default: assert false; result = null;
            }
            preds.put(index, predType, result);
            return result;
        }
    }

    public PredicateCache(Parse parse) {
        this.parse = parse;
        this.preds = HashBasedTable.create();
    }

    private final Parse parse;
    private final HashBasedTable<Integer, Predication.Type, Predication> preds;
}
