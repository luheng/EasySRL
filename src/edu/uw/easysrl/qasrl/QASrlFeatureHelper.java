package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.qa.QASlots;
import edu.uw.easysrl.corpora.qa.QuestionEncoder;
import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.model.feature.*;
import edu.uw.easysrl.syntax.model.feature.Feature.FeatureKey;
import edu.uw.easysrl.syntax.training.CompressedChart;
import edu.uw.easysrl.util.CountDictionary;
import gnu.trove.map.hash.TIntIntHashMap;

import java.io.IOException;
import java.util.*;

/**
 * Created by luheng on 11/23/15.
 */
public class QASrlFeatureHelper {
    public CountDictionary featureDictionary = null;

    public int getNumFeatures() {
        return featureDictionary == null ? 0 : featureDictionary.size();
    }

    public Clique getClique(ParallelCorpusReader.Sentence sentence, SRLDependency srlDependency,
                            QADependency qaDependency) {
        TIntIntHashMap features = extractFeatures(sentence, srlDependency, qaDependency);
        int numFeatures = features.size();
        assert (numFeatures > 0);
        int[] featureIds = new int[numFeatures];
        double[] featureValues = new double[numFeatures];
        for (int i = 0; i < numFeatures; i++) {
            featureIds[i] = features.keys()[i];
        }
        Arrays.sort(featureIds);
        for (int i = 0; i < featureIds.length; i++) {
            featureValues[i] = features.get(featureIds[i]);
        }
        return new Clique(featureIds, featureValues);
    }

    public TIntIntHashMap extractFeatures(ParallelCorpusReader.Sentence sentence, SRLDependency srlDependency,
                                          QADependency qaDependency) {
        TIntIntHashMap featureVector = new TIntIntHashMap();
        String pbLabel = srlDependency.getLabel().toString();
        String[] qwords = qaDependency.getQuestion();

        // Label mapping
        String qaLabel = qaDependency.getLabel().toString();
        featureVector.adjustOrPutValue(featureDictionary.addString("lb_lb=" + pbLabel + "_" + qaLabel), 1, 1);
        // Question voice.
        String isPassive = String.valueOf(QuestionEncoder.isPassiveVoice(qwords));
        featureVector.adjustOrPutValue(featureDictionary.addString("lb_voice=" + pbLabel + "_" + isPassive), 1, 1);
        for (int slotId = 0; slotId < QASlots.numSlots; slotId ++) {
            String prefix = "lb_" + QASlots.slotNames[slotId] + "=";
            featureVector.adjustOrPutValue(featureDictionary.addString(prefix + pbLabel + "_" + qwords[slotId]), 1, 1);
        }
        // bias feature
        featureVector.adjustOrPutValue(featureDictionary.addString("lb=" + pbLabel), 1, 1);
        featureVector.remove(-1);
        return featureVector;
    }


    public void extractFrequentFeatures(List<MappedDependency> data, final int minimumFeatureFrequency) {
        featureDictionary = new CountDictionary();
        data.forEach(dep -> extractFeatures(dep.pbSentence, dep.srlDependency, dep.qaDependency));
        featureDictionary.freeze();
        featureDictionary = new CountDictionary(featureDictionary, minimumFeatureFrequency, false /* can grow */);
        System.out.println("Total features: " + featureDictionary.size());
    }


}
