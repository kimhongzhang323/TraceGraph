# Phase 4 — langgraph4j Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close five concrete parity gaps with langgraph4j (streaming, HITL interrupts, subgraphs, dynamic routing, graph visualization) without compromising TraceGraph's minimal-deps core or breaking existing replay/trace contracts.

**Architecture:** All features land in `tracegraph-core` except `TraceStep.children` (touches `tracegraph-observability`) and the new starter REST endpoints (touches `tracegraph-spring-boot-starter`). Streaming uses `java.util.concurrent.Flow` (JDK-only). Interrupts piggyback on the existing checkpoint mechanism. Subgraphs reuse the `Executor` machinery via a new `NodeKind.subgraph(...)` variant. Dynamic routing introduces a sibling `RoutingNode<S>` SAM. Visualization is pure string templating.

**Tech Stack:** JDK 21, Maven, JUnit 5, AssertJ, SLF4J. Spring Boot 3.3.5 in starter. No new runtime deps.

**Spec:** `docs/superpowers/specs/2026-04-29-phase-4-langgraph4j-parity-design.md`

---

## File map

**`tracegraph-core`:**
- Create: `src/main/java/io/tracegraph/core/NodeEvent.java` (sealed event hierarchy)
- Create: `src/main/java/io/tracegraph/core/NodeResult.java` (sealed; `Continue`/`GoTo`)
- Create: `src/main/java/io/tracegraph/core/RoutingNode.java` (functional interface)
- Create: `src/main/java/io/tracegraph/core/viz/MermaidRenderer.java`
- Create: `src/main/java/io/tracegraph/core/viz/PlantUmlRenderer.java`
- Modify: `src/main/java/io/tracegraph/core/Graph.java` — add `stream()`, `toMermaid()`, `toPlantUml()`, `Builder.interruptBefore/After`, `Builder.subgraph`, `Builder.routingNode`
- Modify: `src/main/java/io/tracegraph/core/Status.java` — add `INTERRUPTED`
- Modify: `src/main/java/io/tracegraph/core/Checkpoint.java` — add `interruptPending` field
- Modify: `src/main/java/io/tracegraph/core/exec/NodeKind.java` — add `subgraph` variant + `routing` variant
- Modify: `src/main/java/io/tracegraph/core/exec/Executor.java` — interrupt seams, subgraph dispatch, routing dispatch

**`tracegraph-observability`:**
- Modify: `src/main/java/io/tracegraph/observability/replay/TraceStep.java` — add `children` component
- Modify: file-based and JDBC trace stores to round-trip `children`

**`tracegraph-spring-boot-starter`:**
- Create: `src/main/java/io/tracegraph/boot/web/TraceStreamController.java` (SSE)
- Modify: `src/main/java/io/tracegraph/boot/web/TraceController.java` — add `POST /tracegraph/traces/{id}/resume`
- Modify: `TraceWebAutoConfiguration` to register the new controller(s)

**Tests** mirror the source structure in each module's `src/test/java/...`.

---

## Task 1: `Status.INTERRUPTED` enum value

**Files:**
- Modify: `tracegraph-core/src/main/java/io/tracegraph/core/Status.java`
- Test: `tracegraph-core/src/test/java/io/tracegraph/core/StatusTest.java`

- [ ] **Step 1: Write failing test**

```java
package io.tracegraph.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StatusTest {
    @Test
    void interruptedValueExists() {
        assertThat(Status.valueOf("INTERRUPTED")).isEqualTo(Status.INTERRUPTED);
    }
}
```

- [ ] **Step 2: Run test (expect FAIL — `INTERRUPTED` not declared)**

`mvn -pl tracegraph-core test -Dtest=StatusTest`

- [ ] **Step 3: Add the enum value**

Open `Status.java`, add `INTERRUPTED` to the enum list (additive, after `FAILED`).

- [ ] **Step 4: Re-run, expect PASS**

- [ ] **Step 5: Commit**

```bash
git add tracegraph-core/src/main/java/io/tracegraph/core/Status.java tracegraph-core/src/test/java/io/tracegraph/core/StatusTest.java
git commit -m "feat(core): add Status.INTERRUPTED for HITL pauses"
```

---

## Task 2: `NodeEvent<S>` sealed hierarchy

**Files:**
- Create: `tracegraph-core/src/main/java/io/tracegraph/core/NodeEvent.java`
- Test: `tracegraph-core/src/test/java/io/tracegraph/core/NodeEventTest.java`

- [ ] **Step 1: Write failing test**

```java
package io.tracegraph.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NodeEventTest {
    @Test
    void eventsCarryExecutionIdAndNodeName() {
        NodeEvent<String> e = new NodeEvent.NodeEnter<>("eid", "n", "before");
        assertThat(e.executionId()).isEqualTo("eid");
        assertThat(e.nodeName()).isEqualTo("n");
    }

    @Test
    void exitCarriesBeforeAndAfter() {
        var e = new NodeEvent.NodeExit<>("eid", "n", "b", "a");
        assertThat(e.before()).isEqualTo("b");
        assertThat(e.after()).isEqualTo("a");
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

- [ ] **Step 3: Create `NodeEvent.java`**

```java
package io.tracegraph.core;

