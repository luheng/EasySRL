package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.hcoref.CorefCoreAnnotations;
import edu.stanford.nlp.hcoref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.*;

import edu.stanford.nlp.util.CoreMap;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.Prepositions;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.qg.util.VerbHelper;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;
import scala.util.parsing.combinator.SubSequence;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;



/**
 * Pronoun:    if exists a pronoun A and votes(pronoun) > 0: C+(A), C-(everything else)
 * Appositive: any A,B construction, where votes(A) is high-agreement, and votes(B) > 0: C+(A,B)
 * Relative:   any "A is/, B who/that" construction, where votes(A) > 0, and votes(B) > 0: C+(B), C-(everything else)
 * Subspan:    any
 * Coordinate: any "A and/or/as well as/nor B" construction, where votes(A) > 0 and votes(B) > 0, C+(A,B)
 * Created by luheng on 5/29/16.
 */

/**
 * A cleaner version of the heurstic fixer.
 * Created by luheng on 5/26/16.
 */
public class FixerNewStanford {

    final static int minVotesToFix = 1;
    final static double minMarginToFix = 0.33;

    final static LexicalizedParser parser = LexicalizedParser.loadModel(
            "./lib/stanford-english-corenlp-2016-01-10-models/edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz",
            "-maxLength", "100", "-retainTmpSubcategories");
    final static TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    final static GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();

    static final ImmutableSet<String> salientDependencies = ImmutableSet.of("appos", "acl", "acl:relcl", "nsubj", "cop");
    static int cachedSentenceId = -1;
    static ImmutableSet<TypedDependency> cachedDependencies = null;


    public static void cacheDependencies(final int sentenceId, final ImmutableList<String> sentence) {
        final String[] sent = new String[sentence.size()];
        IntStream.range(0, sentence.size()).forEach(i -> sent[i] = sentence.get(i));
        Tree parse = parser.apply(Sentence.toWordList(sent));
        GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
        // TODO: double check that we are using enhanced dependencies.
        cachedDependencies = gs.typedDependenciesCCprocessed().stream()
                .filter(dep -> salientDependencies.contains(dep.reln().toString()))
                .collect(GuavaCollectors.toImmutableSet());
        //cachedDependencies.forEach(System.out::println);

        //// Cache coref chains ..
        /*
        Annotation document =  new Annotation(sentence.stream().collect(Collectors.joining(" ")));
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,mention,coref");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        pipeline.annotate(document);
        System.out.println("---");
        System.out.println("coref chains");
        for (CorefChain cc : document.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
            for (cc.ge)
            System.out.println("\t"+cc);
        }
        for (CoreMap corefSent : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            System.out.println("---");
            System.out.println("mentions");
            for (Mention m : corefSent.get(CorefCoreAnnotations.CorefMentionsAnnotation.class)) {
                System.out.println("\t"+m);
            }
        }*/
        cachedSentenceId = sentenceId;
    }

