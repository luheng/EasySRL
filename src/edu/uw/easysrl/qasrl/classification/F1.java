package edu.uw.easysrl.qasrl.classification;

import edu.uw.easysrl.qasrl.model.Constraint;

import java.util.Collection;

/**
 * Created by luheng on 5/9/16.
 */
public class F1 {
    int matched;
    int pred;
    int gold;

    F1() {
        matched = 0;
        pred = 0;
        gold = 0;
    }

    F1(int matched, int pred, int gold) {
        this.matched = matched;
        this.pred = pred;
        this.gold = gold;
    }

    void add(final F1 other) {
        this.matched += other.matched;
        this.pred += other.pred;
        this.gold += other.gold;
    }

    double getF1() {
        return 2.0 * getPrecision() * getRecall() / (getPrecision() + getRecall());
    }

    double getPrecision() {
        return 1.0 * matched / pred;
    }

    double getRecall() {
        return 1.0 * matched / gold;
    }

    @Override
    public String toString() {
        return String.format("Prec:\t%.3f\tRecall:\t%.3f\tF1:\t%.3f", 100.0 * getPrecision(), 100.0 * getRecall(),
                100.0 * getF1());
    }

    static F1 computeConstraintF1(final Collection<Constraint.AttachmentConstraint> constraints,
                                  final Collection<Constraint.AttachmentConstraint> goldConstraints) {
        return new F1((int) constraints.stream()
                .filter(c -> goldConstraints.stream()
                        .anyMatch(c2 -> c.getHeadId() == c2.getHeadId() && c.getArgId() == c2.getArgId()))
                .count(),
                constraints.size(),
                goldConstraints.size());
    }
}
