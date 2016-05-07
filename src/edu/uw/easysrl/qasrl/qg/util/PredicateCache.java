package edu.uw.easysrl.qasrl.qg.util;

import edu.uw.easysrl.qasrl.Parse;
import com.google.common.collect.HashBasedTable;

public final class PredicateCache {

    public Predication getPredication(int index, Predication.Type predType) {
        if(preds.contains(index, predType)) {
            return preds.get(index, predType);
        } else {
            final Predication result;
            switch(predType) {
            case VERB: result = Verb.getFromParse(index, this, parse); break;
            case NOUN: result = Noun.getFromParse(index, parse); break;
            default: assert false; //result = null;
            }
            preds.put(index, predType, result);
            result.resolveArguments();
            return result;
        }
    }

    public PredicateCache(Parse parse) {
        this.parse = parse;
        this.preds = new HashBasedTable<>();
    }

    private final Parse parse;
    private final HashBasedTable<Integer, Predication.Type, Predication> preds;
}
