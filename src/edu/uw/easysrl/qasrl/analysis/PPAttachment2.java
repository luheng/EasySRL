package edu.uw.easysrl.qasrl.analysis;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.AnalysisHelper;
import edu.uw.easysrl.qasrl.DebugHelper;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.pomdp.POMDP;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Find PP attachment ambiguity without generation questions. (because we can't generate questions for pp-args)
 * Created by luheng on 3/15/16.
 */
public class PPAttachment2 {

    public static Set<Category> verbAdjuncts = new HashSet<>();
    public static Set<Category> nounAdjuncts = new HashSet<>();
    static {
        verbAdjuncts.add(Category.valueOf("((S\\NP)\\(S\\NP))/NP"));
        verbAdjuncts.add(Category.valueOf("((S\\NP)\\(S\\NP))/S[dcl]"));
        nounAdjuncts.add(Category.valueOf("(NP\\NP)/NP"));
    }

    public enum AttachmentType {
        VERB_ARGUMENT, VERB_ADJUNCT, NOUN_ADJUNCT, PP_ARGUMENT;
    }

    final int sentenceId;
    final int ppHead;
    final List<String> sentence;
    final List<Parse> parses;
    final Parse goldParse;

    Table<AttachmentType, Integer, Set<Integer>> attachments;
    AttachmentType goldAttachmentType, onebestAttachmentType;
    // Noun or verb attachment.
    int goldAttachment1, onebestAttachment1;
    // The argument of the PP.
    int goldAttachment2, onebestAttachment2;

    private PPAttachment2(int sentenceId, int ppHead, final List<String> sentence, final List<Parse> parses,
                          final Parse goldParse) {
        this.sentenceId = sentenceId;
        this.ppHead = ppHead;
        this.sentence = sentence;
        this.attachments = HashBasedTable.create();
        this.parses = parses;
        this.goldParse = goldParse;
    }

    public static Optional<PPAttachment2> findPPAttachmentAmbiguity(int sentenceId, int ppHead, List<String> sentence,
                                                                    List<Parse> parses, Parse goldParse) {
        PPAttachment2 ppAttachment = new PPAttachment2(sentenceId, ppHead, sentence, parses, goldParse);

        for (int parseId = 0; parseId < parses.size(); parseId++) {
            final Parse parse = parses.get(parseId);
            for (Table.Cell<AttachmentType, Integer, Boolean> c : getAttachments(ppHead, parse).cellSet()) {
                AttachmentType attachmentType = c.getRowKey();
                int attachmentId = c.getColumnKey();
                ppAttachment.addAttachment(attachmentType, attachmentId, parseId);
                if (parseId == 0) {
                    if (attachmentType != AttachmentType.PP_ARGUMENT) {
                        ppAttachment.onebestAttachmentType = attachmentType;
                        // Look at directed attachment accuracy.
                        ppAttachment.onebestAttachment1 = attachmentType == AttachmentType.VERB_ARGUMENT ? -attachmentId : attachmentId;
                        //ppAttachment.onebestAttachment1 = attachmentId;
                    } else {
                        ppAttachment.onebestAttachment2 = attachmentId;
                    }
                }
            }
        }
        for (Table.Cell<AttachmentType, Integer, Boolean> c : getAttachments(ppHead, goldParse).cellSet()) {
            AttachmentType attachmentType = c.getRowKey();
            int attachmentId = c.getColumnKey();
            ppAttachment.addAttachment(attachmentType, attachmentId, -1 /* id for gold */);
            if (attachmentType != AttachmentType.PP_ARGUMENT) {
                ppAttachment.goldAttachmentType = attachmentType;
                ppAttachment.goldAttachment1 = attachmentId;
            } else {
                ppAttachment.goldAttachment2 = attachmentId;
            }
        }
        int ambiguity = ppAttachment.attachments.rowKeySet().size() - 1;
        return ambiguity > 1 ? Optional.of(ppAttachment) : Optional.empty();
    }

    public void prettyPrint() {
        System.out.println("SID=" + sentenceId + "\t" + sentence.stream().collect(Collectors.joining(" ")));
        System.out.println(String.format("%d:%s", ppHead, sentence.get(ppHead)));
        final double totalScore = AnalysisHelper.getScore(parses);
        for (AttachmentType attachmentType : new AttachmentType[] {
                AttachmentType.VERB_ARGUMENT, AttachmentType.VERB_ADJUNCT, AttachmentType.NOUN_ADJUNCT,
                AttachmentType.PP_ARGUMENT }) {
            if (!attachments.rowKeySet().contains(attachmentType)) {
                continue;
            }
            List<Integer> attachmentIds = attachments.row(attachmentType).keySet().stream().sorted()
                    .collect(Collectors.toList());
            List<Double> scores = attachmentIds.stream()
                    .map(id -> AnalysisHelper.getScore(attachments.get(attachmentType, id), parses) / totalScore)
                    .collect(Collectors.toList());
            double scoreSum = scores.stream().mapToDouble(s -> s).sum();
            System.out.println(String.format("%s\t%.3f", attachmentType, scoreSum));
            for (int i = 0; i < attachmentIds.size(); i++) {
                int attachmentId = attachmentIds.get(i);
                boolean isGold = attachments.get(attachmentType, attachmentId).contains(-1 /* gold parse */);
                List<Integer> parseIds = attachments.get(attachmentType, attachmentId).stream()
                        .filter(id -> id >= 0).sorted()
                        .collect(Collectors.toList());
                System.out.println(String.format("%s%d:%s\t%.3f\t%s",
                        (isGold ? "*" : ""),
                        attachmentId, sentence.get(attachmentId), scores.get(i),
                        DebugHelper.getShortListString(parseIds)));
            }
            System.out.println();
        }
        System.out.println();
    }

    private static Table<AttachmentType, Integer, Boolean> getAttachments(int ppHead, Parse parse) {
        Table<AttachmentType, Integer, Boolean> attachments = HashBasedTable.create();
        final Category category = parse.categories.get(ppHead);

        // Add adjunct attachments.
        final Set<ResolvedDependency> children = parse.dependencies.stream()
                .filter(d -> d.getHead() == ppHead)
                .collect(Collectors.toSet());

        if (verbAdjuncts.contains(category)) {
            // verb arg: 2, pp arg: 3
            children.forEach(d -> {
                if (d.getArgNumber() == 2) {
                    attachments.put(AttachmentType.VERB_ADJUNCT, d.getArgument(), Boolean.TRUE);
                } else if (d.getArgNumber() == 3) {
                    attachments.put(AttachmentType.PP_ARGUMENT, d.getArgument(), Boolean.TRUE);
                }
            });
        } else if (nounAdjuncts.contains(category)) {
            // noun arg: 1, pp arg: 2
            children.forEach(d -> {
                if (d.getArgNumber() == 1) {
                    attachments.put(AttachmentType.NOUN_ADJUNCT, d.getArgument(), Boolean.TRUE);
                } else if (d.getArgNumber() == 2) {
                    attachments.put(AttachmentType.PP_ARGUMENT, d.getArgument(), Boolean.TRUE);
                }
            });
        } else if (category.isFunctionInto(Category.PP)) {
            children.stream()
                    .filter(d -> d.getArgNumber() == 1)
                    .forEach(d -> attachments.put(AttachmentType.PP_ARGUMENT, d.getArgument(), Boolean.TRUE));
        }
        // Find pp-arg attachments.
        parse.dependencies.stream()
                .filter(d -> d.getArgument() == ppHead &&
                        d.getCategory().getArgument(d.getArgNumber()) == Category.PP)
                .forEach(d -> attachments.put(AttachmentType.VERB_ARGUMENT, d.getHead(), Boolean.TRUE));
        return attachments;
    }

    private void addAttachment(AttachmentType type, int argId, int parseId) {
        if (!attachments.contains(type, argId)) {
            attachments.put(type, argId, new HashSet<>());
        }
        attachments.get(type, argId).add(parseId);
    }

    public static void main(String[] args) {
        POMDP learner = new POMDP(100 /* nbest */, 10000 /* horizon */, 0.0 /* money penalty */);
        List<PPAttachment2> ppAmbiguities = new ArrayList<>();
        for (int sid : learner.allParses.keySet()) {
            final List<String> sentence = learner.getSentenceById(sid);
            final List<Parse> parses = learner.allParses.get(sid);
            final Parse goldParse = learner.goldParses.get(sid);
            if (parses == null) {
                continue;
            }
            for (int predId = 0; predId < sentence.size(); predId++) {
                Optional<PPAttachment2> ppAttachmentOpt = findPPAttachmentAmbiguity(sid, predId, sentence, parses,
                        goldParse);
                if (ppAttachmentOpt.isPresent()) {
                    ppAmbiguities.add(ppAttachmentOpt.get());
                }
            }
        }
        System.out.println("Found " + ppAmbiguities.size() + " ambiguous PP attachments.");
        List<PPAttachment2> verbArgs = ppAmbiguities.stream()
                .filter(pp -> pp.goldAttachmentType == AttachmentType.VERB_ARGUMENT)
                .collect(Collectors.toList());
        List<PPAttachment2> verbAdjs = ppAmbiguities.stream()
                .filter(pp -> pp.goldAttachmentType == AttachmentType.VERB_ADJUNCT)
                .collect(Collectors.toList());
        List<PPAttachment2> nounAdjs = ppAmbiguities.stream()
                .filter(pp -> pp.goldAttachmentType == AttachmentType.NOUN_ADJUNCT)
                .collect(Collectors.toList());

        //verbArgs.forEach(PPAttachment2::prettyPrint);
        //verbAdjs.forEach(PPAttachment2::prettyPrint);
        //nounAdjs.forEach(PPAttachment2::prettyPrint);

        Table<AttachmentType, AttachmentType, Integer> confusionMatrix = HashBasedTable.create();
        for (PPAttachment2 pp : ppAmbiguities) {
            int count = 0;
            if (pp.goldAttachmentType == null || pp.onebestAttachmentType == null) {
                continue;
            }
            if (confusionMatrix.contains(pp.goldAttachmentType, pp.onebestAttachmentType)) {
                count = confusionMatrix.get(pp.goldAttachmentType, pp.onebestAttachmentType);
            }
            confusionMatrix.put(pp.goldAttachmentType, pp.onebestAttachmentType, count + 1);
        }

        System.out.println(String.format("Verb args: Found %d cases.", verbArgs.size()));
        System.out.println(String.format("Verb adjs: Found %d cases.", verbAdjs.size()));
        System.out.println(String.format("Noun adjs: Found %d cases.", nounAdjs.size()));


        // Print confusion matrix.
        System.out.print("gold\\onebest");
        List<AttachmentType> orderedKeySet = new ArrayList<>();
        Collections.addAll(orderedKeySet, AttachmentType.VERB_ARGUMENT, AttachmentType.VERB_ADJUNCT,
                AttachmentType.NOUN_ADJUNCT);
        orderedKeySet.forEach(ck -> System.out.print("\t" + ck));
        System.out.println();
        orderedKeySet.forEach(rk -> {
            System.out.print(rk);
            for (AttachmentType ck : orderedKeySet) {
                System.out.print("\t" + confusionMatrix.get(rk, ck));
            }
            System.out.println();
        });
        System.out.println();

        // Attachment accuracy.
        double acc1 = ppAmbiguities.stream().filter(pp -> pp.goldAttachment1 == pp.onebestAttachment1 &&
                pp.goldAttachmentType == AttachmentType.NOUN_ADJUNCT &&
                pp.onebestAttachmentType == AttachmentType.NOUN_ADJUNCT).count();
        double acc2 = ppAmbiguities.stream().filter(pp -> pp.goldAttachment1 == pp.onebestAttachment1 &&
                pp.goldAttachmentType == AttachmentType.VERB_ADJUNCT &&
                pp.onebestAttachmentType == AttachmentType.VERB_ADJUNCT).count();
        double acc3 = ppAmbiguities.stream().filter(pp -> pp.goldAttachment1 == pp.onebestAttachment1 &&
                (pp.goldAttachmentType == AttachmentType.VERB_ADJUNCT || pp.goldAttachmentType == AttachmentType.VERB_ARGUMENT) &&
                (pp.onebestAttachmentType == AttachmentType.VERB_ADJUNCT || pp.onebestAttachmentType == AttachmentType.VERB_ARGUMENT)).count();
        double norm1 = ppAmbiguities.stream().filter(pp ->
                pp.goldAttachmentType == AttachmentType.NOUN_ADJUNCT &&
                pp.onebestAttachmentType == AttachmentType.NOUN_ADJUNCT).count();
        double norm2 = ppAmbiguities.stream().filter(pp ->
                pp.goldAttachmentType == AttachmentType.VERB_ADJUNCT &&
                pp.onebestAttachmentType == AttachmentType.VERB_ADJUNCT).count();
        double norm3 = ppAmbiguities.stream().filter(pp ->
                (pp.goldAttachmentType == AttachmentType.VERB_ADJUNCT || pp.goldAttachmentType == AttachmentType.VERB_ARGUMENT) &&
                (pp.onebestAttachmentType == AttachmentType.VERB_ADJUNCT || pp.onebestAttachmentType == AttachmentType.VERB_ARGUMENT)).count();
        System.out.println(String.format("NOUN_ADJ attachment accuracy: %.3f%%", 100.0 * acc1 / norm1));
        System.out.println(String.format("VERB_ADJ attachment accuracy: %.3f%%", 100.0 * acc2 / norm2));
        System.out.println(String.format("VERB (directed) attachment accuracy: %.3f%%", 100.0 * acc3 / norm3));
    }
}
