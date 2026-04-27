package io.tracegraph.memory;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import io.tracegraph.core.spi.MemoryStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class FileMemoryStore implements MemoryStore {

    private static final String EXT = ".json";

    private final Path directory;
    private final ObjectMapper mapper;

    @SuppressWarnings("deprecation")
    private FileMemoryStore(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        this.mapper = new ObjectMapper()
                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create memory store directory: " + directory, e);
        }
    }

    public static FileMemoryStore of(Path directory) {
        return new FileMemoryStore(directory);
    }

    @Override
    public Optional<Object> get(String scope, String key) {
        Path file = pathFor(scope, key);
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.ofNullable(mapper.readValue(Files.readAllBytes(file), Object.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read memory entry " + scope + "/" + key, e);
        }
    }

    @Override
    public void put(String scope, String key, Object value) {
        Path scopeDir = scopeDir(scope);
        try {
            Files.createDirectories(scopeDir);
            Path target = scopeDir.resolve(safeName(key) + EXT);
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(tmp, mapper.writeValueAsBytes(value));
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write memory entry " + scope + "/" + key, e);
        }
    }

    @Override
    public boolean delete(String scope, String key) {
        try {
            return Files.deleteIfExists(pathFor(scope, key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete memory entry " + scope + "/" + key, e);
        }
    }

    @Override
    public Set<String> keys(String scope) {
        Path scopeDir = scopeDir(scope);
        if (!Files.isDirectory(scopeDir)) return Set.of();
        try (Stream<Path> entries = Files.list(scopeDir)) {
            Set<String> out = new HashSet<>();
            entries.forEach(p -> {
                String name = p.getFileName().toString();
                if (name.endsWith(EXT)) out.add(name.substring(0, name.length() - EXT.length()));
            });
            return Set.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list memory keys for scope " + scope, e);
        }
    }

    private Path scopeDir(String scope) {
        return directory.resolve(safeName(scope));
    }

    private Path pathFor(String scope, String key) {
        return scopeDir(scope).resolve(safeName(key) + EXT);
    }

    private static String safeName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.contains("..")) {
            throw new IllegalArgumentException("Illegal scope/key name: " + name);
        }
        return name;
    }
}
