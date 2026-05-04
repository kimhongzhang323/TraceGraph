# Spring Boot Integration

Add the starter to your `pom.xml`:

```xml
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>langgraph-spring-boot-starter</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

The starter auto-configures no-op implementations of all SPIs. Override any with your own `@Bean`:

```java
@Configuration
public class AppConfig {

    @Bean
    public Graph<MyState> graph(NodeListener listener, TraceRecorder recorder) {
        return Graph.<MyState>builder()
            .node("step1", ...)
            .entry("step1")
            .listener(listener)
            .traceRecorder(recorder)
            .build();
    }

    @Bean
    public TraceStore traceStore() {
        return new InMemoryTraceStore();
    }
}
```

## REST Endpoints

With `TraceStore` in context, the starter exposes:

| Endpoint | Description |
|----------|-------------|
| `GET /tracegraph/traces` | List all execution IDs |
| `GET /tracegraph/traces/{id}` | Full trace JSON |
| `GET /tracegraph/traces/{a}/diff/{b}` | Diff two traces |
| `DELETE /tracegraph/traces/{id}` | Delete a trace |
| `POST /tracegraph/traces/{id}/replay?step=N` | Re-run from step N |
| `POST /tracegraph/traces/stream` | SSE event stream |

## Configuration

```properties
tracegraph.web.enabled=true
tracegraph.memory.jdbc.enabled=false
tracegraph.llm.provider=openai
tracegraph.llm.api-key=sk-...
tracegraph.llm.model=gpt-4o
```
