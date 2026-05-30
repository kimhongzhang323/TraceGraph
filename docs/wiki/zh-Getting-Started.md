# 快速开始

本页带你从零运行一个图，并指引后续步骤。

> 🌐 English: **[[Getting Started]]**

## 要求

- **JDK 21**（record、模式匹配、虚拟线程）
- **Maven 3.9+**

若 Maven 选错了 JDK，检查 `mvn -version` 并确保 `JAVA_HOME` 指向 Java 21。GitHub Actions CI 同样基于 JDK 21，本地与 CI 一致。

## 安装

选择与用例匹配的最小模块集。Maven `groupId` 为 `site.tracegraph`；Java 包名为 `io.tracegraph.*`（与 groupId 独立）。

```xml
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-core</artifactId>
    <version>0.3.0</version>
</dependency>
```

按需添加支撑模块：

| 模块 | 何时添加 |
|---|---|
| `tracegraph-runtime` | 执行需跨进程存活或从检查点恢复 |
| `tracegraph-memory` | 节点需要作用域化的跨运行记忆 |
| `tracegraph-observability` | 需要追踪重放、调试产物或 OpenTelemetry span |
| `tracegraph-connectors` | 节点需要 LLM、提示词、结构化输出或 MCP 辅助 |
| `tracegraph-rag` | 需要检索与重排工具 |
| `tracegraph-spring-boot-starter` | 以最少接线接入 Spring Boot 应用 |

完整列表见 **[[模块|zh-Modules]]**。

### 从源码构建

```bash
mvn -B -ntp install      # 将所有产物安装到本地仓库
```

## 你的第一个图

核心 API 使用普通 Java record 与函数。图被显式装配，并返回类型化的 `ExecutionResult`。

```java
import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;

record OrderState(String id, boolean valid, boolean charged, boolean shipped) {
    OrderState withValid(boolean value)   { return new OrderState(id, value, charged, shipped); }
    OrderState withCharged(boolean value) { return new OrderState(id, valid, value, shipped); }
    OrderState withShipped(boolean value) { return new OrderState(id, valid, charged, value); }
}

Graph<OrderState> graph = Graph.<OrderState>builder()
        .node("validate", (state, ctx) -> state.withValid(true))
        .node("charge",   (state, ctx) -> state.withCharged(true))
        .node("ship",     (state, ctx) -> state.withShipped(true))
        .entry("validate")
        .edge("validate", "charge", OrderState::valid)   // 条件边
        .edge("charge", "ship")                          // 无条件边
        .terminal("ship")
        .build();

ExecutionResult<OrderState> result = graph.run(new OrderState("o-1", false, false, false));
```

`ExecutionResult<S>` 暴露 `executionId`、`finalState`、`path`、`status`、`error`。见 **[[核心概念|zh-Core-Concepts]]** 与 **[[执行模型|zh-Execution-Model]]**。

### 加入持久化与可观测性

```java
Graph<OrderState> durableGraph = Graph.<OrderState>builder()
        .node("validate", (state, ctx) -> state.withValid(true))
        .node("charge",   (state, ctx) -> state.withCharged(true),
                RetryPolicy.fixed(3, Duration.ofMillis(100)))
        .entry("validate")
        .edge("validate", "charge", OrderState::valid)
        .terminal("charge")
        .traceRecorder(new RecordingTraceRecorder(new InMemoryTraceStore()))
        .listener(OtelNodeListener.usingGlobal())
        .build();
```

## 推荐的采用顺序

1. 用 `mvn -B -ntp verify` 构建仓库。
2. 运行 `examples/quickstart`，体验一个纯 Java 小图。
3. 若需要 HTTP 端点与自动配置，转到 `examples/spring-boot-app`。
4. **逐模块**加入可观测性、重放、记忆或连接器，而非一次性全加。

对许多团队，最佳路径是**先 `core`，再 `observability`**，然后只加真正需要的存储与连接器模块。

## 可运行示例

```bash
mvn -f examples/quickstart/pom.xml exec:java
mvn -f examples/spring-boot-app/pom.xml spring-boot:run
mvn -f examples/rag-agent/pom.xml exec:java
mvn -f examples/react-agent/pom.xml exec:java
mvn -f examples/hitl-approval/pom.xml exec:java
```

## 构建与测试

```bash
mvn test                       # 运行全部测试
mvn -pl tracegraph-core test    # 测试单个模块
mvn verify                     # 完整验证构建
mvn -B install -DskipTests     # 安装产物到本地仓库
mvn clean                      # 清理 target/
```

构建使用**严格编译设置**（`-Xlint:all -Werror`）——警告即中断构建。

---

**下一步：** **[[教程|zh-Tutorial]]** → **[[核心概念|zh-Core-Concepts]]** → **[[运行时特性|zh-Runtime-Features]]**
