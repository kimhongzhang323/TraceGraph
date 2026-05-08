---
title: 状态与上下文
---

# 状态与上下文


# 状态与上下文



本章内容：

- 为什么使用不可变 `record` 建模状态
- 如何在节点间变更状态（返回新实例）
- `Context` 能做什么：`MemoryStore`、`idempotencyKey()`、`reportUsage()` 等
- 示例与练习

不可变状态（推荐）

使用 Java `record` 建模状态有两个好处：语义明确（字段不可变）和小巧的构造器/访问器。示例：

```java
public record SessionState(String sessionId, Map<String,String> metadata, boolean finished) {}
```

节点应当返回新的 `SessionState` 实例，而不是修改传入对象：

```java
.node("addMetadata", (state, ctx) -> state.withMetadata(Map.of("k","v")))
```

Context 的作用

`Context` 在节点执行时传入，提供：

- `memory()`：访问跨执行的 `MemoryStore`。
- `idempotencyKey()`：在做外部请求时用于去重。
- `reportUsage(promptTokens, completionTokens)`：记录 LLM token 使用。
- `interruptRequested()` / 控制信号。

示例：使用 `Context.memory()` 实现简单去重缓存：

```java
node("maybeCall", (state, ctx) -> {
	String key = "call:" + state.sessionId();
	Optional<String> existing = ctx.memory().get("calls", key, String.class);
	if (existing.isPresent()) return state;
	// do call
	ctx.memory().put("calls", key, "done");
	return state;
});
```

练习：把 `examples/quickstart` 的示例改成使用 `Context.memory()` 在多个执行间共享一个计数器。
