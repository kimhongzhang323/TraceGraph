package io.tracegraph.connectors.prompt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class PromptLibrary {

    private final Map<String, PromptTemplate> templates;

    private PromptLibrary(Map<String, PromptTemplate> templates) {
        this.templates = Collections.unmodifiableMap(templates);
    }

    public static PromptLibrary fromClasspath(String directory) {
        String dir = directory.endsWith("/") ? directory : directory + "/";
        Map<String, PromptTemplate> map = new LinkedHashMap<>();
        // Use a known resource to enumerate siblings is not trivial on classpath;
        // we rely on the caller to have the resource index available via listing.
        // We load via a known index file or scan by attempting known entries.
        // Since Java classpath directories can't be listed universally,
        // we load an index file listing prompt file names.
        String indexPath = dir + "index.txt";
        InputStream indexStream = PromptLibrary.class.getClassLoader().getResourceAsStream(indexPath);
        if (indexStream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(indexStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.strip();
                    if (line.isEmpty()) continue;
                    String resourcePath = dir + line;
                    InputStream is = PromptLibrary.class.getClassLoader().getResourceAsStream(resourcePath);
                    if (is == null) continue;
                    PromptTemplate t = parseStream(is);
                    map.put(t.id(), t);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read prompt index: " + indexPath, e);
            }
        }
        return new PromptLibrary(map);
    }

    public static PromptLibrary fromDirectory(Path directory) {
        Map<String, PromptTemplate> map = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(p -> p.toString().endsWith(".prompt"))
                 .sorted()
                 .forEach(p -> {
                     try (InputStream is = Files.newInputStream(p)) {
                         PromptTemplate t = parseStream(is);
                         map.put(t.id(), t);
                     } catch (IOException e) {
                         throw new UncheckedIOException("Failed to read prompt file: " + p, e);
                     }
                 });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list directory: " + directory, e);
        }
        return new PromptLibrary(map);
    }

    public PromptTemplate get(String id) {
        PromptTemplate t = templates.get(id);
        if (t == null) {
            throw new IllegalArgumentException("No template with id: " + id);
        }
        return t;
    }

    public Optional<PromptTemplate> find(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    public Collection<PromptTemplate> all() {
        return templates.values();
    }

    private static PromptTemplate parseStream(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String idLine = reader.readLine();
            String versionLine = reader.readLine();
            if (idLine == null || versionLine == null) {
                throw new IllegalArgumentException("Prompt file must have at least two header lines");
            }
            String id = parseHeader(idLine, "id");
            String version = parseHeader(versionLine, "version");
            StringBuilder body = new StringBuilder();
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) body.append('\n');
                body.append(line);
                first = false;
            }
            return PromptTemplate.of(id, version, body.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String parseHeader(String line, String key) {
        String prefix = "# " + key + ":";
        if (!line.startsWith(prefix)) {
            throw new IllegalArgumentException("Expected header line starting with '" + prefix + "', got: " + line);
        }
        return line.substring(prefix.length()).strip();
    }
}
