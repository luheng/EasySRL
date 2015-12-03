package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.dependencies.QADependency;

/**
 * Created by luheng on 11/30/15.
 */
public class CCGandQADependency {
    public final ParallelCorpusReader.Sentence sentence;
    public final CCGBankDependencies.CCGBankDependency ccgDependency;
    public final QADependency qaDependency;
    public final int numCCGtoQAMaps, numQAtoCCGMaps;

    public CCGandQADependency(ParallelCorpusReader.Sentence sentence,
                              CCGBankDependencies.CCGBankDependency ccgDependency,
                              QADependency qaDependency, int numSRLtoQAMaps, int numQAtoSRLMaps) {
        this.sentence = sentence;
        this.ccgDependency = ccgDependency;
        this.qaDependency = qaDependency;
        this.numCCGtoQAMaps = numSRLtoQAMaps;
        this.numQAtoCCGMaps = numQAtoSRLMaps;
    }

    @Override
    public String toString() {
        // TODO
        return "";
    }
}
