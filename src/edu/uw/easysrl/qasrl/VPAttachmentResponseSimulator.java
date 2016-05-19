package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableMap;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.qg.*;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAPairSurfaceForm;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import static edu.uw.easysrl.util.GuavaCollectors.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class VPAttachmentResponseSimulator implements ResponseSimulator {
    private final ImmutableMap<Integer, Parse> parses;

    public VPAttachmentResponseSimulator(ImmutableMap<Integer, Parse> parses) {
        this.parses = parses;
    }

     public ImmutableList<Integer> respondToQuery(ScoredQuery<QAStructureSurfaceForm> query) {
         final int sentenceId = query.getSentenceId();
         final Parse parse = parses.get(sentenceId);

         final ImmutableList<QAStructureSurfaceForm> qaOptions = query.getQAPairSurfaceForms();
         // assume the target dep is the same for all
         final ResolvedDependency targetDep = qaOptions.get(0).getQAPairs().get(0).getTargetDependency();

         final ImmutableList<Integer> chosenOptions;
         ImmutableList<Integer> opts = IntStream.range(0, qaOptions.size())
             .filter(optionId -> qaOptions.get(optionId).getAnswerStructures().stream()
             .anyMatch(astr -> Stream.concat(Stream.of(targetDep), astr.adjunctDependencies.stream())
             .allMatch(answerDep -> parse.dependencies.stream()
             .anyMatch(goldDep -> !(goldDep.getCategory().isFunctionInto(Category.valueOf("S\\NP")) && goldDep.getArgNumber() == 1) &&
                       (answerDep.getHead() == goldDep.getHead() &&
                        answerDep.getArgument() == goldDep.getArgument()) ||
                       (answerDep.getHead() == goldDep.getArgument() &&
                        answerDep.getArgument() == goldDep.getHead())))))
             .boxed()
             .collect(toImmutableList());
         if(opts.isEmpty() || query.getQAPairSurfaceForms().stream().flatMap(sf -> sf.getQuestionStructures().stream()).noneMatch(qStr -> {
                     Category category = qStr.category;
                     Category parseCategory = parse.categories.get(qStr.predicateIndex);
                     return category.isFunctionInto(parseCategory) ||
                         parseCategory.isFunctionInto(category);
                 })) {
             chosenOptions = ImmutableList.of(qaOptions.size());
         } else {
             chosenOptions = opts;
         }
         return chosenOptions;
     }
}
