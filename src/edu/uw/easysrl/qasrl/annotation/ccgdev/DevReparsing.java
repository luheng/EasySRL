package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.ParseDataLoader;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.*;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;
import jdk.nashorn.internal.ir.annotations.Immutable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by luheng on 5/26/16.
 */
public class DevReparsing {

    ///////////////////////////////// Knobs...
    final static boolean fixPronouns = false;
    final static boolean fixSubspans = false;
    final static boolean fixAppositves = false;
    final static boolean fixRelatives = false;
    final static boolean fixConjunctions = false;
    final static boolean useSubspanDisjunctives = false;
    final static boolean useOldConstraints = false;

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
        reparsingParameters.positiveConstraintMinAgreement = 3;
        reparsingParameters.negativeConstraintMaxAgreement = 2;
        reparsingParameters.badQuestionMinAgreement = 2;
        reparsingParameters.skipPronounEvidence = false;
        reparsingParameters.jeopardyQuestionWeight = 1.0;
        reparsingParameters.oraclePenaltyWeight = 5.0;
        reparsingParameters.attachmentPenaltyWeight = 2.0;
        reparsingParameters.supertagPenaltyWeight = 2.0;

    }

    private static final ParseData dev = ParseDataLoader.loadFromDevPool().get();
    private static final Map<Integer, NBestList> nbestLists = NBestList
            .loadNBestListsFromFile("parses.tagged.dev.100best.out", 100).get();
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
            for (AnnotatedQuery annotation : annotations.get(sentenceId)) {
                final Optional<ScoredQuery<QAStructureSurfaceForm>> matchQueryOpt =
                        ExperimentUtils.getBestAlignedQuery(annotation, queries);
                if (!matchQueryOpt.isPresent()) {
                    continue;
                }
                final ScoredQuery<QAStructureSurfaceForm> query = matchQueryOpt.get();
                final ImmutableList<ImmutableList<Integer>> matchedResponses = annotation.getResponses(query);
                if (matchedResponses.stream().filter(r -> r.size() > 0).count() < 5) {
                    continue;
                }
                numMatchedAnnotations ++;

                ///// Heuristics
                final int[] optionDist = new int[query.getOptions().size()];
                int[] newOptionDist = new int[optionDist.length];
                Arrays.fill(optionDist, 0);
                Arrays.fill(newOptionDist, 0);
                matchedResponses.forEach(r -> r.stream().forEach(op -> optionDist[op] ++));

                final Multiset<Integer> votes = HashMultiset.create(matchedResponses.stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toList()));
                final ImmutableList<Integer> agreedOptions = votes.entrySet().stream()
                        .filter(e -> e.getCount() >= reparsingParameters.positiveConstraintMinAgreement)
                        .map(e -> e.getElement()).distinct().sorted()
                        .collect(GuavaCollectors.toImmutableList());

                /*
                final ImmutableList<Integer> pronounFix = Fixer.pronounFixer(sentence, query, matchedResponses);
                final ImmutableList<Integer> appositiveFix = Fixer.appositiveFixer(sentence, query, matchedResponses);
                final ImmutableList<Integer> subspanFix = Fixer.subspanFixer(sentence, query, matchedResponses);
                final ImmutableList<Integer> relative = Fixer.relativeFixer(sentence, query, matchedResponses);
                */
                final ImmutableList<Integer> pronounFix = FixerNew.pronounFixer(query, agreedOptions, optionDist);
                final ImmutableList<Integer> appositiveFix = FixerNew.appositiveFixer(sentence, query, agreedOptions, optionDist);
                final ImmutableList<Integer> subspanFix = FixerNew.subspanFixer(sentence, query, agreedOptions, optionDist);
                final ImmutableList<Integer> relativeFix = FixerNew.relativeFixer(sentence, query, agreedOptions, optionDist);
                final ImmutableList<Integer> conjunctionFix = FixerNew.conjunctionFixer(sentence, query, agreedOptions, optionDist);
                //boolean fixedPronoun = false, fixedAppositive = false, fixedSubspan = false, fixedRelative = false;
                String fixType = "None";
                List<Integer> fixedResopnse = null;

                if (fixRelatives && !relativeFix.isEmpty()) {
                    fixedResopnse = relativeFix;
                    fixType = "relative";
                } else if (fixAppositves && !appositiveFix.isEmpty()) {
                    fixedResopnse = appositiveFix;
                    fixType = "appositive";
                } else if (fixPronouns && !pronounFix.isEmpty()) {
                    fixedResopnse = pronounFix;
                    fixType = "pronoun";
                } else if (fixSubspans && !subspanFix.isEmpty()) {
                    fixedResopnse = subspanFix;
                    fixType = "subspan";
                } else if (fixConjunctions && !conjunctionFix.isEmpty()) {
                    fixedResopnse = conjunctionFix;
                    fixType = "conjunction";
                }
                if (fixedResopnse != null) {
                    fixedResopnse.stream().forEach(op -> newOptionDist[op] += 5);
                } else {
                    for (ImmutableList<Integer> response : matchedResponses) {
                        response.stream().forEach(op -> newOptionDist[op] ++);
                    }
                }
                //System.out.println("---");
                //constraints.forEach(c -> System.out.println(c.toString(sentence)));
                final ImmutableSet<Constraint> constraints = getConstraints(query, newOptionDist);
                history.addEntry(sentenceId, query, parser.getUserOptions(query, newOptionDist),
                        useOldConstraints ? parser.getConstraints(query, newOptionDist) : constraints);


               // if (IntStream.range(0, query.getQAPairSurfaceForms().size())
                     //   .anyMatch(i -> optionDist[i] == 1 || optionDist[i] == 2)) {
                //if (hasSpanIssue(query)) {
                if (history.lastIsWorsened()) {
                    history.printLatestHistory();
                    System.out.println(query.toString(sentence, 'G', parser.getGoldOptions(query), '*', optionDist));
                    System.out.println("Fixed:\t" + fixType);
                    System.out.println(query.toString(sentence, 'G', parser.getGoldOptions(query), '*', newOptionDist));
                }
            }
        }
        System.out.println("Num. matched annotation:\t" + numMatchedAnnotations);
        history.printSummary();
    }

    private static boolean hasSpanIssue(final ScoredQuery<QAStructureSurfaceForm> query) {
        for (int opId1 = 0; opId1 < query.getQAPairSurfaceForms().size(); opId1 ++) {
            final QAStructureSurfaceForm qa = query.getQAPairSurfaceForms().get(opId1);
            final ImmutableList<Integer> argIds = qa.getAnswerStructures().stream()
                    .flatMap(ans -> ans.argumentIndices.stream())
                    .distinct().sorted()
                    .collect(GuavaCollectors.toImmutableList());
            final String opStr = qa.getAnswer().toLowerCase();

            for (int opId2 = 0; opId2 < query.getQAPairSurfaceForms().size(); opId2 ++) {
                if (opId2 != opId1) {
                    final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                    final String opStr2 = qa2.getAnswer().toLowerCase();
                    if (opStr.contains(opStr2) || opStr2.contains(opStr)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static ImmutableSet<Constraint> getConstraints(final ScoredQuery<QAStructureSurfaceForm> query,
                                                           final int[] optionDist) {
        final Set<Constraint> constraints = new HashSet<>();

        final int naOptionId = query.getBadQuestionOptionId().getAsInt();
        final int numQA = query.getQAPairSurfaceForms().size();

        if (optionDist[naOptionId] >= reparsingParameters.badQuestionMinAgreement) {
            query.getQAPairSurfaceForms().stream()
                    .flatMap(qa -> qa.getQuestionStructures().stream())
                    .forEach(qstr -> constraints
                            .add(new Constraint.SupertagConstraint(qstr.predicateIndex, qstr.category, false,
                                    reparsingParameters.supertagPenaltyWeight)));
            return ImmutableSet.copyOf(constraints);
        }

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
            if (useSubspanDisjunctives) {
                boolean hasDisjunctiveConstraints = false;
                for (int opId2 : optionOrder) {
                    if (opId2 != opId1) {
                        final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                        final String opStr2 = qa2.getAnswer().toLowerCase();
                        if (votes + numVotes.get(opId2) >= reparsingParameters.positiveConstraintMinAgreement
                                && votes > 0 && numVotes.get(opId2) > 0) {
                            // TODO: + or
                            if (opStr.startsWith(opStr2 + " and ") || opStr.endsWith(" and " + opStr2)
                                    || opStr2.endsWith(" and " + opStr) || opStr2.startsWith(opStr + " and ")) {
                                final ImmutableList<Integer> concatArgs = Stream
                                        .concat(argIds.stream(), qa2.getArgumentIndices().stream())
                                        .distinct().sorted().collect(GuavaCollectors.toImmutableList());
                                if (votes + numVotes.get(opId2) >= reparsingParameters.positiveConstraintMinAgreement
                                        && votes > 0 && numVotes.get(opId2) > 0) {
                                    constraints.add(new Constraint.DisjunctiveAttachmentConstraint(headId, concatArgs, true, 1.0));
                                    hasDisjunctiveConstraints = true;
                                    skipOps.add(opId2);
                                }
                            } else if (opStr.endsWith(" of " + opStr2) || opStr2.endsWith(" of " + opStr)) {
                                final ImmutableList<Integer> concatArgs = Stream
                                        .concat(argIds.stream(), qa2.getArgumentIndices().stream())
                                        .distinct().sorted().collect(GuavaCollectors.toImmutableList());
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

    private static ImmutableList<Integer> getGoldOption(ScoredQuery<QAStructureSurfaceForm> query, Parse goldParse,
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

}