public sealed interface NodeEvent<S> {
    String executionId();
    String nodeName();

    record NodeEnter<S>(String executionId, String nodeName, S before) implements NodeEvent<S> {}
    record NodeExit<S>(String executionId, String nodeName, S before, S after) implements NodeEvent<S> {}
    record NodeRetry<S>(String executionId, String nodeName, int attempt, Throwable cause) implements NodeEvent<S> {}
    record Failed<S>(String executionId, String nodeName, Throwable cause) implements NodeEvent<S> {}
    record Complete<S>(String executionId, String nodeName, ExecutionResult<S> result) implements NodeEvent<S> {}
}
```

(Note: `Complete` carries `nodeName` = last node for interface compliance; in tests assert it equals the terminal.)

- [ ] **Step 4: Run, expect PASS**

- [ ] **Step 5: Commit**

```bash
git add tracegraph-core/src/main/java/io/tracegraph/core/NodeEvent.java tracegraph-core/src/test/java/io/tracegraph/core/NodeEventTest.java
git commit -m "feat(core): add NodeEvent sealed hierarchy for streaming"
```

---

## Task 3: `Graph.stream(initial)` returns `Flow.Publisher<NodeEvent<S>>`

**Files:**
- Modify: `tracegraph-core/src/main/java/io/tracegraph/core/Graph.java`
- Test: `tracegraph-core/src/test/java/io/tracegraph/core/GraphStreamingTest.java`

- [ ] **Step 1: Write failing test**

```java
package io.tracegraph.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

class GraphStreamingTest {
    @Test
    void streamsEnterAndExitForEachNode() throws Exception {
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s + "A")
                .node("b", (s, ctx) -> s + "B")
                .edge("a", "b").entry("a").terminal("b")
                .build();

        List<NodeEvent<String>> events = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        g.stream("").subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(NodeEvent<String> e) { events.add(e); }
            public void onError(Throwable t) { done.countDown(); }
            public void onComplete() { done.countDown(); }
        });
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(events).hasSizeGreaterThanOrEqualTo(4); // enter+exit a, enter+exit b, complete
        assertThat(events.get(events.size() - 1)).isInstanceOf(NodeEvent.Complete.class);
    }
}
```

- [ ] **Step 2: Run, expect compile failure (`stream` not defined)**

- [ ] **Step 3: Implement `Graph.stream`**

Add to `Graph.java`:

```java
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.ForkJoinPool;
import io.tracegraph.core.spi.NodeListener;

public Flow.Publisher<NodeEvent<S>> stream(S initial) {
    return stream(initial, Executor.newExecutionId());
}

public Flow.Publisher<NodeEvent<S>> stream(S initial, String executionId) {
    SubmissionPublisher<NodeEvent<S>> pub = new SubmissionPublisher<>(
            ForkJoinPool.commonPool(), Flow.defaultBufferSize());
    NodeListener streamingListener = new NodeListener() {
        @Override public void onEnter(String name, Object before) {
            @SuppressWarnings("unchecked") S b = (S) before;
            pub.offer(new NodeEvent.NodeEnter<>(executionId, name, b), null);
        }
        @Override public void onState(String name, Object before, Object after) {
            @SuppressWarnings("unchecked") S b = (S) before; @SuppressWarnings("unchecked") S a = (S) after;
            pub.offer(new NodeEvent.NodeExit<>(executionId, name, b, a), null);
        }
        @Override public void onRetry(String name, int attempt, Throwable cause) {
            pub.offer(new NodeEvent.NodeRetry<>(executionId, name, attempt, cause), null);
        }
        @Override public void onError(String name, Throwable cause) {
            pub.offer(new NodeEvent.Failed<>(executionId, name, cause), null);
        }
    };
    NodeListener composed = io.tracegraph.core.spi.Listeners.compose(this.listener, streamingListener);
    Thread.startVirtualThread(() -> {
        try {
            ExecutionResult<S> r = withListener(composed).run(initial, executionId);
            pub.offer(new NodeEvent.Complete<>(executionId, r.lastCompletedNode(), r), null);
        } catch (Throwable t) {
            pub.closeExceptionally(t);
            return;
        }
        pub.close();
    });
    return pub;
}

