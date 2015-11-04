package edu.uw.easysrl.corpora.qa;

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
    // TODO: add QA-SRL train and test files, respectively ...
    public static final File QASRL_TRAIN = new File(PROPERTIES.getProperty("qasrl") + ".train.qa");
    public static final File QASRL_DEV = new File(PROPERTIES.getProperty("qasrl") + ".dev.qa");
    public static final File QASRL_TEST = new File(PROPERTIES.getProperty("qasrl") + ".test.qa");

    private final File qaFileTrain, qaFileDev;
    private List<QASentence> parsesTrain, parsesDev;
    public final static QACorpusReader READER = new QACorpusReader(QASRL_TRAIN, QASRL_DEV);

    private QACorpusReader(final File qaFileTrain, final File qaFileDev) {
        super();
        this.qaFileTrain = qaFileTrain;
        this.qaFileDev = qaFileDev;
    }

    public Iterator<QASentence> readCorpus(final boolean isDev) throws IOException {
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
        // System.out.println("Total PTB: " + PTB.size());

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

    public Collection<QASentence> getDepCorpus() throws IOException {
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
            newSent.sentenceId = sentId;
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
            iter1 = QACorpusReader.READER.readCorpus(false /* is dev */);
            iter2 = QACorpusReader.READER.readCorpus(true  /* is dev */);
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
