package edu.uw.easysrl.qasrl.qg;

import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.qasrl.corpora.AlignedDependency;
import edu.uw.easysrl.qasrl.corpora.PropBankAligner;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.CountDictionary;

import java.io.*;
import java.util.*;

/**
 * Now able to generate 3378 (72.69% of 4647 dependencies, 73.66% of 3979 aligned dependencies) questions.
 * 612 (18.12%) exact matches among all generated.
 *
 * Now able to generate 3541 (76.20% of 4647 dependencies, 74.72% of 3979 aligned dependencies) questions.
 * 615 (17.37%) exact matches among all generated.
 *
 * +++++++++ With Rebanking
 * Now able to generate 3893 (98.81% of 3940 dependencies, 85.45% of 3979 aligned dependencies) questions.
 * 662 (17.00%) exact matches among all generated.
 *
 * +++++++++ Without Rebanking
 * Now able to generate 3083 (100.00% of 3083 dependencies, 82.13% of 2982 aligned dependencies) questions.
 * 469 (15.21%) exact matches among all generated.
 *
 * Created by luheng on 12/8/15.
 */
public class QuestionGenerationBenchmark {
    private static QuestionGenerator questionGenerator;

    /**
     * Go over all the sentences with aligned ccg-qa dependencies and generate questions. (we can as well generate
     * for unaligned dependencies, but we want to do evaluation with the annotated QAs.
     * @param alignedDependencies gold ccg dependencies
     */
    public static void generateFromGoldCCG(Map<Integer, List<AlignedDependency<CCGBankDependency,
                                           QADependency>>> alignedDependencies) throws IOException {
        CountDictionary coveredDeps = new CountDictionary();
        CountDictionary uncoveredDeps = new CountDictionary();
        CountDictionary alignedDeps = new CountDictionary();
        int numDependenciesProcessed = 0;
        int numQuestionsGenerated = 0;
        int numGeneratedAligned = 0;
        int numQuestionExactMatch = 0;
        int numAligned = 0;

        // TODO: shuffle sentence ids :)
        File outputFile = new File("qg.out.txt");
        if (!outputFile.exists()) {
            outputFile.createNewFile();
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

        for (int sentIdx : alignedDependencies.keySet()) {
            List<AlignedDependency<CCGBankDependency, QADependency>> deps = alignedDependencies.get(sentIdx);
            if (deps == null) {
                continue;
            }
            Sentence sentence = deps.get(0).sentence;
            List<String> words = sentence.getWords();
            List<Category> categories = sentence.getLexicalCategories();
            Collection<CCGBankDependency> ccgDeps = sentence.getCCGBankDependencyParse().getDependencies();
            for (AlignedDependency<CCGBankDependency, QADependency> dep : deps) {
                CCGBankDependency ccgDep = dep.dependency1;
                QADependency qaDep = dep.dependency2;
                if (ccgDep == null) {
                    continue;
                }
                if (qaDep != null) {
                    numAligned ++;
                    alignedDeps.addString(ccgDep.getCategory().toString());
                }
                String word = words.get(ccgDep.getSentencePositionOfPredicate());
                Category category = ccgDep.getCategory();
                if (questionGenerator.filterPredicate(word, category)) {
                    continue;
                }
                numDependenciesProcessed ++;
                int predicateIndex = ccgDep.getSentencePositionOfPredicate();
                QuestionTemplate template = questionGenerator.getTemplateFromCCGBank(predicateIndex, words,
                        categories, ccgDeps);
                if (template == null) {
                    uncoveredDeps.addString(ccgDep.getCategory().toString());
                    /*if (category.getNumberOfArguments() > 3) {
                        printPredicateInfo(null, sentence, ccgDep, qaDep);
                    }*/
                    continue;
                }
                List<String> question = questionGenerator.generateQuestionFromTemplate(template,
                        ccgDep.getArgNumber());
                if (question.size() == 0) {
                    continue;
                }
                numQuestionsGenerated ++;
                if (qaDep != null) {
                    numGeneratedAligned ++;
                }
                coveredDeps.addString(ccgDep.getCategory().toString());

                // Output template.
                String questionString = "";
                for (String w : question) {
                    if (!w.trim().isEmpty()) {
                        questionString += w.trim() + " ";
                    }
                }
                questionString += "?";
                String ccgInfo = ccgDep.getCategory() + "_" + ccgDep.getArgNumber();

                System.out.println("\n" + StringUtils.join(words) + "\n" + ccgInfo);
                writer.write("\n" + StringUtils.join(words) + "\n" + ccgInfo + "\n");

                for (QuestionSlot slot : template.slots) {
                    String slotStr = (slot.argumentNumber == ccgDep.getArgNumber() ?
                            String.format("{%s}", slot.toString(words)) : slot.toString(words));
                    System.out.print(slotStr + "\t");
                    writer.write(slotStr + "\t");
                }
                System.out.println("\n" + questionString + "\t" + words.get(ccgDep.getSentencePositionOfArgument()));
                writer.write("\n" + questionString + "\t" + words.get(ccgDep.getSentencePositionOfArgument()) + "\n");
                if (qaDep == null) {
                    System.out.println("[no-qa]");
                    writer.write("[no-qa]\n");
                } else {
                    System.out.println(qaDep.getQuestionString() + "\t" + qaDep.getAnswerString(words));
                    writer.write(qaDep.getQuestionString() + "\t" + qaDep.getAnswerString(words) + "\n");
                    if (qaDep.getQuestionString().equalsIgnoreCase(questionString)) {
                        numQuestionExactMatch++;
                    }
                }
            }
        }
        System.out.println("\n++++++++++++++++++++++++++++++++++++++++++");
        System.out.println(String.format("Now able to generate %d " +
                        "(%.2f%% of %d dependencies, %.2f%% of %d aligned dependencies) questions. " +
                        "%d (%.2f%%) exact matches among all generated.",
                numQuestionsGenerated,
                100.0 * numQuestionsGenerated / numDependenciesProcessed, numDependenciesProcessed,
                100.0 * numGeneratedAligned / numAligned, numAligned,
                numQuestionExactMatch, 100.0 * numQuestionExactMatch / numQuestionsGenerated));
        // uncoveredDeps.prettyPrint();
        writer.close();
    }

    @SuppressWarnings("unused")
    private static void printPredicateInfo(BufferedWriter writer, Sentence sentence, CCGBankDependency ccgDep,
                                           QADependency qaDep) throws IOException {
        List<String> words = sentence.getWords();
        int predicateIndex = ccgDep.getSentencePositionOfPredicate();
        List<Integer> wordIndices = new ArrayList<>();
        Map<Integer, Integer> wordIdToSlot = new HashMap<>();
        wordIndices.add(predicateIndex);
        for (CCGBankDependency d : sentence.getCCGBankDependencyParse().getDependencies()) {
            int predId = d.getSentencePositionOfPredicate();
            int argId = d.getSentencePositionOfArgument();
            if (predId == predicateIndex && predId != argId) {
                wordIdToSlot.put(argId, d.getArgNumber());
                wordIndices.add(argId);
            }
        }
        Collections.sort(wordIndices);
        if (writer != null) {
            writer.write(StringUtils.join(words) + "\n");
        } else {
            System.out.print(StringUtils.join(words) + "\n");
        }
        List<String> lineBuf = new ArrayList<>();
        wordIndices.forEach(id -> {
            if (id == predicateIndex) {
                lineBuf.add(String.format("%s ", words.get(id)));
            } else if (id == ccgDep.getSentencePositionOfArgument()) {
                lineBuf.add(String.format("{%d:%s} ", wordIdToSlot.get(id), words.get(id)));
            } else {
                lineBuf.add(String.format("%d:%s ", wordIdToSlot.get(id), words.get(id)));
            }
        });
        if (writer != null) {
            writer.write(StringUtils.join(lineBuf) + "\n");
            writer.write(words.get(predicateIndex) + "\t" + ccgDep.getCategory() + "\t" +
                    ccgDep.getArgNumber() + "\n");
            writer.write(qaDep == null ? "*N/A*" : StringUtils.join(qaDep.getQuestion()) + "?");
            writer.write("\n\n");
        } else {
            System.out.print(StringUtils.join(lineBuf) + "\n");
            System.out.print(words.get(predicateIndex) + "\t" + ccgDep.getCategory() + "\t" +
                   ccgDep.getArgNumber() + "\n");
            System.out.print(qaDep == null ? "*N/A*" : StringUtils.join(qaDep.getQuestion()) + "?");
            System.out.print("\n\n");
        }
    }

    @SuppressWarnings("unused")
    private static void outputInfo(CCGBankDependency ccgDep,
                                   QADependency qaDep,
                                   List<String> words,
                                   List<Category> categories,
                                   Collection<CCGBankDependency> dependencies,
                                   Map<Integer, Integer> wordToSlot,
                                   Map<Integer, Integer> slotToWord,
                                   List<Integer> wordIndices,
                                   List<String> question) {
        Category predicateCategory = ccgDep.getCategory();
        int predicateIndex = ccgDep.getSentencePositionOfPredicate();
        int targetSlotId = ccgDep.getArgNumber();
        String ccgInfo = ccgDep.getCategory() + "_" + ccgDep.getArgNumber();
        System.out.println("\n" + StringUtils.join(words));
        wordIndices.forEach(id -> {
            if (id == predicateIndex) {
                System.out.print(String.format("%s ", words.get(id)));
            } else if (id == ccgDep.getSentencePositionOfArgument()) {
                System.out.print(String.format("{%d:%s} ", wordToSlot.get(id), words.get(id)));
            } else {
                System.out.print(String.format("%d:%s ", wordToSlot.get(id), words.get(id)));
            }
        });
        System.out.println("\n" + words.get(predicateIndex) + "\t" + ccgInfo);
        System.out.println(StringUtils.join(question) + "?");
        System.out.println(qaDep == null ? "*N/A*" : StringUtils.join(qaDep.getQuestion()) + "?");
    }

    public static void main(String[] args) {
        questionGenerator = new QuestionGenerator();
        Map<Integer, List<AlignedDependency<CCGBankDependency, QADependency>>>
            mappedDependencies = PropBankAligner.getCcgAndQADependenciesTrain();
        try {
            generateFromGoldCCG(mappedDependencies);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

