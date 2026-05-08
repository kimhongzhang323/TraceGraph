# tracegraph-connectors（连接器）

连接器模块提供对外服务（尤其是 LLM 提供商）的轻量级适配器：抽象 `LlmClient`，以及 OpenAI/Anthropic 的 HTTP 客户端实现。

要点：

- 提供 `MockLlmClient` 作为测试替身。
- Adapter 封装网络请求与错误（`LlmHttpException`），并暴露 `complete()` 与可选的 `stream()`。
