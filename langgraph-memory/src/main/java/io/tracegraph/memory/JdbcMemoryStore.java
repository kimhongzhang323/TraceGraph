package io.tracegraph.memory;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import io.tracegraph.core.spi.MemoryStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * JDBC-backed {@link MemoryStore} that persists scoped key/value entries as JSON.
 */
public final class JdbcMemoryStore implements MemoryStore {

    private static final String DEFAULT_TABLE = "tracegraph_memory";

    private final DataSource dataSource;
    private final String table;
    private final ObjectMapper mapper;

    @SuppressWarnings("deprecation")
    private JdbcMemoryStore(DataSource dataSource, String table) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.table = Objects.requireNonNull(table, "table");
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        this.mapper = new ObjectMapper()
                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
    }

    public static JdbcMemoryStore of(DataSource dataSource) {
        return new JdbcMemoryStore(dataSource, DEFAULT_TABLE);
    }

    public static JdbcMemoryStore of(DataSource dataSource, String table) {
        return new JdbcMemoryStore(dataSource, table);
    }

    public void initSchema() {
        String ddl = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "scope VARCHAR(255) NOT NULL, "
                + "key_name VARCHAR(255) NOT NULL, "
                + "value_json TEXT NOT NULL, "
                + "updated_at TIMESTAMP NOT NULL, "
                + "PRIMARY KEY (scope, key_name)"
                + ")";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(ddl)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new MemoryPersistenceException("failed to initialize memory schema", e);
        }
    }

    @Override
    public Optional<Object> get(String scope, String key) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(key, "key");
        String sql = "SELECT value_json FROM " + table + " WHERE scope = ? AND key_name = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, scope);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.ofNullable(mapper.readValue(rs.getString(1), Object.class));
            }
        } catch (SQLException e) {
            throw new MemoryPersistenceException("failed to load memory " + scope + "/" + key, e);
        } catch (Exception e) {
            throw new MemoryPersistenceException("failed to deserialize memory " + scope + "/" + key, e);
        }
    }

    @Override
    public void put(String scope, String key, Object value) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        String json;
        try {
            json = mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new MemoryPersistenceException("failed to serialize memory " + scope + "/" + key, e);
        }
        String update = "UPDATE " + table
                + " SET value_json = ?, updated_at = ? WHERE scope = ? AND key_name = ?";
        String insert = "INSERT INTO " + table
                + " (scope, key_name, value_json, updated_at) VALUES (?, ?, ?, ?)";
        Timestamp now = Timestamp.from(Instant.now());
        try (Connection c = dataSource.getConnection()) {
            boolean prevAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = c.prepareStatement(update)) {
                    ps.setString(1, json);
                    ps.setTimestamp(2, now);
                    ps.setString(3, scope);
                    ps.setString(4, key);
                    rows = ps.executeUpdate();
                }
                if (rows == 0) {
                    try (PreparedStatement ps = c.prepareStatement(insert)) {
                        ps.setString(1, scope);
                        ps.setString(2, key);
                        ps.setString(3, json);
                        ps.setTimestamp(4, now);
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
            throw new MemoryPersistenceException("failed to save memory " + scope + "/" + key, e);
        }
    }

    @Override
    public boolean delete(String scope, String key) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(key, "key");
        String sql = "DELETE FROM " + table + " WHERE scope = ? AND key_name = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, scope);
            ps.setString(2, key);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new MemoryPersistenceException("failed to delete memory " + scope + "/" + key, e);
        }
    }

    @Override
    public Set<String> keys(String scope) {
        Objects.requireNonNull(scope, "scope");
        String sql = "SELECT key_name FROM " + table + " WHERE scope = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, scope);
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> out = new HashSet<>();
                while (rs.next()) out.add(rs.getString(1));
                return Set.copyOf(out);
            }
        } catch (SQLException e) {
            throw new MemoryPersistenceException("failed to list memory keys for " + scope, e);
        }
    }

    @Override
    public List<String> pagedKeys(String scope, int offset, int limit) {
        Objects.requireNonNull(scope, "scope");
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
        if (limit < 0) throw new IllegalArgumentException("limit must be >= 0");
        String sql = "SELECT key_name FROM " + table
                + " WHERE scope = ? ORDER BY key_name LIMIT ? OFFSET ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, scope);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> out = new ArrayList<>();
                while (rs.next()) out.add(rs.getString(1));
                return List.copyOf(out);
            }
        } catch (SQLException e) {
            throw new MemoryPersistenceException("failed to page memory keys for " + scope, e);
        }
    }
}
