# TraceGraph :: UI (可视化界面)

## 📖 TraceGraph UI 简介

欢迎使用 `tracegraph-ui`！构建基于图的代理可能会变得很复杂。当代理进行自我循环或采取意想不到的路径时，通过标准的文本日志进行调试将是一场噩梦。

UI 模块为您的 TraceGraph 应用程序提供了一个**直观的、实时的仪表板**。它允许开发人员和最终用户查看图结构、观察代理从一个节点移动到另一个节点，并在每一步检查状态数据。

### 为什么我需要这个？
- **调试 (Debugging)**: 轻松确切地看到代理*为什么*做出特定决定。
- **状态检查 (State Inspection)**: 单击节点执行以确切查看当时状态中的 JSON 数据是什么。
- **演示 (Demonstration)**: 向非技术人员直观地展示您复杂的 AI 工作流。

## 🏗️ UI 架构

UI 模块钩入 TraceGraph 的可观测性层，将事件流式传输到 Web 前端。

```mermaid
flowchart LR
    subgraph Backend [后端]
        Core[TraceGraph 执行] --> Obs[事件发布器]
        Obs --> Controller[Spring WebSockets / REST]
    end
    
    subgraph Frontend [前端 (浏览器)]
        Controller -->|状态事件| Dashboard[React / Vue 仪表板]
        Dashboard --> Vis[节点可视化图]
        Dashboard --> Inspector[JSON 状态检查器]
    end
```

## 🚀 如何使用

由于这是专门为 Spring Boot 构建的，因此通过 Spring 的自动配置启用 UI 非常容易。

### 1. 添加依赖

在应用程序的 `pom.xml` 中包含此模块：

```xml
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-ui</artifactId>
</dependency>
```

### 2. 在属性中启用

在您的 `application.yml` 或 `application.properties` 中，只需启用 UI 端点：

```yaml
tracegraph:
  ui:
    enabled: true
    port: 8081 # 可选: 在单独的端口上运行 UI
```

### 3. 访问仪表板

启动 Spring Boot 应用程序后，打开 Web 浏览器并导航到：
`http://localhost:8080/tracegraph-ui`

您将立即看到任何当前正在运行的代理的可视化！
