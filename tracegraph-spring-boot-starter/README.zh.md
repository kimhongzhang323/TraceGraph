# tracegraph-spring-boot-starter（Spring Boot 启动器）

`tracegraph-spring-boot-starter` 旨在让 Spring Boot 开发者能够以“零配置”的顺滑体验，将 TraceGraph 图执行引擎无缝接入到现有的 Spring 生态中。通过自动装配和预设的条件 Bean，它极大简化了环境初始化的繁琐步骤。

## 核心特性与集成优势：

1. **智能条件自动装配 (Auto-Configuration)**：
   - 利用 Spring 的 `@ConditionalOnMissingBean` 和 `@ConditionalOnBean` 注解，启动器会智能检测容器中已有的 Bean。
   - 默认情况下，如果开发者没有显式提供，它会自动注入安全的“空操作 (noop)” SPI 实现（如 NoopNodeListener、NoopTraceRecorder），确保项目能够即刻运行，而无需准备所有的底层服务配置。

2. **无缝注入与图构建**：
   - 开发者可以直接在 Spring 的 `@Configuration` 类中使用 `@Bean` 定义自己的 `Graph` 实例，所有的 SPI 组件均可由 Spring IOC 容器自动注入到执行图中，完美契合依赖注入模式。

3. **内置 Trace 管理与 Web API**：
   - 当你的项目中启用了可观测性组件（即注入了 `TraceStore` Bean 时），启动器会自动激活并暴露 `TraceController`。
   - 这为你提供了一组开箱即用的 RESTful 端点（如 `/tracegraph/traces`），允许你通过 HTTP 直接获取历史执行轨迹、执行回放，甚至在开发调试界面中可视化当前应用的执行图运行状况。
