package io.tracegraph.observability.replay;

import java.util.List;
import java.util.Objects;

public record TraceDiff<S>(String leftExecutionId, String rightExecutionId,
                           List<TraceStep<S>> commonPrefix,
                           int divergenceIndex,
                           List<TraceStep<S>> leftRemainder,
                           List<TraceStep<S>> rightRemainder,
                           boolean sameStatus,
                           boolean sameFinalState) {

    public TraceDiff {
        Objects.requireNonNull(leftExecutionId, "leftExecutionId");
        Objects.requireNonNull(rightExecutionId, "rightExecutionId");
        commonPrefix = List.copyOf(commonPrefix);
        leftRemainder = List.copyOf(leftRemainder);
        rightRemainder = List.copyOf(rightRemainder);
    }

    public static <S> TraceDiff<S> between(ExecutionTrace<S> left, ExecutionTrace<S> right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");

        List<TraceStep<S>> ls = left.steps();
        List<TraceStep<S>> rs = right.steps();
        int common = 0;
        int min = Math.min(ls.size(), rs.size());
        while (common < min && stepsEqual(ls.get(common), rs.get(common))) {
            common++;
        }

        int divergence = (common == ls.size() && common == rs.size()) ? -1 : common;
        List<TraceStep<S>> leftRemainder = ls.subList(common, ls.size());
        List<TraceStep<S>> rightRemainder = rs.subList(common, rs.size());

        return new TraceDiff<>(left.executionId(), right.executionId(),
                ls.subList(0, common), divergence, leftRemainder, rightRemainder,
                left.status() == right.status(),
                Objects.equals(left.finalState(), right.finalState()));
    }

    public boolean identical() {
        return divergenceIndex == -1 && sameStatus && sameFinalState;
    }

    private static <S> boolean stepsEqual(TraceStep<S> a, TraceStep<S> b) {
        return a.nodeName().equals(b.nodeName())
                && Objects.equals(a.before(), b.before())
                && Objects.equals(a.after(), b.after());
    }
}
