package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.AtomicDouble;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.ParseDataLoader;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.*;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by luheng on 5/26/16.
 */
public class DevReparsing {
    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.maxNumOptionsPerQuery = 6;
        queryPruningParameters.skipPPQuestions = true;
        queryPruningParameters.skipSAdjQuestions = true;
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = -1;
        queryPruningParameters.minPromptConfidence = 0.1;
    }

    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.jeopardyQuestionMinAgreement = 1;
        reparsingParameters.positiveConstraintMinAgreement = 4;
        reparsingParameters.negativeConstraintMaxAgreement = 1;
        reparsingParameters.skipPronounEvidence = false;
        reparsingParameters.jeopardyQuestionWeight = 1.0;
        reparsingParameters.oraclePenaltyWeight = 5.0;
        reparsingParameters.attachmentPenaltyWeight = 2.0;
        reparsingParameters.supertagPenaltyWeight = 2.0;
    }

    private static final ParseData dev = ParseDataLoader.loadFromDevPool().get();
    private static final Map<Integer, NBestList> nbestLists = NBestList.loadNBestListsFromFile("parses.tagged.dev.100best.out", 100).get();
    private static final HITLParser parser = new HITLParser(dev, nbestLists);
    private static final ReparsingHistory history =  new ReparsingHistory(parser);
    private static final Map<Integer, List<AnnotatedQuery>> annotations = AnnotationFileLoader.loadDev();

    public static void main(String[] args) {
        parser.setQueryPruningParameters(queryPruningParameters);
        parser.setReparsingParameters(reparsingParameters);
        int numMatchedAnnotations = 0;

        for (int sentenceId : parser.getAllSentenceIds()) {
            history.addSentence(sentenceId);

            final ImmutableList<String> sentence = parser.getSentence(sentenceId);
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
                    parser.getAllCoreArgQueriesForSentence(sentenceId);
            if (queries == null || queries.isEmpty() || !annotations.containsKey(sentenceId)) {
                continue;
            }
            IntStream.range(0, annotations.get(sentenceId).size())
                    .forEach(id -> annotations.get(sentenceId).get(id).annotationId = id);
            Set<Integer> matchedAnnotationId = new HashSet<>();

            for (ScoredQuery<QAStructureSurfaceForm> query : queries) {
                final Optional<AnnotatedQuery> matchAnnotationOpt =
                        ExperimentUtils.getAlignedAnnotatedQuery(query, annotations.get(sentenceId));
                if (!matchAnnotationOpt.isPresent()) {
                    continue;
                }
                final AnnotatedQuery annotation = matchAnnotationOpt.get();
                final ImmutableList<ImmutableList<Integer>> matchedResponses = annotation.getResponses(query);
                if (matchedResponses.stream().filter(r -> r.size() > 0).count() < 5) {
                    continue;
                }
                if (matchedAnnotationId.contains(annotation.annotationId)) {
                    continue;
                } else {
                    matchedAnnotationId.add(annotation.annotationId);
                }

                ///// TODO: make heuristic pipeline.
                ///// Heuristics
                final int[] optionDist = new int[query.getOptions().size()];
                Arrays.fill(optionDist, 0);
                matchedResponses.forEach(r -> r.stream().forEach(op -> optionDist[op] ++));
                int[] newOptionDist = new int[optionDist.length];
                Arrays.fill(newOptionDist, 0);
                for (ImmutableList<Integer> response : matchedResponses) {
                    final ImmutableList<Integer> pronounFix = Fixer.pronounFixer(sentence, query, response);
                    final ImmutableList<Integer> appositiveFix = Fixer.appositiveFixer(sentence, query, response);
                    final ImmutableList<Integer> subspanFix = Fixer.subspanFixer(sentence, query, response);
                    final ImmutableList<Integer> relative = Fixer.relativeFixer(sentence, query, response);
                    List<Integer> fixedResopnse = response;
                    if (!relative.isEmpty()) {
                        fixedResopnse = relative;
                    } else if (!appositiveFix.isEmpty()) {
                        fixedResopnse = appositiveFix;
                    } else if (!pronounFix.isEmpty()) {
                        fixedResopnse = pronounFix;
                    } else if (!subspanFix.isEmpty()) {
                        fixedResopnse = subspanFix;
                    }
                    fixedResopnse.stream().forEach(op -> newOptionDist[op] ++);
                }
                final ImmutableSet<Constraint> constraints = getConstraints(query, newOptionDist);
                //System.out.println("---");
                //constraints.forEach(c -> System.out.println(c.toString(sentence)));

                history.addEntry(sentenceId, query, parser.getUserOptions(query, newOptionDist), constraints);
                if (history.lastIsWorsened()) {
                    history.printLatestHistory();
                    System.out.println(query.toString(sentence, 'G', parser.getGoldOptions(query), '*', optionDist));
                }
            }

            numMatchedAnnotations += matchedAnnotationId.size();
        }
        System.out.println("Num. matched annotation:\t" + numMatchedAnnotations);
        history.printSummary();
    }

    private static ImmutableSet<Constraint> getConstraints(final ScoredQuery<QAStructureSurfaceForm> query,
                                                           final int[] optionDist) {
        final Set<Constraint> constraints = new HashSet<>();

        final int naOptionId = query.getBadQuestionOptionId().getAsInt();
        final int numQA = query.getQAPairSurfaceForms().size();
        /*
        if (optionDist[naOptionId] >= reparsingParameters.positiveConstraintMinAgreement) {
            query.getQAPairSurfaceForms().stream()
                    .flatMap(qa -> qa.getQuestionStructures().stream())
                    .forEach(qstr -> constraints
                            .add(new Constraint.SupertagConstraint(qstr.predicateIndex, qstr.category, false,
                                    reparsingParameters.supertagPenaltyWeight)));
            return ImmutableSet.copyOf(constraints);
        }*/

        final ImmutableList<Integer> numVotes = IntStream.range(0, numQA)
                .mapToObj(i -> optionDist[i]).collect(GuavaCollectors.toImmutableList());
        final ImmutableList<Integer> optionOrder = IntStream.range(0, numQA).boxed()
                .sorted((i, j) -> Integer.compare(-numVotes.get(i), -numVotes.get(j)))
                .collect(GuavaCollectors.toImmutableList());

        Set<Integer> skipOps = new HashSet<>();
        final int headId = query.getPrepositionIndex().isPresent() ?
                query.getPrepositionIndex().getAsInt() : query.getPredicateId().getAsInt();

        for (int opId1 : optionOrder) {
            if (skipOps.contains(opId1)) {
                continue;
            }
            final int votes = numVotes.get(opId1);
            final QAStructureSurfaceForm qa = query.getQAPairSurfaceForms().get(opId1);
            final ImmutableList<Integer> argIds = qa.getAnswerStructures().stream()
                    .flatMap(ans -> ans.argumentIndices.stream())
                    .distinct().sorted()
                    .collect(GuavaCollectors.toImmutableList());
            final String opStr = qa.getAnswer().toLowerCase();

            // Handle subspan/superspan.
            boolean hasDisjunctiveConstraints = false;
            for (int opId2 : optionOrder) {
                if (opId2 != opId1) {
                    final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                    final String opStr2 = qa2.getAnswer().toLowerCase();
                    if (opStr.endsWith(" of " + opStr2) || opStr.endsWith(" and " + opStr2)|| opStr.startsWith(opStr2 + " and ")) {
                        //    opStr2.endsWith(" of " + opStr) || opStr2.endsWith(" and " + opStr) || opStr2.startsWith(opStr + " and ")) {
                        final ImmutableList<Integer> concatArgs = Stream
                                .concat(argIds.stream(), qa2.getArgumentIndices().stream())
                                .distinct().sorted().collect(GuavaCollectors.toImmutableList());
                       if (votes + numVotes.get(opId2) >= reparsingParameters.positiveConstraintMinAgreement) {
                       // if (votes > reparsingParameters.positiveConstraintMinAgreement) {
                            constraints.add(new Constraint.DisjunctiveAttachmentConstraint(headId, concatArgs, true, 1.0));
                            hasDisjunctiveConstraints = true;
                            skipOps.add(opId2);
                        }
                    }
                }
            }
            if (hasDisjunctiveConstraints) {
                continue;
            }
            if (votes >= reparsingParameters.positiveConstraintMinAgreement) {
                if (argIds.size() == 1) {
                    constraints.add(new Constraint.AttachmentConstraint(headId, argIds.get(0), true, 1.0));
                } else {
                    constraints.add(new Constraint.DisjunctiveAttachmentConstraint(headId, argIds, true, 1.0));
                }
            } else if (votes <= reparsingParameters.negativeConstraintMaxAgreement && !skipOps.contains(opId1)) {
                argIds.forEach(argId ->
                        constraints.add(new Constraint.AttachmentConstraint(headId, argId, false, 1.0)));
            }
        }
        constraints.forEach(c -> c.setStrength(reparsingParameters.attachmentPenaltyWeight));
        return ImmutableSet.copyOf(constraints);
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
