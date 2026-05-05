package io.tracegraph.connectors.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PromptLibraryTest {

    @Test
    void fromClasspathLoadsTemplates() {
        PromptLibrary library = PromptLibrary.fromClasspath("prompts");
        assertThat(library.all()).hasSize(2);
    }

    @Test
    void getReturnsCorrectTemplate() {
        PromptLibrary library = PromptLibrary.fromClasspath("prompts");
        PromptTemplate t = library.get("greet-user");
        assertThat(t.id()).isEqualTo("greet-user");
        assertThat(t.version()).isEqualTo("1.0");
        assertThat(t.variables()).containsExactlyInAnyOrder("name", "project");
    }

    @Test
    void findReturnsEmptyForUnknownId() {
        PromptLibrary library = PromptLibrary.fromClasspath("prompts");
        Optional<PromptTemplate> result = library.find("no-such-template");
        assertThat(result).isEmpty();
    }

    @Test
    void fromDirectoryLoadsPromptFiles(@TempDir Path dir) throws IOException {
        String content = "# id: hello\n# version: 0.1\nHi {{user}}!";
        Files.write(dir.resolve("hello.prompt"), content.getBytes(StandardCharsets.UTF_8));

        PromptLibrary library = PromptLibrary.fromDirectory(dir);
        assertThat(library.all()).hasSize(1);
        PromptTemplate t = library.get("hello");
        assertThat(t.id()).isEqualTo("hello");
        assertThat(t.variables()).containsExactly("user");
    }
}
