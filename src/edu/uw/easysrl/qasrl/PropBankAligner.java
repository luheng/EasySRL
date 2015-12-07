package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.qa.QACorpusReader;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;

import java.io.IOException;
import java.rmi.NotBoundException;

import java.util.*;
import java.util.stream.Collectors;

import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.dependencies.DependencyStructure.ResolvedDependency;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.parser.SRLParser;

/**
 * Align PropBank data with newswire QA-SRL data.
 * Number of unmatched sentences: 9.
 * Created by luheng on 11/19/15.
 */
// TODO: get dev ....
public class PropBankAligner {
    static AlignedCorpus<SRLDependency, QADependency> pbAndQADependencies = null;
    static AlignedCorpus<CCGBankDependency, QADependency> ccgAndQADependencies = null;

    public static AlignedCorpus<SRLDependency, QADependency> getPbAndQADependencies() {
        if (pbAndQADependencies == null) {
            alignDependencies();
        }
        return pbAndQADependencies;
    }

    public static AlignedCorpus<CCGBankDependency, QADependency> getCcgAndQADependencies() {
        if (ccgAndQADependencies == null) {
            alignDependencies();
        }
        return ccgAndQADependencies;
    }

    public static Map<Integer, List<AlignedDependency<SRLDependency, QADependency>>> getPbAndQADependenciesTrain() {
        if (pbAndQADependencies == null) {
            alignDependencies();
        }
        return pbAndQADependencies.training;
    }

    public static Map<Integer, List<AlignedDependency<SRLDependency, QADependency>>> getPbAndQADependenciesDev() {
        if (pbAndQADependencies == null) {
            alignDependencies();
        }
        return pbAndQADependencies.dev;
    }

    public static Map<Integer, List<AlignedDependency<CCGBankDependency, QADependency>>> getCcgAndQADependenciesTrain() {
        if (ccgAndQADependencies == null) {
            alignDependencies();
        }
        return ccgAndQADependencies.training;
    }

    public static Map<Integer, List<AlignedDependency<CCGBankDependency, QADependency>>> getCcgAndQADependenciesDev() {
        if (ccgAndQADependencies == null) {
            alignDependencies();
        }
        return ccgAndQADependencies.dev;
    }

    /**
     * Given a list of PropBank sentences and a list of QA sentence, return a mapping from
     * PropBank sentence index to QA sentence index, iff. the sentences align.
     * @param pbSentenceList
     * @param qaSentenceList
     * @return
     */
    public static Map<Integer, Integer> getPropBankToQASentenceMapping(
            List<ParallelCorpusReader.Sentence> pbSentenceList, List<QASentence> qaSentenceList) {
        HashMap<Integer, Integer> sentenceMap = new HashMap<>();
        HashMap<String, Integer> sentenceStringMap = new HashMap<>();
        for (int qaIdx = 0; qaIdx < qaSentenceList.size(); qaIdx++) {
            String sentStr = getQASentenceString(qaSentenceList.get(qaIdx));
            sentenceStringMap.put(sentStr, qaIdx);
        }
        for (int pbIdx = 0; pbIdx < pbSentenceList.size(); pbIdx++) {
            String sentStr = StringUtils.join(pbSentenceList.get(pbIdx).getWords(), "");
            if (sentenceStringMap.containsKey(sentStr)) {
                sentenceMap.put(pbIdx, sentenceStringMap.get(sentStr));
                // TODO: debug why this happens ... are there duplicate sentences in PropBank ???
                sentenceStringMap.remove(sentStr);
            }
        }
        return sentenceMap;
    }

