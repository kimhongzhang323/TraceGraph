package io.tracegraph.observability.replay;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.tracegraph.core.Status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class JsonFileTraceStore<S> implements TraceStore {

    private final Path directory;
    private final Class<S> stateType;
    private final ObjectMapper mapper;

    private JsonFileTraceStore(Path directory, Class<S> stateType) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.stateType = Objects.requireNonNull(stateType, "stateType");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create trace store directory: " + directory, e);
        }
    }

    public static <S> JsonFileTraceStore<S> of(Path directory, Class<S> stateType) {
        return new JsonFileTraceStore<>(directory, stateType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(ExecutionTrace<?> trace) {
        Path target = pathFor(trace.executionId());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Dto<S> dto = Dto.from((ExecutionTrace<S>) trace);
        try {
            Files.write(tmp, mapper.writeValueAsBytes(dto));
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save trace " + trace.executionId(), e);
        }
    }

    @Override
    public Optional<ExecutionTrace<?>> load(String executionId) {
        Path file = pathFor(executionId);
        if (!Files.exists(file)) return Optional.empty();
        try {
            byte[] bytes = Files.readAllBytes(file);
            Dto<S> dto = mapper.readValue(bytes, mapper.getTypeFactory()
                    .constructParametricType(Dto.class, stateType));
            return Optional.of(dto.toTrace());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load trace " + executionId, e);
        }
    }

    @Override
    public void delete(String executionId) {
        try {
            Files.deleteIfExists(pathFor(executionId));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete trace " + executionId, e);
        }
    }

    @Override
    public List<String> listIds() {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> entries = Files.list(directory)) {
            List<String> out = new ArrayList<>();
            entries.forEach(p -> {
                String name = p.getFileName().toString();
                if (name.endsWith(".json")) out.add(name.substring(0, name.length() - 5));
            });
            return List.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list traces in " + directory, e);
        }
    }

    private Path pathFor(String executionId) {
        if (executionId.indexOf('/') >= 0 || executionId.indexOf('\\') >= 0 || executionId.contains("..")) {
            throw new IllegalArgumentException("Illegal characters in executionId: " + executionId);
        }
        return directory.resolve(executionId + ".json");
    }

    record Dto<S>(String executionId, S initialState, S finalState,
                  Status status, ErrorDto error,
                  List<StepDto<S>> steps,
                  Instant startedAt, Instant completedAt,
                  String forkedFromExecutionId, int forkedFromStepIndex) {

        static <S> Dto<S> from(ExecutionTrace<S> t) {
            List<StepDto<S>> steps = new ArrayList<>(t.steps().size());
            for (TraceStep<S> s : t.steps()) steps.add(StepDto.from(s));
            return new Dto<>(t.executionId(), t.initialState(), t.finalState(),
                    t.status(), ErrorDto.from(t.error()),
                    steps, t.startedAt(), t.completedAt(),
                    t.forkedFromExecutionId(), t.forkedFromStepIndex());
        }

        ExecutionTrace<S> toTrace() {
            List<TraceStep<S>> out = new ArrayList<>(steps == null ? 0 : steps.size());
            if (steps != null) for (StepDto<S> s : steps) out.add(s.toStep());
            return new ExecutionTrace<>(executionId, initialState, finalState,
                    status, ErrorDto.toThrowable(error), out, startedAt, completedAt,
                    forkedFromExecutionId, forkedFromStepIndex);
        }
    }

    record StepDto<S>(int index, String nodeName, int attempts,
                      S before, S after, long durationNanos, ErrorDto error,
                      List<StepDto<S>> children, TraceStep.Usage usage,
                      String rawInput, String rawOutput) {
        static <S> StepDto<S> from(TraceStep<S> s) {
            List<StepDto<S>> childDtos = new ArrayList<>(s.children().size());
            for (TraceStep<S> c : s.children()) childDtos.add(StepDto.from(c));
            return new StepDto<>(s.index(), s.nodeName(), s.attempts(),
                    s.before(), s.after(), s.duration().toNanos(), ErrorDto.from(s.error()),
                    childDtos.isEmpty() ? null : childDtos, s.usage(), s.rawInput(), s.rawOutput());
        }
        TraceStep<S> toStep() {
            List<TraceStep<S>> childSteps = new ArrayList<>();
            if (children != null) for (StepDto<S> c : children) childSteps.add(c.toStep());
            return new TraceStep<>(index, nodeName, attempts, before, after,
                    Duration.ofNanos(durationNanos), ErrorDto.toThrowable(error), childSteps, usage,
                    rawInput, rawOutput);
        }
    }

    record ErrorDto(String className, String message) {
        static ErrorDto from(Throwable t) {
            return t == null ? null : new ErrorDto(t.getClass().getName(), t.getMessage());
        }
        static Throwable toThrowable(ErrorDto e) {
            return e == null ? null : new RuntimeException("[" + e.className() + "] " + e.message());
        }
    }
}
