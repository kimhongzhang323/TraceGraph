---
title: Spring Boot 设置
---

# Spring Boot 设置



使用 `tracegraph-spring-boot-starter` 可以自动注入默认 SPI bean，并在具备 `TraceStore` 时暴露 `/tracegraph/traces` REST 端点。将 `Graph<YourState>` 注册为一个 `@Bean` 并注入需要的 SPI。