    public static List<AlignedDependency<ResolvedDependency, QADependency>> alignDependencies(
                ParallelCorpusReader.Sentence pbSentence, // TODO: change to pb words ...
                List<String> qaWords, SRLParser.CCGandSRLparse parse, Collection<QADependency> qaDeps) {
        List<ResolvedDependency> srlDependencies = parse.getDependencyParse().stream()
                .filter(dep -> {
                    SRLLabel role = dep.getSemanticRole();
                    return role != SRLFrame.NONE && role != SRLFrame.UNLABELLED_ARGUMENT;
                }).collect(Collectors.toList());
        List<String> pbWords = pbSentence.getWords();
        List<QADependency> qaDependencies = new ArrayList(fixQASentenceAlignment(pbWords, qaWords, qaDeps));
        List<AlignedDependency<ResolvedDependency, QADependency>> matchedDependencies = new ArrayList<>();
        int numSrlDependencies = srlDependencies.size(),
            numQaDependencies = qaDependencies.size();
        List<List<Integer>> srl2qa = new ArrayList<>(), qa2srl = new ArrayList<>();
        for (int pbIdx = 0; pbIdx < numSrlDependencies; pbIdx++) {
            srl2qa.add(new ArrayList<>());
        }
        for (int qaIdx = 0; qaIdx < numQaDependencies; qaIdx++) {
            qa2srl.add(new ArrayList<>());
            QADependency qaDependency = qaDependencies.get(qaIdx);
            for (int pbIdx = 0; pbIdx < numSrlDependencies; pbIdx++) {
                ResolvedDependency pbDependency = srlDependencies.get(pbIdx);
                if (match(pbDependency, qaDependency)) {
                    qa2srl.get(qaIdx).add(pbIdx);
                    srl2qa.get(pbIdx).add(qaIdx);
                }
            }
        }
        for (int srlIdx = 0; srlIdx < numSrlDependencies; srlIdx++) {
            ResolvedDependency srlDependency = srlDependencies.get(srlIdx);
            List<Integer> matchedIds = srl2qa.get(srlIdx);
            if (matchedIds.size() == 0) {
                matchedDependencies.add(new AlignedDependency(pbSentence, srlDependency, null /* QA */, 0, 0));
            } else {
                for (int qaIdx : matchedIds) {
                    matchedDependencies.add(new AlignedDependency(pbSentence, srlDependency, qaDependencies.get(qaIdx),
                            matchedIds.size(), qa2srl.get(qaIdx).size()));
                }
            }
        }
        for (int qaIdx = 0; qaIdx < numQaDependencies; qaIdx++) {
            QADependency qaDependency = qaDependencies.get(qaIdx);
            if (qa2srl.get(qaIdx).size() == 0) {
                matchedDependencies.add(new AlignedDependency(pbSentence, null /* PB */, qaDependency, 0, 0));
            }
        }
        return matchedDependencies;
    }

