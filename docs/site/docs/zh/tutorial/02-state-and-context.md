---
title: 状态与上下文
---

# 状态与上下文

> AI 翻译草稿 — 请校对。

说明 `S` 类型的状态对象如何在节点间传递，`Context` 提供执行相关工具（如 `idempotencyKey()`、`memory()`、`reportUsage()` 等）。推荐使用不可变记录作为状态。
