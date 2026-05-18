package io.tracegraph.eval.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import io.tracegraph.eval.EvalCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalCaseLoaderJsonlTest {

    @TempDir
    Path tmp;

    @Test
    void loadsCasesFromJsonl() throws IOException {
        Path file = tmp.resolve("cases.jsonl");
        Files.writeString(file, """
                {"id":"c-1","input":"hello","expected":"HELLO"}
                {"id":"c-2","input":"world","expected":"WORLD"}
                """);

        List<EvalCase<String>> cases = EvalCaseLoader.fromJsonl(file, node -> node.get("input").asText());

        assertThat(cases).hasSize(2);
        assertThat(cases.get(0).id()).isEqualTo("c-1");
        assertThat(cases.get(0).input()).isEqualTo("hello");
        assertThat(cases.get(0).expected()).isEqualTo("HELLO");
        assertThat(cases.get(1).id()).isEqualTo("c-2");
        assertThat(cases.get(1).input()).isEqualTo("world");
        assertThat(cases.get(1).expected()).isEqualTo("WORLD");
    }

    @Test
    void skipsBlankLinesAndIgnoresTrailingWhitespace() throws IOException {
        Path file = tmp.resolve("cases.jsonl");
        Files.writeString(file, """
                {"id":"a","input":"x"}

                {"id":"b","input":"y"}
                  \t
                """);

        List<EvalCase<String>> cases = EvalCaseLoader.fromJsonl(file, node -> node.get("input").asText());

        assertThat(cases).extracting(EvalCase::id).containsExactly("a", "b");
    }

    @Test
    void passesFullJsonNodeToFactoryForComplexInputs() throws IOException {
        Path file = tmp.resolve("cases.jsonl");
        Files.writeString(file, """
                {"id":"complex","query":"q","docs":["d1","d2"]}
                """);

        List<EvalCase<List<String>>> cases = EvalCaseLoader.fromJsonl(file, node -> {
            List<String> result = new java.util.ArrayList<>();
            result.add(node.get("query").asText());
            for (JsonNode doc : node.get("docs")) {
                result.add(doc.asText());
            }
            return List.copyOf(result);
        });

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).input()).containsExactly("q", "d1", "d2");
    }

    @Test
    void carriesMetadataWhenPresent() throws IOException {
        Path file = tmp.resolve("cases.jsonl");
        Files.writeString(file, """
                {"id":"m-1","input":"hi","expected":"HI","metadata":{"difficulty":"easy","tags":["greeting"]}}
                """);

        List<EvalCase<String>> cases = EvalCaseLoader.fromJsonl(file, node -> node.get("input").asText());

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).metadata())
                .containsEntry("difficulty", "easy");
        assertThat(cases.get(0).metadata().get("tags")).isInstanceOf(List.class);
    }

    @Test
    void rejectsMissingIdField() throws IOException {
        Path file = tmp.resolve("cases.jsonl");
        Files.writeString(file, """
                {"input":"oops"}
                """);

        assertThatThrownBy(() -> EvalCaseLoader.fromJsonl(file, node -> node.get("input").asText()))
                .isInstanceOf(EvalDatasetException.class)
                .hasMessageContaining("id");
    }

    @Test
    void wrapsParseErrorWithLineNumber() throws IOException {
        Path file = tmp.resolve("cases.jsonl");
        Files.writeString(file, """
                {"id":"ok","input":"x"}
                {this is not json
                """);

        assertThatThrownBy(() -> EvalCaseLoader.fromJsonl(file, node -> node.get("input").asText()))
                .isInstanceOf(EvalDatasetException.class)
                .hasMessageContaining("line 2");
    }

    @Test
    void failsOnMissingFile() {
        Path missing = tmp.resolve("nope.jsonl");

        assertThatThrownBy(() -> EvalCaseLoader.fromJsonl(missing, node -> node.get("input").asText()))
                .isInstanceOf(EvalDatasetException.class)
                .hasCauseInstanceOf(NoSuchFileException.class);
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> EvalCaseLoader.fromJsonl(null, node -> "x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EvalCaseLoader.fromJsonl(tmp.resolve("x"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
