package io.tracegraph.eval.baseline;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

public final class EvalBaselineStore {

    private final Path file;
    private final ObjectMapper mapper;

    public EvalBaselineStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    public void save(EvalBaseline baseline) {
        Objects.requireNonNull(baseline, "baseline");
        Path parent = file.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, mapper.writeValueAsBytes(baseline));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save baseline to " + file, e);
        }
    }

    public Optional<EvalBaseline> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(Files.readAllBytes(file), EvalBaseline.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load baseline from " + file, e);
        }
    }

    public Path file() {
        return file;
    }
}
