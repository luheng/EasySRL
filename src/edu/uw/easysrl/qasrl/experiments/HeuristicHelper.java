package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.*;

import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.IntStream;


public class HeuristicHelper {
    private final static LexicalizedParser parser = LexicalizedParser.loadModel(
            "./edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz",
            "-maxLength", "100", "-retainTmpSubcategories");
    private final static TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    private final static GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();

    private static final ImmutableSet<String> salientDependencies =
            ImmutableSet.of("appos", "acl", "acl:relcl", "nsubj", "cop");
    private static int cachedSentenceId = -1;
    private static ImmutableSet<TypedDependency> cachedDependencies = null;

    public static void cacheDependenciesAndCoref(final int sentenceId, final ImmutableList<String> sentence) {
        final String[] sent = new String[sentence.size()];
        IntStream.range(0, sentence.size()).forEach(i -> sent[i] = sentence.get(i));
        Tree parse = parser.apply(Sentence.toWordList(sent));
        GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
        cachedDependencies = gs.typedDependenciesCCprocessed().stream()
                .filter(dep -> salientDependencies.contains(dep.reln().toString()))
                .collect(GuavaCollectors.toImmutableSet());
        cachedSentenceId = sentenceId;
    }

    public static Table<Integer, Integer, String> getOptionRelations(final int sentenceId,
                                                                     final ImmutableList<String> sentence,
                                                                     final ScoredQuery<QAStructureSurfaceForm> query) {
        Table<Integer, Integer, String> relations = HashBasedTable.create();
        final int predicateId = query.getPredicateId().getAsInt();
        final int headId = query.getPrepositionIndex().isPresent() ?
                query.getPrepositionIndex().getAsInt() : predicateId;
        if (sentenceId != cachedSentenceId) {
            cacheDependenciesAndCoref(sentenceId, sentence);
        }
        final int numQAs = query.getQAPairSurfaceForms().size();
        for (int opId1 = 0; opId1 < numQAs; opId1++) {
            final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(opId1);
            final ImmutableList<Integer> args1 = qa1.getAnswerStructures().stream()
                    .flatMap(ans -> ans.argumentIndices.stream())
                    .distinct().sorted().collect(GuavaCollectors.toImmutableList());
            final String answer1 = qa1.getAnswer().toLowerCase();
            final int dist1 = Math.abs(headId - args1.get(0));

            for (int opId2 = 0; opId2 < numQAs; opId2++) {
                if (opId2 == opId1 || relations.contains(opId1, opId2) || relations.contains(opId2, opId1)) {
                    continue;
                }
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final ImmutableList<Integer> args2 = qa2.getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream())
                        .distinct().sorted().collect(GuavaCollectors.toImmutableList());
                final String answer2 = qa2.getAnswer().toLowerCase();
                final int dist2 = Math.abs(headId - args2.get(0));

                boolean isSubspan = answer1.endsWith(" " + answer2) || answer1.startsWith(answer2 + " ");

                final boolean isPronoun = PronounList.nonPossessivePronouns.contains(qa2.getAnswer().toLowerCase())
                        && dist2 < dist1;

                final boolean hasAppositive = !answer1.contains(" of ")
                        && cachedDependencies.stream()
                        .filter(dep -> dep.reln().toString().equals("appos"))
                        .anyMatch(dep -> {
                            final int head = dep.gov().index() - 1, child = dep.dep().index() - 1;
                            return (args1.contains(head) && args2.contains(child));
                        });

                final boolean hasRelative = cachedDependencies.stream()
                        .filter(dep -> dep.reln().toString().equals("acl:relcl") || dep.reln().toString().equals("acl"))
                        .anyMatch(dep -> {
                            final int head = dep.gov().index() - 1, child = dep.dep().index() - 1;
                            return args2.contains(head) && child == predicateId;
                        });

                final boolean hasCopula = cachedDependencies.stream()
                        .filter(dep -> dep.reln().toString().equals("cop"))
                        .anyMatch(dep -> {
                            final int head = dep.gov().index() - 1, child = dep.dep().index() - 1;
                            return args2.contains(head) && args1.get(0) < child && child < args2.get(0);
                        })
                        && cachedDependencies.stream()
                        .filter(dep -> dep.reln().toString().equals("nsubj"))
                        .anyMatch(dep -> {
                            final int head = dep.gov().index() - 1, child = dep.dep().index() - 1;
                            return args2.contains(head) && args1.contains(child);
                        });

                if (isSubspan) {
                    relations.put(opId1, opId2, "subspan");
                } else if (isPronoun) {
                    relations.put(opId1, opId2, "pronoun");
                } else if (hasRelative && hasAppositive) {
                    relations.put(opId1, opId2, "relative");
                } else if (hasRelative && hasCopula) {
                    relations.put(opId1, opId2, "relative");
                } else if (hasAppositive) {
                    relations.put(opId1, opId2, "appositive");
                }
            }
        }
        return relations;
    }
}

