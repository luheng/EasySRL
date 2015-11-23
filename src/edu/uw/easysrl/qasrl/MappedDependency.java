package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLDependency;

/**
 * Created by luheng on 11/22/15.
 * Mapped SRL and QA dependency
 */
public class MappedDependency {
    public final ParallelCorpusReader.Sentence pbSentence;
    public final SRLDependency srlDependency;
    public final QADependency qaDependency;
    public final int numSRLtoQAMaps, numQAtoSRLMaps;

    public MappedDependency(ParallelCorpusReader.Sentence pbSentence, SRLDependency srlDependency,
                            QADependency qaDependency, int numSRLtoQAMaps, int numQAtoSRLMaps) {
        this.pbSentence = pbSentence;
        this.srlDependency = srlDependency;
        this.qaDependency = qaDependency;
        this.numSRLtoQAMaps = numSRLtoQAMaps;
        this.numQAtoSRLMaps = numQAtoSRLMaps;
    }

}