    private static void alignDependencies(List<ParallelCorpusReader.Sentence> pbSentenceList,
            List<QASentence> qaSentenceList,
            Map<Integer, List<AlignedDependency<SRLDependency, QADependency>>> alignedPB,
            Map<Integer, List<AlignedDependency<CCGBankDependency, QADependency>>> alignedCCG) {
        HashMap<String, Integer> sentmap = new HashMap<>();
        for (int i = 0; i < pbSentenceList.size(); i++) {
            String sentStr = StringUtils.join(pbSentenceList.get(i).getWords(), "");
            sentmap.put(sentStr, i);
        }
        // Map SRL, QA and CCG dependencies.
        int numUnmappedSentences = 0;
        int totalNumPbDependencies = 0,
                totalNumCcgDependencies = 0,
                totalNumQaDependencies = 0,
                totalMatchedPbQa = 0,
                totalMatchedCcgQa = 0,
                totalUniquelyMatchedPbQa = 0,
                totalUniquelyMatchedCcgQa = 0;
        for (int qaSentIdx = 0; qaSentIdx < qaSentenceList.size(); qaSentIdx++) {
            QASentence qaSentence = qaSentenceList.get(qaSentIdx);
            String qaSentenceString = getQASentenceString(qaSentence);
            // QA sentence does not match any PropBank sentence.
            if (!sentmap.containsKey(qaSentenceString)) {
                //System.err.println("[error!] unmapped sentence:\t" + propbankSentId + "\t" +    sentStr);
                numUnmappedSentences ++;
                continue;
            }
            int sentIdx = sentmap.get(qaSentenceString);
            alignedPB.put(sentIdx, new ArrayList<>());
            alignedCCG.put(sentIdx, new ArrayList<>());
            ParallelCorpusReader.Sentence pbSentence = pbSentenceList.get(sentIdx);
            List<SRLDependency> pbDependencies = new ArrayList<>(pbSentence.getSrlParse().getDependencies());
            List<CCGBankDependencies.CCGBankDependency> ccgDependencies =
                    pbSentence.getCCGBankDependencyParse().getDependencies().stream()
                            .filter(ccg -> ccg.getCategory().isFunctionInto(Category.valueOf("S|NP")) ||
                                    ccg.getCategory().isFunctionInto(Category.valueOf("S|S")))
                            .collect(Collectors.toList());
            List<QADependency> qaDependencies = new ArrayList<>(fixQASentenceAlignment(pbSentence.getWords(),
                    qaSentence.getWords(), qaSentence.getDependencies()));
            int numPbDependencies = pbDependencies.size(),
                    numCcgDependencies = ccgDependencies.size(),
                    numQaDependencies = qaDependencies.size();
            List<List<Integer>> pb2qa = new ArrayList<>(), ccg2qa = new ArrayList<>(),
                    qa2pb = new ArrayList<>(), qa2ccg = new ArrayList<>();
            for (int pbIdx = 0; pbIdx < numPbDependencies; pbIdx++) {
                pb2qa.add(new ArrayList<>());
            }
            for (int ccgIdx = 0; ccgIdx < numCcgDependencies; ccgIdx++) {
                ccg2qa.add(new ArrayList<>());
            }
            for (int qaIdx = 0; qaIdx < numQaDependencies; qaIdx++) {
                qa2pb.add(new ArrayList<>());
                qa2ccg.add(new ArrayList<>());
            }
            for (int qaIdx = 0; qaIdx < numQaDependencies; qaIdx++) {
                QADependency qaDependency = qaDependencies.get(qaIdx);
                for (int pbIdx = 0; pbIdx < numPbDependencies; pbIdx++) {
                    SRLDependency pbDependency = pbDependencies.get(pbIdx);
                    if (match(pbDependency, qaDependency)) {
                        qa2pb.get(qaIdx).add(pbIdx);
                        pb2qa.get(pbIdx).add(qaIdx);
                    }
                }
                for (int ccgIdx = 0; ccgIdx < numCcgDependencies; ccgIdx++) {
                    CCGBankDependencies.CCGBankDependency ccgDependency = ccgDependencies.get(ccgIdx);
                    if (match(ccgDependency, qaDependency)) {
                        qa2ccg.get(qaIdx).add(ccgIdx);
                        ccg2qa.get(ccgIdx).add(qaIdx);
                    }
                }
            }
            int matchedPbQa = 0, matchedCcgQa = 0, uniquelyMatchedPbQa = 0, uniquelyMatchedCcgQa = 0;
            for (int pbIdx = 0; pbIdx < numPbDependencies; pbIdx++) {
                List<Integer> matchedIds = pb2qa.get(pbIdx);
                List<AlignedDependency<SRLDependency, QADependency>> matchedDependencies = alignedPB.get(sentIdx);
                if (matchedIds.size() == 0) {
                    matchedDependencies.add(new AlignedDependency(
                            pbSentence,
                            pbDependencies.get(pbIdx), null /* mapped qa dependency */,
                            0, 0));
                } else {
                    for (int qaIdx : matchedIds) {
                        matchedDependencies.add(new AlignedDependency(
                                pbSentence,
                                pbDependencies.get(pbIdx), qaDependencies.get(qaIdx),
                                matchedIds.size(), qa2pb.get(qaIdx).size()));
                    }
                    matchedPbQa ++;
                    uniquelyMatchedPbQa += (matchedIds.size() == 1 ? 1 : 0);
                }
            }
            for (int ccgIdx = 0; ccgIdx < numCcgDependencies; ccgIdx++) {
                List<Integer> matchedIds = ccg2qa.get(ccgIdx);
                List<AlignedDependency<CCGBankDependency, QADependency>> matchedDependencies = alignedCCG.get(sentIdx);
                if (matchedIds.size() == 0) {
                    matchedDependencies.add(new AlignedDependency(
                            pbSentence,
                            ccgDependencies.get(ccgIdx),
                            null /* mapped qa dependency */, 0, 0));
                } else {
                    for (int qaIdx : matchedIds) {
                        matchedDependencies.add(new AlignedDependency(
                                pbSentence,
                                ccgDependencies.get(ccgIdx), qaDependencies.get(qaIdx),
                                matchedIds.size(), qa2ccg.get(qaIdx).size()));
                    }
                    matchedCcgQa ++;
                    uniquelyMatchedCcgQa += (matchedIds.size() == 1 ? 1 : 0);
                }
            }
            for (int qaIdx = 0; qaIdx < numQaDependencies; qaIdx++) {
                QADependency qaDependency = qaDependencies.get(qaIdx);
                if (qa2pb.get(qaIdx).size() == 0) {
                    alignedPB.get(sentIdx).add(new AlignedDependency(
                            pbSentence,
                            null /* mapped pb dep */, qaDependency,
                            0, 0));
                }
                if (qa2ccg.get(qaIdx).size() == 0) {
                    alignedCCG.get(sentIdx).add(new AlignedDependency(
                            pbSentence,
                            null /* mapped ccg dep */,
                            qaDependency, 0, 0));
                }
            }
            totalNumPbDependencies += numPbDependencies;
            totalNumCcgDependencies += numCcgDependencies;
            totalNumQaDependencies += numQaDependencies;
            totalMatchedPbQa += matchedPbQa;
            totalMatchedCcgQa += matchedCcgQa;
            totalUniquelyMatchedPbQa += uniquelyMatchedPbQa;
            totalUniquelyMatchedCcgQa += uniquelyMatchedCcgQa;
        }
        System.out.println("Number of unmapped sentences:\t" + numUnmappedSentences);
        System.out.println(
                String.format("PB deps:\t%d\nCCG deps:\t%d\nQA deps:\t%d\n" +
                                "Matched PB-QA:\t%d\nMatched CCG-QA:\t%d\n" +
                                "Uniquely matched PB-QA:\t%d\nUniquely matched CCG-QA:\t%d\n",
                        totalNumPbDependencies, totalNumCcgDependencies, totalNumQaDependencies,
                        totalMatchedPbQa, totalMatchedCcgQa,
                        totalUniquelyMatchedPbQa, totalUniquelyMatchedCcgQa));
    }