private Graph<S> withListener(NodeListener l) {
    Builder<S> b = new Builder<>();
    b.nodes.putAll(this.nodes);
    b.edges.addAll(this.edges);
    b.terminals.addAll(this.terminals);
    b.entry = this.entry;
    b.listener = l;
    b.maxSteps = this.maxSteps;
    b.nodePolicies.putAll(this.nodePolicies);
    b.defaultPolicy = this.defaultPolicy;
    b.checkpointStore = this.checkpointStore;
    b.traceRecorder = this.traceRecorder;
    b.memoryStore = this.memoryStore;
    b.userExecutor = this.userExecutor;
    return new Graph<>(b);
}
```

(If `Listeners.compose` doesn't exist yet on the SPI, check `tracegraph-core/src/main/java/io/tracegraph/core/spi/`. If absent, implement a 5-line composing wrapper inline instead — fan out to both listeners, swallow nothing.)

- [ ] **Step 4: Verify `ExecutionResult` exposes `lastCompletedNode()`** — if not, use `r.finalNode()` or whatever accessor exists; otherwise use the empty string. Adjust the test assertion accordingly.

- [ ] **Step 5: Run, expect PASS**

- [ ] **Step 6: Commit**

```bash
git add tracegraph-core/src/main/java/io/tracegraph/core/Graph.java tracegraph-core/src/test/java/io/tracegraph/core/GraphStreamingTest.java
git commit -m "feat(core): Graph.stream() returns Flow.Publisher<NodeEvent>"
```

---

## Task 4: Streaming error path test

**Files:**
- Modify: `tracegraph-core/src/test/java/io/tracegraph/core/GraphStreamingTest.java`

- [ ] **Step 1: Add failing test**

```java
@Test
void failingNodeSurfacesAsFailedEventThenError() throws Exception {
    Graph<String> g = Graph.<String>builder()
            .node("boom", (s, ctx) -> { throw new RuntimeException("nope"); })
            .entry("boom").terminal("boom")
            .build();
    List<NodeEvent<String>> events = new ArrayList<>();
    CountDownLatch done = new CountDownLatch(1);
    Throwable[] err = new Throwable[1];
    g.stream("").subscribe(new Flow.Subscriber<>() {
        public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
        public void onNext(NodeEvent<String> e) { events.add(e); }
        public void onError(Throwable t) { err[0] = t; done.countDown(); }
        public void onComplete() { done.countDown(); }
    });
    assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(events).anyMatch(e -> e instanceof NodeEvent.Failed);
    assertThat(err[0]).isNotNull();
}
```

- [ ] **Step 2: Run, expect PASS** (Task 3 implementation should already cover this)

- [ ] **Step 3: If failing, fix Task 3's error path** — ensure `Failed` event is offered before `closeExceptionally`.

- [ ] **Step 4: Commit**

```bash
git commit -am "test(core): streaming surfaces Failed event then onError"
```

---

## Task 5: `Checkpoint.interruptPending` field

**Files:**
- Modify: `tracegraph-core/src/main/java/io/tracegraph/core/Checkpoint.java`
- Test: `tracegraph-core/src/test/java/io/tracegraph/core/CheckpointTest.java` (create or extend)

- [ ] **Step 1: Read current `Checkpoint` definition**

`Read tracegraph-core/src/main/java/io/tracegraph/core/Checkpoint.java`

- [ ] **Step 2: Write failing test**

```java
@Test
void interruptPendingDefaultsFalse() {
    Checkpoint<String> c = new Checkpoint<>(/* existing args */, /* interruptPending: */ false);
    assertThat(c.interruptPending()).isFalse();
}
```

(Adapt to actual constructor signature.)

- [ ] **Step 3: Add `boolean interruptPending` as the last record component**

Update record declaration. Update any call sites that construct `Checkpoint` (executor, JdbcCheckpointStore, InMemoryCheckpointStore) to pass `false` as default.

- [ ] **Step 4: Make Jackson tolerant** — confirm the JDBC store's Jackson deserialization accepts old payloads missing `interruptPending`. If `JsonCreator` is used, add `@JsonProperty(... required=false)`; if default constructor binding is used, no change needed (Jackson defaults to `false`).

- [ ] **Step 5: Run all tracegraph-core + tracegraph-runtime tests, expect PASS**

`mvn -pl tracegraph-core,tracegraph-runtime test`

- [ ] **Step 6: Commit**

```bash
git commit -am "feat(core): add Checkpoint.interruptPending flag"
```

---

## Task 6: `Builder.interruptBefore` / `interruptAfter`

**Files:**
- Modify: `Graph.java` (Builder + Graph fields)
- Test: `tracegraph-core/src/test/java/io/tracegraph/core/GraphInterruptTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test
void interruptBeforePausesExecution() {
    InMemoryCheckpointStore<String> store = new InMemoryCheckpointStore<>();
    Graph<String> g = Graph.<String>builder()
            .node("a", (s, ctx) -> s + "A")
            .node("b", (s, ctx) -> s + "B")
            .edge("a", "b").entry("a").terminal("b")
            .interruptBefore("b")
            .checkpointStore(store)
            .build();
    ExecutionResult<String> r = g.run("");
    assertThat(r.status()).isEqualTo(Status.INTERRUPTED);
    assertThat(r.state()).isEqualTo("A");
}

