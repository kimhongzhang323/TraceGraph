---
title: TraceGraph 文档（中文）
---

# TraceGraph

**面向 JVM 的生产级代理运行时。** 类型化图、持久化内存、深度可观测性。

## 为什么选择 TraceGraph？

- **类型化图** — 通过 Java 类型系统实现编译期安全
- **持久化内存** — 支持 JDBC、文件和内存后端的跨执行状态
- **深度可观测性** — 完整的追踪回放、步骤级差异对比，以及 OpenTelemetry 集成
- **Spring Boot 就绪** — 所有 SPI 的自动配置

## 快速示例

```java
record MyState(String input, String output) {}

Graph<MyState> graph = Graph.<MyState>builder()
    .node("process", (state, ctx) -> new MyState(state.input(), "已处理：" + state.input()))
    .entry("process")
    .build();

var result = graph.run(new MyState("hello", null));
System.out.println(result.finalState().output()); // 已处理：hello
```

## 安装

```xml
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-core</artifactId>
    <version>0.3.0</version>
</dependency>
```

## 模块说明

| 模块 | 用途 |
|------|------|
| `tracegraph-core` | 图引擎、SPI 接口 |
| `tracegraph-runtime` | 检查点、重试、异步执行 |
| `tracegraph-observability` | OTel 集成、追踪回放、差异对比 |
| `tracegraph-memory` | 内存存储实现 |
| `tracegraph-connectors` | LLM 客户端、ReAct、工具调用 |
| `tracegraph-rag` | 向量存储、嵌入向量、检索器 |
| `tracegraph-spring-boot-starter` | Spring Boot 自动配置 |

## 导航

- **快速入门** — [安装](getting-started/installation.md) · [第一个图](getting-started/first-graph.md) · [快速上手](getting-started/quickstart.md) · [Spring Boot 设置](getting-started/spring-boot.md)
- **教程** — 从节点与边开始，逐步深入到 LLM、ReAct 代理、RAG 管道、HITL 中断和追踪回放
- **概念** — 图与节点、状态与上下文、边与路由、可观测性、内存、连接器
- **参考** — [REST API](reference/rest-api.md) · [配置](reference/configuration.md) · [图复杂度](reference/complexity.md)
