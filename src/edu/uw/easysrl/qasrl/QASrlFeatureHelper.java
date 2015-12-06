package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.qa.QASlots;
import edu.uw.easysrl.corpora.qa.QuestionEncoder;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.util.CountDictionary;
import gnu.trove.map.hash.TIntIntHashMap;

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
        String[] qwords = qaDependency.getQuestion();
        String isPassive = String.valueOf(QuestionEncoder.isPassiveVoice(qwords));

        // SRL Label features
        final String[] srlFeatures = {
                "srl=" + srlDependency.getLabel(),
                "core=" + String.valueOf(srlDependency.getLabel().isCoreArgument())
        };
        String qstr = "";
        for (int sid = 0; sid < QASlots.numSlots; sid++) {
            if (sid != QASlots.AUXSlotId && sid != QASlots.TRGSlotId) {
                qstr += qwords[sid] + " ";
            }
        }
        qstr += isPassive;

        for (String srlFeature : srlFeatures) {
            // Label mapping
            String qaLabel = qaDependency.getLabel().toString();
            featureVector.adjustOrPutValue(featureDictionary.addString(srlFeature + "_qalb=" + qaLabel), 1, 1);
            // Question voice.
            featureVector.adjustOrPutValue(featureDictionary.addString(srlFeature + "_voice=" + isPassive), 1, 1);
            for (int slotId = 0; slotId < QASlots.numSlots; slotId++) {
                String prefix = QASlots.slotNames[slotId] + "=";
                featureVector.adjustOrPutValue(featureDictionary.addString(srlFeature + "_" + prefix + qwords[slotId]), 1, 1);
            }
            // Almost the entire question
            featureVector.adjustOrPutValue(featureDictionary.addString(srlFeature + "_qstr=" + qstr), 1, 1);
            // bias feature
            featureVector.adjustOrPutValue(featureDictionary.addString(srlFeature), 1, 1);
        }
        featureVector.remove(-1);
        return featureVector;
    }


    public void extractFrequentFeatures(List<AlignedDependency<SRLDependency, QADependency>> data,
                                        final int minimumFeatureFrequency) {
        featureDictionary = new CountDictionary();
        data.forEach(dep -> extractFeatures(dep.sentence, dep.dependency1, dep.dependency2));
        featureDictionary.freeze();
        featureDictionary = new CountDictionary(featureDictionary, minimumFeatureFrequency, false /* can grow */);
        System.out.println("Total features: " + featureDictionary.size());
    }


}