@Test
void resumeContinuesPastInterrupt() {
    InMemoryCheckpointStore<String> store = new InMemoryCheckpointStore<>();
    Graph<String> g = /* same as above */;
    String eid = "eid-1";
    g.run("", eid);
    ExecutionResult<String> r = g.resume(eid).orElseThrow();
    assertThat(r.status()).isEqualTo(Status.SUCCEEDED);
    assertThat(r.state()).isEqualTo("AB");
}
```

(`InMemoryCheckpointStore` lives in `tracegraph-runtime`; if this test depends on it, place test in `tracegraph-runtime` instead, or use a tiny inline test double.)

- [ ] **Step 2: Run, expect compile failure**

- [ ] **Step 3: Add Builder fields and methods**

```java
private final java.util.Set<String> interruptBefore = new java.util.HashSet<>();
private final java.util.Set<String> interruptAfter = new java.util.HashSet<>();

public Builder<S> interruptBefore(String... names) {
    for (String n : names) interruptBefore.add(java.util.Objects.requireNonNull(n));
    return this;
}
public Builder<S> interruptAfter(String... names) {
    for (String n : names) interruptAfter.add(java.util.Objects.requireNonNull(n));
    return this;
}
```

Add immutable fields to `Graph`, copy from builder in constructor, pass through to `Executor`.

- [ ] **Step 4: Update `Executor` constructor to accept the two sets, add interrupt seams**

In the main run loop in `Executor.run`:
- BEFORE invoking node body: if `interruptBefore.contains(name)` AND `!checkpoint.interruptPending()` (loaded from prior resume) → write checkpoint with `interruptPending=true`, `lastCompletedNode = previousNode`, return `ExecutionResult` with `Status.INTERRUPTED`.
- AFTER `onState` fires for that node's exit: if `interruptAfter.contains(name)` → write checkpoint with `lastCompletedNode = name`, `interruptPending=false`, return `INTERRUPTED`.
- On resume: clear `interruptPending` immediately so the same node's `interruptBefore` doesn't re-trigger.

- [ ] **Step 5: Validate interrupt sets reference declared nodes** in `Builder.validate()`

```java
for (String n : interruptBefore) if (!nodes.containsKey(n)) throw new GraphValidationException("interruptBefore references unknown node: '" + n + "'");
for (String n : interruptAfter) if (!nodes.containsKey(n)) throw new GraphValidationException("interruptAfter references unknown node: '" + n + "'");
```

- [ ] **Step 6: Run, expect PASS**

- [ ] **Step 7: Commit**

```bash
git commit -am "feat(core): interruptBefore/After for human-in-the-loop pauses"
```

---

## Task 7: `NodeResult` + `RoutingNode` SAM

**Files:**
- Create: `tracegraph-core/src/main/java/io/tracegraph/core/NodeResult.java`
- Create: `tracegraph-core/src/main/java/io/tracegraph/core/RoutingNode.java`
- Test: `tracegraph-core/src/test/java/io/tracegraph/core/RoutingNodeTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test
void goToBypassesEdges() {
    Graph<String> g = Graph.<String>builder()
            .routingNode("router", (s, ctx) -> NodeResult.goTo("c", s + "R"))
            .node("b", (s, ctx) -> s + "B")
            .node("c", (s, ctx) -> s + "C")
            .edge("router", "b") // would go to b normally
            .edge("b", "c").edge("c", "c")
            .entry("router").terminal("c")
            .build();
    ExecutionResult<String> r = g.run("");
    assertThat(r.state()).isEqualTo("RC"); // skipped b
}

@Test
void continueFallsThroughToEdges() {
    Graph<String> g = Graph.<String>builder()
            .routingNode("router", (s, ctx) -> NodeResult.of(s + "R"))
            .node("b", (s, ctx) -> s + "B")
            .edge("router", "b").entry("router").terminal("b")
            .build();
    assertThat(g.run("").state()).isEqualTo("RB");
}

@Test
void unknownGoToTargetRejectedAtRuntime() {
    Graph<String> g = Graph.<String>builder()
            .routingNode("router", (s, ctx) -> NodeResult.goTo("ghost", s))
            .node("b", (s, ctx) -> s)
            .edge("router", "b").entry("router").terminal("b")
            .build();
    assertThatThrownBy(() -> g.run(""))
        .isInstanceOf(io.tracegraph.core.exec.NodeExecutionException.class)
        .hasMessageContaining("ghost");
}
```

- [ ] **Step 2: Run, expect compile failure**

- [ ] **Step 3: Create `NodeResult.java`**

```java
package io.tracegraph.core;

