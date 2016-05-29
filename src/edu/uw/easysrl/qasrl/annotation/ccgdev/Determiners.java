package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.ParseDataLoader;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by luheng on 5/28/16.
 */
public class Determiners {
    public static ImmutableList<String> determinerList = ImmutableList.of(
            "your", "dozen", "these", "that", "either", "his", "her", "few",
            "those", "all", "this", "its", "my", "an", "each", "both",
            "some", "no", "another",
            "their",
            "our",
            "every",
            "a",
            "any",
            "the",
            "neither"
    );


    public static void main(String[] args) {
        ParseData dev = ParseDataLoader.loadFromTrainingPool().get();
        Multiset<String> determiners = HashMultiset.create();
        for (int i = 0; i < dev.getSentences().size(); i++) {
            Parse gold = dev.getGoldParses().get(i);
            gold.syntaxTree.getLeaves().stream()
                .filter(l -> l.getCategory() == Category.valueOf("NP[nb]/N"))
                    .forEach(l -> determiners.add(l.getWord().toLowerCase()));
        }
        determiners.elementSet().stream().filter(d -> determiners.count(d) >= 10)
                .forEach(d -> System.out.println(String.format("\"%s\",", d)));

    }
}
