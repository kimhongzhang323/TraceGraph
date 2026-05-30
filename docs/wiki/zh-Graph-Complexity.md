# 图复杂度

`GraphComplexity` 是由 `graph.complexity()` 与 `GET /tracegraph/ui/complexity` 端点返回的不可变记录。它揭示已编译图的结构指标，让你在部署**之前**就能推断可维护性、可测试性与运行时行为。

> 🌐 English: **[[Graph Complexity]]**

## 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `nodeCount` | int | 节点总数，包含并行分支与在父级计数的子图节点。 |
| `edgeCount` | int | 有向边总数，含条件边。 |
| `maxFanOut` | int | 任一节点的最大出边数。1 = 线性；更高 = 分支决策点。 |
| `maxDepth` | int | 从入口到任一终止的最长路径，以节点跳数计。 |
| `cyclomaticComplexity` | int | McCabe 复杂度：`edgeCount - nodeCount + 2`。与全路径覆盖所需最小测试数相关。 |
| `parallelBranches` | int | 所有 `parallel(...)` 与 `sendAll` 处的匿名并行分支总数。 |
| `subgraphDepth` | int | 嵌入子图的最大嵌套深度。平图 = 0；一层 `subgraph(...)` = 1。 |
| `hotspots` | `List<String>` | 超过启发式复杂度阈值的节点名——通常高扇出或处在许多最长路径上。优先评审。 |

## 何时采取行动

| 指标 | 警告阈值 | 行动 |
|---|---|---|
| `nodeCount` | > 30 | 把图拆成子图。 |
| `cyclomaticComplexity` | > 10 | 为未覆盖路径加测试；简化路由。 |
| `maxFanOut` | > 5 | 某节点做了太多路由决策——用专门的 `RoutingNode` 或拆分。 |
| `maxDepth` | > 15 | 长链难调试——为里程碑设检查点，拆成子图。 |
| `subgraphDepth` | > 3 | 深嵌套使追踪难导航——尽量扁平化。 |
| `parallelBranches` | > 10 | 高并行压榨执行器——确认线程池能承受。 |

## 示例输出

```json
{
  "nodeCount": 8,
  "edgeCount": 10,
  "maxFanOut": 3,
  "maxDepth": 6,
  "cyclomaticComplexity": 4,
  "parallelBranches": 2,
  "subgraphDepth": 1,
  "hotspots": ["route", "enrich"]
}
```

`cyclomaticComplexity` 为 4 意味着至少 4 条不同执行路径。`hotspots: ["route", "enrich"]` 告诉你从哪两个节点开始评审。

## 访问复杂度

**编程方式：**

```java
GraphComplexity c = graph.complexity();
if (c.cyclomaticComplexity() > 10) {
    log.warn("Graph complexity {} exceeds threshold", c.cyclomaticComplexity());
}
```

**经 REST**（需 `tracegraph-ui`）：

```
GET /tracegraph/ui/complexity
```

返回相同 JSON。见 **[[REST API 参考|zh-REST-API-Reference]]** 与 **[[追踪界面|zh-Trace-UI]]**。
