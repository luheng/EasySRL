package edu.uw.easysrl.syntax.training;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.qa.QACorpusReader;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.qasrl.PropBankAligner;

import java.io.IOException;
import java.util.*;

/**
 * Some broiler-plate code goes here.
 * Created by luheng on 12/14/15.
 */
public class SemiSupervisedLearningHelper {

    public static void prepareCorpora(List<ParallelCorpusReader.Sentence> trainingPool,
                                       List<ParallelCorpusReader.Sentence> evalSentences,
                                       List<ParallelCorpusReader.Sentence> alignedPBSentences,
                                       List<QASentence> qaTrainingSentences,
                                       List<QASentence> alignedQASentences,
                                       List<Integer> trainingSentenceIds) throws IOException {
        trainingPool.clear();
        evalSentences.clear();
        alignedPBSentences.clear();
        qaTrainingSentences.clear();
        alignedQASentences.clear();
        trainingSentenceIds.clear();
        List<QASentence> qaEvalSentences = new ArrayList<>();
        Iterator<QASentence> qaIter1 = QACorpusReader.getReader("newswire").readTrainingCorpus(),
                qaIter2 = QACorpusReader.getReader("newswire").readEvaluationCorpus();
        Iterator<ParallelCorpusReader.Sentence> pbIter1 = ParallelCorpusReader.READER.readCorpus(false /* not dev */),
                pbIter2 = ParallelCorpusReader.READER.readCorpus(true  /* dev */);
        while (qaIter1.hasNext()) {
            qaTrainingSentences.add(qaIter1.next());
        }
        while (qaIter2.hasNext()) {
            qaEvalSentences.add(qaIter2.next());
        }
        while (pbIter1.hasNext()) {
            trainingPool.add(pbIter1.next());
        }
        while (pbIter2.hasNext()) {
            evalSentences.add(pbIter2.next());
        }
        Map<Integer, Integer> sentMap = PropBankAligner.getPropBankToQASentenceMapping(trainingPool,
                qaTrainingSentences);
        Map<Integer, Integer> evalSentMap = PropBankAligner.getPropBankToQASentenceMapping(trainingPool,
                qaEvalSentences);
        System.out.println("mapped sentences:\t" + sentMap.size() + "\t" + evalSentMap.size());
        for (int pbIdx = 0; pbIdx < trainingPool.size(); pbIdx ++) {
            if (!sentMap.containsKey(pbIdx) && !evalSentMap.containsKey(pbIdx)) {
                trainingSentenceIds.add(pbIdx);
            }
        }
        for (int pbIdx : evalSentMap.keySet()) {
            alignedPBSentences.add(trainingPool.get(pbIdx));
            alignedQASentences.add(qaEvalSentences.get(evalSentMap.get(pbIdx)));
        }
        System.out.println(String.format("Aligned %d sentences in dev set.", alignedPBSentences.size()));
    }


    public static List<ParallelCorpusReader.Sentence> subsample(final int numPropBankTrainingSentences,
                                                                final List<ParallelCorpusReader.Sentence> trainingPool,
                                                                final List<Integer> trainingSentenceIds,
                                                                Random random) {
        if (random == null) {
            random = new Random(0);
        }
        List<ParallelCorpusReader.Sentence> trainingSentences = new ArrayList<>();
        Collections.shuffle(trainingSentenceIds, random);
        for (int pbIdx : trainingSentenceIds) {
            trainingSentences.add(trainingPool.get(pbIdx));
            if (trainingSentences.size() >= numPropBankTrainingSentences) {
                break;
            }
        }
        return trainingSentences;
    }

}
