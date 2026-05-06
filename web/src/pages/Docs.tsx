import { useState, useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Code, CodeBlock } from '@/components'
import { DOCS_TREE } from '@/data/mock'
import { highlightJava } from '@/lib/highlight'

export function Docs() {
  const location = useLocation()
  const navigate = useNavigate()
  const segId = location.pathname.replace(/^\/docs\/?/, '') || 'quickstart'
  const [active, setActive] = useState(segId)

  useEffect(() => {
    const id = location.pathname.replace(/^\/docs\/?/, '') || 'quickstart'
    setActive(id)
  }, [location.pathname])

  return (
    <div className="max-w-[1320px] mx-auto px-6 lg:px-8 py-12 grid grid-cols-1 lg:grid-cols-[240px_1fr_200px] gap-12 fade-up">
      <aside className="lg:sticky lg:top-20 lg:self-start lg:max-h-[calc(100vh-100px)] overflow-y-auto scroll-thin pr-2">
        {DOCS_TREE.map((group) => (
          <div key={group.section} className="mb-6">
            <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mb-3">{group.section}</h4>
            <ul className="space-y-0.5">
              {group.items.map((it) => (
                <li key={it.id}>
                  <a
                    href={`/docs/${it.id}`}
                    onClick={(e) => { e.preventDefault(); navigate(`/docs/${it.id}`) }}
                    className={`block px-3 py-1.5 rounded-md text-[13.5px] transition-colors ${
                      active === it.id
                        ? 'bg-ink-100 dark:bg-ink-900 text-ink-950 dark:text-white font-medium'
                        : 'text-ink-600 dark:text-ink-400 hover:text-ink-950 dark:hover:text-white'
                    }`}
                  >{it.label}</a>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </aside>

      <article className="min-w-0">
        <DocPage id={active} />
      </article>

      <aside className="hidden lg:block lg:sticky lg:top-20 lg:self-start text-[12.5px]">
        <h5 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mb-3">On this page</h5>
        <OnThisPage id={active} />
      </aside>
    </div>
  )
}

function OnThisPage({ id }: { id: string }) {
  const page = DOC_PAGES[id] ?? DOC_PAGES['quickstart']
  return (
    <div className="space-y-2 border-l hairline pl-4">
      {page.toc.map((item) => (
        <a key={item.id} className="block text-ink-500 hover:text-ink-950 dark:hover:text-white" href={`#${item.id}`}>{item.label}</a>
      ))}
    </div>
  )
}

// ── Doc building blocks ────────────────────────────────────────────────────

const H2 = ({ children, id }: { children: React.ReactNode; id?: string }) => (
  <h2 id={id} className="text-[26px] text-ink-950 dark:text-white tracking-tight font-medium mt-12 mb-3">{children}</h2>
)
const P = ({ children }: { children: React.ReactNode }) => (
  <p className="text-[15px] text-ink-700 dark:text-ink-300 leading-relaxed mb-4">{children}</p>
)
const Cb = ({ src, file, lang = 'java' }: { src: string; file?: string; lang?: string }) => (
  <div className="my-4"><CodeBlock filename={file} language={lang}>{highlightJava(src)}</CodeBlock></div>
)
const Callout = ({ children }: { children: React.ReactNode }) => (
  <div className="my-6 p-4 pl-5 rounded-xl border-l-2 border-accent-500 bg-accent-50/60 dark:bg-accent-700/10 text-[14.5px] text-ink-700 dark:text-ink-300">{children}</div>
)
const Table = ({ headers, rows }: { headers: string[]; rows: string[][] }) => (
  <div className="my-6 rounded-xl border hairline overflow-hidden">
    <table className="w-full text-[13.5px]">
      <thead className="bg-ink-50 dark:bg-ink-900">
        <tr>{headers.map((h) => <th key={h} className="text-left mono text-[11px] uppercase tracking-wider text-ink-500 px-4 py-3 font-medium">{h}</th>)}</tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i} className="border-t hairline">
            {r.map((c, j) => <td key={j} className={`px-4 py-3 ${j === 0 ? 'mono text-[12.5px] text-ink-950 dark:text-white' : 'text-ink-700 dark:text-ink-300'}`}>{c}</td>)}
          </tr>
        ))}
      </tbody>
    </table>
  </div>
)

// ── Doc page content ───────────────────────────────────────────────────────

interface DocPageDef {
  crumb: string
  title: string
  lede: string
  toc: { id: string; label: string }[]
  body: () => React.ReactNode
}

const DOC_PAGES: Record<string, DocPageDef> = {
  quickstart: {
    crumb: 'GETTING STARTED / QUICKSTART',
    title: 'Quickstart',
    lede: 'Get a typed graph running on the JVM in under five minutes. This walkthrough builds the smallest useful TraceGraph and layers on retries and replay.',
    toc: [{ id:'install', label:'Install' }, { id:'state', label:'Define state' }, { id:'build', label:'Build the graph' }, { id:'run', label:'Run it' }],
    body: () => (<>
      <H2 id="install">1. Install</H2>
      <P>Add the core module to your <Code>pom.xml</Code>. Everything else is additive.</P>
      <Cb file="pom.xml" lang="xml" src={`<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-core</artifactId>
    <version>0.3.0</version>
</dependency>`} />
      <H2 id="state">2. Define your state</H2>
      <P>State is a Java record. Records give you immutability, equality, and structural diffing for free.</P>
      <Cb file="OrderState.java" src={`record OrderState(String id, boolean valid, boolean charged) {
    OrderState withValid(boolean v)   { return new OrderState(id, v, charged); }
    OrderState withCharged(boolean v) { return new OrderState(id, valid, v); }
}`} />
      <H2 id="build">3. Build the graph</H2>
      <P>Three nodes, two edges, one entry, one terminal. The fluent builder is the public contract.</P>
      <Cb file="Pipeline.java" src={`Graph<OrderState> graph = Graph.<OrderState>builder()
    .node("validate", (s, ctx) -> s.withValid(true))
    .node("charge",   (s, ctx) -> s.withCharged(true))
    .entry("validate")
    .edge("validate", "charge", OrderState::valid)
    .terminal("charge")
    .build();`} />
      <H2 id="run">4. Run it</H2>
      <Cb file="Main.java" src={`ExecutionResult<OrderState> r = graph.run(new OrderState("o-1", false, false));
System.out.println(r.status());     // COMPLETED
System.out.println(r.finalState()); // OrderState[id=o-1, valid=true, charged=true]
System.out.println(r.path());       // [validate, charge]`} />
      <Callout><strong>What just happened.</strong> The executor ran <Code>validate</Code>, applied its return value as the new state, evaluated the predicate <Code>OrderState::valid</Code>, and continued to <Code>charge</Code>. Terminal reached, run done.</Callout>
    </>),
  },

  install: {
    crumb: 'GETTING STARTED / INSTALLATION',
    title: 'Installation',
    lede: 'TraceGraph is published to Maven Central. Pick the smallest module set that matches your use case.',
    toc: [{ id:'req', label:'Requirements' }, { id:'maven', label:'Maven' }, { id:'gradle', label:'Gradle' }, { id:'bom', label:'BOM' }],
    body: () => (<>
      <H2 id="req">Requirements</H2>
      <Table headers={['Dependency','Version']} rows={[
        ['JDK','21+ (records, pattern matching, virtual threads)'],
        ['Maven','3.9+'],
        ['SLF4J binding','any (logback, log4j2, jul)'],
      ]} />
      <H2 id="maven">Maven</H2>
      <Cb file="pom.xml" lang="xml" src={`<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-core</artifactId>
    <version>0.3.0</version>
</dependency>`} />
      <H2 id="gradle">Gradle</H2>
      <Cb file="build.gradle.kts" lang="kotlin" src={`implementation("site.tracegraph:tracegraph-core:0.3.0")
implementation("site.tracegraph:tracegraph-observability:0.3.0")
implementation("site.tracegraph:tracegraph-spring-boot-starter:0.3.0")`} />
      <H2 id="bom">Using the BOM</H2>
      <P>Import <Code>tracegraph-bom</Code> to align all module versions automatically.</P>
      <Cb file="pom.xml" lang="xml" src={`<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>site.tracegraph</groupId>
            <artifactId>tracegraph-bom</artifactId>
            <version>0.3.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>`} />
    </>),
  },

  firstgraph: {
    crumb: 'GETTING STARTED / YOUR FIRST GRAPH',
    title: 'Your first graph',
    lede: 'A five-node order pipeline with retries, parallel enrichment, and an async LLM scoring call.',
    toc: [{ id:'state', label:'State' }, { id:'graph', label:'Graph' }, { id:'run', label:'Run' }],
    body: () => (<>
      <H2 id="state">Define the state record</H2>
      <Cb file="OrderState.java" src={`record OrderState(
    String id, boolean valid,
    String profile, double fraud, String inventory,
    double riskScore, boolean charged, boolean shipped
) {}`} />
      <H2 id="graph">Build the graph</H2>
      <Cb file="OrderPipeline.java" src={`Graph<OrderState> graph = Graph.<OrderState>builder()
    .node("validate", OrderNodes::validate)
    .parallel("enrich",
        List.of(OrderNodes::profile, OrderNodes::fraud, OrderNodes::inventory),
        (input, branches) -> branches.stream().reduce(input, OrderState::merge))
    .asyncNode("score", OrderNodes::scoreAsync)
    .node("charge", OrderNodes::charge,
        RetryPolicy.exponential(3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(2)))
    .node("ship", OrderNodes::ship)
    .entry("validate")
    .edge("validate", "enrich", OrderState::valid)
    .edge("enrich",   "score")
    .edge("score",    "charge", s -> s.riskScore() < 0.5)
    .edge("charge",   "ship",   OrderState::charged)
    .terminal("ship")
    .build();`} />
      <H2 id="run">Run and inspect</H2>
      <Cb src={`ExecutionResult<OrderState> r = graph.run(seed);
r.path();       // [validate, enrich, score, charge, ship]
r.finalState(); // fully enriched + charged + shipped`} />
    </>),
  },

  graphs: {
    crumb: 'CONCEPTS / GRAPHS & NODES',
    title: 'Graphs & nodes',
    lede: 'Graph<S> is the main runtime abstraction. The single type parameter S threads through every node.',
    toc: [{ id:'kinds', label:'Node kinds' }, { id:'entry', label:'Entry & terminal' }, { id:'immutability', label:'Immutability' }],
    body: () => (<>
      <H2 id="kinds">The four node kinds</H2>
      <Table headers={['Builder method','Use when']} rows={[
        ['node',         'Synchronous transitions. The 80% case.'],
        ['asyncNode',    'Network calls, LLM completions — returns CompletableFuture<S>.'],
        ['parallel',     'Fan-out enrichment: N branches receive the same state and merge.'],
        ['routingNode',  'Dynamic next-node selection via NodeResult.goTo(name, state).'],
      ]} />
      <H2 id="entry">Entry and terminal nodes</H2>
      <P>Every graph has exactly one entry, declared with <Code>.entry("name")</Code>. It can have one or more terminals, declared with <Code>.terminal("name")</Code>. The executor stops when it reaches any terminal.</P>
      <H2 id="immutability">Nodes return state, never mutate it</H2>
      <Callout><strong>Single type parameter.</strong> Use state composition (sub-records inside <Code>S</Code>) for sub-results. <Code>Node&lt;S, R&gt;</Code> is intentionally not supported.</Callout>
    </>),
  },

  state: {
    crumb: 'CONCEPTS / STATE & CONTEXT',
    title: 'State & context',
    lede: 'State threads through your graph as an immutable value. Context is the per-execution toolkit handed to every node.',
    toc: [{ id:'state', label:'State record' }, { id:'ctx', label:'NodeContext' }, { id:'memory', label:'Memory' }],
    body: () => (<>
      <H2 id="state">The state record</H2>
      <P>Prefer Java records. They are immutable, have structural equality, and serialize cleanly for tracing and diffing. Each node returns a new state value — it never mutates the input.</P>
      <Cb src={`record AppState(String userId, boolean authenticated, List<String> roles) {
    AppState withAuthenticated(boolean v) {
        return new AppState(userId, v, roles);
    }
}`} />
      <H2 id="ctx">NodeContext</H2>
      <Table headers={['Method','What it gives you']} rows={[
        ['ctx.executionId()',    'The unique ID for this run. Use it to correlate logs and traces.'],
        ['ctx.idempotencyKey()', 'Stable key for at-least-once dedup. Safe to use as an HTTP idempotency key.'],
        ['ctx.memory()',         'MemoryStore access. Default is no-op; wire in InMemoryMemoryStore or JdbcMemoryStore.'],
        ['ctx.executor()',       'The virtual-thread ExecutorService for this run. Use it inside parallel branches.'],
      ]} />
      <H2 id="memory">Memory vs. state</H2>
      <P>Working memory <em>is</em> the state object — it threads through every node automatically. The <Code>MemoryStore</Code> is for cross-execution data: user preferences, conversation history, cached embeddings.</P>
    </>),
  },

  edges: {
    crumb: 'CONCEPTS / EDGES & ROUTING',
    title: 'Edges & routing',
    lede: 'Edges are first-class records with pure predicate functions. They are the only place conditional logic enters the graph.',
    toc: [{ id:'edges', label:'Edge basics' }, { id:'predicates', label:'Predicates' }, { id:'routing', label:'Dynamic routing' }],
    body: () => (<>
      <H2 id="edges">Edge basics</H2>
      <P>An edge connects two node names. Without a predicate it always fires. With a predicate it fires only when the predicate returns true on the post-node state.</P>
      <Cb src={`.edge("score", "approve", s -> s.risk() < 0.3)  // fires when risk is low
.edge("score", "review",  s -> s.risk() < 0.7)  // fires when risk is medium
.edge("score", "reject")                          // fallthrough — always fires`} />
      <H2 id="predicates">Predicates must be pure</H2>
      <P>Edge predicates are evaluated during edge resolution, which happens after every node exit and again on resume. They must be pure functions of state — no side effects, no I/O, no randomness.</P>
      <H2 id="routing">Dynamic routing with RoutingNode</H2>
      <Cb src={`.routingNode("router", (s, ctx) -> {
    if (s.needsReview()) return NodeResult.goTo("review", s);
    return NodeResult.of(s); // fall through to normal edges
})`} />
    </>),
  },

  parallel: {
    crumb: 'CONCEPTS / ASYNC & PARALLEL',
    title: 'Async & parallel',
    lede: 'Virtual-thread-per-task by default. Parallel branches are anonymous and collapse to a single trace step.',
    toc: [{ id:'async', label:'asyncNode' }, { id:'parallel', label:'parallel' }, { id:'executor', label:'Executor' }],
    body: () => (<>
      <H2 id="async">asyncNode</H2>
      <P>Returns <Code>CompletableFuture&lt;S&gt;</Code>. Integrates with retry and checkpoint identically to sync nodes. The future is scheduled on the configured virtual-thread executor.</P>
      <Cb src={`.asyncNode("score", (s, ctx) ->
    CompletableFuture.supplyAsync(() -> callLlm(s), ctx.executor()))`} />
      <H2 id="parallel">parallel</H2>
      <P>Branches receive the same input state. They run concurrently. Results merge in declaration order. First failure wins.</P>
      <Cb src={`.parallel("enrich",
    List.of(
        (s, ctx) -> fetchProfile(s),
        (s, ctx) -> fetchFraudScore(s),
        (s, ctx) -> fetchInventory(s)),
    (input, branches) -> branches.stream().reduce(input, AppState::merge))`} />
      <Callout><strong>Branches are anonymous.</strong> They do not appear in the trace path, do not fire NodeListener events, and cannot be individually interrupted. Each parallel node collapses to one TraceStep.</Callout>
      <H2 id="executor">Executor lifecycle</H2>
      <P>The default executor is a virtual-thread-per-task service created lazily per run and shut down on completion. Supply your own via <Code>.executor(myPool)</Code> — user-supplied executors are never shut down by the graph.</P>
    </>),
  },

  retries: {
    crumb: 'RUNTIME / RETRIES',
    title: 'Retries',
    lede: 'Retry policy is a graph-definition concern, not runtime config. Attach it per-node or as a graph-wide default.',
    toc: [{ id:'policy', label:'RetryPolicy' }, { id:'pernode', label:'Per-node' }, { id:'listener', label:'onRetry' }],
    body: () => (<>
      <H2 id="policy">RetryPolicy</H2>
      <Table headers={['Factory','Parameters']} rows={[
        ['RetryPolicy.fixed(n, delay)',                           'Retry up to n times, waiting delay between attempts.'],
        ['RetryPolicy.exponential(n, initial, mult, max)',        'Exponential backoff capped at max delay.'],
        ['RetryPolicy.none()',                                    'No retries (the default when no policy is set).'],
      ]} />
      <Cb src={`RetryPolicy policy = RetryPolicy.exponential(
    3,                        // max attempts
    Duration.ofMillis(100),   // initial delay
    2.0,                      // multiplier
    Duration.ofSeconds(2));   // max delay`} />
      <H2 id="pernode">Attaching a policy</H2>
      <Cb src={`// Per-node
.node("charge", chargeFn, RetryPolicy.exponential(3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(2)))

// Graph-wide default (per-node wins)
.defaultRetryPolicy(RetryPolicy.fixed(2, Duration.ofMillis(500)))`} />
      <H2 id="listener">Observing retries</H2>
      <P><Code>NodeListener.onRetry(name, attempt, cause)</Code> fires before each retry. OtelNodeListener records retries as span events on the same span — no extra span per attempt.</P>
      <Callout><strong>Error and InterruptedException always short-circuit retries.</strong> Only RuntimeException and its non-Error subclasses are retried.</Callout>
    </>),
  },

  checkpoints: {
    crumb: 'RUNTIME / CHECKPOINTS',
    title: 'Checkpoints & resume',
    lede: 'Checkpoints write after each node exit, before edge resolution. Resume re-evaluates edges from the saved position.',
    toc: [{ id:'store', label:'CheckpointStore' }, { id:'resume', label:'Resume' }, { id:'idem', label:'Idempotency' }],
    body: () => (<>
      <H2 id="store">CheckpointStore</H2>
      <P>The default store is a no-op. Opt in by wiring one on the builder.</P>
      <Cb src={`// In-memory (test/dev only)
.checkpointStore(new InMemoryCheckpointStore())

// JDBC (production)
JdbcCheckpointStore<OrderState> store =
    new JdbcCheckpointStore<>(dataSource, OrderState.class);
store.initSchema();
.checkpointStore(store)`} />
      <H2 id="resume">Resuming an interrupted run</H2>
      <Cb src={`// Graph.resume() picks up from the last completed node checkpoint.
ExecutionResult<OrderState> r = graph.resume(executionId);`} />
      <Callout><strong>At-least-once on resume.</strong> If a crash happens mid-node, that node re-runs from attempt 1. Use <Code>ctx.idempotencyKey()</Code> to make side effects safe.</Callout>
      <H2 id="idem">Idempotency keys</H2>
      <Cb src={`// Inside a node: use the key to deduplicate HTTP calls.
String key = ctx.idempotencyKey();
httpClient.post("/charge", body, Map.of("Idempotency-Key", key));`} />
    </>),
  },

  memory: {
    crumb: 'RUNTIME / MEMORY',
    title: 'Memory',
    lede: 'MemoryStore is a scoped key-value store for cross-execution data — conversation history, user preferences, cached embeddings.',
    toc: [{ id:'api', label:'API' }, { id:'impls', label:'Implementations' }, { id:'scope', label:'Scopes' }],
    body: () => (<>
      <H2 id="api">API</H2>
      <Cb src={`// Inside a node
ctx.memory().put("session", "lastIntent", intent);
Optional<String> last = ctx.memory().get("session", "lastIntent", String.class);
ctx.memory().delete("session", "lastIntent");
List<String> keys = ctx.memory().keys("session");`} />
      <H2 id="impls">Implementations</H2>
      <Table headers={['Class','When to use']} rows={[
        ['MemoryStore.noop()',       'Default. No-op — nodes that call ctx.memory() compile and run fine.'],
        ['InMemoryMemoryStore',      'Tests and local dev. Not durable across restarts.'],
        ['FileMemoryStore',          'Single-process deployments. Atomic writes via tmp + ATOMIC_MOVE.'],
        ['JdbcMemoryStore',          'Production. Transactional upsert, compatible with PostgreSQL and H2.'],
      ]} />
      <H2 id="scope">Scopes</H2>
      <P>Every get/put/delete call takes an explicit scope string. Use it to namespace by user ID, session ID, or any logical boundary. There is no global scope — every value is scoped.</P>
    </>),
  },

  interrupts: {
    crumb: 'RUNTIME / INTERRUPTS & HITL',
    title: 'Interrupts & HITL',
    lede: 'Pause executions at named nodes for human-in-the-loop approvals, then resume when the human acts.',
    toc: [{ id:'declare', label:'Declare interrupts' }, { id:'resume', label:'Resume' }, { id:'api', label:'REST API' }],
    body: () => (<>
      <H2 id="declare">Declaring interrupt points</H2>
      <Cb src={`Graph<ApprovalState> graph = Graph.<ApprovalState>builder()
    .node("analyze", fn)
    .node("approve", approveFn)
    .node("execute", executeFn)
    .entry("analyze")
    .edge("analyze", "approve")
    .edge("approve", "execute", ApprovalState::approved)
    .terminal("execute")
    .interruptBefore("approve")   // pause before the node runs
    // or .interruptAfter("analyze")  // pause after the node completes
    .checkpointStore(jdbcStore)   // required for durable pause
    .build();`} />
      <Callout><strong>A checkpoint store is required.</strong> Interrupts write a checkpoint with <Code>interruptPending=true</Code>. Without a durable store the execution cannot be resumed after process restart.</Callout>
      <H2 id="resume">Resuming</H2>
      <Cb src={`// Poll until INTERRUPTED, then resume
ExecutionResult<ApprovalState> r = graph.run(seed);
if (r.status() == Status.INTERRUPTED) {
    // Human approves via your UI...
    graph.resume(r.executionId());
}`} />
      <H2 id="api">Spring Boot REST</H2>
      <P>The starter exposes <Code>POST /tracegraph/traces/{'{id}'}/resume</Code>. Returns 409 if the execution is not in INTERRUPTED state, 404 if unknown.</P>
    </>),
  },

  otel: {
    crumb: 'OBSERVABILITY / OPENTELEMETRY',
    title: 'OpenTelemetry',
    lede: 'One span per node. Retries are span events on the same span. Errors set StatusCode.ERROR.',
    toc: [{ id:'wire', label:'Wiring' }, { id:'spans', label:'Span structure' }, { id:'state', label:'State diffs' }],
    body: () => (<>
      <H2 id="wire">Wiring OtelNodeListener</H2>
      <Cb src={`// Use the global OpenTelemetry instance (OTEL Java agent / manual setup)
.listener(OtelNodeListener.usingGlobal())

// Or pass an explicit instance
.listener(OtelNodeListener.using(openTelemetry))`} />
      <H2 id="spans">Span structure</H2>
      <Table headers={['Event','Span behavior']} rows={[
        ['Node enter',       'Span starts. Attributes: tracegraph.node.name, tracegraph.execution.id.'],
        ['Node exit (ok)',    'Span ends with StatusCode.OK.'],
        ['Retry',            'Span event added with attempt number and exception class.'],
        ['Node exit (fail)', 'Span event with exception, StatusCode.ERROR, exception recorded.'],
        ['State diff',       'Span event "state" with before/after rendered via StateRenderer.'],
      ]} />
      <H2 id="state">State diffs in spans</H2>
      <P>State diffs flow through <Code>NodeListener.onState(name, before, after)</Code>. The OtelNodeListener binds them as a <Code>state</Code> span event. The renderer is pluggable via <Code>StateRenderer</Code> (default: <Code>String::valueOf</Code>).</P>
      <Callout>OTel lives in <Code>tracegraph-observability</Code> — core stays OTel-free. Compose multiple listeners via <Code>Listeners.compose(...)</Code>.</Callout>
    </>),
  },

  replay: {
    crumb: 'OBSERVABILITY / REPLAY & DIFF',
    title: 'Replay & diff',
    lede: 'Plug a TraceRecorder in once and every run becomes a deterministic replay artifact you can fork against a modified graph.',
    toc: [{ id:'record', label:'Recording' }, { id:'replay', label:'Replay' }, { id:'diff', label:'Diff' }, { id:'fork', label:'Fork' }],
    body: () => (<>
      <H2 id="record">Recording traces</H2>
      <Cb src={`// In-memory (good for tests)
TraceStore<OrderState> store = new InMemoryTraceStore<>();
.traceRecorder(new RecordingTraceRecorder<>(store))

// JSON file (local dev)
TraceStore<OrderState> store = JsonFileTraceStore.of(Path.of("/tmp/traces"), OrderState.class);

// JDBC (production)
JdbcTraceStore<OrderState> store = new JdbcTraceStore<>(dataSource, OrderState.class);
store.initSchema();`} />
      <H2 id="replay">Walking a trace</H2>
      <Cb src={`ExecutionTrace<OrderState> trace = store.load(id).orElseThrow();
Replayer<OrderState> replay = Replayer.of(trace);
for (int i = 0; i < replay.stepCount(); i++) {
    TraceStep<OrderState> step = replay.stepAt(i);
    System.out.printf("Step %d: %s → %s%n", i, step.before(), step.after());
}`} />
      <H2 id="diff">Diffing two runs</H2>
      <Cb src={`TraceDiff<OrderState> diff = TraceDiff.between(left, right);
diff.commonPrefix();      // steps that matched
diff.divergenceIndex();   // first index that diverged (-1 if identical)
diff.leftRemainder();     // steps only in left
diff.rightRemainder();    // steps only in right
diff.identical();         // true iff no divergence + same status + same final state`} />
      <H2 id="fork">Forking from a step</H2>
      <Cb src={`// Re-run from step 2 against a modified graph (e.g. after a bug fix)
ReplayRunner<OrderState> runner = ReplayRunner.of(trace, fixedGraph);
ExecutionResult<OrderState> fork = runner.reRunFrom(2);
// fork.executionId() carries forkedFromExecutionId + forkedFromStepIndex`} />
    </>),
  },

  spring: {
    crumb: 'INTEGRATION / SPRING BOOT',
    title: 'Spring Boot starter',
    lede: 'Auto-config registers no-op beans for the four SPIs. Your own @Bean definitions always win.',
    toc: [{ id:'dep', label:'Dependency' }, { id:'graph', label:'Define Graph bean' }, { id:'props', label:'Properties' }],
    body: () => (<>
      <H2 id="dep">Add the starter</H2>
      <Cb file="pom.xml" lang="xml" src={`<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-spring-boot-starter</artifactId>
    <version>0.3.0</version>
</dependency>`} />
      <H2 id="graph">Define your Graph bean</H2>
      <P><Code>Graph&lt;?&gt;</Code> is not auto-registered — it's generic in <Code>S</Code>, so you define your own.</P>
      <Cb file="AppConfiguration.java" src={`@Configuration
public class AppConfiguration {
    @Bean
    public Graph<MyState> graph(TraceStore<MyState> store) {
        return Graph.<MyState>builder()
            .node("step1", fn1)
            .entry("step1").terminal("step1")
            .traceRecorder(new RecordingTraceRecorder<>(store))
            .build();
    }

    @Bean
    public TraceStore<MyState> traceStore() {
        return new InMemoryTraceStore<>();
    }
}`} />
      <H2 id="props">Key properties</H2>
      <Cb file="application.yml" lang="yaml" src={`tracegraph:
  web:
    enabled: true          # expose /tracegraph/traces endpoints (default: true)
  ui:
    enabled: true          # serve embedded trace UI at /tracegraph/ui (default: true)
  memory:
    jdbc:
      enabled: true        # auto-wire JdbcMemoryStore when DataSource is present
      table: tracegraph_memory
  llm:
    provider: openai       # or anthropic
    api-key: \${OPENAI_API_KEY}`} />
    </>),
  },

  llm: {
    crumb: 'INTEGRATION / LLM CONNECTORS',
    title: 'LLM connectors',
    lede: 'Vendor-neutral LlmClient SPI. OpenAI-compatible and Anthropic adapters ship in tracegraph-connectors.',
    toc: [{ id:'client', label:'LlmClient' }, { id:'openai', label:'OpenAI' }, { id:'anthropic', label:'Anthropic' }, { id:'chat', label:'ChatNode' }],
    body: () => (<>
      <H2 id="client">LlmClient interface</H2>
      <Cb src={`LlmResponse res = client.complete(LlmRequest.builder()
    .model("gpt-4o")
    .message(ChatMessage.user("Summarize this order: " + state))
    .temperature(0.2)
    .maxTokens(256)
    .build());
String text = res.content();`} />
      <H2 id="openai">OpenAI adapter</H2>
      <Cb src={`LlmClient client = OpenAiLlmClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .model("gpt-4o-mini")
    // .endpoint("https://api.together.xyz/v1")  // any OpenAI-compatible URL
    .build();`} />
      <H2 id="anthropic">Anthropic adapter</H2>
      <Cb src={`LlmClient client = AnthropicLlmClient.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .model("claude-sonnet-4-6")
    .build();`} />
      <H2 id="chat">ChatNode</H2>
      <P><Code>ChatNode&lt;S&gt;</Code> bridges any <Code>LlmClient</Code> to a <Code>Node&lt;S&gt;</Code>. You provide a request-builder function and a response-folder that merges the LLM output back into state.</P>
      <Cb src={`Node<AgentState> scoreNode = ChatNode.<AgentState>builder()
    .client(client)
    .requestFactory(s -> LlmRequest.builder().model("gpt-4o").message(ChatMessage.user(prompt(s))).build())
    .responseFolder((s, r) -> s.withScore(parseScore(r.content())))
    .build();`} />
    </>),
  },

  rest: {
    crumb: 'INTEGRATION / REST API',
    title: 'REST API',
    lede: 'A summary of the HTTP endpoints exposed by the Spring Boot starter. See the full API Reference for details.',
    toc: [{ id:'traces', label:'Traces' }, { id:'replay', label:'Replay' }, { id:'stream', label:'Stream' }],
    body: () => (<>
      <H2 id="traces">Trace endpoints</H2>
      <Table headers={['Method','Path','Description']} rows={[
        ['GET',    '/tracegraph/traces',                  'List execution IDs. ?limit=N&offset=M for pagination.'],
        ['GET',    '/tracegraph/traces/{id}',             'Fetch a full ExecutionTrace with all steps.'],
        ['DELETE', '/tracegraph/traces/{id}',             '204 on success. Lineage links preserved.'],
        ['GET',    '/tracegraph/traces/{a}/diff/{b}',     'Diff two executions; returns TraceDiff JSON.'],
      ]} />
      <H2 id="replay">Replay endpoints</H2>
      <Table headers={['Method','Path','Description']} rows={[
        ['POST', '/tracegraph/traces/{id}/replay',         'Re-run entire trace against the current graph.'],
        ['POST', '/tracegraph/traces/{id}/replay?step=N',  'Fork from step N.'],
        ['POST', '/tracegraph/traces/{id}/resume',         'Resume an INTERRUPTED execution.'],
      ]} />
      <H2 id="stream">SSE stream</H2>
      <P>Subscribe to live node events with <Code>GET /tracegraph/traces/stream</Code>. Filter by execution with <Code>?execution={'{id}'}</Code>. The stream emits <Code>NodeEnter</Code>, <Code>NodeExit</Code>, <Code>NodeRetry</Code>, <Code>Failed</Code>, and <Code>Complete</Code> events as JSON.</P>
      <Cb src={`// JavaScript: subscribe to a live execution
const es = new EventSource('/tracegraph/traces/stream?execution=' + id);
es.onmessage = (e) => {
    const event = JSON.parse(e.data);
    console.log(event.type, event.nodeName);
};`} />
    </>),
  },
}

function DocPage({ id }: { id: string }) {
  const page = DOC_PAGES[id] ?? DOC_PAGES['quickstart']
  return (
    <div>
      <div className="mono text-[11px] uppercase tracking-[0.14em] text-ink-500 mb-4">
        {page.crumb}
      </div>
      <h1 className="display-tight text-[44px] text-ink-950 dark:text-white">{page.title}</h1>
      <p className="mt-4 text-[16px] text-ink-600 dark:text-ink-400 leading-relaxed max-w-2xl">{page.lede}</p>
      <div className="mt-10">{page.body()}</div>
      <div className="mt-16 pt-8 border-t hairline flex items-center justify-between mono text-[12px] text-ink-500">
        <a href="/docs" className="hover:text-ink-950 dark:hover:text-white inline-flex items-center gap-1.5">← Back to docs</a>
        <span>Edit on GitHub →</span>
      </div>
    </div>
  )
}
