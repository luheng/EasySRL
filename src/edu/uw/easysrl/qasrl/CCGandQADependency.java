package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLDependency;

/**
 * Created by luheng on 11/30/15.
 */
public class CCGandQADependency {
    public final ParallelCorpusReader.Sentence sentence;
    public final CCGBankDependencies.CCGBankDependency ccgDependency;
    public final QADependency qaDependency;
    public final int numSRLtoQAMaps, numQAtoSRLMaps;

    public CCGandQADependency(ParallelCorpusReader.Sentence sentence,
                              CCGBankDependencies.CCGBankDependency ccgDependency,
                              QADependency qaDependency, int numSRLtoQAMaps, int numQAtoSRLMaps) {
        this.sentence = sentence;
        this.ccgDependency = ccgDependency;
        this.qaDependency = qaDependency;
        this.numSRLtoQAMaps = numSRLtoQAMaps;
        this.numQAtoSRLMaps = numQAtoSRLMaps;
    }

    @Override
    public String toString() {
        // TODO
        return "";
    }
}
