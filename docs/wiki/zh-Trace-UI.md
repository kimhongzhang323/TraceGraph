# 追踪界面

追踪界面是一个基于浏览器的仪表板，用于检视、重放与比较智能体执行。它直接读取 TraceGraph REST API——无需单独部署后端。由 `tracegraph-ui` 模块提供。

> 🌐 English: **[[Trace UI]]**

## 前置条件

1. 类路径上有 **`tracegraph-spring-boot-starter`**。
2. Spring 上下文中有一个 **`Graph<?>` bean**。
3. 一个 **`TraceStore` bean**——开发用 `InMemoryTraceStore`；生产用 `JsonFileTraceStore` 或 `JdbcTraceStore`。
4. 类路径上有 **`tracegraph-observability`**（提供 `TraceStore`、`RecordingTraceRecorder` 等）。

## 启用与访问

满足上述条件时 UI 默认启用；`TraceUiAutoConfiguration` 提供静态资源与 API 路由。禁用：

```yaml
tracegraph:
  ui:
    enabled: false
```

访问：

```
http://localhost:8080/tracegraph/ui/
```

它在同源连接 REST API（无需 CORS）。你也可直接使用 REST 端点而不用 UI——见 **[[REST API 参考|zh-REST-API-Reference]]**。

## 四个视图

| 视图 | URL 片段 | 后端 |
|---|---|---|
| 追踪列表 | `#/traces` | `GET /tracegraph/traces` |
| 追踪详情 | `#/traces/:id` | `GET /tracegraph/traces/{id}` + 重放/恢复 |
| 图结构 | `#/graph` | `GET /tracegraph/ui/graph` |
| 差异视图 | `#/diff` | `GET /tracegraph/traces/{a}/diff/{b}` |

### 追踪列表（`#/traces`）

主屏——每次执行一行：**执行 ID**（点击打开）、**状态**（`COMPLETED` / `FAILED` / `INTERRUPTED` / `RUNNING`）、**起止**时间戳与**节点数**。分页用 上一页/下一页 与页大小；`X-Total-Count` 头驱动页数。搜索框按部分 id 或状态在客户端过滤。

### 追踪详情（`#/traces/:id`）

单条追踪的完整逐步历史。每个 `TraceStep` 行显示**节点名**、**尝试次数**、**token 用量**（LLM 节点）与成功/失败图标。点击行展开**状态差异面板**——before/after 的两列 JSON 差异，变化字段高亮（理解节点对状态做了什么的主要工具）。

- 任意步骤的 **从此处重放** → `POST /tracegraph/traces/{id}/replay?step=N`；新执行 id 出现在提示中，分叉追踪显示 `forkedFrom` 元数据。
- 状态为 `INTERRUPTED` 时出现 **恢复** → `POST /tracegraph/traces/{id}/resume`；视图轮询直至完成或失败。

### 图结构（`#/graph`）

把已编译的图渲染为交互式 DAG。**节点**是带标签的矩形（入口带箭头进图标，终止带双边框）；**边**是有向箭头（条件边显示谓词徽标）；**子图**是可折叠的带标签簇；**并行组**是包住匿名分支的虚线框。悬停节点看其重试策略；点击节点跳到其最近的追踪步骤。

### 差异视图（`#/diff`）

并排比较两条追踪。选**左**与**右**追踪（或粘贴 id）并点 **比较**。渲染**匹配前缀**（按节点名 + before/after 状态相同的步骤）、**分歧点**（首个不同步骤，高亮）、分歧后的**左/右余量**与**摘要徽标**（同状态？同终态？相同？）。用它比较基线与重放，看提示词或图改动的影响。

---

**相关：** **[[REST API 参考|zh-REST-API-Reference]]** · **[[可观测性与重放|zh-Observability-and-Replay]]** · **[[图复杂度|zh-Graph-Complexity]]**