    public static ImmutableList<ImmutableList<Integer>> getEquivalentOptionCluster(final int sentenceId,
                                                                                   final ImmutableList<String> sentence,
                                                                                   final ScoredQuery<QAStructureSurfaceForm> query) {
        if (sentenceId != cachedSentenceId) {
            cacheDependencies(sentenceId, sentence);
        }
        final int numQAs = query.getQAPairSurfaceForms().size();
        Set<Integer> processed = new HashSet<>();
        for (int opId1 = 0; opId1 < numQAs; opId1++) {
            if (processed.contains(opId1)) {
                continue;
            }
            final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(opId1);
            final ImmutableList<Integer> args1 = qa1.getAnswerStructures().stream()
                    .flatMap(ans -> ans.argumentIndices.stream())
                    .distinct().sorted().collect(GuavaCollectors.toImmutableList());
            for (int opId2 = 0; opId2 < numQAs; opId2++) {
                if (opId2 == opId1 || processed.contains(opId2)) {
                    continue;
                }
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final ImmutableList<Integer> args2 = qa2.getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream())
                        .distinct().sorted().collect(GuavaCollectors.toImmutableList());

            }
        }
        return null;
    }

    /**
     * Pronoun rule:
     * @param query
     * @param options
     * @param optionDist
     * @return
     */
    public static ImmutableList<Integer> pronounFixer(final int sentenceId,
                                                      final ImmutableList<String> sentence,
                                                      final ScoredQuery<QAStructureSurfaceForm> query,
                                                      final ImmutableList<Integer> options,
                                                      final int[] optionDist) {
        if (sentenceId != cachedSentenceId) {
            cacheDependencies(sentenceId, sentence);
        }
        final int headId = query.getPrepositionIndex().isPresent() ?
                query.getPrepositionIndex().getAsInt() : query.getPredicateId().getAsInt();
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int minOptionDist = options.stream()
                .filter(op -> op < numQAs)
                .mapToInt(op -> {
                    final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(op);
                    return qa1.getAnswerStructures().stream()
                            .flatMap(ans -> ans.argumentIndices.stream())
                            .mapToInt(argId -> Math.abs(argId - headId))
                            .min().getAsInt();
                })
                .min().orElse(-1);
        for (int opId2 = 0; opId2 < numQAs; opId2++) {
            if (optionDist[opId2] < minVotesToFix) {
                continue;
            }
            final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
            final String op2 = query.getOptions().get(opId2).toLowerCase();
            final int dist2 = Math.abs(qa2.getAnswerStructures().get(0).argumentIndices.get(0) - headId);
            if (PronounList.nonPossessivePronouns.contains(op2) && dist2 < minOptionDist) {
                return ImmutableList.of(opId2);
            }
        }
        return ImmutableList.of();
    }

    public static ImmutableList<Integer> appositiveFixer(final int sentenceId,
                                                         final ImmutableList<String> sentence,
                                                         final ScoredQuery<QAStructureSurfaceForm> query,
                                                         final ImmutableList<Integer> options,
                                                         final int[] optionDist) {
        if (sentenceId != cachedSentenceId) {
            cacheDependencies(sentenceId, sentence);
        }
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int predicateId = query.getPredicateId().getAsInt();
        final Set<Integer> newOptions = new HashSet<>();
        for (int opId1 : options) {
            if (opId1 >= numQAs) {
                continue;
            }
            final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(opId1);
            final ImmutableList<Integer> args1 = qa1.getAnswerStructures().stream()
                    .flatMap(ans -> ans.argumentIndices.stream())
                    .distinct().sorted().collect(GuavaCollectors.toImmutableList());
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2) {
                    continue;
                }
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final ImmutableList<Integer> args2 = qa2.getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream())
                        .distinct().sorted().collect(GuavaCollectors.toImmutableList());
                //System.err.println(cachedDependencies + "\n" + args1 + "\n" + args2);
                boolean hasAppositive = cachedDependencies.stream()
                        .filter(dep -> dep.reln().toString().equals("appos"))
                        .anyMatch(dep -> {
                            final int head = dep.gov().index() - 1, child = dep.dep().index() - 1;
                            return (args1.contains(head) && args2.contains(child));
                                    // ||(args2.contains(head) && args1.contains(child));
                        });
                boolean hasRelative = cachedDependencies.stream()
                        .filter(dep -> dep.reln().toString().equals("acl:relcl") || dep.reln().toString().equals("acl"))
                        .anyMatch(dep -> {
                            final int head = dep.gov().index() - 1, child = dep.dep().index() - 1;
                            return args2.contains(head) && child == predicateId;
                        });
                //System.out.println("has appositive:\t" + hasAppositive + "\thas relative:\t" + hasRelative);
                if (hasAppositive && hasRelative) {
                    System.out.println("[relative fix:]");
                    return ImmutableList.of(opId2);
                }
                if (hasAppositive) {
                    System.out.println("[appositive fix:]");
                    newOptions.add(opId2);
                }
                // Handle copula.
                boolean hasCopulaRelation =
                        cachedDependencies.stream()
                                .filter(dep -> dep.reln().toString().equals("cop"))
                                .anyMatch(dep -> {
                                    final int head = dep.gov().index() - 1, child = dep.dep().index() - 1;
                                    return args2.contains(head) && args1.get(0) < child && child < args2.get(0);
                                }) &&
                        cachedDependencies.stream()
                                .filter(dep -> dep.reln().toString().equals("nsubj"))
                                .anyMatch(dep -> {
                                    final int head = dep.gov().index() - 1, child = dep.dep().index() - 1;
                                    return args2.contains(head) && args1.contains(child);
                                });
                if (hasCopulaRelation && hasRelative) {
                    System.out.println("[copula relative fix:]");
                    return ImmutableList.of(opId2);
                }
            }
        }
        return ImmutableList.of();
        /*
        if (newOptions.isEmpty()) {
            return ImmutableList.of();
        }
        newOptions.addAll(options);
        return newOptions.stream().distinct().sorted().collect(GuavaCollectors.toImmutableList());
        */
    }
}