public sealed interface NodeResult<S> {
    S state();
    record Continue<S>(S state) implements NodeResult<S> {}
    record GoTo<S>(String nodeName, S state) implements NodeResult<S> {}
    static <S> NodeResult<S> of(S state) { return new Continue<>(state); }
    static <S> NodeResult<S> goTo(String nodeName, S state) { return new GoTo<>(nodeName, state); }
}
```

- [ ] **Step 4: Create `RoutingNode.java`**

```java
package io.tracegraph.core;

@FunctionalInterface
public interface RoutingNode<S> {
    NodeResult<S> apply(S state, Context ctx) throws Exception;
}
```

- [ ] **Step 5: Add `NodeKind.routing(...)` variant**

In `NodeKind.java`, add a new sum case carrying `RoutingNode<S>`. Update its sealed interface / pattern matching.

- [ ] **Step 6: Add `Builder.routingNode(name, fn)` and `(name, fn, retryPolicy)`**

```java
public Builder<S> routingNode(String name, RoutingNode<S> node) { return routingNode(name, node, null); }
public Builder<S> routingNode(String name, RoutingNode<S> node, RetryPolicy r) {
    register(name, NodeKind.routing(node), r);
    return this;
}
```

- [ ] **Step 7: Update `Executor` to dispatch routing nodes**

When the active node is a routing kind: invoke it (under the same retry envelope as `Node<S>`); inspect result. If `GoTo`, validate target exists (throw `NodeExecutionException("Routing node 'X' targeted unknown node 'Y'")` if not); set next node directly, skip edge resolution. If `Continue`, fall through to edge resolution. Trace step records the `nameOfActualSuccessor`.

- [ ] **Step 8: Run, expect PASS**

- [ ] **Step 9: Commit**

```bash
git commit -am "feat(core): RoutingNode + NodeResult.goTo for dynamic routing"
```

---

## Task 8: Subgraph — `TraceStep.children` evolution

**Files:**
- Modify: `tracegraph-observability/src/main/java/io/tracegraph/observability/replay/TraceStep.java`
- Modify: `JsonFileTraceStore.java`, `JdbcTraceStore.java` if they have explicit field handling
- Test: `tracegraph-observability/src/test/java/io/tracegraph/observability/replay/TraceStepTest.java`

- [ ] **Step 1: Read `TraceStep.java`**

- [ ] **Step 2: Write failing test**

```java
@Test
void leafFactoryProducesEmptyChildren() {
    TraceStep<String> s = TraceStep.leaf(/* existing args */);
    assertThat(s.children()).isEmpty();
}

@Test
void childrenPreservedOnSerialization() {
    TraceStep<String> child = TraceStep.leaf(...);
    TraceStep<String> parent = new TraceStep<>(... List.of(child));
    // round-trip through Jackson
    String json = mapper.writeValueAsString(parent);
    TraceStep<String> back = mapper.readValue(json, new TypeReference<>() {});
    assertThat(back.children()).hasSize(1);
}
```

- [ ] **Step 3: Add `List<TraceStep<S>> children` as last record component**

```java
public record TraceStep<S>(
    /* ...existing components..., */
    List<TraceStep<S>> children
) {
    public TraceStep {
        children = children == null ? List.of() : List.copyOf(children);
    }
    public static <S> TraceStep<S> leaf(/* existing args */) {
        return new TraceStep<>(/* existing args */, List.of());
    }
}
```

- [ ] **Step 4: Update all call sites** that construct `TraceStep` to use `leaf(...)` or pass children explicitly.

`Grep -r "new TraceStep<" tracegraph-observability/src` and update each.

- [ ] **Step 5: Update `JsonFileTraceStore` / `JdbcTraceStore`** — if Jackson handles records by component order, old JSON missing `children` will fail to deserialize. Configure ObjectMapper with `DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES = false`, OR add `@JsonCreator` with `@JsonProperty(value = "children", required = false, defaultValue = "[]")`. Test with a fixture old-format JSON.

- [ ] **Step 6: Run all observability tests, expect PASS**

- [ ] **Step 7: Update `CHANGELOG.md`** under `## Unreleased` / `### Breaking` section:

```markdown
### Breaking
- `TraceStep<S>` gained a trailing `children` record component for subgraph support. Positional constructor consumers must update; `TraceStep.leaf(...)` is a convenience for the common case.
```

- [ ] **Step 8: Commit**

```bash
git add tracegraph-observability/ CHANGELOG.md
git commit -m "feat(observability)!: TraceStep.children for subgraph nesting"
```

---

## Task 9: `Builder.subgraph(name, inner)`

