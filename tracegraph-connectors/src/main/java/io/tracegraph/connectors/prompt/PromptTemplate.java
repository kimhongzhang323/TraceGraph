package io.tracegraph.connectors.prompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PromptTemplate(String id, String version, String body, List<String> variables) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    public PromptTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(body, "body");
        variables = variables == null ? List.of() : List.copyOf(variables);
    }

    public String render(Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String val = values.get(key);
            if (val == null) {
                throw new IllegalArgumentException("Missing variable: " + key);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public String checksum() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static PromptTemplate of(String id, String version, String body) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(body, "body");
        Matcher m = PLACEHOLDER.matcher(body);
        List<String> vars = new ArrayList<>();
        while (m.find()) {
            String v = m.group(1);
            if (!vars.contains(v)) {
                vars.add(v);
            }
        }
        return new PromptTemplate(id, version, body, Collections.unmodifiableList(vars));
    }
}
