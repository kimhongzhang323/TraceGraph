# Trace UI — Overview

The Trace UI is a browser-based dashboard for inspecting, replaying, and comparing agent executions. It reads directly from the TraceGraph REST API so there is no separate backend to deploy.

## Prerequisites

To use the Trace UI you need:

1. **`tracegraph-spring-boot-starter`** on the classpath.
2. **A `Graph<?>` bean** declared in your Spring application context.
3. **A `TraceStore` bean** — `InMemoryTraceStore` works for development; `JsonFileTraceStore` or `JdbcTraceStore` for production.
4. **`tracegraph-observability`** on the classpath (provides `TraceStore`, `RecordingTraceRecorder`, and related types).

## Enabling the UI

The UI is enabled by default when the above conditions are met. The static assets and API routes are served by `TraceUiAutoConfiguration`.

To disable:

```yaml
tracegraph:
  ui:
    enabled: false
```

## Accessing the UI

Once your Spring Boot application is running, open:

```
http://localhost:8080/tracegraph/ui/
```

The dashboard loads in the browser and connects to the REST API at the same origin. No CORS configuration is needed for same-origin use.

## The four views

| View | URL fragment | Purpose |
|------|-------------|---------|
| Trace list | `#/traces` | Browse all stored executions with status and timestamp. |
| Trace detail | `#/traces/:id` | Step-by-step state diff for one execution; trigger replay from any step. |
| Graph structure | `#/graph` | Interactive DAG render of the compiled graph (nodes, edges, subgraph clusters). |
| Diff view | `#/diff` | Side-by-side comparison of two execution traces. |

See [Views Guide](views.md) for full details on each view.

## Architecture note

The UI is a single-page application served as static assets from the `tracegraph-ui` JAR. It calls the standard TraceGraph REST endpoints — the same ones documented in the [REST API reference](../reference/rest-api.md). You can use the REST API directly (e.g., from `curl` or Postman) without the UI.
