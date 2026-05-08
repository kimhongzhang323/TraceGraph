---
title: 检查点与恢复
---

# 检查点与恢复

> AI 翻译草稿 — 请校对。

说明检查点写入时机（节点退出后、边解析前）、恢复语义（从 `lastCompletedNode` 继续，节点可能会重新执行），以及 `CheckpointStore` 的实现要点。
