package edu.uw.easysrl.qasrl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * i.e. AlignedCorpus<SRLDependency, QADependency>
 * Created by luheng on 12/6/15.
 */
public class AlignedCorpus<T1, T2> {
    public Map<Integer, List<AlignedDependency<T1, T2>>> training = null;
    public Map<Integer, List<AlignedDependency<T1, T2>>> dev = null;
    public Map<Integer, List<AlignedDependency<T1, T2>>> test = null;

    public AlignedCorpus() {
        training = new HashMap<>();
        dev = new HashMap<>();
        test = new HashMap<>();
    }
}


