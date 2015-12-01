package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.qa.QACorpusReader;
import edu.uw.easysrl.corpora.qa.QASentence;

import java.io.IOException;
import java.rmi.NotBoundException;

import java.util.*;
import java.util.stream.Collectors;

import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLDependency;

/**
 * Align PropBank data with newswire QA-SRL data.
 * Number of unmatched sentences: 9.
 * Created by luheng on 11/19/15.
 */
public class PropBankAligner {
    static List<QASentence> qaSentenceList = null;
    static List<ParallelCorpusReader.Sentence> pbSentenceList = null;
    static Map<Integer, List<SRLandQADependency>> srlAndQADependencies = null;
    static Map<Integer, List<CCGandQADependency>> ccgAndQADependencies = null;

    public static Map<Integer, List<SRLandQADependency>> getSrlAndQADependencies() {
        if (srlAndQADependencies == null) {
            try {
                alignPropBankQADependencies();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return srlAndQADependencies;
    }

    public static Map<Integer, List<CCGandQADependency>> getCcgAndQADependencies() {
        if (ccgAndQADependencies == null) {
            try {
                alignPropBankQADependencies();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ccgAndQADependencies;
    }

    /**
     * Given a list of PropBank sentences and a list of QA sentence, return a mapping from
     * PropBank sentence index to QA sentence index, iff. the sentences align.
     * @param pbSentences
     * @param qaSentences
     * @return
     */
    public static Map<Integer, Integer> getPropBankToQASentenceMapping(
            List<ParallelCorpusReader.Sentence> pbSentenceList,
            List<QASentence> qaSentenceList) {
        HashMap<Integer, Integer> sentenceMap = new HashMap<>();
        HashMap<String, Integer> sentenceStringMap = new HashMap<>();
        for (int qaIdx = 0; qaIdx < qaSentenceList.size(); qaIdx++) {
            String sentStr = StringUtils.join(qaSentenceList.get(qaIdx).getWords(), "");
            sentenceStringMap.put(sentStr, qaIdx);
        }
        for (int pbIdx = 0; pbIdx < pbSentenceList.size(); pbIdx++) {
            String sentStr = StringUtils.join(pbSentenceList.get(pbIdx).getWords(), "");
            if (sentenceStringMap.containsKey(sentStr)) {
                sentenceMap.put(pbIdx, sentenceStringMap.get(sentStr));
            }
        }
        return sentenceMap;
    }

    private static boolean match(SRLDependency srlDependency, QADependency qaDependency) {
        boolean predicateMatch = srlDependency.getPredicateIndex() == qaDependency.getPredicateIndex();
        boolean argumentMatch = srlDependency.getArgumentPositions().stream()
                .filter(idx -> qaDependency.getAnswerPositions().contains(idx))
                .count() > 0;
        return predicateMatch && argumentMatch;
    }

    private static boolean match(CCGBankDependencies.CCGBankDependency ccgDependency, QADependency qaDependency) {
        // Undirected match.
        // TODO: figure out when do we need a reversedMatch.
        int ccgPredicate = ccgDependency.getSentencePositionOfPredicate();
        int ccgArgument = ccgDependency.getSentencePositionOfArgument();
        int qaPredicate = qaDependency.getPredicateIndex();
        Collection<Integer> qaArguments = qaDependency.getAnswerPositions();
        boolean match = qaPredicate == ccgPredicate && qaArguments.contains(ccgArgument);
        boolean reversedMatch = qaArguments.contains(ccgPredicate) && qaPredicate == ccgArgument;
        return match || reversedMatch;
    }

    // QA-newswire and PropBank are tokenized differently: A - B vs A-B
    private static void alignPropBankQADependencies() throws IOException {
        // Align sentences.
        srlAndQADependencies = new HashMap<>();
        ccgAndQADependencies = new HashMap<>();
        pbSentenceList =  new ArrayList<>();
        qaSentenceList = new ArrayList<>();
        Iterator<QASentence> qaSentenceIterator = QACorpusReader.getReader("newswire").readTrainingCorpus();
        while (qaSentenceIterator.hasNext()) {
            qaSentenceList.add(qaSentenceIterator.next());
        }
        System.out.println(String.format("%d sentences in QA-SRL.", qaSentenceList.size()));
        Iterator<ParallelCorpusReader.Sentence> sentenceIterator = ParallelCorpusReader.READER
                .readCorpus(false /* not dev */);
        while (sentenceIterator.hasNext()) {
            pbSentenceList.add(sentenceIterator.next());
        }
        System.out.println(String.format("%d sentences in PropBank SRL.", pbSentenceList.size()));
        HashMap<String, Integer> sentmap = new HashMap<>();
        for (int i = 0; i < pbSentenceList.size(); i++) {
            String sentStr = StringUtils.join(pbSentenceList.get(i).getWords(), "");
            sentmap.put(sentStr, i);
        }

        // Map SRL, QA and CCG dependencies.
        int numUnmappedSentences = 0;
        for (int i = 0; i < qaSentenceList.size(); i++) {
            QASentence qaSentence = qaSentenceList.get(i);
            // int propbankSentId = Integer.parseInt(sent.referenceId.split("_")[1]);
            String sentStr = StringUtils.join(qaSentence.getWords(), "");
            sentStr = sentStr.replace("(", "-LRB-").replace(")", "-RRB-")
                             .replace("[", "-RSB-").replace("]", "-RSB-")
                             .replace("{", "-LCB-").replace("}", "-RCB-"); //.replace("/", r"\/");
            if (sentmap.containsKey(sentStr)) {
                // Try to map dependencies ...
                int sentIdx =sentmap.get(sentStr);
                ParallelCorpusReader.Sentence pbSentence = pbSentenceList.get(sentIdx);
                List<SRLDependency> pbDependencies = new ArrayList<>(pbSentence.getSrlParse().getDependencies());
                List<CCGBankDependencies.CCGBankDependency> ccgDependencies =
                        new ArrayList<>(pbSentence.getCCGBankDependencyParse().getDependencies());
                List<QADependency> qaDependencies = new ArrayList<>(fixQASentenceAlignment(pbSentence, qaSentence));
                Map<Integer, List<Integer>> pb2qa = new HashMap<>(),
                                            qa2pb = new HashMap<>(),
                                            ccg2qa = new HashMap<>(),
                                            qa2ccg = new HashMap<>();
                for (int j = 0; j < qaDependencies.size(); j++) {
                    QADependency qaDependency = qaDependencies.get(j);
                    for (int k = 0; k < pbDependencies.size(); k++) {
                        SRLDependency pbDependency = pbDependencies.get(k);
                        if (match(pbDependency, qaDependency)) {
                            if (!qa2pb.containsKey(j)) {
                                qa2pb.put(j, new ArrayList<>());
                            }
                            if (!pb2qa.containsKey(k)) {
                                pb2qa.put(k, new ArrayList<>());
                            }
                            qa2pb.get(j).add(k);
                            pb2qa.get(k).add(j);
                        }
                    }
                    for (int k = 0; k < ccgDependencies.size(); k++) {
                        CCGBankDependencies.CCGBankDependency ccgDependency = ccgDependencies.get(k);
                        if (match(ccgDependency, qaDependency)) {
                            if (!qa2ccg.containsKey(j)) {
                                qa2ccg.put(j, new ArrayList<>());
                            }
                            if (!ccg2qa.containsKey(k)) {
                                ccg2qa.put(k, new ArrayList<>());
                            }
                            qa2ccg.get(j).add(k);
                            ccg2qa.get(k).add(j);
                        }
                    }
                }
                // FIXME: change this to QA based match? meaning: a QA-pair can map to a null PB/CCG relation.
                for (int pbIdx : pb2qa.keySet()) {
                    List<Integer> matched = pb2qa.get(pbIdx);
                    for (int qaIdx : matched) {
                        if (!srlAndQADependencies.containsKey(sentIdx)) {
                            srlAndQADependencies.put(sentIdx, new ArrayList<>());
                        }
                        srlAndQADependencies.get(sentIdx).add(
                                new SRLandQADependency(pbSentence, pbDependencies.get(pbIdx),
                                        qaDependencies.get(qaIdx), matched.size(), qa2pb.get(qaIdx).size())
                        );
                    }
                }
                for (int ccgIdx : ccg2qa.keySet()) {
                    List<Integer> matched = ccg2qa.get(ccgIdx);
                    for (int qaIdx : matched) {
                        if (!ccgAndQADependencies.containsKey(sentIdx)) {
                            ccgAndQADependencies.put(sentIdx, new ArrayList<>());
                        }
                        ccgAndQADependencies.get(sentIdx).add(
                                new CCGandQADependency(pbSentence, ccgDependencies.get(ccgIdx),
                                        qaDependencies.get(qaIdx), matched.size(), qa2ccg.get(qaIdx).size())
                        );
                    }
                }
            } else {
                //System.err.println("[error!] unmapped sentence:\t" + propbankSentId + "\t" +    sentStr);
                numUnmappedSentences ++;
            }
        }
        System.out.println("Number of unmapped sentences:\t" + numUnmappedSentences);
    }

    /**
     * Change the A - B style tokenization into A-B, A - B - C to A-B-C, so on and so forth ..
     */
    private static Collection<QADependency> fixQASentenceAlignment(ParallelCorpusReader.Sentence pbSentence,
                                                                   QASentence qaSentence) {
        if (pbSentence.getLength() == qaSentence.getSentenceLength()) {
            return qaSentence.getDependencies();
        }
        List<String> pbWords = pbSentence.getWords(),
                     qaWords = qaSentence.getWords();
        int qaSentLength = qaWords.size();
        int[] offsets = new int[qaSentLength];
        Arrays.fill(offsets, 0);
        for (int firstHyphenIdx = 1; firstHyphenIdx < qaSentLength - 1; firstHyphenIdx++) {
            int lastHyphenIdx = firstHyphenIdx;
            for  ( ;lastHyphenIdx < qaSentLength - 1; lastHyphenIdx += 2) {
                if (!qaWords.get(lastHyphenIdx).equals("-")) {
                    break;
                }
            }
            if (firstHyphenIdx == lastHyphenIdx) {
                continue;
            }
            String mw = "";
            for (int i = firstHyphenIdx - 1; i < lastHyphenIdx; i++) {
                mw += qaWords.get(i);
            }
            if (pbWords.contains(mw)) {
                for (int i = firstHyphenIdx; i < lastHyphenIdx; i++) {
                    offsets[i] -= (i + 1 - firstHyphenIdx);
                }
                for (int i = lastHyphenIdx; i < qaSentLength; i++) {
                    offsets[i] -= (lastHyphenIdx - firstHyphenIdx);
                }
                firstHyphenIdx = lastHyphenIdx - 1;
            } else {
                System.err.println("Unable to fix alignment:\n" + mw + "\n" + StringUtils.join(pbWords) + "\n" +
                        StringUtils.join(qaWords));
            }
        }
        Collection<QADependency> realignedDeps = new HashSet<>();
        qaSentence.getDependencies().stream().map(dep -> {
            int newPredicateIndex = dep.getPredicateIndex() - offsets[dep.getPredicateIndex()];
            List<Integer> newAnswerIndices = dep.getAnswerPositions().stream()
                    .map(idx -> idx - offsets[idx]).distinct().sorted().collect(Collectors.toList());
            return new QADependency(dep.getPredicate(), newPredicateIndex, dep.getQuestion(), newAnswerIndices);
        });
        return realignedDeps;
    }

    // TODO: learn a distribution of questions given gold parse/dependencies
    // TODO: learn a distribution or Propbank tags given question and predicate
    public static void main(String[] args)  throws IOException, InterruptedException, NotBoundException {
        PropBankAligner.getSrlAndQADependencies();
        int numAlignedDependencies = 0, numUniquelyAlignedDependencies = 0,
                numPropBankDependencies = 0, numQADependencies = 0;
        // Print aligned dependencies
        for (int sentIdx : srlAndQADependencies.keySet()) {
            ParallelCorpusReader.Sentence pbSentence = pbSentenceList.get(sentIdx);
            List<String> words = pbSentence.getWords();
            System.out.println("\n" + StringUtils.join(words, " "));
            for (SRLandQADependency dep : srlAndQADependencies.get(sentIdx)) {
                System.out.println(dep.srlDependency.toString(words) + "\t|\t" +
                        dep.qaDependency.toString(words) + "\t" +
                        dep.numSRLtoQAMaps + "\t" + dep.numQAtoSRLMaps);
                if (dep.numQAtoSRLMaps == 1 && dep.numSRLtoQAMaps == 1) {
                    numUniquelyAlignedDependencies ++;
                }
            }
            numAlignedDependencies += srlAndQADependencies.get(sentIdx).size();
            numPropBankDependencies += pbSentence.getSrlParse().getDependencies().size();
        }
        System.out.println("Number of aligned dependencies:\t" + numAlignedDependencies);
        System.out.println("Number of uniquely aligned dependencies:\t" + numUniquelyAlignedDependencies);
        System.out.println("Number of total PropBank relations:\t" + numPropBankDependencies);
    }
}
