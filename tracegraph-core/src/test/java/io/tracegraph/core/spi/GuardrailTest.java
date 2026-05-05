package io.tracegraph.core.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuardrailTest {

    @Test
    void allowVerdictIsAllowed() {
        GuardrailVerdict v = GuardrailVerdict.allow();
        assertThat(v.isAllowed()).isTrue();
        assertThat(v.isBlocked()).isFalse();
        assertThat(v.action()).isEqualTo(GuardrailVerdict.Action.ALLOW);
    }

    @Test
    void blockVerdictIsBlocked() {
        GuardrailVerdict v = GuardrailVerdict.block("bad input");
        assertThat(v.isBlocked()).isTrue();
        assertThat(v.isAllowed()).isFalse();
        assertThat(v.reason()).isEqualTo("bad input");
    }

    @Test
    void andThenShortCircuitsOnFirstBlock() {
        Guardrail<String> blocker = input -> GuardrailVerdict.block("first blocked");
        Guardrail<String> shouldNotRun = input -> GuardrailVerdict.block("second blocked");

        GuardrailVerdict result = blocker.andThen(shouldNotRun).check("anything");

        assertThat(result.isBlocked()).isTrue();
        assertThat(result.reason()).isEqualTo("first blocked");
    }

    @Test
    void andThenAllowsThroughWhenBothAllow() {
        Guardrail<String> first = input -> GuardrailVerdict.allow();
        Guardrail<String> second = input -> GuardrailVerdict.allow();

        GuardrailVerdict result = first.andThen(second).check("anything");

        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    void andThenDelegatesToNextWhenFirstAllows() {
        Guardrail<String> first = input -> GuardrailVerdict.allow();
        Guardrail<String> second = input -> GuardrailVerdict.block("second blocked");

        GuardrailVerdict result = first.andThen(second).check("anything");

        assertThat(result.isBlocked()).isTrue();
        assertThat(result.reason()).isEqualTo("second blocked");
    }
}
