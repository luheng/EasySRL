package edu.uw.easysrl.qasrl.qg.util;

import edu.uw.easysrl.syntax.grammar.Category;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public final class Gap extends Predication {
    public static final String PRED = "'e'";

    @Override
    public ImmutableList<String> getPhrase() {
        return ImmutableList.of();
    }

    public Gap(Category predicateCategory) {
        super(PRED, predicateCategory, ImmutableMap.of());
    }
}
