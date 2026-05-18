package io.tracegraph.eval.dataset;

import io.tracegraph.eval.EvalCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalCaseLoaderCsvTest {

    @TempDir
    Path tmp;

    @Test
    void loadsCasesFromSimpleCsv() throws IOException {
        Path file = tmp.resolve("cases.csv");
        Files.writeString(file, """
                id,input,expected
                c-1,hello,HELLO
                c-2,world,WORLD
                """);

        List<EvalCase<String>> cases = EvalCaseLoader.fromCsv(file, row -> row.get("input"));

        assertThat(cases).hasSize(2);
        assertThat(cases).extracting(EvalCase::id).containsExactly("c-1", "c-2");
        assertThat(cases).extracting(EvalCase::input).containsExactly("hello", "world");
        assertThat(cases).extracting(EvalCase::expected).containsExactly("HELLO", "WORLD");
    }

    @Test
    void handlesQuotedFieldsWithCommasAndNewlines() throws IOException {
        Path file = tmp.resolve("quoted.csv");
        Files.writeString(file, """
                id,input,expected
                c-1,"hello, world","HELLO, WORLD"
                c-2,"line1
                line2",multi
                """);

        List<EvalCase<String>> cases = EvalCaseLoader.fromCsv(file, row -> row.get("input"));

        assertThat(cases).hasSize(2);
        assertThat(cases.get(0).input()).isEqualTo("hello, world");
        assertThat(cases.get(0).expected()).isEqualTo("HELLO, WORLD");
        assertThat(cases.get(1).input()).isEqualTo("line1\nline2");
    }

    @Test
    void handlesEscapedDoubleQuotesInsideQuotedFields() throws IOException {
        Path file = tmp.resolve("escaped.csv");
        Files.writeString(file, "id,input,expected\nc-1,\"she said \"\"hi\"\"\",reply\n");

        List<EvalCase<String>> cases = EvalCaseLoader.fromCsv(file, row -> row.get("input"));

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).input()).isEqualTo("she said \"hi\"");
    }

    @Test
    void skipsBlankLines() throws IOException {
        Path file = tmp.resolve("blanks.csv");
        Files.writeString(file, """
                id,input
                a,x

                b,y
                """);

        List<EvalCase<String>> cases = EvalCaseLoader.fromCsv(file, row -> row.get("input"));

        assertThat(cases).extracting(EvalCase::id).containsExactly("a", "b");
    }

    @Test
    void treatsAbsentExpectedColumnAsNull() throws IOException {
        Path file = tmp.resolve("noexpected.csv");
        Files.writeString(file, """
                id,input
                a,x
                """);

        List<EvalCase<String>> cases = EvalCaseLoader.fromCsv(file, row -> row.get("input"));

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).expected()).isNull();
    }

    @Test
    void exposesAdditionalColumnsToFactoryFunction() throws IOException {
        Path file = tmp.resolve("extra.csv");
        Files.writeString(file, """
                id,query,language,expected
                q-1,bonjour,fr,hello
                q-2,hola,es,hello
                """);

        List<EvalCase<Map<String, String>>> cases = EvalCaseLoader.fromCsv(file,
                row -> Map.of("query", row.get("query"), "language", row.get("language")));

        assertThat(cases).hasSize(2);
        assertThat(cases.get(0).input()).containsEntry("query", "bonjour").containsEntry("language", "fr");
        assertThat(cases.get(1).input()).containsEntry("query", "hola").containsEntry("language", "es");
    }

    @Test
    void rejectsCsvWithoutHeader() throws IOException {
        Path file = tmp.resolve("empty.csv");
        Files.writeString(file, "");

        assertThatThrownBy(() -> EvalCaseLoader.fromCsv(file, row -> row.get("input")))
                .isInstanceOf(EvalDatasetException.class)
                .hasMessageContaining("header");
    }

    @Test
    void rejectsRowMissingIdColumn() throws IOException {
        Path file = tmp.resolve("noid.csv");
        Files.writeString(file, """
                input,expected
                x,y
                """);

        assertThatThrownBy(() -> EvalCaseLoader.fromCsv(file, row -> row.get("input")))
                .isInstanceOf(EvalDatasetException.class)
                .hasMessageContaining("id");
    }

    @Test
    void rejectsRowWithFewerFieldsThanHeader() throws IOException {
        Path file = tmp.resolve("short.csv");
        Files.writeString(file, """
                id,input,expected
                ok,hi,HI
                broken,onlyone
                """);

        assertThatThrownBy(() -> EvalCaseLoader.fromCsv(file, row -> row.get("input")))
                .isInstanceOf(EvalDatasetException.class)
                .hasMessageContaining("line 3");
    }

    @Test
    void failsOnMissingFile() {
        Path missing = tmp.resolve("nope.csv");

        assertThatThrownBy(() -> EvalCaseLoader.fromCsv(missing, row -> row.get("input")))
                .isInstanceOf(EvalDatasetException.class)
                .hasCauseInstanceOf(NoSuchFileException.class);
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> EvalCaseLoader.fromCsv(null, row -> "x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EvalCaseLoader.fromCsv(tmp.resolve("x"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
