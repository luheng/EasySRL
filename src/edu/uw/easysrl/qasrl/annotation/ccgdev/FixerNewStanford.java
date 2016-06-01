package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.hcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.parser.common.ParserAnnotations;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.DependencyParseAnnotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.*;

import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

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
    private final static int minVotesToFix = 1;
    private final static double minMarginToFix = 0.33;

    private final static LexicalizedParser parser = LexicalizedParser.loadModel(
            "./edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz",
            "-maxLength", "100", "-retainTmpSubcategories");
    private final static TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    private final static GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();

    private final static Properties props = new Properties();
    static {
        //props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,mention,dcoref");
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,mention,coref");
        props.setProperty("tokenize.whitespace", "true");
    }
    //private final static StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    private static final ImmutableSet<String> salientDependencies =
            ImmutableSet.of("appos", "acl", "acl:relcl", "nsubj", "cop");
    private static int cachedSentenceId = -1;
    private static ImmutableSet<TypedDependency> cachedDependencies = null;
    private static ImmutableList<CorefChain> cachedCorefChains = null;


    public static void cacheDependenciesAndCoref(final int sentenceId, final ImmutableList<String> sentence) {
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
        //TODO: use dummy tokenizer if there is one.
        pipeline.annotate(document);
        cachedCorefChains = document.get(CorefCoreAnnotations.CorefChainAnnotation.class).values().stream()
                .collect(GuavaCollectors.toImmutableList());
        */
        /*
        for (CorefChain cc : document.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
            System.out.println(sentence.stream().collect(Collectors.joining(" ")));
            System.out.println("chain:\t" + cc.getChainID());
            cc.getMentionsInTextualOrder().stream().forEach(mention -> {
                System.out.println(mention.startIndex + "\t" + mention.endIndex + "\t" + mention.toString());
            });
        }*/
        cachedSentenceId = sentenceId;
    }

    public static Table<Integer, Integer, String> getOptionRelations(final int sentenceId,
                                                                     final ImmutableList<String> sentence,
                                                                     final ScoredQuery<QAStructureSurfaceForm> query) {
        Table<Integer, Integer, String> relations = HashBasedTable.create();
        final int predicateId = query.getPredicateId().getAsInt();
        final int headId = query.getPrepositionIndex().isPresent() ? query.getPrepositionIndex().getAsInt() : predicateId;
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

            for (int opId2 = 0; opId2 < numQAs; opId2++) {
                if (opId2 == opId1 || relations.contains(opId1, opId2) || relations.contains(opId2, opId1)) {
                    continue;
                }
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final ImmutableList<Integer> args2 = qa2.getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream())
                        .distinct().sorted().collect(GuavaCollectors.toImmutableList());
                final String answer2 = qa2.getAnswer().toLowerCase();

                final boolean isPronoun = PronounList.nonPossessivePronouns.contains(qa2.getAnswer().toLowerCase())
                        && !answer1.contains(" " + answer2)
                        && !IntStream.range(args2.get(0), headId).mapToObj(sentence::get).anyMatch(","::equals);
                /*
                final boolean isCoref = cachedCorefChains.stream().anyMatch(corefChain ->
                            corefChain.getMentionsInTextualOrder().stream()
                                .anyMatch(m -> m.startIndex - 1 <= args1.get(0) && args1.get(0) < m.endIndex) &&
                            corefChain.getMentionsInTextualOrder().stream()
                                .anyMatch(m -> m.startIndex - 1 <= args2.get(0) && args2.get(0) < m.endIndex)); */

                final boolean hasAppositive = cachedDependencies.stream()
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
                        }) && cachedDependencies.stream()
                        .filter(dep -> dep.reln().toString().equals("nsubj"))
                        .anyMatch(dep -> {
                            final int head = dep.gov().index() - 1, child = dep.dep().index() - 1;
                            return args2.contains(head) && args1.contains(child);
                        });
                // OP1 = X of OP2 / X and OP2
                boolean hasXofY = answer1.endsWith(" of " + answer2);
                if (isPronoun) {
                    relations.put(opId1, opId2, "coref:pronoun");
                } else if (hasRelative && hasAppositive) {
                    relations.put(opId1, opId2, "relative");
                } else if (hasRelative && hasCopula) {
                    relations.put(opId1, opId2, "relative:copula");
                } else if (hasAppositive) {
                    relations.put(opId1, opId2, "appositive");
                } else if (hasXofY) {
                    relations.put(opId1, opId2, "subspan");
                }
            }
        }
        relations.cellSet().forEach(c -> {
            System.out.println(query.getOptions().get(c.getRowKey()) + "\t"
                    + query.getOptions().get(c.getColumnKey()) + "\t"
                    + c.getValue());
        });
        return relations;
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
            cacheDependenciesAndCoref(sentenceId, sentence);
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
            cacheDependenciesAndCoref(sentenceId, sentence);
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

