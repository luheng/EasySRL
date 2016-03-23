package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.BaseCcgParser;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QAPairAggregators;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryGenerators;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;

import java.util.List;
import java.util.Map;

/**
 * Output jeopardy queries.
 * Created by luheng on 3/22/16.
 */
public class JeopardyQueryExperiment {
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
        final ImmutableMap<Integer, NBestList> nbestLists = NBestList.getAllNBestLists(parser, devData.getSentenceInputWords());

        QueryPruningParameters queryPruningParameters = new QueryPruningParameters();

        // TODO: test gold simulator

        // Initialize parses.
        for (int sentId = 0; sentId < numSentences; sentId++) {
            final ImmutableList<String> sentence = sentences.get(sentId);
            final NBestList nBestList = nbestLists.get(sentId);
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList =
                    ExperimentUtils.generateAllQueries(sentId, sentence, nBestList,
                            true /* jeopardy */,
                            true /* checkbox */,
                            queryPruningParameters
                    );

            queryList.forEach(query -> query.computeScores(nBestList));
            queryList.forEach(query -> {
                System.out.println(query.toString(sentence) + "\n");
            });

        }
    }
}
