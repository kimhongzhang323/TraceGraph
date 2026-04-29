package io.tracegraph.core;

public sealed interface NodeResult<S> {
    S state();

    record Continue<S>(S state) implements NodeResult<S> {}
    record GoTo<S>(String nodeName, S state) implements NodeResult<S> {}

    static <S> NodeResult<S> of(S state) { return new Continue<>(state); }
    static <S> NodeResult<S> goTo(String nodeName, S state) { return new GoTo<>(nodeName, state); }
}
