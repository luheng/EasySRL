package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.BaseCcgParser;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.qg.IQuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QAPairAggregators;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryGenerators;
import edu.uw.easysrl.qasrl.query.ScoredQuery;

import java.util.List;

/**
 * Not doing anything other than printing the queries.
 * Created by luheng on 3/18/16.
 */
public class RefactorExperiment {
    private static int nBest = 100;

    public static void main(String[] args) {
        final ParseData devData = ParseData.loadFromDevPool().get();
        final ImmutableList<ImmutableList<String>> sentences = devData.getSentences();
        final ImmutableList<Parse> goldParses = devData.getGoldParses();
        final int numSentences = goldParses.size();
        System.out.println(String.format("Read %d sentences from the dev set.", sentences.size()));

        String preparsedFile = "parses.100best.out";
        BaseCcgParser parser = new BaseCcgParser.MockParser(preparsedFile, nBest);
        System.err.println("Parse initialized.");

        // Initialize parses.
        for (int i = 0; i < numSentences; i++) {
            ImmutableList<InputReader.InputWord> inputSentence = devData.getSentenceInputWords().get(i);
            ImmutableList<String> sentence = sentences.get(i);
            List<Parse> nbestParses = parser.parseNBest(i, inputSentence);
            if (nbestParses == null) {
                continue;
            }
            NBestList nBestList = new NBestList(ImmutableList.copyOf(nbestParses));
            ImmutableList<IQuestionAnswerPair> qaPairs1 = QuestionGenerator
                    .generateAllQAPairs(i, sentences.get(i), nBestList);

            //ImmutableList<QAStructureSurfaceForm> qaPairs2 = QAPairAggregators.aggregateForMultipleChoiceQA().aggregate(qaPairs1);
            //ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList = QueryGenerators.checkboxQueryAggregator().generate(qaPairs2);

            ImmutableList<QAStructureSurfaceForm> qaPairs2 = QAPairAggregators.aggregateForSingleChoiceQA().aggregate(qaPairs1);
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList = QueryGenerators.radioButtonQueryAggregator().generate(qaPairs2);

            queryList.forEach(query -> query.computeScores(nBestList));
            queryList.forEach(query -> {
                System.out.println(query.toString(sentence) + "\n");
            });

        }
    }
}
