---
title: ReAct 代理
---

# ReAct 代理


# ReAct 代理

> AI 翻译草稿 — 请校对。

ReAct（Reason+Act）代理模式常见于需要工具调用的任务：LLM 提出下一步动作（reason），框架执行工具（act），并把结果反馈给 LLM 作为下一轮输入。TraceGraph 提供 `ReActAgent` 工厂来把 ReAct 工作流编译成一个 `Graph`。

流程简述：

1. `llm` 节点生成动作或工具请求。
2. `tool` 节点执行工具（可能是 HTTP、数据库、外部命令）。
3. 结果回到 `llm` 节点，形成 loop，直到输出 `done`。

练习：使用 `ReActAgent.builder()` 创建一个小型代理，集成一个 `EchoTool`（返回输入字符串），观察循环终止条件。
