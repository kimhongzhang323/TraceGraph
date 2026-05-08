---
title: HITL 批准
---

# 人在环路 (HITL) 批准

本指南演示了如何构建一个在继续执行敏感步骤之前暂停执行以等待人工批准的图。

## 概述

在许多工作流程中，您不能允许 AI 代理在没有明确人工确认的情况下执行关键操作（例如发送电子邮件、删除数据库记录或转账）。

TraceGraph 使用检查点和中断来处理此问题。

1. 图执行到预定义的断点。
2. 执行暂停，状态持久化到内存中。
3. 人工审查状态（例如，通过 UI 或 API 调用）。
4. 使用更新后的状态或批准标志恢复执行。

有关可运行的示例，请参阅 [`examples/hitl-approval/`](https://github.com/kimho/TraceGraph/tree/main/examples/hitl-approval)。
