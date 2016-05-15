package edu.uw.easysrl.qasrl.qg.util;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.Parse;
import static edu.uw.easysrl.util.GuavaCollectors.*;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;

public final class PredicateCache {

    public Predication getPredication(int index, Predication.Type predType) {
        if(preds.contains(index, predType)) {
            return preds.get(index, predType);
        } else {
            final Predication result;
            switch(predType) {
            case VERB:
                // one day, I hope to fix the issues with auxiliaries... :(
                // final ImmutableList<String> words = parse.syntaxTree.getLeaves().stream()
                //     .map(leaf -> leaf.getWord())
                //     .collect(toImmutableList());
                // if(VerbHelper.isAuxiliaryVerb(words.get(index), parse.categories.get(index))) {
                //     int curIndex = index,
                //         lastAuxIndex = index;
                //     while(curIndex < words.size() &&
                //           (VerbHelper.isAuxiliaryVerb(words.get(curIndex), parse.categories.get(curIndex)) ||
                //            VerbHelper.isNegationWord(words.get(curIndex)) ||
                //            parse.categories.get(curIndex).isFunctionInto(Category.ADVERB))) {
                //         if(VerbHelper.isAuxiliaryVerb(words.get(curIndex), parse.categories.get(curIndex))) {
                //             lastAuxIndex = curIndex;
                //         }
                //         curIndex++;
                //     }
                //     final String pos = parse.syntaxTree.getLeaves().get(curIndex).getPos();
                //     if(pos.equals("VB") || pos.equals("VBD") || pos.equals("VBG") || pos.equals("VBN") || pos.equals("VBP") || pos.equals("VBZ")) {
                //         // if we discovered a verb by moving forward from the auxiliary we were at, great! use that instead.
                //         result = Verb.getFromParse(curIndex, this, parse);
                //     } else {
                //         // otherwise fall back to the last auxiliary we saw---this may be what happens when "be" or "do" is the main verb.
                //         result = Verb.getFromParse(lastAuxIndex, this, parse);
                //     }
                // } else {
                //     result = Verb.getFromParse(index, this, parse);
                // }
                result = Verb.getFromParse(index, this, parse);
                break;
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
