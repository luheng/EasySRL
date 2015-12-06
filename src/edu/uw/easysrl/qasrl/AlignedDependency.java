package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.corpora.ParallelCorpusReader;

/**
 * Created by luheng on 12/5/15.
 */
public class AlignedDependency<T1, T2> {
    public final ParallelCorpusReader.Sentence sentence;
    public final T1 dependency1;
    public final T2 dependency2;
    public final int numD1toD2, numD2toD1;

    public AlignedDependency(ParallelCorpusReader.Sentence sentence, T1 dependency1, T2 dependency2,
                             int numD1toD2, int numD2toD1) {
        this.sentence = sentence;
        this.dependency1 = dependency1;
        this.dependency2 = dependency2;
        this.numD1toD2 = numD1toD2;
        this.numD2toD1 = numD2toD1;
    }

    @Override
    public String toString() {
        // TODO
        return "";
    }
}