    // QA-newswire and PropBank are tokenized differently: A - B vs A-B
    private static void alignDependencies() {
        // Align sentences.
        pbAndQADependencies = new AlignedCorpus();
        ccgAndQADependencies = new AlignedCorpus();
        List<ParallelCorpusReader.Sentence> pbSentenceList =  new ArrayList<>();
        List<QASentence> qaTrainingList = new ArrayList<>(),
                         qaDevList = new ArrayList<>();

        try {
            Iterator<QASentence> qaSentenceIterator = QACorpusReader.getReader("newswire").readTrainingCorpus();
            while (qaSentenceIterator.hasNext()) {
                qaTrainingList.add(qaSentenceIterator.next());
            }
            System.out.println(String.format("%d sentences in QA-SRL training.", qaTrainingList.size()));
            qaSentenceIterator = QACorpusReader.getReader("newswire").readEvaluationCorpus();
            while (qaSentenceIterator.hasNext()) {
                qaDevList.add(qaSentenceIterator.next());
            }
            System.out.println(String.format("%d sentences in QA-SRL dev.", qaDevList.size()));
            Iterator<ParallelCorpusReader.Sentence> sentenceIterator = ParallelCorpusReader.READER
                    .readCorpus(false /* not dev */);
            while (sentenceIterator.hasNext()) {
                pbSentenceList.add(sentenceIterator.next());
            }
            System.out.println(String.format("%d sentences in PropBank SRL.", pbSentenceList.size()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        alignDependencies(pbSentenceList, qaTrainingList, pbAndQADependencies.training, ccgAndQADependencies.training);
        alignDependencies(pbSentenceList, qaDevList, pbAndQADependencies.dev, ccgAndQADependencies.dev);
    }

    private static String getQASentenceString(QASentence qaSentence) {
        String sentStr = StringUtils.join(qaSentence.getWords(), "");
        sentStr = sentStr.replace("(", "-LRB-").replace(")", "-RRB-")
                .replace("[", "-RSB-").replace("]", "-RSB-")
                .replace("{", "-LCB-").replace("}", "-RCB-"); //.replace("/", r"\/");
        return sentStr;
    }

    /**
     * Change the A - B style tokenization into A-B, A - B - C to A-B-C, so on and so forth ..
     */
    private static Collection<QADependency> fixQASentenceAlignment(List<String> pbSentenceTokens,
                                                                   List<String> qaSentenceTokens,
                                                                   Collection<QADependency> qaDependencies) {
        int qaSentLength = qaSentenceTokens.size();
        if (pbSentenceTokens.size() == qaSentLength) {
            return qaDependencies;
        }

        int[] offsets = new int[qaSentLength];
        Arrays.fill(offsets, 0);
        for (int firstHyphenIdx = 1; firstHyphenIdx < qaSentLength - 1; firstHyphenIdx++) {
            int lastHyphenIdx = firstHyphenIdx;
            for  ( ; lastHyphenIdx < qaSentLength - 1; lastHyphenIdx += 2) {
                if (!qaSentenceTokens.get(lastHyphenIdx).equals("-")) {
                    break;
                }
            }
            if (firstHyphenIdx == lastHyphenIdx) {
                continue;
            }
            String mw = "";
            for (int i = firstHyphenIdx - 1; i < lastHyphenIdx; i++) {
                mw += qaSentenceTokens.get(i);
            }
            if (pbSentenceTokens.contains(mw)) {
                for (int i = firstHyphenIdx; i < lastHyphenIdx; i++) {
                    offsets[i] -= (i + 1 - firstHyphenIdx);
                }
                for (int i = lastHyphenIdx; i < qaSentLength; i++) {
                    offsets[i] -= (lastHyphenIdx - firstHyphenIdx);
                }
                firstHyphenIdx = lastHyphenIdx - 1;
            } else {
                System.err.println("Unable to fix alignment:\n" + mw + "\n" + StringUtils.join(pbSentenceTokens) +
                        "\n" + StringUtils.join(qaSentenceTokens));
            }
        }
        return qaDependencies.stream().map(
                dep -> new QADependency(dep.getPredicate(),
                        dep.getPredicateIndex() + offsets[dep.getPredicateIndex()], // new predicate index
                        dep.getQuestion(),
                        dep.getAnswerPositions().stream() // new argument indices
                                .map(idx -> idx + offsets[idx]).distinct().sorted()
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    private static boolean match(ResolvedDependency dependency, QADependency qaDependency) {
        int predicate = dependency.getPredicateIndex(),
            argument = dependency.getArgumentIndex(),
            qaPredicate = qaDependency.getPredicateIndex();
        Collection<Integer> qaArguments = qaDependency.getAnswerPositions();
        boolean match = (predicate == qaPredicate) && qaArguments.contains(argument);
        boolean reversedMatch = (argument == qaPredicate) && qaArguments.contains(predicate);
        return match || reversedMatch;
    }

    private static boolean match(SRLDependency srlDependency, QADependency qaDependency) {
        boolean predicateMatch = srlDependency.getPredicateIndex() == qaDependency.getPredicateIndex();
        boolean argumentMatch = srlDependency.getArgumentPositions().stream()
                .filter(idx -> qaDependency.getAnswerPositions().contains(idx))
                .count() > 0;
        return predicateMatch && argumentMatch;
    }

    private static boolean match(CCGBankDependency ccgDependency, QADependency qaDependency) {
        // Undirected match.
        // TODO: figure out when do we need a reversedMatch.
        int ccgPredicate = ccgDependency.getSentencePositionOfPredicate();
        int ccgArgument = ccgDependency.getSentencePositionOfArgument();
        int qaPredicate = qaDependency.getPredicateIndex();
        Collection<Integer> qaArguments = qaDependency.getAnswerPositions();
        boolean match = (qaPredicate == ccgPredicate && qaArguments.contains(ccgArgument));
        boolean reversedMatch = (qaArguments.contains(ccgPredicate) && qaPredicate == ccgArgument);
        return match || reversedMatch;
    }

    // TODO: learn a distribution of questions given gold parse/dependencies
    // TODO: learn a distribution or Propbank tags given question and predicate
    public static void main(String[] args)  throws IOException, InterruptedException, NotBoundException {
        AlignedCorpus<SRLDependency, QADependency> pbQA = PropBankAligner.getPbAndQADependencies();
        AlignedCorpus<CCGBankDependency, QADependency> ccgQA = PropBankAligner.getCcgAndQADependencies();
        pbQA.printAnalysis();
        ccgQA.printAnalysis();
    }
}
