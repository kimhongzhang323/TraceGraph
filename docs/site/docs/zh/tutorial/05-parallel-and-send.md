---
title: 并行与动态扇出
---

# 05 — 并行与动态扇出

TraceGraph 支持两种并发执行形式：在图构建时静态声明的 `parallel` 分支，以及在路由节点内通过 `sendAll` 在运行时动态解析的扇出。

## 静态并行分支

`.parallel(name, branches, merger)` 并发运行一组固定的 `Node<S>` 分支。所有分支接收相同的输入状态；其结果由 `merger` 函数按声明顺序合并。

```java
record EnrichState(String input, String geoResult, String sentimentResult, String combined) {}

Node<EnrichState> geoNode       = (s, ctx) -> s.withGeoResult(geoApi.lookup(s.input()));
Node<EnrichState> sentimentNode = (s, ctx) -> s.withSentimentResult(sentimentApi.analyze(s.input()));

Graph<EnrichState> graph = Graph.<EnrichState>builder()
    .parallel(
        "enrich",
        List.of(geoNode, sentimentNode),
        (a, b) -> new EnrichState(a.input(), a.geoResult(), b.sentimentResult(), null)
    )
    .node("combine", (s, ctx) -> s.withCombined(s.geoResult() + " | " + s.sentimentResult()))
    .edge("enrich", "combine")
    .entry("enrich")
    .terminal("combine")
    .build();
```

分支在虚拟线程执行器上运行。它们是匿名的——不会出现在追踪步骤中，也不会触发监听器事件。若任意分支抛出异常，按声明顺序第一个失败的分支会传播其异常。

## 自定义执行器

默认情况下，每次 `run` 调用都会创建一个虚拟线程执行器，并在完成时关闭。提供自定义执行器可控制线程池的生命周期：

```java
ExecutorService sharedPool = Executors.newVirtualThreadPerTaskExecutor();

Graph<EnrichState> graph = Graph.<EnrichState>builder()
    // ... 节点 ...
    .executor(sharedPool)   // 图不会关闭此执行器
    .build();
```

用户提供的执行器不会被图关闭。

## 使用 sendAll 动态扇出

当运行时并行目标的数量未知时，可在 `RoutingNode` 内使用 `NodeResult.sendAll(...)`：

```java
record BatchState(List<String> items, Map<String, String> results) {}

Graph<BatchState> graph = Graph.<BatchState>builder()
    .routingNode("dispatch", (state, ctx) -> {
        List<Send<BatchState>> sends = state.items().stream()
            .map(item -> new Send<>("process", state.withSingleItem(item)))
            .toList();
        return NodeResult.sendAll(sends, BatchState::merge, state);
    })
    .node("process", (s, ctx) -> s.withResult(s.currentItem(), process(s.currentItem())))
    .edge("process", "done")
    .entry("dispatch")
    .terminal("done")
    .build();
```

`sendAll` 在运行时与 `parallel` 展开方式相同——分支在配置的执行器上并发运行。

## 要点总结

- `parallel(name, branches, merger)` 在构建时声明；所有分支接收相同的输入状态。
- 分支是匿名的——无名称、无追踪步骤、无监听器事件。
- 多个分支抛出异常时，按声明顺序第一个失败的分支获胜。
- 路由节点内的 `NodeResult.sendAll(...)` 支持运行时动态扇出。
- 通过 `.executor(...)` 提供的用户自定义执行器不会被图关闭。
