package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.qa.QACorpusReader;
import edu.uw.easysrl.corpora.qa.QASentence;

import java.io.IOException;
import java.rmi.NotBoundException;

import java.util.*;

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
    static Map<Integer, List<MappedDependency>> mappedDependencies = null;

    public static Map<Integer, List<MappedDependency>> getMappedDependencies() {
        if (mappedDependencies == null) {
            try {
                alignPropBankQADependencies();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return mappedDependencies;
    }

    private static boolean match(SRLDependency srlDependency, QADependency qaDependency) {
        boolean predicateMatch = srlDependency.getPredicateIndex() == qaDependency.getPredicateIndex();
        boolean argumentMatch = srlDependency.getArgumentPositions().stream()
                .filter(idx -> qaDependency.getAnswerPositions().contains(idx))
                .count() > 0;
        return predicateMatch && argumentMatch;
    }

    private static void alignPropBankQADependencies() throws IOException {
        Iterator<QASentence> qaSentenceIterator = QACorpusReader.getReader("newswire").readTrainingCorpus();
        qaSentenceList = new ArrayList<>();
        while (qaSentenceIterator.hasNext()) {
            qaSentenceList.add(qaSentenceIterator.next());
        }
        System.out.println(String.format("%d sentences in QA-SRL.", qaSentenceList.size()));

        Iterator<ParallelCorpusReader.Sentence> sentenceIterator = ParallelCorpusReader.READER
                .readCorpus(false /* not dev */);
        pbSentenceList =  new ArrayList<>();
        while (sentenceIterator.hasNext()) {
            pbSentenceList.add(sentenceIterator.next());
        }
        System.out.println(String.format("%d sentences in PropBank SRL.", pbSentenceList.size()));

        HashMap<String, Integer> sentmap = new HashMap<>();
        for (int i = 0; i < pbSentenceList.size(); i++) {
            String sentStr = StringUtils.join(pbSentenceList.get(i).getWords(), "");
            sentmap.put(sentStr, i);
        }

        // Map dependencies.
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
                int mappedSentenceId =sentmap.get(sentStr);
                ParallelCorpusReader.Sentence pbSentence = pbSentenceList.get(mappedSentenceId);
                List<SRLDependency> pbDependencies = new ArrayList<>(pbSentence.getSrlParse().getDependencies());
                List<QADependency> qaDependencies = new ArrayList<>(qaSentence.getDependencies());
                Map<Integer, List<Integer>> pb2qa = new HashMap<>(),
                                            qa2pb = new HashMap<>();
                for (int j = 0; j < pbDependencies.size(); j++) {
                    SRLDependency pbDependency = pbDependencies.get(j);
                    for (int k = 0; k < qaDependencies.size(); k++) {
                        QADependency qaDependency = qaDependencies.get(k);
                        if (match(pbDependency, qaDependency)) {
                            System.out.println(pbDependency + "\t" + qaDependency);
                            if (!pb2qa.containsKey(j)) {
                                pb2qa.put(j, new ArrayList<>());
                            }
                            if (!qa2pb.containsKey(k)) {
                                qa2pb.put(k, new ArrayList<>());
                            }
                            pb2qa.get(j).add(k);
                            qa2pb.get(k).add(j);
                        }
                    }
                }
                for (int pbIdx : pb2qa.keySet()) {
                    List<Integer> matched = pb2qa.get(pbIdx);
                    for (int qaIdx : matched) {
                        if (mappedDependencies.containsKey(mappedSentenceId)) {
                            mappedDependencies.put(mappedSentenceId, new ArrayList<>());
                        }
                        mappedDependencies.get(mappedSentenceId).add(
                                new MappedDependency(pbSentence, pbDependencies.get(pbIdx),
                                        qaDependencies.get(qaIdx), matched.size(), qa2pb.get(qaIdx).size())
                        );
                    }
                }
            } else {
                //System.err.println("[error!] unmapped sentence:\t" + propbankSentId + "\t" +    sentStr);
                numUnmappedSentences ++;
            }
        }
        System.err.println("Number of unmapped sentences:\t" + numUnmappedSentences);
    }

    // TODO: Align and output SRL and QA dependencies
    // TODO: learn a distribution of questions given gold parse/dependencies
    // TODO: learn a distribution or Propbank tags given question and predicate

    public static void main(String[] args)  throws IOException, InterruptedException, NotBoundException {
      /*  if (args.length == 0) {
            System.out.println("Please supply a file containing training settings");
            System.exit(0);
        }*/

    }
}
