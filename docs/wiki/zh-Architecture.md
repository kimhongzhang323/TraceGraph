# 架构设计

TraceGraph 背后的设计理由：它为何如此成形、接缝在哪里、哪些约束是刻意为之。如果 **[[zh-Core-Concepts|核心概念]]** 讲的是 *what*，本页讲的是 *why*。

> 🌐 本页为 AI 机翻草稿，需人工校对。English: **[[Architecture]]**

## 产品主旨

> **面向 JVM 的生产级智能体运行时。** 类型化图、持久化记忆、深度可观测性。

TraceGraph **不是**"Java 版 LangGraph"。每个设计选择都强化三个属性：**可靠性、可调试性、企业就绪**。整个架构要保护的杀手级差异化：

> **可以重放任意一次智能体执行，并附带完整的状态差异与推理追踪。**

这一能力解释了下面大多数接缝——记录与监听分离、边是数据而非 lambda、状态不可变、检查点在循环中的精确点写入。

## 模块边界

硬规则：**`tracegraph-core` 保持精简**——运行时仅 SLF4J API，无 Spring、Jackson 或 OpenTelemetry。更重的东西通过 SPI 放在其他模块。

**决策规则：** 如果某功能会迫使 `tracegraph-core` 依赖 Spring / Jackson / OTel / 某个记忆存储——它就放到别的模块。具体：

- 记忆实现（JDBC、Redis、向量）→ `tracegraph-memory`，绝不进 core。
- 任何 Spring 内容 → 仅 `tracegraph-spring-boot-starter`（它只依赖 `spring-boot-autoconfigure`）。
- OpenTelemetry → 通过 `tracegraph-observability` 的 `NodeListener` 接入，core 绝不导入。

各模块内容见 **[[zh-Modules|模块]]**。

## SPI 接缝

core 定义五个服务提供接口，并提供 no-op 或最小默认实现；其他模块提供真实实现；Spring starter 自动接线。

| SPI | 形态 | 为何是 SPI |
|---|---|---|
| `NodeListener` | span 形态；**对 executionId 无感知** | 指标/追踪而不把 core 耦合到 OTel |
| `TraceRecorder` | 步骤记录；**感知 executionId** | 重放需要逐执行历史；与监听器分离 |
| `CheckpointStore` | 按 executionId 存取 | 持久化而不把 core 耦合到 JDBC |
| `MemoryStore` | 作用域键值 | 跨运行状态而不把 core 耦合到数据库 |
| `Guardrail<T>` | ALLOW/BLOCK/TRANSFORM | 在 core 之外可组合的内容门控 |

### 为何要两个可观测性 SPI？

`NodeListener` 与 `TraceRecorder` 看似相似，却刻意区分：

- **`NodeListener` span 形态且对 executionId 无感知**——它干净地映射到 OpenTelemetry span（当前 span 是环境隐含的）。每节点一个 span；重试是 span 事件；`parallel(...)` 内分支不可见。
- **`TraceRecorder` 感知 executionId**——重放必须重建*哪次运行*产生了*哪些步骤*、在恢复时追加、并追踪分叉/父级血缘。这需要手握 executionId。

把二者合并会迫使 OTel 携带 executionId（不该），或迫使重放丢失血缘（不能）。保持分离正是让两者都干净的原因。

## 执行循环，以及顺序为何重要

两条顺序规则承载关键作用：

1. **检查点在节点退出之后、解析边之前写入。** 恢复时执行器重新求值 `lastCompletedNode` 的出边并继续。仅当**边谓词是状态的纯函数**时才正确——因此这是硬契约，而非建议。
2. **状态差异在每次成功节点退出时触发一次**——不是每次重试、不是失败时。重试是 span 事件 / `attempts` 计数器，绝不产生额外步骤。这让追踪与 span 与逻辑运行保持忠实的 1:1 映射。

**恢复时至少一次：** 节点执行中途崩溃会从第 1 次尝试重跑。有副作用的节点用 `ctx.idempotencyKey()` 保持安全。TraceGraph 刻意选择至少一次（简单、持久）而非恰好一次（分布式共识范畴）。

## 并发模型

为 **Project Loom / 虚拟线程**（JDK 21）而建：

- 默认执行器是**每任务虚拟线程**，按 `run` 惰性创建并在完成时关闭。用户提供的 `.executor(...)` **绝不**被图关闭——生命周期由你负责。
- 节点内每个阻塞调用都应对虚拟线程友好。**阻塞 I/O 上不用 `synchronized`**（会钉住载体线程——用 `ReentrantLock`）。**节点路径不用 `ThreadLocal`**（虚拟线程会扰乱其语义）——用 `Context` 参数。
- `Graph<S>` 在 `build()` 后**不可变**，可安全跨线程共享；`Graph.Builder<S>` 仅单线程。
- 并行分支不得共享可变状态，必须有由图（而非运行时）定义的确定性合并，并把任意分支失败上报给父执行（按声明顺序第一个胜出）。

见仓库内[并发规则](https://github.com/kimhongzhang323/TraceGraph/blob/main/.claude/rules/concurrency.md)。

## API 设计哲学

项目定位"生产级 JVM"，因此消费者会锁定版本并依赖二进制兼容。关键立场（完整规则见 [api-design.md](https://github.com/kimhongzhang323/TraceGraph/blob/main/.claude/rules/api-design.md)）：

- `Node`/`Graph` 上**单一类型参数 `<S>`**。两个类型参数让流式构建器推断负担翻倍；子结果用**状态组合**。
- **边是一等数据**（顶层 record），以便重放/可视化枚举。
- **优先不可变状态**——节点返回下一个状态，绝不就地修改。final 字段安全发布。
- **流式构建器即契约**——setter 返回 `Builder<S>`（绝不 `Builder<? extends S>`，那会破坏链式推断）；`build()` 立即校验并抛 `*ValidationException`。
- **1.0 之前的 semver：** `0.x` 次版本可能破坏，但每处破坏都记录在 `CHANGELOG.md`。`tracegraph-core` 是最稳妥的构建目标。

## 新功能如何找到归宿

以"加入持久化追踪"为例的决策规则：

1. 它需要 Jackson/JDBC 吗？**需要** → 不进 core。
2. 是可观测性吗？**是** → `tracegraph-observability`。
3. 通过既有 `TraceStore` SPI 暴露（`JsonFileTraceStore`、`JdbcTraceStore`），让 core 与执行器不变。
4. 让重型依赖（Jackson）`<optional>`，未选用者不付代价。
5. 仅当类路径 + bean 存在时才在 starter 自动接线（`@ConditionalOnClass`、`@ConditionalOnBean`）。

这五步形态在整个代码库反复出现——正是它让 core 保持小巧而表面积持续增长。

---

**相关：** **[[zh-Core-Concepts|核心概念]]** · **[[zh-Execution-Model|执行模型]]** · **[[zh-Modules|模块]]** · **[[zh-Observability-and-Replay|可观测性与重放]]**
