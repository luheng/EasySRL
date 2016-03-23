package edu.uw.easysrl.qasrl.qg.util;

import edu.uw.easysrl.syntax.grammar.Category;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public final class Gap extends Predication {
    public static final String PRED = "'e'";

    @Override
    public ImmutableList<String> getCompletePhrase() {
        return ImmutableList.of();
    }

    @Override
    public QuestionData getQuestionData() {
        throw new UnsupportedOperationException("cannot ask about a gap");
    }

    public Gap(Category predicateCategory) {
        super(PRED, predicateCategory, ImmutableMap.of());
    }
}
