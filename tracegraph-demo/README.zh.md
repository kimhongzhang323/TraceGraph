# TraceGraph :: Demo (演示应用)

## 📖 演示应用简介
欢迎使用 `tracegraph-demo` 模块！这是一个独立的、预先配置好的 Spring Boot 应用程序模块，它作为一个完整的端到端示例，展示了如何在生产级环境中使用 TraceGraph。

如果您想向团队展示 TraceGraph 的功能，或者想了解如何将执行图、内存存储、自动配置、REST 端点和 UI 仪表板整合在一起，那么这就是您要找的地方。

### 核心亮点
- **开箱即用 (Out-of-the-Box)**: 这是一个自包含的 Spring Boot 应用，具有零配置启动体验。
- **全家桶集成 (Full-Stack Integration)**: 它内置并启用了 `tracegraph-spring-boot-starter`、`tracegraph-memory` 以及可视化前端 `tracegraph-ui`。
- **多种代理示例 (Multiple Agent Examples)**: 它注册了多个图 bean，包括用于问答的 RAG 代理，以及用于复杂工具使用的 ReAct 代理。

## 🏗️ 演示应用架构

```mermaid
flowchart TD
    subgraph Spring Boot 容器
        DemoApp[DemoApplication] --> Starter[TraceGraph Auto-Config]
        Starter --> Mem[内存存储 (JdbcMemoryStore)]
        Starter --> UI[UI 端点暴露]
        
        DemoApp --> Agent1[RAG Agent Bean]
        DemoApp --> Agent2[ReAct Agent Bean]
    end
    
    User((最终用户)) -->|HTTP POST| REST[API Controller]
    REST --> Agent1
    REST --> Agent2
```

## 🚀 如何运行和体验

此模块设计为可通过几条命令即可立即运行。

### 1. 启动应用
您可以直接使用 Maven 的 `spring-boot:run` 插件启动此演示应用。
```bash
cd tracegraph-demo
mvn spring-boot:run
```

### 2. 访问控制台与界面
应用程序启动后（默认运行在 8080 端口）：

- **UI 仪表板**: 打开您的浏览器并访问 `http://localhost:8080/tracegraph-ui` 即可查看图形化的追踪界面。
- **REST API 端点**: 应用程序公开了可以触发 Agent 执行的 API。您可以使用 curl 或 Postman 调用它们：
  ```bash
  curl -X POST http://localhost:8080/api/agent/rag \
       -H "Content-Type: application/json" \
       -d '{"query": "TraceGraph 支持哪些数据库？"}'
  ```

### 3. 查看 UI 追踪结果
发送 REST 请求后，刷新您的 UI 仪表板，您将看到代理执行的完整流转过程。您可以点击具体的节点以检查内存中的状态变化！
