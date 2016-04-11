package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.qg.QAPairAggregator;
import edu.uw.easysrl.qasrl.qg.QAPairAggregators;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.surfaceform.QADependenciesSurfaceForm;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAPairSurfaceForm;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.Query;
import edu.uw.easysrl.qasrl.query.QueryFilters;
import edu.uw.easysrl.qasrl.query.QueryGenerators;
import edu.uw.easysrl.qasrl.query.ScoredQuery;

import java.util.stream.Collectors;

/**
 * Created by luheng on 4/10/16.
 */
public class CleftingQuestionExperiment {

    public static void main(String[] args) {
        ParseData devData = ParseData.loadFromDevPool().get();
        QuestionGenerator.setAskPPAttachmentQuestions(true);

        ImmutableMap<Integer, NBestList> nBestLists = NBestList.loadNBestListsFromFile("parses.100best.out", 100).get();
        for(int sentenceId = 0; sentenceId < 20; sentenceId++) {
            if(!nBestLists.containsKey(sentenceId)) {
                continue;
            }
            ImmutableList<String> words = devData.getSentences().get(sentenceId);
            NBestList nBestList = nBestLists.get(sentenceId);
            ImmutableList<QuestionAnswerPair> qaPairs = QuestionGenerator.generateAllQAPairs(sentenceId, words, nBestList);

            /*
            qaPairs.forEach(qa -> {
                System.out.println(qa.getQuestion() + "\t" + qa.getAnswer());
                System.out.println(qa.getQuestionDependencies().stream().map(dep -> dep.toString(words)).collect(Collectors.joining(";\t")));
                System.out.println(qa.getTargetDependency().toString(words));
                System.out.println(qa.getAnswerDependencies().stream().map(dep -> dep.toString(words)).collect(Collectors.joining(";\t")));
                System.out.println();
            });*/

            ImmutableList<QAStructureSurfaceForm> surfaceForms = QAPairAggregators
                    .aggregateWithAnswerAdjunctDependencies()
                    .aggregate(qaPairs);
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
                    QueryGenerators
                    .checkboxQueryGenerator()
                    .generate(surfaceForms);

            queries.forEach(q -> q.computeScores(nBestList));
            queries.forEach(q -> System.out.println(q.toString(words)));
        }
    }
}
