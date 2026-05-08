# tracegraph-memory（内存）

此模块包含 `MemoryStore` SPI 及几种实现：内存实现、基于文件的实现与 JDBC 实现。用于跨执行的键值持久化，常用于保存会话、缓存或外部上下文。

要点：

- API 以 `scope` 与 `key` 为单位组织数据。
- `FileMemoryStore` 使用 JSON 与原子换名（*.tmp + ATOMIC_MOVE）来保证写入安全。
- `JdbcMemoryStore` 提供可选的数据库后端并暴露 `initSchema()`。
