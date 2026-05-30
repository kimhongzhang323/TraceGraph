# 运行时特性

执行器在普通节点到节点行走之外所做的一切。大多数特性是纯 `tracegraph-core`；持久化检查点在 `tracegraph-runtime`。

> 🌐 English: **[[Runtime Features]]**

## 重试

重试策略是**图定义，而非运行时配置**。逐节点附加 `RetryPolicy`，或设图默认。逐节点优先于默认。

```java
RetryPolicy policy = RetryPolicy.exponential(
        3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(2));

Graph<OrderState> graph = Graph.<OrderState>builder()
        .node("charge", chargeNode, policy)
        .defaultRetryPolicy(RetryPolicy.fixed(2, Duration.ofMillis(50)))
        .entry("charge").terminal("charge")
        .build();
```

- 执行器处理退避并触发 `NodeListener.onRetry`。
- `Error` 与 `InterruptedException` **总是短路**重试。
- 在节点内用 `ctx.idempotencyKey()` 做去重。
- 重试**不**产生额外追踪步骤——`TraceStep.attempts` 记录次数。

`RetryPolicy.fixed(...)` 与 `RetryPolicy.exponential(...)` 是内置策略。

## 异步节点

`AsyncNode<S>` 返回 `CompletableFuture<S>`，与重试/检查点的集成方式和同步节点一致。

```java
.asyncNode("score", (state, ctx) -> CompletableFuture.supplyAsync(() -> score(state)))
```

## 并行扇出

`parallel(name, branches, merger)` 在配置的执行器上并发运行分支。

```java
.parallel("enrich",
        List.of(
                (s, ctx) -> withCustomerProfile(s),
                (s, ctx) -> withFraudCheck(s),
                (s, ctx) -> withInventory(s)),
        (input, branchResults) -> {
            OrderState merged = input;
            for (OrderState branch : branchResults) merged = merged.merge(branch);
            return merged;
        })
```

分支契约（Phase 2c）：

- 分支是**匿名**的——无名称、无路径条目、无监听器事件，对监听器不可见。
- 所有分支接收**相同输入状态**，按**声明顺序**合并。
- **按声明顺序的第一个失败胜出。**
- `parallelAsync(...)` 是 `CompletableFuture` 变体。

默认执行器是**每任务虚拟线程**，按 `run` 惰性创建。用户 `.executor(...)` 不会被图关闭。

## Send / 动态扇出

当并行工作的数量/目标只在运行时已知时，路由节点可生成它们：

```java
NodeResult.sendAll(
        List.of(new Send<>("worker", payloadA), new Send<>("worker", payloadB)),
        merger, currentState);
```

`Send<S>(target, payload)` 是轻量 record。执行器对 `SendAll` 的展开与 `parallel(...)` 一致，但目标与载荷在运行时决定。

## 检查点与恢复

接入 `CheckpointStore`，之后按 executionId 恢复：

```java
Graph<OrderState> graph = Graph.<OrderState>builder()
        .checkpointStore(new InMemoryCheckpointStore())
        .build();

Optional<ExecutionResult<OrderState>> resumed = graph.resume("execution-123");
```

语义：

- **检查点在节点退出之后、解析边之前写入。** 恢复时重新求值已保存 `lastCompletedNode` 的出边。
- 节点在恢复时**至少一次**——节点中途崩溃会从第 1 次尝试重跑。
- 默认 `CheckpointStore` 是 **no-op**；需显式接入。

`tracegraph-runtime` 提供 `InMemoryCheckpointStore` 与 `JdbcCheckpointStore`（单表，默认 `tracegraph_checkpoint`，事务内 UPDATE-then-INSERT upsert，幂等 `initSchema()`，通过 `JdbcCheckpointStore.of(dataSource, mapper, stateType[, table])` 构造，Jackson 可选）。

## 中断（人在回路）

为人工审批暂停运行，之后恢复：

```java
Graph<S> graph = Graph.<S>builder()
        .interruptBefore("approve")   // 或 .interruptAfter("review")
        .checkpointStore(store)
        .build();

ExecutionResult<S> r = graph.run(seed);   // status == INTERRUPTED
graph.resume(r.executionId());            // 继续
```

- `interruptBefore` 写入 `interruptPending=true` 的检查点；`interruptAfter` 写入正常 `lastCompletedNode`。两者都返回 `Status.INTERRUPTED`。
- 不支持 `parallel(...)` 内的逐分支中断。
- Spring Boot starter 暴露 `POST /tracegraph/traces/{id}/resume`——见 **[[REST API 参考|zh-REST-API-Reference]]**。

## 动态路由

`RoutingNode<S>` 在运行时选择下一个节点：

```java
.routingNode("router", (state, ctx) ->
        state.needsReview()
                ? NodeResult.goTo("review", state)   // 绕过边
                : NodeResult.of(state))              // 走正常边解析
```

- `NodeResult.goTo(name, state)` 绕过边解析直接跳转。
- `NodeResult.of(state)` 走正常边解析。
- 未知 `goTo` 目标抛 `NodeExecutionException`。

## 子图

把已编译的图作为单个节点嵌入：

```java
.subgraph("inner", innerGraph)   // 两个图共享状态类型 <S>
```

- 两个图必须共享状态类型 `<S>`。
- 追踪记录**一个父步骤**，其 `children` 由内部追踪填充（需兼容的 `TraceRecorder` 设置）。
- 不支持把父图恢复到子图中途——子图从头重跑。

## 流式

`Graph.stream(initial)` 返回 `Flow.Publisher<NodeEvent<S>>`：

```java
graph.stream(initial).subscribe(subscriber);
// 事件：NodeEnter / NodeExit / NodeRetry / Failed / Complete
```

- 由 `SubmissionPublisher` 支撑（默认缓冲 256）；缓冲满时生产者阻塞——**不丢事件**，且持久记录也在 `TraceStore`。
- core 保持纯 JDK。
- Spring Boot starter 以 SSE 暴露 `POST /tracegraph/traces/stream`。

> 这里的流式是**图级事件**，区别于 **LLM token 流式**（`LlmClient.stream(...)`）——见 **[[LLM 连接器|zh-LLM-Connectors]]**。

## 可视化

纯结构渲染，无新依赖：

```java
String mermaid  = graph.toMermaid();
String plantUml = graph.toPlantUml();
```

子图渲染为 `subgraph` / `package` 簇。

---

**相关：** **[[记忆|zh-Memory]]** · **[[可观测性与重放|zh-Observability-and-Replay]]** · **[[执行模型|zh-Execution-Model]]**
