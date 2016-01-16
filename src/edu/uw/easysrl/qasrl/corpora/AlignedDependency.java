package edu.uw.easysrl.qasrl.corpora;

import edu.uw.easysrl.corpora.ParallelCorpusReader;

/**
 * Created by luheng on 12/5/15.
 */
public class AlignedDependency<T1, T2> {
    // TODO: change this to more ``generic'' sentence types, i.e. List<String>
    // FIXME: null T1 and T2 causes a lot of trouble, need to fix this.
    public final ParallelCorpusReader.Sentence sentence;
    public final T1 dependency1;
    public final T2 dependency2;
    public final int d1ToHowManyD2, d2ToHowManyD1;

    public AlignedDependency(ParallelCorpusReader.Sentence sentence, T1 dependency1, T2 dependency2,
                             int d1ToHowManyD2, int d2ToHowManyD1) {
        this.sentence = sentence;
        this.dependency1 = dependency1;
        this.dependency2 = dependency2;
        this.d1ToHowManyD2 = d1ToHowManyD2;
        this.d2ToHowManyD1 = d2ToHowManyD1;
    }

    @Override
    public String toString() {
        return String.format("[dep1]=%s\n[dep2]=%s\n%d\t%d",
                dependency1 == null ? "null" : dependency1.toString(),
                dependency2 == null ? "null" : dependency2.toString(),
                d1ToHowManyD2, d2ToHowManyD1);
    }
}
