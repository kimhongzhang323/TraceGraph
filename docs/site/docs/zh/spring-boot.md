---
title: Spring Boot 集成
---

# Spring Boot 集成

将 Starter 添加到 `pom.xml`：

```xml
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-spring-boot-starter</artifactId>
    <version>0.3.0</version>
</dependency>
```

Starter 会自动配置所有 SPI 的无操作实现。您可以用自己的 `@Bean` 覆盖任意实现：

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

## REST 端点

当 `TraceStore` 存在于上下文中时，Starter 会暴露以下端点：

| 端点 | 说明 |
|------|------|
| `GET /tracegraph/traces` | 列出所有执行 ID |
| `GET /tracegraph/traces/{id}` | 完整追踪记录 JSON |
| `GET /tracegraph/traces/{a}/diff/{b}` | 比较两条追踪记录 |
| `DELETE /tracegraph/traces/{id}` | 删除追踪记录 |
| `POST /tracegraph/traces/{id}/replay?step=N` | 从第 N 步重新运行 |
| `POST /tracegraph/traces/stream` | SSE 事件流 |

## 配置

```properties
tracegraph.web.enabled=true
tracegraph.memory.jdbc.enabled=false
tracegraph.llm.provider=openai
tracegraph.llm.api-key=sk-...
tracegraph.llm.model=gpt-4o
```
