package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.Partitive;
import edu.uw.easysrl.qasrl.qg.util.Prepositions;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;
import scala.tools.cmd.gen.AnyVals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by luheng on 4/30/16.
 */
public class TemplateHelper {

    public static String identifyTemplate(final ImmutableList<String> sentence,
                                          final ScoredQuery<QAStructureSurfaceForm> query,
                                          final int opId1, final int opId2) {
        final int predicateId = query.getPredicateId().getAsInt();
        final String op1 = query.getOptions().get(opId1).toLowerCase();
        final String op2 = query.getOptions().get(opId2).toLowerCase();
        final int argId1 = query.getQAPairSurfaceForms().get(opId1).getAnswerStructures().get(0).argumentIndices.get(0);
        final int argId2 = query.getQAPairSurfaceForms().get(opId2).getAnswerStructures().get(0).argumentIndices.get(0);

        // op1 is superspan of op2
        for (String tok : Partitive.tokens) {
            if (op1.equals(tok + " of " + op2)) {
                return "[op1] : [partitive] of [op2]";
            }
        }
        if (op1.contains(" of " + op2)) {
            return "[op1] := X of [op2]";
        }
        if (op1.endsWith(op2)) {
            return "[op1] := X [op2]";
        }
        // op1 is subspan of op2
        for (String pp : ImmutableList.copyOf(Prepositions.prepositionWords)) {
            if (!pp.equals("of") && op2.startsWith(op1 + " " + pp + " ")) {
                return "[op2] := [op1] [pp] X";
            }
        }
        if (op2.startsWith(op1 + " ")) {
            return "[op2] := [op1] X";
        }
        // Appositive templates.
        int commaBetweenArgs = 0, commaBetweenPredArg1 = 0, commaBetweenArg2Pred = 0;
        for (int i = argId1 + 1; i < argId2; i++) {
            if (sentence.get(i).equals(",")) {
                commaBetweenArgs ++;
            }
        }
        for (int i = predicateId + 1; i < argId1; i++) {
            if (sentence.get(i).equals(",")) {
                commaBetweenPredArg1 ++;
            }
        }
        for (int i = argId2 + 1; i < predicateId; i++) {
            if (sentence.get(i).equals(",")) {
                commaBetweenArg2Pred ++;
            }
        }
        if (argId1 < argId2 && argId2 < predicateId && commaBetweenArgs == 1 &&
                !(sentence.get(argId2).equalsIgnoreCase("years") && argId2 < sentence.size()
                        && sentence.get(argId2 + 1).equalsIgnoreCase("old"))) {
            if (commaBetweenArg2Pred == 0) {
                //return "[op1] , [op2] [pred]";
                return "[appositive-restrictive]";
            }
            // One or more comma
            if (commaBetweenArg2Pred >= 1) {
                //return "[op1] , [op2] , [pred]";
                return "[appositive]";
            }
        }
        if (predicateId < argId1 && argId1 < argId2 && commaBetweenArgs == 1) {
            if (commaBetweenPredArg1 == 0) {
                //return "[pred] [op1] , [op2]";
                return "[appositive]";
            }
            // One or more comma
            if (commaBetweenPredArg1 >= 1) {
                //return "[pred] , [op1] , [op2]";
                return "[appositive]";
            }
        }
        // Pronoun template.
        if (PronounList.englishPronounSet.contains(op2)) {
            if (argId1 < argId2 && argId2 < predicateId) {
                return "[op1] [pron] [pred]";
            }
            if (predicateId < argId2 && argId2 < argId1) {
                return "[pred] [pron] [op1]";
            }
        }
        return "";
    }
}
