---
title: 追踪 UI 概览
---

# 追踪 UI 概览

Trace UI 是一个基于浏览器的仪表盘，用于检查、回放和比较代理执行记录。它直接从 TraceGraph REST API 读取数据，无需单独部署后端服务。

## 前提条件

使用 Trace UI 需要：

1. **`tracegraph-spring-boot-starter`** 在类路径上。
2. **声明了 `Graph<?>` Bean** 的 Spring 应用上下文。
3. **`TraceStore` Bean** — 开发环境使用 `InMemoryTraceStore`；生产环境使用 `JsonFileTraceStore` 或 `JdbcTraceStore`。
4. **`tracegraph-observability`** 在类路径上（提供 `TraceStore`、`RecordingTraceRecorder` 及相关类型）。

## 启用 UI

满足上述条件后，UI 默认启用。静态资源和 API 路由由 `TraceUiAutoConfiguration` 提供服务。

如需禁用：

```yaml
tracegraph:
  ui:
    enabled: false
```

## 访问 UI

Spring Boot 应用启动后，在浏览器中打开：

```
http://localhost:8080/tracegraph/ui/
```

仪表盘将在浏览器中加载，并连接到同源的 REST API。同源使用无需配置 CORS。

## 四个视图

| 视图 | URL 片段 | 用途 |
|------|---------|------|
| 追踪列表 | `#/traces` | 浏览所有已存储的执行记录，显示状态和时间戳。 |
| 追踪详情 | `#/traces/:id` | 查看某次执行的逐步状态差异；可从任意步骤触发回放。 |
| 图结构 | `#/graph` | 已编译图的交互式 DAG 渲染（节点、边、子图集群）。 |
| 差异视图 | `#/diff` | 并排比较两条执行追踪记录。 |

每个视图的详细说明请参见[视图指南](views.md)。

## 架构说明

UI 是一个单页应用，以静态资源形式从 `tracegraph-ui` JAR 中提供服务。它调用标准的 TraceGraph REST 端点——与 [REST API 参考](../reference/rest-api.md) 中记录的端点相同。您也可以直接使用 REST API（例如通过 `curl` 或 Postman），无需依赖 UI。
