# TraceGraph Spring Boot App Example

A full Spring Boot application demonstrating TraceGraph auto-configuration, trace storage, and REST endpoints.

## Run

```bash
mvn -f examples/spring-boot-app/pom.xml spring-boot:run
```

## Explore

```bash
# List all trace IDs
curl http://localhost:8080/tracegraph/traces

# View a specific trace (use an ID from the list)
curl http://localhost:8080/tracegraph/traces/{id}
```

## What it demonstrates

- `@SpringBootApplication` with TraceGraph auto-config
- Defining a `Graph<S>` bean with `TraceRecorder` and `NodeListener` injected
- `InMemoryTraceStore` for trace persistence
- `CommandLineRunner` that runs the graph and prints execution IDs
- REST endpoints from `TraceController` (no extra code needed)
