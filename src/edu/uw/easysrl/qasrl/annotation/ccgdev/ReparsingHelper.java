package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by luheng on 5/30/16.
 */
public class ReparsingHelper {
    public static int[] getNewOptionDist (final ImmutableList<String> sentence,
                                          final ScoredQuery<QAStructureSurfaceForm> query,
                                          final ImmutableList<ImmutableList<Integer>> matchedResponses,
                                          final NBestList nBestList,
                                          final ReparsingConfig config) {
        final int[] optionDist = new int[query.getOptions().size()];
        int[] newOptionDist = new int[optionDist.length];
        Arrays.fill(optionDist, 0);
        Arrays.fill(newOptionDist, 0);
        matchedResponses.forEach(r -> r.stream().forEach(op -> optionDist[op] ++));

        final Multiset<Integer> votes = HashMultiset.create(matchedResponses.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList()));
        final ImmutableList<Integer> agreedOptions = votes.entrySet().stream()
                .filter(e -> e.getCount() >= config.positiveConstraintMinAgreement)
                .map(e -> e.getElement()).distinct().sorted()
                .collect(GuavaCollectors.toImmutableList());
        final ImmutableList<Integer> pronounFix = FixerNew.pronounFixer(query, agreedOptions, optionDist);
        final ImmutableList<Integer> subspanFix = FixerNew.subspanFixer(query, agreedOptions, optionDist);
        final ImmutableList<Integer> appositiveFix = FixerNew.appositiveFixer(sentence, query, agreedOptions, optionDist);
        final ImmutableList<Integer> relativeFix = FixerNew.relativeFixer(sentence, query, agreedOptions, optionDist);
        String fixType = "None";
        List<Integer> fixedResopnse = null;
        if (config.fixPronouns && !pronounFix.isEmpty()) {
            fixedResopnse = pronounFix;
            fixType = "pronoun";
        } else if (config.fixSubspans && !subspanFix.isEmpty()) {
            fixedResopnse = subspanFix;
            fixType = "subspan";
        } else if (config.fixRelatives && !relativeFix.isEmpty()) {
            fixedResopnse = relativeFix;
            fixType = "relative";
        } else if (config.fixAppositves && !appositiveFix.isEmpty()) {
            fixedResopnse = appositiveFix;
            fixType = "appositive";
        }
        if (fixedResopnse != null) {
            fixedResopnse.stream().forEach(op -> newOptionDist[op] += config.positiveConstraintMinAgreement);
        } else {
            for (ImmutableList<Integer> response : matchedResponses) {
                response.stream().forEach(op -> newOptionDist[op] ++);
            }
        }
        return newOptionDist;
    }

    public static ImmutableSet<Constraint> getConstraints(final ScoredQuery<QAStructureSurfaceForm> query,
                                                          final int[] optionDist,
                                                          final NBestList nBestList,
                                                          final ReparsingConfig config) {
        final Set<Constraint> constraints = new HashSet<>();
        final int numQA = query.getQAPairSurfaceForms().size();
        final ImmutableList<QuestionStructure> questionStructures = query.getQAPairSurfaceForms()
                .stream()
                .flatMap(qa -> qa.getQuestionStructures().stream())
                .distinct()
                .collect(GuavaCollectors.toImmutableList());

        if (IntStream.range(0, numQA).map(i -> optionDist[i]).sum() <= config.negativeConstraintMaxAgreement) {
            questionStructures.forEach(qstr -> constraints.add(new Constraint.SupertagConstraint(qstr.predicateIndex,
                    qstr.category, false, config.supertagPenalty)));
            return ImmutableSet.copyOf(constraints);
        }

        final ImmutableList<Integer> numVotes = IntStream.range(0, numQA)
                .mapToObj(i -> optionDist[i]).collect(GuavaCollectors.toImmutableList());
        final ImmutableList<Integer> optionOrder = IntStream.range(0, numQA).boxed()
                .sorted((i, j) -> Integer.compare(-numVotes.get(i), -numVotes.get(j)))
                .collect(GuavaCollectors.toImmutableList());

        Set<Integer> skipOps = new HashSet<>();
        for (int opId1 : optionOrder) {
            if (skipOps.contains(opId1)) {
                continue;
            }
            final int votes = numVotes.get(opId1);
            //final double votes = (numVotes.get(opId1) * 0.2 + getNBestPrior(query, opId1, nBestList)) * 2.5;
            final QAStructureSurfaceForm qa = query.getQAPairSurfaceForms().get(opId1);
            final ImmutableList<Integer> argIds1 = qa.getAnswerStructures().stream()
                    .flatMap(ans -> ans.argumentIndices.stream())
                    .distinct().sorted()
                    .collect(GuavaCollectors.toImmutableList());
            final String opStr = qa.getAnswer().toLowerCase();

            // Handle subspan/superspan.
            if (config.useSubspanDisjunctives) {
                boolean hasDisjunctiveConstraints = false;
                for (int opId2 : optionOrder) {
                    if (opId2 != opId1) {
                        final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                        final String opStr2 = qa2.getAnswer().toLowerCase();
                        final int votes2 = numVotes.get(opId2);
                        final ImmutableList<Integer> argIds2 = qa2.getAnswerStructures().stream()
                                .flatMap(ans -> ans.argumentIndices.stream())
                                .distinct().sorted()
                                .collect(GuavaCollectors.toImmutableList());

                        if (votes + votes2 >= config.positiveConstraintMinAgreement && votes > 0 && votes2 > 0) {
                            if (opStr.startsWith(opStr2 + " and ") || opStr.endsWith(" and " + opStr2)
                                    || opStr2.endsWith(" and " + opStr) || opStr2.startsWith(opStr + " and ")
                                    || opStr.endsWith(" of " + opStr2) || opStr2.endsWith(" of " + opStr)) {
                                for (QuestionStructure qstr : questionStructures) {
                                    final int headId = qstr.targetPrepositionIndex >= 0 ? qstr.targetPrepositionIndex
                                            : qstr.predicateIndex;
                                    final ImmutableList<Integer> concatArgs = Stream
                                            .concat(argIds1.stream(), argIds2.stream())
                                            .filter(argId -> argId != headId)
                                            .distinct().sorted().collect(GuavaCollectors.toImmutableList());
                                    constraints.add(new Constraint.DisjunctiveAttachmentConstraint(headId, concatArgs,
                                            true, config.positiveConstraintPenalty));
                                }
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
            for (QuestionStructure qstr : questionStructures) {
                final int headId = qstr.targetPrepositionIndex >= 0 ? qstr.targetPrepositionIndex : qstr.predicateIndex;
                final ImmutableList<Integer> filteredArgs = argIds1.stream()
                        .filter(argId -> argId != headId)
                        .collect(GuavaCollectors.toImmutableList());
                if (votes >= config.positiveConstraintMinAgreement) {
                    if (filteredArgs.size() == 1) {
                        constraints.add(new Constraint.AttachmentConstraint(headId, filteredArgs.get(0), true,
                                config.positiveConstraintPenalty));
                    } else if (filteredArgs.size() > 1) {
                        constraints.add(new Constraint.DisjunctiveAttachmentConstraint(headId, filteredArgs, true,
                                config.positiveConstraintPenalty));
                    }
                } else if (votes <= config.negativeConstraintMaxAgreement) {
                    filteredArgs.forEach(argId -> constraints.add(new Constraint.AttachmentConstraint(headId, argId,
                            false, config.negativeConstraintPenalty)));
                }
            }
        }
        return ImmutableSet.copyOf(constraints);
    }

    public static ImmutableSet<Constraint> getConstraints2(final int sentenceId,
                                                           final ImmutableList<String> sentence,
                                                           final ScoredQuery<QAStructureSurfaceForm> query,
                                                           final ImmutableList<ImmutableList<Integer>> matchedResponses,
                                                           final NBestList nBestList,
                                                           final ReparsingConfig config) {
        final int[] optionDist = new int[query.getOptions().size()];
        int[] newOptionDist = new int[optionDist.length];
        Arrays.fill(optionDist, 0);
        Arrays.fill(newOptionDist, 0);
        matchedResponses.forEach(r -> r.stream().forEach(op -> optionDist[op] ++));

        final Set<Constraint> constraints = new HashSet<>();
        final int numQA = query.getQAPairSurfaceForms().size();

        // Add supertag constraints.
        //if (IntStream.range(0, numQA).map(i -> optionDist[i]).sum() <= config.negativeConstraintMaxAgreement) {
        if (optionDist[query.getBadQuestionOptionId().getAsInt()] >= config.positiveConstraintMinAgreement) {
            query.getQAPairSurfaceForms().stream()
                    .flatMap(qa -> qa.getQuestionStructures().stream())
                    .distinct()
                    .forEach(qstr -> constraints.add(new Constraint.SupertagConstraint(qstr.predicateIndex,
                            qstr.category, false, config.supertagPenalty)));
            return ImmutableSet.copyOf(constraints);
        }

        final ImmutableList<Integer> numVotes = IntStream.range(0, numQA)
                .mapToObj(i -> optionDist[i]).collect(GuavaCollectors.toImmutableList());
        final ImmutableList<Integer> optionOrder = IntStream.range(0, numQA).boxed()
                .sorted((i, j) -> Integer.compare(-numVotes.get(i), -numVotes.get(j)))
                .collect(GuavaCollectors.toImmutableList());

        Table<Integer, Integer, String> relations = FixerNewStanford.getOptionRelations(sentenceId, sentence, query);
        Set<Integer> skipOps = new HashSet<>();
        for (int opId1 = 0; opId1 < numQA; opId1++) {
            if (skipOps.contains(opId1)) {
                continue;
            }
            skipOps.add(opId1);
            final int votes = numVotes.get(opId1);
            boolean appliedHeuristic = false;
            if (relations.containsRow(opId1)) {
                for (Map.Entry<Integer, String> e : relations.row(opId1).entrySet().stream()
                        .sorted((e1, e2) -> Integer.compare(e1.getKey(), e2.getKey()))
                        .collect(Collectors.toList())) {
                    final int opId2 = e.getKey();
                    final String rel = e.getValue();
                    final int votes2 = numVotes.get(opId2);
                    if (skipOps.contains(opId2)) {
                        continue;
                    }
                    if (config.fixPronouns && rel.startsWith("coref") && votes + votes2 >= config.positiveConstraintMinAgreement) {
                        System.out.println("### coref:\t" + rel);
                        addConstraints(constraints, query, ImmutableList.of(opId1, opId2), true, config);
                        appliedHeuristic = true;
                        skipOps.add(opId2);
                        break;
                    }
                    if (config.fixAppositves && rel.equals("appositive") && votes >= config.positiveConstraintMinAgreement) {
                        System.out.println("### appositives");
                        //addConstraints(constraints, query, ImmutableList.of(opId1, opId2), true, config);
                        addConstraints(constraints, query, ImmutableList.of(opId1), true, config);
                        addConstraints(constraints, query, ImmutableList.of(opId2), true, config);
                        appliedHeuristic = true;
                        skipOps.add(opId2);
                        break;
                    } else if (config.fixRelatives && rel.startsWith("relative") && votes + votes2 >= config.positiveConstraintMinAgreement) {
                        System.out.println("### relatives");
                        addConstraints(constraints, query, ImmutableList.of(opId1, opId2), true, config);
                        //addConstraints(constraints, query, ImmutableList.of(opId1), false, config);
                        //addConstraints(constraints, query, ImmutableList.of(opId2), true, config);
                        appliedHeuristic = true;
                        skipOps.add(opId2);
                        break;
                    }
                    if (config.fixSubspans
                            && rel.equals("subspan")  && votes + votes2 >= config.positiveConstraintMinAgreement
                            && votes > 1 && votes2 > 1 && Math.abs(votes - votes2) <= 1) {
                        System.out.println("### subspans");
                        addConstraints(constraints, query, ImmutableList.of(opId1, opId2), true, config);
                        appliedHeuristic = true;
                        skipOps.add(opId2);
                        break;
                    }
                }
            }
            if (appliedHeuristic) {
                continue;
            }
            if (votes >= config.positiveConstraintMinAgreement) {
                addConstraints(constraints, query, ImmutableList.of(opId1), true, config);
            } else if (votes <= config.negativeConstraintMaxAgreement) {
                addConstraints(constraints, query, ImmutableList.of(opId1), false, config);
            }
        }
        return ImmutableSet.copyOf(constraints);
    }

    private static void addConstraints(final Set<Constraint> constraints,
                                       final ScoredQuery<QAStructureSurfaceForm> query,
                                       final ImmutableList<Integer> options,
                                       boolean positive,
                                       ReparsingConfig config) {
        final ImmutableList<QuestionStructure> questionStructures = query.getQAPairSurfaceForms()
                .stream()
                .flatMap(qa -> qa.getQuestionStructures().stream())
                .distinct()
                .collect(GuavaCollectors.toImmutableList());
        final ImmutableList<Integer> argIds = options.stream()
                .map(query.getQAPairSurfaceForms()::get)
                .flatMap(qa -> qa.getAnswerStructures().stream())
                .flatMap(astr -> astr.argumentIndices.stream())
                .distinct().sorted()
                .collect(GuavaCollectors.toImmutableList());
        for (QuestionStructure qstr : questionStructures) {
            final int headId = qstr.targetPrepositionIndex >= 0 ? qstr.targetPrepositionIndex : qstr.predicateIndex;
            final ImmutableList<Integer> filteredArgs = argIds.stream()
                    .filter(argId -> argId != headId)
                    .collect(GuavaCollectors.toImmutableList());
            if (positive) {
                if (filteredArgs.size() == 1) {
                    constraints.add(new Constraint.AttachmentConstraint(headId, filteredArgs.get(0), true,
                            config.positiveConstraintPenalty));
                } else if (filteredArgs.size() > 1) {
                    constraints.add(new Constraint.DisjunctiveAttachmentConstraint(headId, filteredArgs, true,
                            config.positiveConstraintPenalty));
                }
            } else {
                filteredArgs.forEach(argId -> constraints.add(new Constraint.AttachmentConstraint(headId, argId,
                        false, config.negativeConstraintPenalty)));
            }
        }
    }

    public static double getNBestPrior(final ScoredQuery<QAStructureSurfaceForm> query,
                                       final int optionId,
                                       final NBestList nBestList) {
        final QAStructureSurfaceForm qa = query.getQAPairSurfaceForms().get(optionId);
        final ImmutableList<Integer> argIds = qa.getAnswerStructures().stream()
                .flatMap(astr -> astr.argumentIndices.stream())
                .collect(GuavaCollectors.toImmutableList());
        final double scoreNorm = nBestList.getParses().stream().mapToDouble(p -> p.score).sum();
        return nBestList.getParses()
                .stream()
                .filter(parse -> qa.getQuestionStructures().stream()
                        .anyMatch(qstr -> {
                            final int headId = qstr.targetPrepositionIndex >= 0 ? qstr.targetPrepositionIndex : qstr.predicateIndex;
                            return parse.dependencies.stream()
                                    .filter(dep -> dep.getHead() == headId && dep.getArgument() != dep.getHead())
                                    .anyMatch(dep -> argIds.contains(dep.getArgument()));
                        }))
                .mapToDouble(parse -> parse.score)
                .sum() / scoreNorm;
    }
}
