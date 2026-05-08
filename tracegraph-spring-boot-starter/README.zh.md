# tracegraph-spring-boot-starter（启动器）

该模块集成 Spring Boot 自动配置、默认 SPI bean（noop 实现）和用于在 Web 应用中展示与回放 Trace 的控制器（条件启用）。

特点：

- 条件自动装配：`@ConditionalOnMissingBean` / `@ConditionalOnBean` 控制自动装配逻辑。
- 提供 `TraceController`（`/tracegraph/traces` 等 REST 端点）当 `TraceStore` 可用时。
