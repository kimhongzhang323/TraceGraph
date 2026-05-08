---
title: ReAct 代理
---

# ReAct 代理

本指南演示了如何构建 ReAct（推理和行动）风格的代理。

## 概述

ReAct 代理使用 LLM 交替进行推理和工具使用。它将：
1. 接收任务。
2. 决定是否需要使用工具来收集信息或执行操作。
3. 如果需要，它将执行该工具，观察结果，然后循环回到步骤 2。
4. 如果不需要其他工具，它会生成最终答案。

TraceGraph 的条件路由和工具执行节点原生支持这种模式。

有关可运行的示例，请参阅 [`examples/react-agent/`](https://github.com/kimho/TraceGraph/tree/main/examples/react-agent)。
