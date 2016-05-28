package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.util.concurrent.AtomicDouble;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.ParseDataLoader;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.model.HeuristicHelper;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by luheng on 5/26/16.
 */
public class DevReparsing {
    final static int minAgreement = 4;

    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.jeopardyQuestionMinAgreement = 1;
        reparsingParameters.positiveConstraintMinAgreement = 4;
        reparsingParameters.negativeConstraintMaxAgreement = 1;
        reparsingParameters.skipPronounEvidence = false;
        reparsingParameters.jeopardyQuestionWeight = 1.0;
        reparsingParameters.oraclePenaltyWeight = 5.0;
        reparsingParameters.attachmentPenaltyWeight = 3.0;
        reparsingParameters.supertagPenaltyWeight = 0.0;
    }

    private static final ParseData dev = ParseDataLoader.loadFromDevPool().get();
    private static final Map<Integer, NBestList> nbestLists = NBestList.loadNBestListsFromFile("parses.tagged.dev.100best.out", 100).get();
    private static final HITLParser parser = new HITLParser(dev, nbestLists);
    private static final ReparsingHistory history =  new ReparsingHistory(parser);
    private static final Map<Integer, List<AnnotatedQuery>> annotations = AnnotationFileLoader.loadDev();

    public static void main(String[] args) {
        parser.setReparsingParameters(reparsingParameters);
        int numUnmatchedAnnotations = 0, numMatchedAnnotations = 0, numHighAgreementAnnotations = 0,
                numWrongAnnotations = 0;

        for (int sentenceId : parser.getAllSentenceIds()) {
            if (sentenceId < 1500) { continue; }
            history.addSentence(sentenceId);

            final ImmutableList<String> sentence = parser.getSentence(sentenceId);
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
                    parser.getAllCoreArgQueriesForSentence(sentenceId);
            if (queries == null || queries.isEmpty() || !annotations.containsKey(sentenceId)) {
                continue;
            }

            Set<Constraint> allConstraints = new HashSet<>(), allOracleConstraints = new HashSet<>();

            for (ScoredQuery<QAStructureSurfaceForm> query : queries) {
                final Optional<AnnotatedQuery> matchAnnotation =
                        ExperimentUtils.getAlignedAnnotatedQuery(query, annotations.get(sentenceId));
                if (!matchAnnotation.isPresent()) {
                    numUnmatchedAnnotations ++;
                    continue;
                }
                numMatchedAnnotations ++;
                final ImmutableList<ImmutableList<Integer>> matchedResponses = matchAnnotation.get().getResponses(query);
                final Multiset<ImmutableList<Integer>> responses = HashMultiset.create(matchedResponses);
                final Optional<ImmutableList<Integer>> agreedOptionsOpt = responses.stream()
                        .filter(op -> responses.count(op) >= minAgreement)
                        .findFirst();
                if (!agreedOptionsOpt.isPresent()) {
                    //   continue;
                }
                if (Filter.filter(sentence, nbestLists.get(sentenceId), query, matchedResponses)) {
                    continue;
                }
                ///// Heuristics
                final int[] optionDist = new int[query.getOptions().size()];
                Arrays.fill(optionDist, 0);
                matchedResponses.forEach(r -> r.stream().forEach(op -> optionDist[op] ++));
                int[] newOptionDist = new int[optionDist.length];
                Arrays.fill(newOptionDist, 0);
                for (ImmutableList<Integer> response : matchedResponses) {
                    final ImmutableList<Integer> pronounFix = Fixer.pronounFixer(sentence, query, matchedResponses);
                    final ImmutableList<Integer> appositiveFix = Fixer.appositiveFixer(sentence, query, matchedResponses);
                    final ImmutableList<Integer> subspanFix = Fixer.subspanFixer(sentence, query, matchedResponses);
                    final ImmutableList<Integer> clauseFix = Fixer.restrictiveClauseFixer(sentence, query, matchedResponses);
                    List<Integer> fixedResopnse = response;
                    if (!clauseFix.isEmpty()) {
                        fixedResopnse = clauseFix;
                    } else if (!appositiveFix.isEmpty()) {
                        fixedResopnse = appositiveFix;
                    } else if (!pronounFix.isEmpty()) {
                        fixedResopnse = pronounFix;
                    } else if (!subspanFix.isEmpty()) {
                        fixedResopnse = subspanFix;
                    }
                    fixedResopnse.stream().forEach(op -> newOptionDist[op] ++);
                }
                history.addEntry(sentenceId, query,
                        parser.getUserOptions(query, newOptionDist),
                        parser.getConstraints(query, newOptionDist));
                history.printLatestHistory();
                System.out.println(query.toString(sentence, 'G', parser.getGoldOptions(query), '*', newOptionDist));
            }
        }

        System.out.println("Num. unmatched annotation:\t" + numUnmatchedAnnotations);
        System.out.println("Num. matched annotation:\t" + numMatchedAnnotations);
        System.out.println("Num. high-agreement annotation:\t" + numHighAgreementAnnotations);
        System.out.println("Num. high-agreement wrong annotation:\t" + numWrongAnnotations);

        history.printSummary();
    }

    private static ImmutableList<Integer> getAllGoldOption(ScoredQuery<QAStructureSurfaceForm> query, Parse goldParse,
                                                           ImmutableList<Integer> referenceOptions) {
        List<Integer> goldOptions = null;
        final Map<String, List<Integer>> allGoldOptions = getAllGoldOptions(query, goldParse);
        final List<QuestionStructure> questionStructures = query.getQAPairSurfaceForms().stream()
                .flatMap(qa -> qa.getQuestionStructures().stream()).collect(Collectors.toList());
        boolean labeledMatch = false;
        for (QuestionStructure questionStructure : questionStructures) {
            String label = questionStructure.category + "." + questionStructure.targetArgNum;
            // Labeled match.
            if (allGoldOptions.containsKey(label)) {
                goldOptions = allGoldOptions.get(label);
                labeledMatch = true;
                break;
            }
        }
        // Unlabeled match.
        if (goldOptions == null) {
            int maxOverlap = 0;
            List<Integer> bestMatch = null;
            for (List<Integer> gold : allGoldOptions.values()) {
                int overlap = (int) gold.stream().filter(referenceOptions::contains).count();
                if (overlap > maxOverlap) {
                    maxOverlap = overlap;
                    bestMatch = gold;
                }
            }
            if (maxOverlap > 0) {
                goldOptions = bestMatch;
            }
        }
        // Other.
        if (goldOptions == null) {
            goldOptions = ImmutableList.of(query.getBadQuestionOptionId().getAsInt());
        }
        return ImmutableList.copyOf(goldOptions);
    }
    private static Map<String, List<Integer>> getAllGoldOptions(ScoredQuery<QAStructureSurfaceForm> query,
                                                                Parse goldParse) {

        final Set<ResolvedDependency> goldDeps = goldParse.dependencies;
        final List<QAStructureSurfaceForm> qaStructures = query.getQAPairSurfaceForms();
        final int headId = query.getPredicateId().getAsInt();
        final OptionalInt prepositionIdOpt = query.getPrepositionIndex();
        Map<String, List<Integer>> allGoldOptions = new HashMap<>();
        if (prepositionIdOpt.isPresent()) {
            //System.out.println("PP id:\t" + ppId);
            final int ppId = prepositionIdOpt.getAsInt();
            for (int id = 0; id < qaStructures.size(); id++) {
                final QAStructureSurfaceForm qa = qaStructures.get(id);
                for (ResolvedDependency goldDep : goldDeps) {
                    final String label = goldDep.getCategory() + "." + goldDep.getArgNumber();
                    if (goldDep.getHead() == ppId && qa.getAnswerStructures().stream()
                            .anyMatch(ans -> ans.argumentIndices.contains(goldDep.getArgument()))) {
                        if (!allGoldOptions.containsKey(label)) {
                            allGoldOptions.put(label, new ArrayList<>());
                        }
                        allGoldOptions.get(label).add(id);
                        break;
                    }
                }
            }
            return allGoldOptions;
        }
        for (int id = 0; id < qaStructures.size(); id++) {
            final QAStructureSurfaceForm qa = qaStructures.get(id);
            for (ResolvedDependency goldDep : goldDeps) {
                if (goldDep.getHead() == headId && qa.getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream())
                        .anyMatch(argId -> goldDep.getArgument() == argId)) {
                    final String label = goldDep.getCategory() + "." + goldDep.getArgNumber();
                    if (!allGoldOptions.containsKey(label)) {
                        allGoldOptions.put(label, new ArrayList<>());
                    }
                    allGoldOptions.get(label).add(id);
                    break;
                }
            }
        }
        return allGoldOptions;
    }

    private static double printPrior(ScoredQuery<QAStructureSurfaceForm> query, NBestList nBestList) {
        Map<ImmutableList<Integer>, AtomicDouble> distribution = new HashMap<>();
        final List<QAStructureSurfaceForm> qaStructures = query.getQAPairSurfaceForms();
        final Set<String> labelSet = new HashSet<>();
        qaStructures.stream()
                .flatMap(qa -> qa.getQuestionStructures().stream())
                .map(q -> q.category + "." + q.targetArgNum)
                .forEach(labelSet::add);
        final OptionalInt prepositionIdOpt = query.getPrepositionIndex();
        final int headId = prepositionIdOpt.isPresent() ?
                prepositionIdOpt.getAsInt() :
                query.getPredicateId().getAsInt();
        if (prepositionIdOpt.isPresent()) {
            labelSet.add("PP/NP.1");
        }
        for (Parse parse : nBestList.getParses()) {
            final ImmutableList<Integer> options = IntStream.range(0, qaStructures.size())
                    .boxed()
                    .filter(op -> {
                        final QAStructureSurfaceForm qa = qaStructures.get(op);
                        final ImmutableList<Integer> argIds = qa.getAnswerStructures().stream()
                                .flatMap(ans -> ans.argumentIndices.stream())
                                .sorted()
                                .collect(GuavaCollectors.toImmutableList());
                        return parse.dependencies.stream()
                                .filter(dep -> labelSet.contains(dep.getCategory() + "." + dep.getArgNumber()))
                                .anyMatch(dep -> dep.getHead() == headId && argIds.contains(dep.getArgument()));
                    })
                    .collect(GuavaCollectors.toImmutableList());
            if (!distribution.containsKey(options)) {
                distribution.put(options, new AtomicDouble(0));
            }
            distribution.get(options).addAndGet(parse.score);
        }
        double norm = distribution.entrySet().stream()
                .filter(e -> !e.getKey().isEmpty())
                .mapToDouble(e -> e.getValue().get()).sum();
        double entropy = distribution.entrySet().stream()
                .filter(e -> !e.getKey().isEmpty())
                .mapToDouble(p -> p.getValue().get() / norm)
                .map(p -> 0.0 - p * Math.log(p) / Math.log(2))
                .sum();
        distribution.entrySet().forEach(e -> System.out.println(e.getKey() + "\t" + e.getValue()));
        System.out.println("Entropy:\t" + entropy + "\n");
        return entropy;
    }
}
