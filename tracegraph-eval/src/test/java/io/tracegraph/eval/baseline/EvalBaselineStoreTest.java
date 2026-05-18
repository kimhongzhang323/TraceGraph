package io.tracegraph.eval.baseline;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.EvalResult;
import io.tracegraph.eval.MetricScore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EvalBaselineStoreTest {

    @Test
    void loadReturnsEmptyWhenFileMissing(@TempDir Path tmp) {
        EvalBaselineStore store = new EvalBaselineStore(tmp.resolve("baseline.json"));

        Optional<EvalBaseline> loaded = store.load();

        assertThat(loaded).isEmpty();
    }

    @Test
    void saveAndLoadRoundtripsScoresByCaseId(@TempDir Path tmp) {
        EvalBaselineStore store = new EvalBaselineStore(tmp.resolve("baseline.json"));
        EvalBaseline baseline = EvalBaseline.from(List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0))),
                result("case-2", List.of(MetricScore.fail("exact_match", 0.0, "no match")))
        ));

        store.save(baseline);
        EvalBaseline loaded = store.load().orElseThrow();

        assertThat(loaded.scoresByCaseId()).hasSize(2);
        MetricScore case1Score = loaded.scoresFor("case-1").orElseThrow().get(0);
        assertThat(case1Score.metricName()).isEqualTo("exact_match");
        assertThat(case1Score.passed()).isTrue();
        assertThat(case1Score.score()).isEqualTo(1.0);

        MetricScore case2Score = loaded.scoresFor("case-2").orElseThrow().get(0);
        assertThat(case2Score.passed()).isFalse();
        assertThat(case2Score.detail()).isEqualTo("no match");
    }

    @Test
    void saveAndLoadPreservesCreatedAt(@TempDir Path tmp) {
        EvalBaselineStore store = new EvalBaselineStore(tmp.resolve("baseline.json"));
        EvalBaseline baseline = EvalBaseline.from(List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0)))
        ));

        store.save(baseline);
        EvalBaseline loaded = store.load().orElseThrow();

        assertThat(loaded.createdAt()).isEqualTo(baseline.createdAt());
    }

    @Test
    void savePersistsMultipleMetricsPerCase(@TempDir Path tmp) {
        EvalBaselineStore store = new EvalBaselineStore(tmp.resolve("baseline.json"));
        EvalBaseline baseline = EvalBaseline.from(List.of(
                result("case-1", List.of(
                        MetricScore.pass("exact_match", 1.0),
                        MetricScore.pass("contains", 0.9)
                ))
        ));

        store.save(baseline);
        EvalBaseline loaded = store.load().orElseThrow();

        assertThat(loaded.scoresFor("case-1").orElseThrow()).hasSize(2);
    }

    @Test
    void saveOverwritesExistingFile(@TempDir Path tmp) {
        EvalBaselineStore store = new EvalBaselineStore(tmp.resolve("baseline.json"));
        EvalBaseline first = EvalBaseline.from(List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 0.5)))
        ));
        EvalBaseline second = EvalBaseline.from(List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 0.9)))
        ));

        store.save(first);
        store.save(second);
        EvalBaseline loaded = store.load().orElseThrow();

        assertThat(loaded.scoresFor("case-1").orElseThrow().get(0).score()).isEqualTo(0.9);
    }

    @Test
    void saveCreatesParentDirectories(@TempDir Path tmp) throws Exception {
        Path nested = tmp.resolve("a/b/c/baseline.json");
        EvalBaselineStore store = new EvalBaselineStore(nested);
        EvalBaseline baseline = EvalBaseline.from(List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0)))
        ));

        store.save(baseline);

        assertThat(Files.exists(nested)).isTrue();
    }

    private static EvalResult<String> result(String id, List<MetricScore> scores) {
        EvalCase<String> evalCase = EvalCase.of(id, "input-" + id, "expected");
        boolean passed = scores.stream().allMatch(MetricScore::passed);
        return new EvalResult<>(evalCase, "actual-" + id, scores, 10L, passed);
    }
}
