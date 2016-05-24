package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.corpora.BioinferCCGCorpus;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.util.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Created by luheng on 5/24/16.
 */
public class ParseDataLoader {

    public static Optional<ParseData> loadFromBioinferDev() {
        POSTagger postagger = POSTagger.getStanfordTagger(Util.getFile(BaseCcgParser.modelFolder + "/posTagger"));
        List<List<InputReader.InputWord>> sentenceInputWords = new ArrayList<>();
        List<Parse> goldParses = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(BioinferCCGCorpus.BioinferDevFile)));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] segments = line.split("\\s+");
                List<InputReader.InputWord> inputs = new ArrayList<>();
                List<String> words = new ArrayList<>(); //, pos = new ArrayList<>();
                List<Category> categories = new ArrayList<>();
                for (String seg : segments) {
                    String[] info = seg.split("\\|");
                    words.add(info[0]);
                    // pos.add(info[1]);
                    categories.add(Category.valueOf(info[2]));
                    inputs.add(new InputReader.InputWord(info[0], "", ""));

                }
                if (words.size() > 0) {
                    sentenceInputWords.add(ImmutableList.copyOf(postagger.tag(inputs)));
                    goldParses.add(new Parse(words, categories));
                }
            }

        } catch (IOException e) {
            return Optional.empty();
        }

        System.out.println(String.format("Read %d sentences from %s.", sentenceInputWords.size(),
                BioinferCCGCorpus.BioinferDevFile));
        return Optional.of(ParseData.makeParseData(sentenceInputWords, goldParses));
    }

}
