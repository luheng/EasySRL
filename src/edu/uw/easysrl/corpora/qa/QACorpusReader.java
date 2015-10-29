package edu.uw.easysrl.corpora.qa;

import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import edu.uw.easysrl.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by luheng on 10/29/15.
 */
public class QACorpusReader {

    private final static Properties PROPERTIES = Util.loadProperties(new File("corpora.properties"));
    // TODO: add QA-SRL train and test files, respectively ...
    public static final File QASRL = new File(PROPERTIES.getProperty("qasrl"));

    private final File qaFile;
    private List<QASentence> qaParses;
    public final static QACorpusReader READER = new QACorpusReader(QASRL);

    private QACorpusReader(final File qaFile) {
        super();
        this.qaFile = qaFile;
    }

    public Iterator<QASentence> readCorpus(final boolean isDev) throws IOException {
        return new Iterator<QASentence>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < qaParses.size();
            }

            @Override
            public QASentence next() {
                QASentence sent = qaParses.get(i);
                i++;
                return sent;
            }
        };
    }
}
