package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLDependency;

/**
 * Created by luheng on 11/22/15.
 * Mapped PropBank and QA dependency
 */
public class PBandQADependency {
    public final ParallelCorpusReader.Sentence sentence;
    public final SRLDependency pbDependency;
    public final QADependency qaDependency;
    public final int numSRLtoQAMaps, numQAtoSRLMaps;

    public PBandQADependency(ParallelCorpusReader.Sentence sentence, SRLDependency pbDependency,
                             QADependency qaDependency, int numSRLtoQAMaps, int numQAtoSRLMaps) {
        this.sentence = sentence;
        this.pbDependency = pbDependency;
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