**Files:**
- Modify: `Graph.java`, `NodeKind.java`, `Executor.java`
- Test: `tracegraph-core/src/test/java/io/tracegraph/core/GraphSubgraphTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test
void subgraphRunsAsSingleNode() {
    Graph<String> inner = Graph.<String>builder()
            .node("x", (s, ctx) -> s + "X")
            .node("y", (s, ctx) -> s + "Y")
            .edge("x", "y").entry("x").terminal("y")
            .build();
    Graph<String> outer = Graph.<String>builder()
            .node("a", (s, ctx) -> s + "A")
            .subgraph("nested", inner)
            .node("b", (s, ctx) -> s + "B")
            .edge("a", "nested").edge("nested", "b")
            .entry("a").terminal("b")
            .build();
    assertThat(outer.run("").state()).isEqualTo("AXYB");
}
```

- [ ] **Step 2: Run, expect compile failure**

- [ ] **Step 3: Add `NodeKind.subgraph(Graph<S>)` variant**

- [ ] **Step 4: Add `Builder.subgraph(name, Graph<S> inner)` + retry overload**

```java
public Builder<S> subgraph(String name, Graph<S> inner) { return subgraph(name, inner, null); }
public Builder<S> subgraph(String name, Graph<S> inner, RetryPolicy r) {
    java.util.Objects.requireNonNull(inner, "inner");
    register(name, NodeKind.subgraph(inner), r);
    return this;
}
```

- [ ] **Step 5: Executor dispatch for subgraph nodes**

When current node is subgraph kind: derive a child executionId (`parentEid + ":" + nodeName`); invoke `inner.run(state, childEid)`; on success, emit one parent `TraceStep` with `children = innerTrace.steps()`; the child trace's `TraceRecorder` is a recording one whose result is then attached. Retries wrap the entire inner execution. State `before/after` in the parent step = inner's initial / final state.

(Implementation hint: load the child's recorded trace via `RecordingTraceRecorder`. The simplest path: inject a fresh `RecordingTraceRecorder<S>` for the child run, read its built `ExecutionTrace` after, fold into parent's current step builder.)

- [ ] **Step 6: Document the limitation** in JavaDoc on `Builder.subgraph`: "Mid-subgraph crash semantics: the entire subgraph re-runs from its start on resume. Resuming a parent execution into the middle of a subgraph is not supported in this release."

- [ ] **Step 7: Run, expect PASS**

- [ ] **Step 8: Commit**

```bash
git commit -am "feat(core): Builder.subgraph composes Graph<S> as a node"
```

---

## Task 10: `Graph.toMermaid()`

**Files:**
- Create: `tracegraph-core/src/main/java/io/tracegraph/core/viz/MermaidRenderer.java`
- Modify: `Graph.java` to expose `toMermaid()`
- Test: `tracegraph-core/src/test/java/io/tracegraph/core/viz/MermaidRendererTest.java`

- [ ] **Step 1: Write failing test (golden-string)**

```java
@Test
void rendersLinearGraph() {
    Graph<String> g = Graph.<String>builder()
            .node("a", (s, ctx) -> s).node("b", (s, ctx) -> s)
            .edge("a", "b").entry("a").terminal("b").build();
    String mmd = g.toMermaid();
    assertThat(mmd).contains("flowchart TD");
    assertThat(mmd).contains("a --> b");
    assertThat(mmd).contains("[*] --> a");
}
```

- [ ] **Step 2: Run, expect compile failure**

- [ ] **Step 3: Implement `MermaidRenderer.render(Graph<?> g)`**

```java
package io.tracegraph.core.viz;
import io.tracegraph.core.Edge;
import io.tracegraph.core.Graph;

public final class MermaidRenderer {
    private MermaidRenderer() {}
    public static String render(Graph<?> g) {
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart TD\n");
        sb.append("    [*] --> ").append(g.entry()).append('\n');
        for (Edge<?> e : g.edges()) {
            sb.append("    ").append(e.from()).append(" --> ").append(e.to());
            if (e.condition() != null) sb.append("|cond|");
            sb.append('\n');
        }
        for (String t : g.terminals()) sb.append("    ").append(t).append(" --> [*]\n");
        return sb.toString();
    }
}
```

Add `public String toMermaid() { return MermaidRenderer.render(this); }` to `Graph`.

- [ ] **Step 4: Run, expect PASS**

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(core): Graph.toMermaid() renders flowchart"
```

---

## Task 11: `Graph.toPlantUml()`

**Files:**
- Create: `tracegraph-core/src/main/java/io/tracegraph/core/viz/PlantUmlRenderer.java`
- Modify: `Graph.java`
- Test: `tracegraph-core/src/test/java/io/tracegraph/core/viz/PlantUmlRendererTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test
void rendersLinearGraph() {
    Graph<String> g = /* same as Mermaid test */;
    String puml = g.toPlantUml();
    assertThat(puml).startsWith("@startuml");
    assertThat(puml).endsWith("@enduml\n");
    assertThat(puml).contains("[*] --> a");
    assertThat(puml).contains("a --> b");
    assertThat(puml).contains("b --> [*]");
}
```

- [ ] **Step 2: Implement `PlantUmlRenderer.render`** — same shape as Mermaid, wrap with `@startuml\n` ... `@enduml\n`.

- [ ] **Step 3: Run, expect PASS**

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(core): Graph.toPlantUml() renders state diagram"
```

