package edu.uw.easysrl.qasrl.corpora;

import edu.uw.easysrl.util.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Created by luheng on 10/29/15.
 */
public class QACorpusReader {

    private final static Properties PROPERTIES = Util.loadProperties(new File("corpora.properties"));
    public static final File QASRL_NEWSWIRE_TRAIN = new File(PROPERTIES.getProperty("qa-newswire") + ".train.qa");
    public static final File QASRL_NEWSWIRE_DEV = new File(PROPERTIES.getProperty("qa-newswire") + ".dev.qa");
    public static final File QASRL_WIKI_TRAIN = new File(PROPERTIES.getProperty("qa-wiki") + ".train.qa");
    public static final File QASRL_WIKI_DEV = new File(PROPERTIES.getProperty("qa-wiki") + ".dev.qa");
    //public static final File QASRL_TEST = new File(PROPERTIES.getProperty("qasrl") + ".test.qa");

    private final File qaFileTrain, qaFileDev;
    private List<QASentence> parsesTrain, parsesDev;
    private final static QACorpusReader NEWSWIRE_READER = new QACorpusReader(QASRL_NEWSWIRE_TRAIN, QASRL_NEWSWIRE_DEV);
    private final static QACorpusReader WIKIPEDIA_READER = new QACorpusReader(QASRL_WIKI_TRAIN, QASRL_WIKI_DEV);

    private QACorpusReader(final File qaFileTrain, final File qaFileDev) {
        super();
        this.qaFileTrain = qaFileTrain;
        this.qaFileDev = qaFileDev;
    }

    public static QACorpusReader getReader(String domain) {
        switch (domain.toLowerCase()) {
            case "newswire" :
                return NEWSWIRE_READER;
            case "wikipedia" :
                return WIKIPEDIA_READER;
            default:
                System.err.println("Unrecognized domain name:\t" + domain);
                System.err.println("Try ``newswire\'\' or ``wikipedia\'\'.");
                return null;
        }
    }

    public Iterator<QASentence> readTrainingCorpus() throws IOException {
        return readCorpus(false);
    }

    public Iterator<QASentence> readEvaluationCorpus() throws IOException {
        return readCorpus(true);
    }

    private Iterator<QASentence> readCorpus(final boolean isDev) throws IOException {
        List<QASentence> parses;
        if (isDev) {
            if (parsesDev == null) {
                parsesDev = QACorpusReader.loadCorpus(qaFileDev);
            }
            parses = parsesDev;
        } else {
            if (parsesTrain == null) {
                parsesTrain = QACorpusReader.loadCorpus(qaFileTrain);
            }
            parses = parsesTrain;
        }
        System.out.println("Total QA-SRL Sentences: " + parses.size());

        return new Iterator<QASentence>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < parses.size();
            }

            @Override
            public QASentence next() {
                QASentence sent = parses.get(i);
                i++;
                return sent;
            }
        };
    }

    public Collection<QASentence> getDevCorpus() throws IOException {
        if (parsesDev == null) {
            parsesDev = QACorpusReader.loadCorpus(qaFileDev);
        }
        return parsesDev;
    }

    private static List<QASentence> loadCorpus(File qaFile) throws IOException {
        List<QASentence> parses = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(qaFile));
        while (reader.ready()) {
            String[] sentInfo = reader.readLine().split("\\s+");
            String sentId = sentInfo[0];
            int numPredicates = Integer.parseInt(sentInfo[1]);
            String[] words = reader.readLine().split("\\s+");
            QASentence newSent = new QASentence(words);
            newSent.referenceId = sentId;
            for (int i = 0; i < numPredicates; i++) {
                String[] predInfo = reader.readLine().split("\\s+");
                int predIndex = Integer.parseInt(predInfo[0]);
                int numQAPairs = Integer.parseInt(predInfo[2]);
                for (int j = 0; j < numQAPairs; j++) {
                    newSent.addDependencyFromLine(predIndex, reader.readLine());
                }
            }
            parses.add(newSent);
            reader.readLine(); // skip empty line.
        }
        reader.close();
        return parses;
    }

    /**
     * Test ...
     */
    private static void testQAReader() {
        Iterator<QASentence> iter1 = null, iter2 = null;
        try {
            iter1 = QACorpusReader.WIKIPEDIA_READER.readCorpus(false /* is dev */);
            iter2 = QACorpusReader.NEWSWIRE_READER.readCorpus(true  /* is dev */);
        } catch (IOException e) {
            e.printStackTrace();
        }
        iter1.forEachRemaining(s -> System.out.println(s.toString()));
        iter2.forEachRemaining(s -> System.out.println(s.toString()));
    }

    public static void main(final String[] args) {
        testQAReader();
    }
}
