package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.qa.QACorpusReader;
import edu.uw.easysrl.corpora.qa.QASentence;

import java.io.IOException;
import java.rmi.NotBoundException;

import java.util.*;

import edu.stanford.nlp.util.StringUtils;

/**
 * Align PropBank data with newswire QA-SRL data.
 * Number of unmatched sentences: 9.
 * Created by luheng on 11/19/15.
 */
public class PropBankAligner {

    public static void main(String[] args)  throws IOException, InterruptedException, NotBoundException {
      /*  if (args.length == 0) {
            System.out.println("Please supply a file containing training settings");
            System.exit(0);
        }*/
        boolean isDev = true;

        Iterator<QASentence> qaSentenceIterator = QACorpusReader.getReader("newswire").readTrainingCorpus();
        List<QASentence> qaSentenceList = new ArrayList<>();
        while (qaSentenceIterator.hasNext()) {
            qaSentenceList.add(qaSentenceIterator.next());
        }
        System.out.println(String.format("%d sentences in QA-SRL.", qaSentenceList.size()));

        Iterator<ParallelCorpusReader.Sentence> sentenceIterator = ParallelCorpusReader.READER.readCorpus(!isDev);
        List<ParallelCorpusReader.Sentence> sentenceList =  new ArrayList<>();
        while (sentenceIterator.hasNext()) {
            sentenceList.add(sentenceIterator.next());
        }
        System.out.println(String.format("%d sentences in PropBank SRL.", sentenceList.size()));

        HashMap<String, Integer> sentmap = new HashMap<>();
        for (int i = 0; i < sentenceList.size(); i++) {
            String sentStr = StringUtils.join(sentenceList.get(i).getWords(), "");
            sentmap.put(sentStr, i);
        }

        sentenceIterator = ParallelCorpusReader.READER.readCorpus(isDev);
        while (sentenceIterator.hasNext()) {
            String sentStr = StringUtils.join(sentenceIterator.next().getWords(), "");
            sentmap.put(sentStr, -1);
        }


        int numUnmappedSentences = 0;
        for (int i = 0; i < qaSentenceList.size(); i++) {
            QASentence sent = qaSentenceList.get(i);
            //System.out.println(sent.referenceId);
            int propbankSentId = Integer.parseInt(sent.referenceId.split("_")[1]);
            String sentStr = StringUtils.join(sent.getWords(), "");
            sentStr = sentStr.replace("(", "-LRB-").replace(")", "-RRB-")
                             .replace("[", "-RSB-").replace("]", "-RSB-")
                             .replace("{", "-LCB-").replace("}", "-RCB-");
                             //.replace("/", r"\/");
            if (sentmap.containsKey(sentStr)) {
                System.out.println(propbankSentId + "\tmapped to:\t" + sentmap.get(sentStr));
            } else {
                System.err.println("[error!] unmapped sentence:\t" + propbankSentId + "\t" +    sentStr);
                numUnmappedSentences ++;
            }

            //System.out.println(StringUtils.join(sent.getWords()));
            //System.out.println(StringUtils.join(sentenceList.get(propbankSentId).getWords()));
        }
        System.out.println("Number of unmapped sentences:\t" + numUnmappedSentences);

        for (int i = 0; i < sentenceList.size(); i++) {
            String sentStr = StringUtils.join(sentenceList.get(i).getWords(), " ");
            if (sentStr.contains("Richard")) {
                System.out.println(sentStr);
            }
        }
    }
}