---

## Task 12: Subgraph cluster rendering in Mermaid + PlantUML

**Files:**
- Modify: `MermaidRenderer.java`, `PlantUmlRenderer.java`
- Test: extend the existing renderer tests

- [ ] **Step 1: Write failing test**

```java
@Test
void rendersSubgraphAsCluster() {
    Graph<String> inner = Graph.<String>builder()
            .node("x", (s, ctx) -> s).entry("x").terminal("x").build();
    Graph<String> outer = Graph.<String>builder()
            .subgraph("nested", inner).entry("nested").terminal("nested").build();
    assertThat(outer.toMermaid()).contains("subgraph nested");
    assertThat(outer.toPlantUml()).contains("package \"nested\"");
}
```

- [ ] **Step 2: Implement** — when iterating nodes, query `NodeKind.kind()` and if `SUBGRAPH`, render the cluster + recurse into the inner graph's nodes/edges. Expose a package-private accessor on `Graph` (`Graph<?> innerSubgraph(String name)`) so renderers can introspect.

- [ ] **Step 3: Run, expect PASS**

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(core): renderers emit subgraph clusters"
```

---

## Task 13: Spring Boot starter — `POST /tracegraph/traces/{id}/resume`

**Files:**
- Modify: `tracegraph-spring-boot-starter/.../TraceController.java`
- Modify: `TraceWebAutoConfiguration.java` (only if the controller's bean wiring depends on a new collaborator)
- Test: `tracegraph-spring-boot-starter/.../TraceControllerResumeTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test
void resumeReturns200WhenInterrupted() {
    /* set up MockMvc with a Graph that interrupts before "b", run it, then POST resume */
    mvc.perform(post("/tracegraph/traces/eid-1/resume"))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.executionId").value("eid-1"))
       .andExpect(jsonPath("$.status").value("SUCCEEDED"));
}

@Test
void resumeReturns404ForUnknown() {
    mvc.perform(post("/tracegraph/traces/ghost/resume")).andExpect(status().isNotFound());
}

@Test
void resumeReturns409WhenAlreadyComplete() {
    /* trace exists with status=SUCCEEDED */
    mvc.perform(post("/tracegraph/traces/eid-2/resume")).andExpect(status().isConflict());
}
```

- [ ] **Step 2: Add controller method**

```java
@PostMapping("/{id}/resume")
public ResponseEntity<?> resume(@PathVariable String id) {
    ExecutionTrace<?> trace = traceStore.load(id).orElse(null);
    if (trace == null) return ResponseEntity.notFound().build();
    if (trace.status() != Status.INTERRUPTED) return ResponseEntity.status(409).body(/* error */);
    ExecutionResult<?> r = graph.resume(id).orElseThrow();
    return ResponseEntity.ok(/* dto */);
}
```

(`graph` injection requires `@ConditionalOnSingleCandidate(Graph.class)`. If the controller is split between observability-only and replay variants, place this in the replay-aware controller — same conditions as `TraceReplayController`.)

- [ ] **Step 3: Run, expect PASS**

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(starter): POST /tracegraph/traces/{id}/resume"
```

---

## Task 14: Spring Boot starter — SSE streaming endpoint

**Files:**
- Create: `tracegraph-spring-boot-starter/.../TraceStreamController.java`
- Modify: `TraceWebAutoConfiguration.java`
- Test: `TraceStreamControllerTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test
void streamPublishesSseEvents() {
    String body = mvc.perform(post("/tracegraph/traces/stream").contentType(MediaType.APPLICATION_JSON).content("{\"foo\":\"bar\"}"))
       .andExpect(status().isOk())
       .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
       .andReturn().getResponse().getContentAsString();
    assertThat(body).contains("event:NodeEnter");
    assertThat(body).contains("event:Complete");
}
```

(Adjust to actual graph state type; the test graph should be a `Graph<Map<String,Object>>` registered in the test config.)

- [ ] **Step 2: Implement controller**

```java
@RestController
@RequestMapping("/tracegraph/traces")
public class TraceStreamController<S> {
    private final Graph<S> graph;
    private final ObjectMapper mapper;
    public TraceStreamController(Graph<S> g, ObjectMapper m) { this.graph = g; this.mapper = m; }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody S initial) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        graph.stream(initial).subscribe(new Flow.Subscriber<>() {
            Flow.Subscription sub;
            public void onSubscribe(Flow.Subscription s) { this.sub = s; s.request(Long.MAX_VALUE); }
            public void onNext(NodeEvent<S> e) {
                try { emitter.send(SseEmitter.event().name(e.getClass().getSimpleName()).data(e)); }
                catch (Exception ex) { emitter.completeWithError(ex); sub.cancel(); }
            }
            public void onError(Throwable t) { emitter.completeWithError(t); }
            public void onComplete() { emitter.complete(); }
        });
        return emitter;
    }
}
```

