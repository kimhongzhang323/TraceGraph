package io.tracegraph.rag;

import io.tracegraph.core.spi.VectorStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * {@link VectorStore} adapter backed by PostgreSQL with the pgvector extension.
 *
 * <p>{@code scope} maps to a table name (must match {@code [a-zA-Z_][a-zA-Z0-9_]*}).
 * Each scope table is created on first use; call {@link #initSchema(String)} explicitly
 * during application startup for fail-fast behavior.
 *
 * <p>Vectors are passed as text literals cast to the {@code vector} type
 * ({@code '...'::vector}) — no extra JDBC type extension beyond the standard PostgreSQL driver
 * is required.
 */
public final class PgVectorStore implements VectorStore {

    private final DataSource dataSource;

    private PgVectorStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * Creates (if absent) the table for {@code scope} with:
     * {@code id TEXT PRIMARY KEY, embedding vector, metadata TEXT}.
     */
    public void initSchema(String scope) {
        String sql = "CREATE TABLE IF NOT EXISTS " + quotedTable(scope)
                + " (id TEXT PRIMARY KEY, embedding vector, metadata TEXT)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException e) {
            throw new PgVectorException("Failed to initialize schema for scope: " + scope, e);
        }
    }

    @Override
    public void upsert(String scope, String id, float[] embedding, Map<String, String> metadata) {
        ensureTable(scope);
        String sql = "INSERT INTO " + quotedTable(scope) + " (id, embedding, metadata) "
                + "VALUES (?, ?::vector, ?) "
                + "ON CONFLICT (id) DO UPDATE SET "
                + "embedding = EXCLUDED.embedding, metadata = EXCLUDED.metadata";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, toVectorLiteral(embedding));
            ps.setString(3, serializeMeta(metadata));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PgVectorException("Upsert failed for scope=" + scope + " id=" + id, e);
        }
    }

    @Override
    public List<VectorMatch> query(String scope, float[] embedding, int topK) {
        ensureTable(scope);
        // <-> is L2 distance; convert to a similarity-flavored score with 1/(1+d)
        String sql = "SELECT id, metadata, (1.0 / (1.0 + (embedding <-> ?::vector))) AS score "
                + "FROM " + quotedTable(scope)
                + " ORDER BY embedding <-> ?::vector LIMIT ?";
        String vectorLiteral = toVectorLiteral(embedding);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vectorLiteral);
            ps.setString(2, vectorLiteral);
            ps.setInt(3, topK);
            try (ResultSet rs = ps.executeQuery()) {
                List<VectorMatch> matches = new ArrayList<>();
                while (rs.next()) {
                    matches.add(new VectorMatch(
                            rs.getString("id"),
                            rs.getFloat("score"),
                            deserializeMeta(rs.getString("metadata"))));
                }
                return List.copyOf(matches);
            }
        } catch (SQLException e) {
            throw new PgVectorException("Query failed for scope=" + scope, e);
        }
    }

    @Override
    public void delete(String scope, String id) {
        ensureTable(scope);
        String sql = "DELETE FROM " + quotedTable(scope) + " WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PgVectorException("Delete failed for scope=" + scope + " id=" + id, e);
        }
    }

    private void ensureTable(String scope) {
        initSchema(scope);
    }

    static String toVectorLiteral(float[] embedding) {
        StringJoiner sj = new StringJoiner(",", "[", "]");
        for (float v : embedding) {
            sj.add(Float.toString(v));
        }
        return sj.toString();
    }

    private static String quotedTable(String scope) {
        if (!scope.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new PgVectorException(
                    "Invalid scope name (must match [a-zA-Z_][a-zA-Z0-9_]*): " + scope);
        }
        return "\"" + scope + "\"";
    }

    static String serializeMeta(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : metadata.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(escape(e.getKey())).append("\":\"")
              .append(escape(e.getValue())).append('"');
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    static Map<String, String> deserializeMeta(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.strip())) {
            return Map.of();
        }
        // Minimal flat-map JSON parser — sufficient for the metadata contract (string values only)
        Map<String, String> result = new HashMap<>();
        String s = json.strip();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);
        int i = 0;
        while (i < s.length()) {
            // skip whitespace and commas
            while (i < s.length() && (s.charAt(i) == ',' || Character.isWhitespace(s.charAt(i)))) i++;
            if (i >= s.length()) break;
            String[] kv = nextKeyValue(s, i);
            if (kv == null) break;
            result.put(kv[0], kv[1]);
            i = Integer.parseInt(kv[2]);
        }
        return Map.copyOf(result);
    }

    private static String[] nextKeyValue(String s, int start) {
        if (start >= s.length() || s.charAt(start) != '"') return null;
        int[] keyEnd = nextString(s, start);
        if (keyEnd == null) return null;
        String key = keyEnd[0] < 0 ? "" : unescape(s.substring(start + 1, keyEnd[0]));
        int pos = keyEnd[1];
        while (pos < s.length() && s.charAt(pos) != ':') pos++;
        pos++; // skip ':'
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        if (pos >= s.length() || s.charAt(pos) != '"') return null;
        int[] valEnd = nextString(s, pos);
        if (valEnd == null) return null;
        String value = valEnd[0] < 0 ? "" : unescape(s.substring(pos + 1, valEnd[0]));
        return new String[]{key, value, String.valueOf(valEnd[1])};
    }

    /** Returns [closingQuoteIndex, posAfter] or null on error. */
    private static int[] nextString(String s, int openQuote) {
        int i = openQuote + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == '"') {
                return new int[]{i, i + 1};
            } else {
                i++;
            }
        }
        return null;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    public static PgVectorStore of(DataSource dataSource) {
        return new PgVectorStore(dataSource);
    }
}
