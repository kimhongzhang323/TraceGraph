# Connectors

Connectors are pre-built integrations that allow TraceGraph to interact with external systems seamlessly. They abstract away the boilerplate code required to connect to various APIs and services.

## LLM Connectors

TraceGraph provides out-of-the-box connectors for popular Large Language Models, abstracting their specific API quirks into a unified `LlmClient` interface.
- **OpenAI Connector:** For models like GPT-4, supporting function calling and streaming.
- **Anthropic Connector:** For Claude models, handling tool usage and XML-style formatting when necessary.
- **Mock Connector:** A utility for testing your graphs locally without incurring API costs.

## Extending Connectors

If you need to connect to a service not natively supported, you can easily implement your own connector by creating custom nodes and wrapping the underlying SDK or HTTP client. This ensures your graph remains clean and decoupled from infrastructure concerns.
