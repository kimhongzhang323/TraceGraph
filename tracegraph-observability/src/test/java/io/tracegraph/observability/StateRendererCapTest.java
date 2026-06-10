package io.tracegraph.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateRendererCapTest {

    @Test
    void shortOutputPassesThroughUnchanged() {
        assertThat(StateRenderer.capped(10).render("short")).isEqualTo("short");
    }

    @Test
    void longOutputIsTruncatedWithMarker() {
        String rendered = StateRenderer.capped(5).render("0123456789");
        assertThat(rendered).startsWith("01234").contains("[truncated 5 chars]");
    }

    @Test
    void nullStateRendersAsDefault() {
        assertThat(StateRenderer.capped(10).render(null)).isEqualTo("null");
    }

    @Test
    void wrapsCustomDelegate() {
        StateRenderer upper = state -> String.valueOf(state).toUpperCase(java.util.Locale.ROOT);
        assertThat(StateRenderer.capped(upper, 3).render("abcdef")).startsWith("ABC");
    }

    @Test
    void rejectsNonPositiveCap() {
        assertThatThrownBy(() -> StateRenderer.capped(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
