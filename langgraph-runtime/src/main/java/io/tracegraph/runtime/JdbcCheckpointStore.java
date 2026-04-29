package io.tracegraph.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.core.Checkpoint;
import io.tracegraph.core.spi.CheckpointStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class JdbcCheckpointStore<S> implements CheckpointStore {

    private static final String DEFAULT_TABLE = "tracegraph_checkpoint";

    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final Class<S> stateType;
    private final String table;

    private JdbcCheckpointStore(DataSource dataSource, ObjectMapper mapper, Class<S> stateType, String table) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.stateType = Objects.requireNonNull(stateType, "stateType");
        this.table = Objects.requireNonNull(table, "table");
    }

    public static <S> JdbcCheckpointStore<S> of(DataSource dataSource, ObjectMapper mapper, Class<S> stateType) {
        return new JdbcCheckpointStore<>(dataSource, mapper, stateType, DEFAULT_TABLE);
    }

    public static <S> JdbcCheckpointStore<S> of(DataSource dataSource, ObjectMapper mapper, Class<S> stateType, String table) {
        return new JdbcCheckpointStore<>(dataSource, mapper, stateType, table);
    }

    public void initSchema() {
        String ddl = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "execution_id VARCHAR(255) PRIMARY KEY, "
                + "last_completed_node VARCHAR(255) NOT NULL, "
                + "state_json TEXT NOT NULL, "
                + "updated_at TIMESTAMP NOT NULL"
                + ")";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(ddl)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new CheckpointPersistenceException("failed to initialize checkpoint schema", e);
        }
    }

    @Override
    public void save(Checkpoint<?> checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        String json;
        try {
            json = mapper.writeValueAsString(checkpoint.state());
        } catch (Exception e) {
            throw new CheckpointPersistenceException("failed to serialize checkpoint state", e);
        }
        String update = "UPDATE " + table + " SET last_completed_node = ?, state_json = ?, updated_at = ? "
                + "WHERE execution_id = ?";
        String insert = "INSERT INTO " + table + " (execution_id, last_completed_node, state_json, updated_at) "
                + "VALUES (?, ?, ?, ?)";
        Timestamp ts = Timestamp.from(checkpoint.timestamp());
        try (Connection c = dataSource.getConnection()) {
            boolean prevAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = c.prepareStatement(update)) {
                    ps.setString(1, checkpoint.lastCompletedNode());
                    ps.setString(2, json);
                    ps.setTimestamp(3, ts);
                    ps.setString(4, checkpoint.executionId());
                    rows = ps.executeUpdate();
                }
                if (rows == 0) {
                    try (PreparedStatement ps = c.prepareStatement(insert)) {
                        ps.setString(1, checkpoint.executionId());
                        ps.setString(2, checkpoint.lastCompletedNode());
                        ps.setString(3, json);
                        ps.setTimestamp(4, ts);
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
            throw new CheckpointPersistenceException("failed to save checkpoint " + checkpoint.executionId(), e);
        }
    }

    @Override
    public Optional<Checkpoint<?>> latest(String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        String sql = "SELECT last_completed_node, state_json, updated_at FROM " + table + " WHERE execution_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String node = rs.getString(1);
                String json = rs.getString(2);
                Instant updatedAt = rs.getTimestamp(3).toInstant();
                S state = mapper.readValue(json, stateType);
                return Optional.of(new Checkpoint<>(executionId, node, state, updatedAt, false));
            }
        } catch (SQLException | RuntimeException e) {
            throw new CheckpointPersistenceException("failed to load checkpoint " + executionId, e);
        } catch (Exception e) {
            throw new CheckpointPersistenceException("failed to deserialize checkpoint state for " + executionId, e);
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
            throw new CheckpointPersistenceException("failed to delete checkpoint " + executionId, e);
        }
    }
}
