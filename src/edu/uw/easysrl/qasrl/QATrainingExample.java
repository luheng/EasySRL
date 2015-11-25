package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;
import edu.uw.easysrl.syntax.training.CompressedChart;

import java.util.List;

/**
 * Created by luheng on 11/23/15.
 */

public class QATrainingExample {

    abstract class LabelMappingTrainingExample {
        private static final long serialVersionUID = 1L;

        private final SRLDependency srlDependency;
        private final QADependency qaDependency;
        private final List<InputReader.InputWord> words;

        public LabelMappingTrainingExample(SRLDependency srlDependency,
                                           QADependency qaDependency,
                                           List<InputReader.InputWord> words) {
            super();
            this.srlDependency = srlDependency;
            this.qaDependency = qaDependency;
            this.words = words;
        }

        public abstract void computePosterior(double[] featureWeights, double[] unnormalizedPosterior);
    }

    public class SrlLabelPredictionExample extends LabelMappingTrainingExample {
        public SrlLabelPredictionExample(SRLDependency srlDependency,
                                         QADependency qaDependency,
                                         List<InputReader.InputWord> words) {
            super(srlDependency, qaDependency, words);
        }

        @Override
        public void computePosterior(double[] featureWeights, double[] unnormalizedPosterior) {

        }
    }
}
