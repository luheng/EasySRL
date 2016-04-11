package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.model.AttachmentHelper;
import edu.uw.easysrl.qasrl.qg.*;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAPairSurfaceForm;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.query.Query;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * Simulates user response in active learning. When presented with a queryPrompt, the simulator answerOptions with its
 * knowledge of gold dependencies.
 * Created by luheng on 1/5/16.
 */
public class ResponseSimulatorGold {
    private final ParseData parseData;

    public ResponseSimulatorGold(ParseData parseData) {
        this.parseData = parseData;
    }

    /*
    public ImmutableList<Integer> respondToQuery(ScoredQuery<QAStructureSurfaceForm> query) {
        final int sentenceId = query.getSentenceId();
        final Parse goldParse = parseData.getGoldParses().get(sentenceId);
        final Set<Integer> chosenOptions = new HashSet<>();
        IntStream.range(0, query.getQAPairSurfaceForms().size()).boxed()
                .filter(i -> query.getQAPairSurfaceForms().get(i).canBeGeneratedBy(goldParse))
                .forEach(chosenOptions::add);

        if (chosenOptions.size() == 0) {
            chosenOptions.add(query.getBadQuestionOptionId().getAsInt());
        }
        return chosenOptions.stream().sorted().collect(GuavaCollectors.toImmutableList());
    }*/

     public ImmutableList<Integer> respondToQuery(ScoredQuery<QAStructureSurfaceForm> query) {
         final int sentenceId = query.getSentenceId();
         final Parse goldParse = parseData.getGoldParses().get(sentenceId);

         Set<Integer> chosenOptions = new HashSet<>();

         if (!query.isJeopardyStyle()) {
             final ImmutableList<QAStructureSurfaceForm> qaOptions = query.getQAPairSurfaceForms();
             // The gold considers labeled dependency. If the dependency labels don't match, gold outputs "bad queryPrompt".
             // So the gold outputs "bad queryPrompt" in case of dropped pp argument.
             AtomicBoolean nonEmptyGoldArgs = new AtomicBoolean(false);
             qaOptions.stream().flatMap(qa -> qa.getQuestionStructures().stream())
                     .forEach(qstr -> {
                         // FIXME: this doesn't work for the radiobutton version.
                         final ImmutableSet<Integer> goldArgIds = goldParse.dependencies.stream()
                                 .filter(dep -> (dep.getHead() == qstr.predicateIndex
                                                    && dep.getCategory() == qstr.category
                                                    && dep.getArgNumber() == qstr.targetArgNum)
                                         || dep.getHead() == qstr.targetPrepositionIndex)
                                 .map(ResolvedDependency::getArgument)
                                 .distinct()
                                 .collect(GuavaCollectors.toImmutableSet());
                         if (goldArgIds.size() > 0) {
                             nonEmptyGoldArgs.getAndSet(true);
                         }
                         // Checkbox version.
                         if (query.allowMultipleChoices()) {
                             IntStream.range(0, qaOptions.size())
                                     .filter(optionId -> qaOptions.get(optionId).getAnswerStructures().stream()
                                             .anyMatch(astr ->
                                                     goldArgIds.containsAll(astr.argumentIndices) &&
                                                     goldParse.dependencies.containsAll(astr.adjunctDependencies)))
                                     .forEach(chosenOptions::add);
                         }
                         // Radio Button version.
                         else {
                             IntStream.range(0, qaOptions.size())
                                     .filter(optionId -> qaOptions.get(optionId).getAnswerStructures().stream()
                                                 .map(astr -> astr.argumentIndices)
                                                 .anyMatch(argIds -> goldArgIds.containsAll(argIds) &&
                                                                     argIds.containsAll(goldArgIds)))
                                     .forEach(chosenOptions::add);
                         }
                     });
             if (chosenOptions.size() == 0) {
                 if (nonEmptyGoldArgs.get() && query.getUnlistedAnswerOptionId().isPresent()) {
                     chosenOptions.add(query.getUnlistedAnswerOptionId().getAsInt());
                 } else if (query.getBadQuestionOptionId().isPresent()){
                     chosenOptions.add(query.getBadQuestionOptionId().getAsInt());
                }
             }
         } else {
             IntStream.range(0, query.getQAPairSurfaceForms().size()).boxed()
                     .filter(i -> query.getQAPairSurfaceForms().get(i).canBeGeneratedBy(goldParse))
                     .forEach(chosenOptions::add);
             if (chosenOptions.size() == 0) {
                chosenOptions.add(query.getBadQuestionOptionId().getAsInt());
             }
         }
         return chosenOptions.stream().sorted().collect(GuavaCollectors.toImmutableList());
     }
}
