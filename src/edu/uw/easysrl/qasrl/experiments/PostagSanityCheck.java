package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.ParseDataLoader;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;

import java.util.Map;

/**
 * Created by luheng on 5/23/16.
 */
public class PostagSanityCheck {

    public static void main(String[] args) {
        final int nBest = 100;
        final ParseData parseData = ParseDataLoader.loadFromDevPool().get();
        final ImmutableList<ImmutableList<InputReader.InputWord>> inputSentences = parseData.getSentenceInputWords();
        final ImmutableList<Parse> goldParses = parseData.getGoldParses();
        System.out.println(String.format("Read %d sentences from the dev set.", inputSentences.size()));

        final Map<Integer, NBestList> nbestLists = NBestList.loadNBestListsFromFile("parses.dev.100best.out", nBest).get();
        System.out.println(String.format("Load pre-parsed %d-best lists for %d sentences.", nBest, nbestLists.size()));
        final Map<Integer, NBestList> nbestListsTagged = NBestList.loadNBestListsFromFile("parses.tagged.dev.100best.out", nBest).get();
        System.out.println(String.format("Load pre-parsed %d-best lists for %d sentences.", nBest, nbestListsTagged.size()));

        int numCorrectPostags = 0, numTotal = 0;

        // Compute postagging accuracy: parseData against nBestLists1, nBestLists2 against nBestLists1.
        for (int sid : nbestLists.keySet()) {
            if (!nbestListsTagged.containsKey(sid)) {
                System.err.println("Skipping sentence:\t" + sid);
                continue;
            }
            Parse parse = nbestLists.get(sid).getParse(0), taggedParse = nbestListsTagged.get(sid).getParse(0);
            for (int i = 0; i < parse.syntaxTree.getLeaves().size(); i++) {
                SyntaxTreeNode.SyntaxTreeNodeLeaf leaf1 = parse.syntaxTree.getLeaves().get(i);
                SyntaxTreeNode.SyntaxTreeNodeLeaf leaf2 = taggedParse.syntaxTree.getLeaves().get(i);
                if (!leaf2.getPos().equals(leaf1.getPos())) {
                  //  System.err.println(leaf1.getWord() + "\t" + leaf1.getPos() + "\t" + leaf2.getPos());
                } else {
                    ++numCorrectPostags;
                }
                ++numTotal;
            }
        }
        System.out.println("pos accuracy:\t" + 100.0 * numCorrectPostags / numTotal);

        numCorrectPostags = 0;
        numTotal = 0;
        for (int sid : nbestLists.keySet()) {
            Parse parse = nbestLists.get(sid).getParse(0);
            for (int i = 0; i < parse.syntaxTree.getLeaves().size(); i++) {
                SyntaxTreeNode.SyntaxTreeNodeLeaf leaf1 = parse.syntaxTree.getLeaves().get(i);
                InputReader.InputWord word = parseData.getSentenceInputWords().get(sid).get(i);
                if (!word.pos.equals(leaf1.getPos())) {
                //    System.err.println(leaf1.getWord() + "\t" + leaf1.getPos() + "\t" + word.pos);
                } else {
                    ++numCorrectPostags;
                }
                ++numTotal;
            }
        }
        System.out.println("pos accuracy:\t" + 100.0 * numCorrectPostags / numTotal);
    }
}