- [ ] **Step 3: Register in `TraceWebAutoConfiguration`** with the same `@ConditionalOnSingleCandidate(Graph.class)` guard.

- [ ] **Step 4: Run, expect PASS**

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(starter): SSE streaming endpoint POST /tracegraph/traces/stream"
```

---

## Task 15: Update CLAUDE.md with Phase 4 conventions

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Add Phase 4 entries** under the conventions section:

> - **Streaming.** `Graph.stream(initial)` returns `Flow.Publisher<NodeEvent<S>>` with `NodeEnter`/`NodeExit`/`NodeRetry`/`Failed`/`Complete` events. Backed by a `SubmissionPublisher`; backpressure overflow drops oldest (durable record lives in `TraceStore`). Core stays JDK-only.
> - **Interrupts.** `Builder.interruptBefore/After(name...)` for HITL pauses. Executor writes a checkpoint with `interruptPending=true` (interrupt-before) or normal `lastCompletedNode` (interrupt-after) and returns `Status.INTERRUPTED`. `Graph.resume(id)` continues. Per-branch interrupts inside `parallel` are not supported.
> - **Subgraphs.** `Builder.subgraph(name, Graph<S> inner)` embeds a compiled graph as a node. Trace records one parent step with `children` populated by the inner trace. Resuming a parent into mid-subgraph is not supported — subgraph re-runs from its start.
> - **Dynamic routing.** `RoutingNode<S>` returns `NodeResult.goTo(name, state)` to bypass edges or `NodeResult.of(state)` to fall through. Unknown `goTo` target throws `NodeExecutionException`.
> - **Visualization.** `Graph.toMermaid()` / `Graph.toPlantUml()` are pure structural renders in core.

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: CLAUDE.md conventions for Phase 4"
```

---

## Task 16: Final verification

- [ ] **Step 1: Full build**

`mvn -B install -DskipTests=false` (with `JAVA_HOME` set to JDK 21 per `CLAUDE.local.md`)

Expected: BUILD SUCCESS, all modules.

- [ ] **Step 2: Run the full test suite**

`mvn -B test`

Expected: 0 failures.

- [ ] **Step 3: Smoke-check the renderers**

In a scratch test or REPL, run a small graph through `toMermaid()` and paste into mermaid.live to eyeball the output. Same for PlantUML against plantuml.com.

- [ ] **Step 4: Tag completion in CHANGELOG.md** under `## Unreleased`:

```markdown
### Added
- Streaming: `Graph.stream(initial)` returns `Flow.Publisher<NodeEvent<S>>`.
- HITL: `Builder.interruptBefore/After`; `Status.INTERRUPTED`; `POST /tracegraph/traces/{id}/resume` REST endpoint.
- Subgraphs: `Builder.subgraph(name, Graph<S>)`; `TraceStep.children` field.
- Dynamic routing: `RoutingNode<S>` + `NodeResult.goTo(...)`.
- Visualization: `Graph.toMermaid()`, `Graph.toPlantUml()`.
- Starter: SSE streaming endpoint `POST /tracegraph/traces/stream`.
```

- [ ] **Step 5: Final commit**

```bash
git commit -am "docs: CHANGELOG entries for Phase 4"
```

---

## Self-review notes

**Spec coverage:**
- 4a streaming → Tasks 2, 3, 4, 14 ✓
- 4b interrupts → Tasks 1, 5, 6, 13 ✓
- 4c subgraphs → Tasks 8, 9, 12 ✓
- 4d dynamic routing → Task 7 ✓
- 4e visualization → Tasks 10, 11, 12 ✓
- Backward-compat: `TraceStep.children` → Task 8 (CHANGELOG entry) ✓; `Status.INTERRUPTED` → Task 1; `Checkpoint.interruptPending` → Task 5 (Jackson tolerance noted) ✓
- Testing strategy: matches the spec's behavior-cluster naming (`GraphStreamingTest`, `GraphInterruptTest`, `GraphSubgraphTest`, `RoutingNodeTest`, renderer tests, starter SSE/resume tests) ✓
- Open questions in spec (SSE vs WebSocket, Mermaid version, subgraph trace size) — SSE chosen explicitly in Task 14; Mermaid v10 implied; truncation deferred ✓

**Type consistency:** `NodeResult.goTo`/`of` factories used consistently. `Status.INTERRUPTED` used in Tasks 1/6/13. `interruptPending` consistent in Tasks 5/6.

**Placeholder scan:** Two acceptable hand-waves remain — both flagged in-task: (a) Task 5 says "adapt to actual constructor signature" because the existing `Checkpoint` record's components aren't enumerated here; (b) Task 8 same for `TraceStep`. Both ask the executor to read the file first. Acceptable because the task's first step *is* the read.
