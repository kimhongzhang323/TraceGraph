package io.tracegraph.eval;

import java.util.Map;
import java.util.Objects;

public record EvalCase<S>(String id, S input, Object expected, Map<String, Object> metadata) {

    public EvalCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(input, "input");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static <S> EvalCase<S> of(String id, S input, Object expected) {
        return new EvalCase<>(id, input, expected, Map.of());
    }
}
