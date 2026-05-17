package io.tracegraph.observability.replay;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.tracegraph.core.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcTraceStore<S> implements TraceStore {

    private static final String DEFAULT_TABLE = "tracegraph_trace";

    private final DataSource dataSource;
    private final Class<S> stateType;
    private final String table;
    private final ObjectMapper mapper;

    private JdbcTraceStore(DataSource dataSource, Class<S> stateType, String table) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.stateType = Objects.requireNonNull(stateType, "stateType");
        this.table = Objects.requireNonNull(table, "table");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    public static <S> JdbcTraceStore<S> of(DataSource dataSource, Class<S> stateType) {
        return new JdbcTraceStore<>(dataSource, stateType, DEFAULT_TABLE);
    }

    public static <S> JdbcTraceStore<S> of(DataSource dataSource, Class<S> stateType, String table) {
        return new JdbcTraceStore<>(dataSource, stateType, table);
    }

    public void initSchema() {
        String ddl = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "execution_id VARCHAR(255) PRIMARY KEY, "
                + "status VARCHAR(32) NOT NULL, "
                + "started_at TIMESTAMP NOT NULL, "
                + "completed_at TIMESTAMP NOT NULL, "
                + "forked_from_execution_id VARCHAR(255), "
                + "forked_from_step_index INT NOT NULL, "
                + "parent_execution_id VARCHAR(255), "
                + "parent_step_index INT NOT NULL DEFAULT -1, "
                + "data_json TEXT NOT NULL"
                + ")";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(ddl)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new TracePersistenceException("failed to initialize trace schema", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(ExecutionTrace<?> trace) {
        Objects.requireNonNull(trace, "trace");
        String json;
        try {
            json = mapper.writeValueAsString(Dto.from((ExecutionTrace<S>) trace));
        } catch (Exception e) {
            throw new TracePersistenceException("failed to serialize trace " + trace.executionId(), e);
        }
        String update = "UPDATE " + table
                + " SET status = ?, started_at = ?, completed_at = ?, "
                + "forked_from_execution_id = ?, forked_from_step_index = ?, "
                + "parent_execution_id = ?, parent_step_index = ?, data_json = ? "
                + "WHERE execution_id = ?";
        String insert = "INSERT INTO " + table
                + " (execution_id, status, started_at, completed_at, "
                + "forked_from_execution_id, forked_from_step_index, "
                + "parent_execution_id, parent_step_index, data_json) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Timestamp started = Timestamp.from(trace.startedAt());
        Timestamp completed = Timestamp.from(trace.completedAt());
        try (Connection c = dataSource.getConnection()) {
            boolean prevAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = c.prepareStatement(update)) {
                    ps.setString(1, trace.status().name());
                    ps.setTimestamp(2, started);
                    ps.setTimestamp(3, completed);
                    ps.setString(4, trace.forkedFromExecutionId());
                    ps.setInt(5, trace.forkedFromStepIndex());
                    ps.setString(6, trace.parentExecutionId());
                    ps.setInt(7, trace.parentStepIndex());
                    ps.setString(8, json);
                    ps.setString(9, trace.executionId());
                    rows = ps.executeUpdate();
                }
                if (rows == 0) {
                    try (PreparedStatement ps = c.prepareStatement(insert)) {
                        ps.setString(1, trace.executionId());
                        ps.setString(2, trace.status().name());
                        ps.setTimestamp(3, started);
                        ps.setTimestamp(4, completed);
                        ps.setString(5, trace.forkedFromExecutionId());
                        ps.setInt(6, trace.forkedFromStepIndex());
                        ps.setString(7, trace.parentExecutionId());
                        ps.setInt(8, trace.parentStepIndex());
                        ps.setString(9, json);
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAutoCommit);
            }
        } catch (SQLException e) {
            throw new TracePersistenceException("failed to save trace " + trace.executionId(), e);
        }
    }

    @Override
    public Optional<ExecutionTrace<?>> load(String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        String sql = "SELECT data_json FROM " + table + " WHERE execution_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String json = rs.getString(1);
                Dto<S> dto = mapper.readValue(json, mapper.getTypeFactory()
                        .constructParametricType(Dto.class, stateType));
                return Optional.of(dto.toTrace());
            }
        } catch (SQLException e) {
            throw new TracePersistenceException("failed to load trace " + executionId, e);
        } catch (Exception e) {
            throw new TracePersistenceException("failed to deserialize trace " + executionId, e);
        }
    }

    @Override
    public void delete(String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        String sql = "DELETE FROM " + table + " WHERE execution_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, executionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new TracePersistenceException("failed to delete trace " + executionId, e);
        }
    }

    @Override
    public List<String> listIds() {
        String sql = "SELECT execution_id FROM " + table + " ORDER BY started_at";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<String> out = new ArrayList<>();
            while (rs.next()) out.add(rs.getString(1));
            return List.copyOf(out);
        } catch (SQLException e) {
            throw new TracePersistenceException("failed to list trace ids", e);
        }
    }

    record Dto<S>(String executionId, S initialState, S finalState,
                  Status status, ErrorDto error,
                  List<StepDto<S>> steps,
                  Instant startedAt, Instant completedAt,
                  String forkedFromExecutionId, int forkedFromStepIndex,
                  String parentExecutionId, Integer parentStepIndex,
                  String correlationId) {

        static <S> Dto<S> from(ExecutionTrace<S> t) {
            List<StepDto<S>> steps = new ArrayList<>(t.steps().size());
            for (TraceStep<S> s : t.steps()) steps.add(StepDto.from(s));
            return new Dto<>(t.executionId(), t.initialState(), t.finalState(),
                    t.status(), ErrorDto.from(t.error()),
                    steps, t.startedAt(), t.completedAt(),
                    t.forkedFromExecutionId(), t.forkedFromStepIndex(),
                    t.parentExecutionId(), t.parentStepIndex(),
                    t.correlationId());
        }

        ExecutionTrace<S> toTrace() {
            List<TraceStep<S>> out = new ArrayList<>(steps == null ? 0 : steps.size());
            if (steps != null) for (StepDto<S> s : steps) out.add(s.toStep());
            int parentIdx = parentStepIndex == null ? -1 : parentStepIndex;
            return new ExecutionTrace<>(executionId, initialState, finalState,
                    status, ErrorDto.toThrowable(error), out, startedAt, completedAt,
                    forkedFromExecutionId, forkedFromStepIndex,
                    parentExecutionId, parentIdx, correlationId);
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
