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
            case PREPOSITION: result = Preposition.getFromParse(index, this, parse); break;
            case ADVERB: result = Adverb.getFromParse(index, this, parse); break;
            case CLAUSE: result = Clause.getFromParse(index, this, parse); break;
            default: assert false; result = null;
            }
            // nulls for debugging purposes
            if(result == null) {
                System.err.println("got null result for predication type " + predType.name());
                Predication pronoun = Pronoun.fromString("something").get();
                preds.put(index, predType, pronoun);
                return pronoun;
            } else {
                preds.put(index, predType, result);
                return result;
            }
        }
    }

    public PredicateCache(Parse parse) {
        this.parse = parse;
        this.preds = HashBasedTable.create();
    }

    private final Parse parse;
    private final HashBasedTable<Integer, Predication.Type, Predication> preds;
}
