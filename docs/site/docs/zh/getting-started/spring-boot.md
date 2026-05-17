---
title: Spring Boot 设置
---

# Spring Boot 设置

TraceGraph 提供 Spring Boot 3 Starter，可自动注入 SPI Bean、暴露追踪 REST 端点，并可选地连接 JDBC 内存存储和 LLM 客户端。

## 添加依赖

```xml
<dependency>
  <groupId>io.tracegraph</groupId>
  <artifactId>tracegraph-spring-boot-starter</artifactId>
  <version>0.3.0</version>
</dependency>
```

## 将图声明为 Bean

Starter **不会**自动注册 `Graph<?>` — 状态类型 `<S>` 由您自行定义。

```java
@Configuration
public class AppConfig {

    @Bean
    Graph<ChatState> chatGraph(NodeListener listener, CheckpointStore checkpoints) {
        return Graph.<ChatState>builder()
            .node("ingest", (s, ctx) -> s.withInput(s.rawInput().strip()))
            .node("respond", (s, ctx) -> s.withOutput("Echo: " + s.input()))
            .edge("ingest", "respond")
            .entry("ingest")
            .terminal("respond")
            .listener(listener)
            .checkpointStore(checkpoints)
            .build();
    }
}
```

`NodeListener` 和 `CheckpointStore` Bean 由 `TraceGraphAutoConfiguration` 以无操作实现注册；您自己的 `@Bean` 定义会通过 `@ConditionalOnMissingBean` 自动覆盖它们。

## 启用追踪 REST API

将 `tracegraph-observability` 添加到类路径并声明一个 `TraceStore` Bean。Starter 会自动注册 `TraceController`：

```xml
<dependency>
  <groupId>io.tracegraph</groupId>
  <artifactId>tracegraph-observability</artifactId>
  <version>0.3.0</version>
</dependency>
```

```java
@Bean
TraceStore<ChatState> traceStore() {
    return new InMemoryTraceStore<>();
}
```

Bean 就位后，`GET /tracegraph/traces` 即可使用。

## 配置 LLM 提供商

```yaml
tracegraph:
  llm:
    provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
```

此配置会注册一个 `OpenAiLlmClient` Bean，您的节点可将其注入使用。

## 要点总结

- 将 `Graph<YourState>` 声明为 `@Bean` — Starter 会围绕它注入各 SPI。
- 四个 SPI（`NodeListener`、`CheckpointStore`、`TraceRecorder`、`MemoryStore`）的无操作默认实现可被您自己的 Bean 覆盖。
- 追踪 REST 端点仅在类路径上存在 `TraceStore` Bean 时出现。
- LLM 自动配置通过 `tracegraph.llm.provider` 激活。
