import { useState, useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Code, CodeBlock, Badge } from '@/components'
import { Seo } from '@/components/Seo'
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

  const page = DOC_PAGES[active] ?? DOC_PAGES.quickstart

  return (
    <div className="max-w-[1320px] mx-auto px-6 lg:px-8 py-12 grid grid-cols-1 lg:grid-cols-[240px_1fr_220px] gap-12 fade-up">
      <Seo
        title={`${page.title} docs`}
        description={page.lede}
        path={location.pathname}
      />
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
                  >
                    {it.label}
                  </a>
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
  const page = DOC_PAGES[id] ?? DOC_PAGES.quickstart
  return (
    <div className="space-y-2 border-l hairline pl-4">
      {page.toc.map((item) => (
        <a key={item.id} className="block text-ink-500 hover:text-ink-950 dark:hover:text-white" href={`#${item.id}`}>
          {item.label}
        </a>
      ))}
    </div>
  )
}

const H2 = ({ children, id }: { children: React.ReactNode; id?: string }) => (
  <h2 id={id} className="text-[26px] text-ink-950 dark:text-white tracking-tight font-medium mt-12 mb-3">{children}</h2>
)

const H3 = ({ children }: { children: React.ReactNode }) => (
  <h3 className="text-[18px] text-ink-950 dark:text-white tracking-tight font-medium mt-7 mb-2">{children}</h3>
)

const P = ({ children }: { children: React.ReactNode }) => (
  <p className="text-[15px] text-ink-700 dark:text-ink-300 leading-relaxed mb-4">{children}</p>
)

const UL = ({ items }: { items: React.ReactNode[] }) => (
  <ul className="mb-5 space-y-2 text-[15px] text-ink-700 dark:text-ink-300 leading-relaxed">
    {items.map((item, i) => (
      <li key={i} className="flex gap-3">
        <span className="mt-[9px] w-1.5 h-1.5 rounded-full bg-ink-400 dark:bg-ink-500 flex-shrink-0" />
        <span>{item}</span>
      </li>
    ))}
  </ul>
)

const Steps = ({ items }: { items: React.ReactNode[] }) => (
  <ol className="mb-5 space-y-3">
    {items.map((item, i) => (
      <li key={i} className="grid grid-cols-[28px_1fr] gap-3 text-[15px] text-ink-700 dark:text-ink-300 leading-relaxed">
        <span className="mono text-[11px] h-7 w-7 rounded-full border hairline inline-flex items-center justify-center text-ink-500">{i + 1}</span>
        <span className="pt-1">{item}</span>
      </li>
    ))}
  </ol>
)

const Cb = ({ src, file, lang = 'java' }: { src: string; file?: string; lang?: string }) => (
  <div className="my-4"><CodeBlock filename={file} language={lang}>{highlightJava(src)}</CodeBlock></div>
)

const Callout = ({ children }: { children: React.ReactNode }) => (
  <div className="my-6 p-4 pl-5 rounded-xl border-l-2 border-accent-500 bg-accent-50/60 dark:bg-accent-700/10 text-[14.5px] text-ink-700 dark:text-ink-300">
    {children}
  </div>
)

const Table = ({ headers, rows }: { headers: string[]; rows: string[][] }) => (
  <div className="my-6 rounded-xl border hairline overflow-hidden">
    <table className="w-full text-[13.5px]">
      <thead className="bg-ink-50 dark:bg-ink-900">
        <tr>
          {headers.map((h) => (
            <th key={h} className="text-left mono text-[11px] uppercase tracking-wider text-ink-500 px-4 py-3 font-medium">
              {h}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i} className="border-t hairline">
            {r.map((c, j) => (
              <td key={j} className={`px-4 py-3 align-top ${j === 0 ? 'mono text-[12.5px] text-ink-950 dark:text-white' : 'text-ink-700 dark:text-ink-300'}`}>
                {c}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  </div>
)

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
    lede: 'Get a typed graph running on the JVM in under five minutes. This walkthrough builds the smallest useful TraceGraph, then shows where retries, replay, and Spring Boot fit next.',
    toc: [
      { id: 'install', label: 'Install' },
      { id: 'state', label: 'Define state' },
      { id: 'build', label: 'Build the graph' },
      { id: 'run', label: 'Run it' },
      { id: 'result', label: 'Inspect the result' },
      { id: 'retries', label: 'Add retries' },
      { id: 'next', label: 'Next steps' },
    ],
    body: () => (
      <>
        <P>TraceGraph is a production-grade agent runtime for the JVM. It gives you typed execution graphs, durable memory, and deep observability — without hiding control flow behind a framework. Everything you define is explicit, versioned, and replayable.</P>

        <H2 id="install">1. Add the dependency</H2>
        <P>Start with the smallest module that solves the problem you have today. Most teams begin with <Code>tracegraph-core</Code> and add durability or observability only when they need it. The BOM keeps versions aligned when you use multiple modules.</P>
        <Table
          headers={['Module', 'Add it when you need']}
          rows={[
            ['tracegraph-core', 'Typed graph execution, retries, edges, async, and parallel nodes. Zero heavy deps.'],
            ['tracegraph-runtime', 'Checkpoint persistence and crash-safe resume across process boundaries.'],
            ['tracegraph-observability', 'Trace recording, replay, diff, OpenTelemetry listeners, and state diffing.'],
            ['tracegraph-memory', 'Scoped cross-run storage: in-memory, file, and JDBC implementations.'],
            ['tracegraph-connectors', 'LLM adapters (OpenAI, Anthropic), ChatNode, ReActAgent, and tool-use.'],
            ['tracegraph-spring-boot-starter', 'Auto-configuration, REST endpoints, and SSE streaming inside Spring Boot.'],
          ]}
        />
        <Cb file="pom.xml" lang="xml" src={`<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-core</artifactId>
    <version>0.3.0</version>
</dependency>`} />
        <P>JDK 21 is required. TraceGraph uses records, pattern matching, and virtual threads. If Maven picks up the wrong JDK, verify with <Code>mvn -version</Code> first.</P>

        <H2 id="state">2. Define your state</H2>
        <P>State is the entire contract between nodes. Every node receives the current state and returns the next state — the graph threads that value through every transition. TraceGraph is easiest to reason about when state is immutable and explicit, which is why Java records are the recommended approach.</P>
        <Cb file="OrderState.java" src={`// A plain Java record is the recommended state type.
// Each "with" helper performs an immutable copy-on-write update.
record OrderState(String orderId, boolean valid, boolean charged) {
    OrderState withValid(boolean v)   { return new OrderState(orderId, v, charged); }
    OrderState withCharged(boolean v) { return new OrderState(orderId, valid, v); }
}`} />
        <UL items={[
          <>Use one record for the entire execution state. Scattering transient fields across services makes replay and diffing impossible.</>,
          <>Return a new value from each node. Never mutate collections or fields on the existing state instance.</>,
          <>Prefer explicit named fields over generic maps or JSON blobs. The type system catches mistakes at compile time; maps do not.</>,
          <>With-copy helpers on the record keep node code concise while preserving immutability.</>,
        ]} />

        <H2 id="build">3. Build the graph</H2>
        <P>The fluent builder is the public contract for graph definitions. You name nodes, wire conditional edges, declare a single entry node, and mark one or more terminals. The graph is fully compiled and validated at build time — there is no hidden scheduler and no runtime graph mutation.</P>
        <Cb file="OrderPipeline.java" src={`import io.tracegraph.core.Graph;
import io.tracegraph.core.ExecutionResult;

Graph<OrderState> graph = Graph.<OrderState>builder()
    // declare nodes as (state, ctx) -> nextState lambdas
    .node("validate", (s, ctx) -> {
        boolean ok = s.orderId() != null && !s.orderId().isBlank();
        return s.withValid(ok);
    })
    .node("charge", (s, ctx) -> {
        // ctx.idempotencyKey() is stable across retries — safe to pass to payment APIs
        chargePaymentGateway(s.orderId(), ctx.idempotencyKey());
        return s.withCharged(true);
    })
    // exactly one entry node
    .entry("validate")
    // conditional edge: only route to charge if valid
    .edge("validate", "charge", OrderState::valid)
    // one or more terminals
    .terminal("charge")
    .build();`} />
        <Callout><strong>Execution model.</strong> After every node exits, TraceGraph evaluates outgoing edges in declaration order against the post-node state. The first matching edge wins. If a terminal is reached the run completes. If a node throws and no retry policy applies, the run fails immediately with <Code>Status.FAILED</Code> and the exception in <Code>r.error()</Code>.</Callout>

        <H2 id="run">4. Run it</H2>
        <P>Call <Code>graph.run(initialState)</Code> with the seed value. The call blocks until the graph reaches a terminal or fails. It is safe to call <Code>run</Code> concurrently from multiple threads — the graph object is immutable after <Code>build()</Code>.</P>
        <Cb file="Main.java" src={`OrderState seed = new OrderState("order-42", false, false);
ExecutionResult<OrderState> r = graph.run(seed);

System.out.println(r.status());     // COMPLETED
System.out.println(r.finalState()); // OrderState[orderId=order-42, valid=true, charged=true]
System.out.println(r.path());       // [validate, charge]
System.out.println(r.executionId());// e.g. "3fa85f64-5717-4562-b3fc-2c963f66afa6"
System.out.println(r.error());      // Optional.empty()`} />

        <H2 id="result">5. Inspect the result</H2>
        <P><Code>ExecutionResult&lt;S&gt;</Code> is an immutable record. Every field is always present; nothing is null.</P>
        <Table
          headers={['Field', 'Type', 'What it tells you']}
          rows={[
            ['executionId()', 'String', 'UUID correlation handle used for traces, replay, resume, and logs.'],
            ['status()', 'Status', 'COMPLETED, FAILED, or INTERRUPTED.'],
            ['finalState()', 'S', 'The last state value committed by the graph.'],
            ['path()', 'List<String>', 'Ordered names of every node that executed, in order.'],
            ['error()', 'Optional<Throwable>', 'Present and non-empty only when status is FAILED.'],
          ]}
        />

        <H2 id="retries">6. Add retries to side-effecting nodes</H2>
        <P>Attach a <Code>RetryPolicy</Code> directly to any node that performs I/O. Retry behavior is part of the graph definition — not runtime configuration — so it is versioned alongside the graph and visible in traces.</P>
        <Cb file="OrderPipeline.java" src={`import io.tracegraph.core.RetryPolicy;
import java.time.Duration;

Graph<OrderState> graph = Graph.<OrderState>builder()
    .node("validate", (s, ctx) -> s.withValid(true))
    // charge retries up to 3 times with exponential backoff, capped at 2 s
    .node("charge", (s, ctx) -> chargeAndReturn(s),
        RetryPolicy.exponential(3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(2)))
    .entry("validate")
    .edge("validate", "charge", OrderState::valid)
    .terminal("charge")
    .build();`} />

        <H2 id="next">7. Where to go next</H2>
        <Steps items={[
          <>Read the <strong>Your first graph</strong> page for a full five-node order pipeline with async enrichment and parallel fan-out.</>,
          <>Add <Code>RecordingTraceRecorder</Code> and <Code>InMemoryTraceStore</Code> when you want deterministic replay and state diffing in tests.</>,
          <>Wire a <Code>JdbcCheckpointStore</Code> when you need crash-safe resume across process boundaries.</>,
          <>Move to the Spring Boot starter when you want the graph embedded in an application with REST and SSE endpoints.</>,
          <>Explore LLM connectors (<Code>OpenAiLlmClient</Code>, <Code>ChatNode</Code>, <Code>ReActAgent</Code>) when building agentic pipelines.</>,
        ]} />
      </>
    ),
  },

  install: {
    crumb: 'GETTING STARTED / INSTALLATION',
    title: 'Installation',
    lede: 'TraceGraph is published to Maven Central. Install the smallest set of modules you need, or import the BOM when you expect to compose several modules together.',
    toc: [
      { id: 'req', label: 'Requirements' },
      { id: 'maven', label: 'Maven' },
      { id: 'gradle', label: 'Gradle' },
      { id: 'bom', label: 'BOM' },
      { id: 'stacks', label: 'Recommended stacks' },
    ],
    body: () => (
      <>
        <H2 id="req">Requirements</H2>
        <Table headers={['Dependency', 'Version']} rows={[
          ['JDK', '21+ (records, virtual threads, modern language features)'],
          ['Maven', '3.9+'],
          ['SLF4J binding', 'Any binding you already use: logback, log4j2, jul bridge, etc.'],
        ]} />
        <P>TraceGraph is intentionally JVM-first and leans on Java 21 features. If Maven is picking up the wrong JDK, verify <Code>mvn -version</Code> before debugging anything else.</P>

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
        <P>Import <Code>tracegraph-bom</Code> when you want version alignment across core, runtime, memory, observability, and integration modules.</P>
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

        <H2 id="stacks">Recommended starting stacks</H2>
        <Table headers={['Use case', 'Suggested modules']} rows={[
          ['Plain JVM orchestration', 'tracegraph-core'],
          ['Durable long-running workflows', 'tracegraph-core + tracegraph-runtime'],
          ['Replay and production debugging', 'tracegraph-core + tracegraph-observability'],
          ['Spring application with traces and UI', 'tracegraph-core + tracegraph-observability + tracegraph-spring-boot-starter + tracegraph-ui'],
        ]} />
      </>
    ),
  },

  firstgraph: {
    crumb: 'GETTING STARTED / YOUR FIRST GRAPH',
    title: 'Your first graph',
    lede: 'This example moves past the toy two-node flow and shows how a real graph reads: validation, parallel enrichment, async fraud scoring, retries on payment, and a terminal shipping step.',
    toc: [
      { id: 'state', label: 'State record' },
      { id: 'nodes', label: 'Node implementations' },
      { id: 'graph', label: 'Build the graph' },
      { id: 'flow', label: 'Execution flow' },
      { id: 'run', label: 'Run and inspect' },
      { id: 'traces', label: 'Add trace recording' },
    ],
    body: () => (
      <>
        <P>A real agentic pipeline needs more than two nodes. This walkthrough builds a five-stage order processing graph that demonstrates every major node kind: sync, parallel fan-out, async, retriable side effects, and terminals. All five stages share a single typed state record.</P>

        <H2 id="state">Define the state record</H2>
        <P>The state record carries every field that any node will read or write. Even as the graph grows, there is only one state type. Nodes that do not touch certain fields just pass them through unchanged.</P>
        <Cb file="OrderState.java" src={`record OrderState(
    // input fields (populated at run start)
    String  orderId,
    String  customerId,
    double  amount,

    // set by validate node
    boolean valid,
    String  validationError,

    // set by parallel enrich branches
    String  customerProfile,   // from CRM
    double  fraudScore,        // from fraud service
    boolean inventoryAvailable,// from warehouse

    // set by async score node
    double  riskScore,

    // set by charge node
    boolean charged,
    String  chargeId,

    // set by ship node
    boolean shipped,
    String  trackingNumber
) {
    // compact "with" helpers for immutable updates
    OrderState withValid(boolean v, String err)    { /* ... */ return this; }
    OrderState withProfile(String p)               { /* ... */ return this; }
    OrderState withFraudScore(double f)            { /* ... */ return this; }
    OrderState withInventory(boolean inv)          { /* ... */ return this; }
    OrderState withRiskScore(double r)             { /* ... */ return this; }
    OrderState withCharged(boolean c, String id)   { /* ... */ return this; }
    OrderState withShipped(boolean s, String trk)  { /* ... */ return this; }

    static OrderState merge(OrderState base, OrderState branch) {
        // merge partial updates from parallel branches
        return new OrderState(
            base.orderId(), base.customerId(), base.amount(), base.valid(),
            base.validationError(),
            branch.customerProfile() != null ? branch.customerProfile() : base.customerProfile(),
            branch.fraudScore() > 0           ? branch.fraudScore()    : base.fraudScore(),
            branch.inventoryAvailable()       || base.inventoryAvailable(),
            base.riskScore(), base.charged(), base.chargeId(),
            base.shipped(), base.trackingNumber());
    }
}`} />

        <H2 id="nodes">Node implementations</H2>
        <P>Each node is a plain static method or lambda. Keeping node implementations in a separate class makes them independently unit-testable without spinning up a graph.</P>
        <Cb file="OrderNodes.java" src={`public final class OrderNodes {

    // sync: validates the order fields
    public static OrderState validate(OrderState s, Context ctx) {
        if (s.amount() <= 0)
            return s.withValid(false, "Amount must be positive");
        return s.withValid(true, null);
    }

    // parallel branch 1: CRM lookup
    public static OrderState enrichProfile(OrderState s, Context ctx) {
        String profile = crmClient.getProfile(s.customerId());
        return s.withProfile(profile);
    }

    // parallel branch 2: fraud scoring service
    public static OrderState enrichFraud(OrderState s, Context ctx) {
        double score = fraudApi.score(s.customerId(), s.amount());
        return s.withFraudScore(score);
    }

    // parallel branch 3: warehouse inventory check
    public static OrderState enrichInventory(OrderState s, Context ctx) {
        boolean avail = warehouseApi.check(s.orderId());
        return s.withInventory(avail);
    }

    // async: ML risk model over enriched state
    public static CompletableFuture<OrderState> scoreAsync(OrderState s, Context ctx) {
        return CompletableFuture.supplyAsync(() -> {
            double risk = riskModel.score(s.fraudScore(), s.customerProfile());
            return s.withRiskScore(risk);
        }, ctx.executor());
    }

    // retriable: charge the customer
    public static OrderState charge(OrderState s, Context ctx) {
        // idempotencyKey is stable across retry attempts
        ChargeResult cr = paymentGateway.charge(
            s.amount(), ctx.idempotencyKey());
        return s.withCharged(true, cr.chargeId());
    }

    // terminal: hand off to fulfillment
    public static OrderState ship(OrderState s, Context ctx) {
        String tracking = fulfillmentApi.ship(s.orderId(), s.chargeId());
        return s.withShipped(true, tracking);
    }
}`} />

        <H2 id="graph">Build the full graph</H2>
        <P>Wire all five stages together. The graph is compiled once, kept as an application singleton, and called concurrently from many threads.</P>
        <Cb file="OrderPipeline.java" src={`import io.tracegraph.core.Graph;
import io.tracegraph.core.RetryPolicy;
import java.time.Duration;
import java.util.List;

Graph<OrderState> graph = Graph.<OrderState>builder()

    // --- stage 1: validate ---
    .node("validate", OrderNodes::validate)

    // --- stage 2: parallel enrichment ---
    .parallel("enrich",
        List.of(
            OrderNodes::enrichProfile,
            OrderNodes::enrichFraud,
            OrderNodes::enrichInventory),
        // merger: fold all branch outputs into one state (declaration order)
        (input, branches) -> branches.stream().reduce(input, OrderState::merge))

    // --- stage 3: async risk score ---
    .asyncNode("score", OrderNodes::scoreAsync)

    // --- stage 4: charge (retriable) ---
    .node("charge", OrderNodes::charge,
        RetryPolicy.exponential(3, Duration.ofMillis(200), 2.0, Duration.ofSeconds(5)))

    // --- stage 5: ship (terminal) ---
    .node("ship", OrderNodes::ship)

    // wiring
    .entry("validate")
    .edge("validate", "enrich",  OrderState::valid)           // only if valid
    .edge("enrich",   "score")                                // unconditional
    .edge("score",    "charge",  s -> s.riskScore() < 0.7)   // low risk only
    .edge("score",    "reject",  s -> s.riskScore() >= 0.7)  // high risk path
    .node("reject",  (s, ctx) -> s)                          // terminal for high risk
    .terminal("ship")
    .terminal("reject")
    .build();`} />
        <Callout><strong>No unconditional fallthrough.</strong> If validate sets <Code>valid=false</Code> and no edge matches, the run fails with <Code>Status.FAILED</Code> and a routing exception. Always add a fallthrough edge or a rejection terminal when validation can fail.</Callout>

        <H2 id="flow">Execution flow, step by step</H2>
        <Steps items={[
          <><strong>validate</strong> — Runs synchronously. Sets <Code>valid=true</Code> if the order fields are legal, otherwise <Code>valid=false</Code>. The edge to <Code>enrich</Code> only fires when <Code>valid</Code> is true.</>,
          <><strong>enrich</strong> — Fans out to three anonymous branches concurrently on virtual threads. Each branch receives the same post-validate state. Results are merged in declaration order by the provided merger function.</>,
          <><strong>score</strong> — An async node: returns <Code>CompletableFuture&lt;OrderState&gt;</Code>. Checkpoint, retry, and listener behavior are identical to sync nodes.</>,
          <><strong>charge</strong> — Has its own exponential retry policy. <Code>ctx.idempotencyKey()</Code> is stable across attempts so the payment gateway can safely deduplicate.</>,
          <><strong>ship or reject</strong> — Two terminal paths. The risk edge predicate routes to <Code>charge → ship</Code> (low risk) or <Code>reject</Code> (high risk).</>,
        ]} />
        <Table
          headers={['Node', 'Kind', 'Retry policy', 'Produces']}
          rows={[
            ['validate',  'sync',     'none',                   'valid, validationError'],
            ['enrich',    'parallel', 'none',                   'customerProfile, fraudScore, inventoryAvailable'],
            ['score',     'async',    'none',                   'riskScore'],
            ['charge',    'sync',     'exponential(3, 200 ms)', 'charged, chargeId'],
            ['ship',      'sync',     'none',                   'shipped, trackingNumber'],
            ['reject',    'sync',     'none',                   '(terminal, no changes)'],
          ]}
        />

        <H2 id="run">Run and inspect the result</H2>
        <Cb file="Main.java" src={`OrderState seed = new OrderState(
    "ord-1001", "cust-42", 149.99,
    false, null, null, 0, false, 0, false, null, false, null);

ExecutionResult<OrderState> r = graph.run(seed);

System.out.println(r.status());                     // COMPLETED
System.out.println(r.path());                       // [validate, enrich, score, charge, ship]
System.out.println(r.finalState().shipped());       // true
System.out.println(r.finalState().trackingNumber());// "TRK-998877"
System.out.println(r.error());                      // Optional.empty()`} />

        <H2 id="traces">Add trace recording (optional but recommended)</H2>
        <P>Attach a <Code>RecordingTraceRecorder</Code> and a store before the first real run. This costs almost nothing at runtime and unlocks replay, step-by-step inspection, and state diffing with no node code changes.</P>
        <Cb file="OrderPipeline.java" src={`TraceStore<OrderState> store = new InMemoryTraceStore<>();

Graph<OrderState> graph = Graph.<OrderState>builder()
    // ... same nodes and edges as above ...
    .traceRecorder(new RecordingTraceRecorder<>(store))
    .build();

ExecutionResult<OrderState> r = graph.run(seed);

// inspect after the run
ExecutionTrace<OrderState> trace = store.load(r.executionId()).orElseThrow();
trace.steps().forEach(step ->
    System.out.printf("[%s] before=%s after=%s%n",
        step.nodeName(), step.before(), step.after()));`} />
        <Callout><strong>Debugging tip.</strong> Once traces are recorded, switch to <Code>JsonFileTraceStore</Code> or <Code>JdbcTraceStore</Code> in production. The REST layer in the Spring Boot starter exposes them as JSON without any extra code on your part.</Callout>
      </>
    ),
  },

  graphs: {
    crumb: 'CONCEPTS / GRAPHS & NODES',
    title: 'Graphs & nodes',
    lede: 'A graph is compiled control flow. It declares execution boundaries explicitly, and each node is a named transition over the same state type. Everything is validated at build time.',
    toc: [
      { id: 'lifecycle', label: 'Graph lifecycle' },
      { id: 'builder', label: 'Builder API reference' },
      { id: 'kinds', label: 'Node kinds' },
      { id: 'entry', label: 'Entry & terminals' },
      { id: 'validation', label: 'Build-time validation' },
      { id: 'subgraphs', label: 'Subgraphs' },
      { id: 'viz', label: 'Visualization' },
    ],
    body: () => (
      <>
        <P>A <Code>Graph&lt;S&gt;</Code> is an immutable object produced by a builder. Once built, it can be shared across threads and called concurrently with no locking. The builder is the place where you declare all structural decisions: which nodes exist, how they connect, how they recover, what infrastructure surrounds them.</P>

        <H2 id="lifecycle">Graph lifecycle</H2>
        <P>The lifecycle has three distinct phases. Understanding the boundaries between them prevents a large class of runtime surprises.</P>
        <Table
          headers={['Phase', 'When', 'What happens']}
          rows={[
            ['Definition', 'Builder calls (.node, .edge, .entry …)', 'Nodes, edges, and infrastructure wiring are accumulated. No execution yet.'],
            ['Compilation', '.build()', 'All declarations are validated. Unreachable nodes, missing entries, duplicate names, and bad policies are rejected here, not at runtime.'],
            ['Execution', 'graph.run(seed) / graph.resume(id) / graph.stream(seed)', 'Immutable graph object is shared. Each call creates an isolated execution context. Safe for concurrent use.'],
          ]}
        />
        <Cb src={`// Definition + compilation — do this once at startup
Graph<OrderState> graph = Graph.<OrderState>builder()
    .node("validate", OrderNodes::validate)
    .node("charge",   OrderNodes::charge)
    .entry("validate")
    .edge("validate", "charge", OrderState::valid)
    .terminal("charge")
    .build();  // <-- validation happens here

// Execution — safe to call from many threads concurrently
ExecutionResult<OrderState> r1 = graph.run(seed1);
ExecutionResult<OrderState> r2 = graph.run(seed2);`} />

        <H2 id="builder">Builder API reference</H2>
        <Table
          headers={['Method', 'Purpose']}
          rows={[
            ['.node(name, fn)', 'Add a synchronous node.'],
            ['.node(name, fn, policy)', 'Add a synchronous node with a per-node RetryPolicy.'],
            ['.asyncNode(name, fn)', 'Add a node that returns CompletableFuture<S>.'],
            ['.parallel(name, branches, merger)', 'Add a fan-out node with concurrent anonymous branches.'],
            ['.routingNode(name, fn)', 'Add a node that can choose the next node at runtime via NodeResult.goTo(…).'],
            ['.subgraph(name, inner)', 'Embed another compiled Graph<S> as a single node.'],
            ['.entry(name)', 'Declare the single entry node.'],
            ['.edge(from, to)', 'Add an unconditional edge.'],
            ['.edge(from, to, predicate)', 'Add a conditional edge. Predicate receives post-node state.'],
            ['.terminal(name)', 'Mark a node as a terminal. Reaching it ends the run.'],
            ['.defaultRetryPolicy(policy)', 'Set a graph-wide fallback retry policy.'],
            ['.listener(fn)', 'Attach a NodeListener for spans, metrics, or logging.'],
            ['.traceRecorder(recorder)', 'Attach a TraceRecorder for step-by-step replay artifacts.'],
            ['.checkpointStore(store)', 'Attach a CheckpointStore for durable resume.'],
            ['.memoryStore(store)', 'Attach a MemoryStore for cross-run key-value storage.'],
            ['.executor(pool)', 'Supply a custom executor for async and parallel nodes.'],
            ['.interruptBefore(names…)', 'Pause execution before the named nodes.'],
            ['.interruptAfter(names…)', 'Pause execution after the named nodes.'],
            ['.build()', 'Compile and validate. Returns an immutable Graph<S>.'],
          ]}
        />

        <H2 id="kinds">Node kinds</H2>
        <P>All four node kinds compile into the same underlying graph model. They share retry, listener, trace, and checkpoint behavior identically.</P>
        <Table
          headers={['Kind', 'Signature', 'Typical use']}
          rows={[
            ['sync',     '(S, Context) -> S',                              'Pure transformations, database reads, validations.'],
            ['async',    '(S, Context) -> CompletableFuture<S>',           'HTTP calls, LLM completions, anything already async.'],
            ['parallel', 'List<(S,Context)->S>, (S,List<S>)->S',           'Concurrent enrichment: fetch profile + fraud + inventory at once.'],
            ['routing',  '(S, Context) -> NodeResult<S>',                  'Dynamic next-hop selection based on runtime state.'],
          ]}
        />
        <Cb src={`// sync
.node("validate", (s, ctx) -> s.withValid(!s.orderId().isBlank()))

// async
.asyncNode("score", (s, ctx) ->
    CompletableFuture.supplyAsync(() -> callModel(s), ctx.executor()))

// parallel (three branches, then merge)
.parallel("enrich",
    List.of(this::fetchProfile, this::fetchFraud, this::fetchInventory),
    (in, outs) -> outs.stream().reduce(in, OrderState::merge))

// routing — dynamic goTo
.routingNode("route", (s, ctx) -> {
    if (s.needsReview()) return NodeResult.goTo("review", s);
    return NodeResult.of(s);  // fall through to normal edges
})`} />

        <H2 id="entry">Entry and terminals</H2>
        <P>Every compiled graph has exactly one entry node and at least one terminal node. A run ends the moment it reaches any terminal — the rest of the graph is simply not evaluated.</P>
        <UL items={[
          <><Code>.entry("name")</Code> is required. Missing it causes a <Code>GraphValidationException</Code> at build time.</>,
          <>Multiple <Code>.terminal("name")</Code> calls are fine. Use them for success, rejection, and error-handling paths.</>,
          <>The ordered list of executed node names is available as <Code>r.path()</Code> after the run.</>,
          <>A terminal node still runs its node function before the run is marked complete. It just has no outgoing edges.</>,
        ]} />

        <H2 id="validation">Build-time validation</H2>
        <P>TraceGraph validates the graph eagerly at <Code>.build()</Code> so structural mistakes surface during startup, not in production traffic.</P>
        <Table
          headers={['Validation check', 'Exception']}
          rows={[
            ['Duplicate node name',          'GraphValidationException'],
            ['Missing .entry()',              'GraphValidationException'],
            ['Missing .terminal()',           'GraphValidationException'],
            ['Edge referencing unknown node', 'GraphValidationException'],
            ['Node with no outgoing edges and not a terminal', 'GraphValidationException'],
          ]}
        />

        <H2 id="subgraphs">Subgraphs</H2>
        <P>Embed a compiled graph as a single logical node with <Code>.subgraph(name, innerGraph)</Code>. Both graphs must share the same state type <Code>S</Code>. The parent trace records one step whose <Code>children</Code> field is populated by the inner execution.</P>
        <Cb src={`Graph<OrderState> validationGraph = Graph.<OrderState>builder()
    .node("checkSchema",  Validators::checkSchema)
    .node("checkLimits",  Validators::checkLimits)
    .entry("checkSchema")
    .edge("checkSchema", "checkLimits")
    .terminal("checkLimits")
    .build();

Graph<OrderState> mainGraph = Graph.<OrderState>builder()
    .subgraph("validate", validationGraph)   // embedded
    .node("charge", OrderNodes::charge)
    .entry("validate")
    .edge("validate", "charge", OrderState::valid)
    .terminal("charge")
    .build();`} />
        <Callout><strong>Resume limitation.</strong> Resuming a parent execution that was interrupted mid-subgraph is not supported. The subgraph always re-runs from its own entry on resume.</Callout>

        <H2 id="viz">Structural visualization</H2>
        <P><Code>Graph.toMermaid()</Code> and <Code>Graph.toPlantUml()</Code> render the structural definition without any deps or execution. Paste the output into Mermaid Live or PlantUML to get an instant diagram. Subgraphs appear as nested clusters.</P>
        <Cb src={`String mermaid  = graph.toMermaid();
String plantuml = graph.toPlantUml();`} />
      </>
    ),
  },

  state: {
    crumb: 'CONCEPTS / STATE & CONTEXT',
    title: 'State & context',
    lede: 'State is the typed execution value that flows through the graph. Context is the per-node toolbox: correlation IDs, scoped memory, executor access, and usage reporting.',
    toc: [
      { id: 'state', label: 'State record design' },
      { id: 'immutability', label: 'Immutability' },
      { id: 'ctx', label: 'Context reference' },
      { id: 'idempotency', label: 'Idempotency keys' },
      { id: 'memory', label: 'State vs memory' },
      { id: 'testing', label: 'Testing nodes' },
    ],
    body: () => (
      <>
        <P>Every node in a TraceGraph takes the current state and returns the next state. That single contract makes execution transparent, replay deterministic, and diffing trivial. State carries what matters to the current run; context carries what the runtime provides around it.</P>

        <H2 id="state">State record design</H2>
        <P>Java records are the recommended state type. They are immutable by construction, serialize cleanly to JSON for traces, and diff naturally across replay runs. Prefer flat fields over nested structures when it simplifies trace readability.</P>
        <Cb file="AgentState.java" src={`// Good: flat, explicit, all fields named after what they represent
record AgentState(
    String  sessionId,
    String  userMessage,
    String  llmResponse,
    boolean toolCallNeeded,
    String  toolName,
    String  toolResult,
    boolean done
) {
    AgentState withLlmResponse(String r)   { return new AgentState(sessionId, userMessage, r, toolCallNeeded, toolName, toolResult, done); }
    AgentState withToolResult(String tr)   { return new AgentState(sessionId, userMessage, llmResponse, false, toolName, tr, done); }
    AgentState withDone()                  { return new AgentState(sessionId, userMessage, llmResponse, false, toolName, toolResult, true); }
}`} />
        <Table
          headers={['Practice', 'Why']}
          rows={[
            ['Use records', 'Immutable by construction, auto-accessors, clean toString in traces.'],
            ['Add with* helpers', 'Keeps node code concise without sacrificing immutability.'],
            ['Flat fields > nested maps', 'Flat fields appear cleanly in state-diff trace events.'],
            ['One state type per graph', 'Avoids the Node<S,R> anti-pattern; sub-results fold back into S.'],
            ['Avoid null for optional values', 'Use explicit boolean flags or empty strings; nulls make diff noisy.'],
          ]}
        />

        <H2 id="immutability">Immutability guarantee</H2>
        <P>Nodes must not mutate the incoming state object. Return a new instance (or the same instance unchanged) from every node. If you keep a mutable collection inside the record, copy it on update.</P>
        <Cb src={`// BAD: mutating inside the node
public static OrderState bad(OrderState s, Context ctx) {
    s.items().add(newItem); // List mutation — breaks replay and parallelism
    return s;
}

// GOOD: copy-on-write
public static OrderState good(OrderState s, Context ctx) {
    var next = new ArrayList<>(s.items());
    next.add(newItem);
    return new OrderState(s.id(), List.copyOf(next));
}`} />

        <H2 id="ctx">Context reference</H2>
        <P><Code>Context</Code> is provided by the runtime on every node invocation. It is per-execution and per-node — never share it across threads or cache it beyond the node call.</P>
        <Table
          headers={['Method', 'Type', 'Description']}
          rows={[
            ['ctx.executionId()',                    'String',      'UUID for the current run. Use in logs, external service calls, and correlation.'],
            ['ctx.idempotencyKey()',                 'String',      'Stable key for the current node + attempt. Safe to forward to payment, email, or HTTP APIs.'],
            ['ctx.memory()',                         'MemoryStore', 'Scoped key-value store for cross-run data. Default is no-op; opt in via .memoryStore(…).'],
            ['ctx.executor()',                       'Executor',    'The run-scoped executor. Use inside asyncNode or parallel branches for any sub-tasks.'],
            ['ctx.reportUsage(prompt, completion)',  'void',        'Report LLM token counts; fires NodeListener.onUsage and records in TraceStep.Usage.'],
          ]}
        />
        <Cb src={`public static AgentState callLlm(AgentState s, Context ctx) {
    // use executionId for distributed tracing correlation
    log.info("run={} calling LLM", ctx.executionId());

    LlmResponse resp = llmClient.complete(buildRequest(s));

    // report usage so OtelNodeListener and LlmCostListener track tokens
    ctx.reportUsage(resp.promptTokens(), resp.completionTokens());

    return s.withLlmResponse(resp.content());
}`} />

        <H2 id="idempotency">Idempotency keys</H2>
        <P>The idempotency key is a stable string derived from the execution ID and the node name. It does not change across retry attempts for the same node in the same run. Pass it as a request header or request body field to any service that supports idempotent operations.</P>
        <Cb src={`public static OrderState charge(OrderState s, Context ctx) {
    // idempotencyKey is the same on attempt 1, 2, 3 …
    // the payment gateway uses it to deduplicate charges
    ChargeResult cr = paymentApi.charge(
        s.amount(),
        Map.of("Idempotency-Key", ctx.idempotencyKey()));
    return s.withCharged(true, cr.chargeId());
}`} />
        <Callout><strong>At-least-once, not exactly-once.</strong> TraceGraph gives you at-least-once execution — retries and resume can re-run a node. The idempotency key is how you make that safe for external side effects. The key is your responsibility to forward; the runtime only provides it.</Callout>

        <H2 id="memory">State vs memory — when to use each</H2>
        <Table
          headers={['Concern', 'Put it in', 'Why']}
          rows={[
            ['Current execution decisions',       'State (S)',   'Appears in traces, replay, and diffs automatically.'],
            ['Cross-run user preferences',         'MemoryStore', 'Persists beyond a single execution; not part of the current trace.'],
            ['Prior conversation history',         'MemoryStore', 'Retrieved at run start, folded into state only if needed for decisions.'],
            ['Shared cross-tenant config',         'MemoryStore', 'Scoped by tenant; not cluttering the execution path.'],
            ['Temporary enrichment for this run',  'State (S)',   'Clear origin, visible in traces, cleaned up naturally at run end.'],
          ]}
        />
        <P>Avoid using memory as a side channel for core graph logic. If a routing decision depends on a memory value, load it into state at the start of the run so it appears in traces and replay sees the same value.</P>

        <H2 id="testing">Testing nodes cleanly</H2>
        <P>Each node is a pure function of state and context. The easiest test is a direct call to the node function with a hand-crafted state and a minimal context stub. Test graph wiring separately with a small integration-style graph test.</P>
        <Cb src={`// unit test the node directly — no graph needed
@Test
void validate_rejectsBlankOrderId() {
    OrderState input = new OrderState("", 149.99, false, false);
    OrderState output = OrderNodes.validate(input, Context.noop());
    assertThat(output.valid()).isFalse();
    assertThat(output.validationError()).isNotBlank();
}

// integration test the graph wiring
@Test
void graph_skipCharge_whenValidationFails() {
    ExecutionResult<OrderState> r = graph.run(new OrderState("", 0, false, false));
    assertThat(r.status()).isEqualTo(Status.FAILED);
    assertThat(r.path()).doesNotContain("charge");
}`} />
      </>
    ),
  },

  edges: {
    crumb: 'CONCEPTS / EDGES & ROUTING',
    title: 'Edges & routing',
    lede: 'Edges are first-class graph data. They make conditional control flow explicit, inspectable in traces, and safely re-evaluatable during replay.',
    toc: [
      { id: 'basics', label: 'Edge basics' },
      { id: 'predicates', label: 'Predicate rules' },
      { id: 'patterns', label: 'Routing patterns' },
      { id: 'dynamic', label: 'Dynamic routing nodes' },
      { id: 'sendfanout', label: 'Send / dynamic fan-out' },
      { id: 'order', label: 'Edge ordering' },
    ],
    body: () => (
      <>
        <P>Edges are how the graph decides what runs next. They are evaluated after every node exit, in declaration order, against the post-node state. The first matching edge wins. If no edge matches and the current node is not a terminal, the run fails with a routing exception.</P>

        <H2 id="basics">Edge basics</H2>
        <Table
          headers={['Form', 'When it fires']}
          rows={[
            ['.edge("a", "b")',             'Always — unconditional. Routes "a" to "b" every time.'],
            ['.edge("a", "b", predicate)',   'When predicate.test(postNodeState) returns true.'],
            ['.terminal("a")',              'Node "a" has no outgoing edges. Reaching it ends the run.'],
          ]}
        />
        <Cb src={`// unconditional
.edge("validate", "enrich")

// conditional — method reference on the state record
.edge("validate", "enrich", OrderState::valid)

// conditional — lambda for compound logic
.edge("score", "approve", s -> s.riskScore() < 0.3 && s.inventoryAvailable())
.edge("score", "review",  s -> s.riskScore() < 0.7)
.edge("score", "reject")   // unconditional fallthrough`} />

        <H2 id="predicates">Predicate rules</H2>
        <P>Predicates must be deterministic, pure functions of state. This is what makes replay and resume correct: the runtime re-evaluates edge predicates from the saved state and gets the same result every time.</P>
        <UL items={[
          <>No I/O, no HTTP calls, no database reads inside a predicate.</>,
          <>No clocks or random values. If timing matters, capture it in state first.</>,
          <>No shared mutable state. Predicates may be called from multiple threads.</>,
          <>Method references on records (<Code>OrderState::valid</Code>) are the clearest form.</>,
        ]} />
        <Callout><strong>Replay correctness.</strong> If a predicate depends only on state, replay and resume re-evaluate it identically. If it depends on external services, the execution path is no longer reproducible and your replay artifacts are untrustworthy.</Callout>

        <H2 id="patterns">Common routing patterns</H2>
        <H3>Linear pipeline</H3>
        <Cb src={`// each step feeds the next unconditionally
.edge("ingest", "normalize")
.edge("normalize", "respond")
.terminal("respond")`} />

        <H3>Binary branch</H3>
        <Cb src={`// two mutually exclusive exits from the same node
.edge("validate", "process", OrderState::valid)
.edge("validate", "reject",  s -> !s.valid())
.terminal("process")
.terminal("reject")`} />

        <H3>Multi-tier classification</H3>
        <Cb src={`// ordered predicates — first match wins
.edge("score", "fastApprove", s -> s.riskScore() < 0.2)
.edge("score", "approve",     s -> s.riskScore() < 0.5)
.edge("score", "review",      s -> s.riskScore() < 0.8)
.edge("score", "reject")       // unconditional fallthrough`} />

        <H3>Loop back (retry at graph level)</H3>
        <Cb src={`// cycle back to a prior node based on state
.edge("respond", "respond", s -> s.needsRefinement() && s.attempts() < 3)
.edge("respond", "done",    s -> !s.needsRefinement() || s.attempts() >= 3)
.terminal("done")`} />

        <H2 id="dynamic">Dynamic routing with RoutingNode</H2>
        <P>When the next node name itself is unknown at graph-definition time — for example, it depends on a runtime value in state — use a routing node. <Code>NodeResult.goTo(name, state)</Code> bypasses edge evaluation entirely and routes to the named node directly.</P>
        <Cb src={`// routing node selects the next hop at runtime
.routingNode("dispatch", (s, ctx) -> {
    String target = switch (s.orderType()) {
        case "digital"  -> "fulfillDigital";
        case "physical" -> "fulfillPhysical";
        default         -> "fulfillManual";
    };
    return NodeResult.goTo(target, s);
})

// all possible targets must still be declared as nodes
.node("fulfillDigital",  DigitalFulfillment::process)
.node("fulfillPhysical", PhysicalFulfillment::process)
.node("fulfillManual",   ManualFulfillment::process)
.terminal("fulfillDigital")
.terminal("fulfillPhysical")
.terminal("fulfillManual")`} />
        <P>An unknown target name in <Code>goTo</Code> throws <Code>NodeExecutionException</Code> at runtime. All candidate node names must still be declared in the builder.</P>

        <H2 id="sendfanout">Send / dynamic fan-out</H2>
        <P>For cases where the number of parallel branches is only known at runtime, <Code>NodeResult.sendAll(sends, merger, currentState)</Code> spawns N parallel executions of target nodes with different payloads, then merges them.</P>
        <Cb src={`// routing node that fans out dynamically based on runtime data
.routingNode("fanout", (s, ctx) -> {
    List<Send<BatchState>> sends = s.items().stream()
        .map(item -> new Send<>("processItem", s.withCurrentItem(item)))
        .toList();
    return NodeResult.sendAll(sends, BatchState::merge, s);
})
.node("processItem", BatchProcessor::process)
.terminal("processItem")`} />

        <H2 id="order">Edge ordering matters</H2>
        <P>Edges from the same source node are evaluated in declaration order. The first predicate that returns true wins. Unconditional edges should be declared last as fallthrough paths.</P>
        <Cb src={`// CORRECT: specific before general
.edge("score", "fastApprove", s -> s.riskScore() < 0.1)
.edge("score", "approve",     s -> s.riskScore() < 0.5)
.edge("score", "reject")   // catches everything else

// BUG: unconditional edge declared first — fastApprove never reached
// .edge("score", "reject")
// .edge("score", "fastApprove", s -> s.riskScore() < 0.1)`} />
      </>
    ),
  },

  parallel: {
    crumb: 'CONCEPTS / ASYNC & PARALLEL',
    title: 'Async & parallel',
    lede: 'TraceGraph uses virtual-thread-per-task execution by default. Async nodes and parallel fan-out behave identically to sync nodes from the graph perspective — same retry, checkpoint, and listener semantics.',
    toc: [
      { id: 'async', label: 'asyncNode' },
      { id: 'parallel', label: 'parallel fan-out' },
      { id: 'merger', label: 'Writing a safe merger' },
      { id: 'errors', label: 'Error semantics' },
      { id: 'executor', label: 'Executor lifecycle' },
      { id: 'gotchas', label: 'Constraints & gotchas' },
    ],
    body: () => (
      <>
        <P>Most LLM pipelines involve multiple concurrent calls: fetch the user profile while scoring fraud while checking inventory. TraceGraph models this as a first-class <Code>parallel</Code> node that fans out, runs branches on virtual threads, then merges results back into a single state value before the next node runs.</P>

        <H2 id="async">asyncNode</H2>
        <P>Use an async node when the work is naturally expressed as a <Code>CompletableFuture</Code> — for example, an HTTP client that returns futures, or a task you want to submit to <Code>ctx.executor()</Code>. From the graph's perspective an async node is still one named step: it gets one trace entry, one OTel span, and retries wrap the whole future.</P>
        <Cb src={`// async LLM call — returns CompletableFuture<AgentState>
.asyncNode("llm", (s, ctx) ->
    CompletableFuture.supplyAsync(() -> {
        LlmResponse r = llmClient.complete(buildRequest(s));
        ctx.reportUsage(r.promptTokens(), r.completionTokens());
        return s.withLlmResponse(r.content());
    }, ctx.executor()))

// async HTTP enrichment
.asyncNode("profile", (s, ctx) ->
    httpClient.sendAsync(buildProfileRequest(s), HttpResponse.BodyHandlers.ofString())
              .thenApply(res -> s.withProfile(parseProfile(res.body()))))`} />
        <Table
          headers={['Aspect', 'Behavior']}
          rows={[
            ['Retry policy', 'Wraps the whole future. If the future completes exceptionally it is eligible for retry.'],
            ['Checkpoint', 'Written after the future resolves, same as sync nodes.'],
            ['NodeListener', 'onEnter fires before submission; onExit fires after the future resolves.'],
            ['OTel span', 'One span per async node, spanning the full future lifecycle.'],
          ]}
        />

        <H2 id="parallel">parallel fan-out</H2>
        <P>All branches receive the same input state. They run concurrently on the configured executor. Results arrive in declaration order (not completion order) and are passed to your merger function.</P>
        <Cb src={`.parallel("enrich",
    List.of(
        // branch 1: CRM
        (s, ctx) -> {
            String profile = crmClient.get(s.customerId());
            return s.withProfile(profile);
        },
        // branch 2: fraud service
        (s, ctx) -> {
            double score = fraudApi.score(s.customerId(), s.amount());
            return s.withFraudScore(score);
        },
        // branch 3: warehouse
        (s, ctx) -> {
            boolean avail = warehouseApi.check(s.orderId());
            return s.withInventory(avail);
        }
    ),
    // merger receives (inputState, List<branchOutputState>) in declaration order
    (input, outs) -> outs.stream().reduce(input, OrderState::merge)
)`} />

        <H2 id="merger">Writing a safe merger</H2>
        <P>The merger is called on the graph thread after all branches complete. It must be a pure function — no I/O, no side effects. Its job is to collapse N partial states into one.</P>
        <Cb src={`// pattern 1: reduce with a merge helper on the record
(input, outs) -> outs.stream().reduce(input, OrderState::merge)

// pattern 2: field-by-field combine
(input, outs) -> new OrderState(
    input.orderId(),
    input.customerId(),
    input.amount(),
    input.valid(),
    input.validationError(),
    outs.get(0).customerProfile(),    // branch 0
    outs.get(1).fraudScore(),          // branch 1
    outs.get(2).inventoryAvailable(), // branch 2
    input.riskScore(), input.charged(), input.chargeId(),
    input.shipped(), input.trackingNumber()
)`} />
        <Callout><strong>Declaration order is stable.</strong> The <Code>List&lt;S&gt;</Code> passed to the merger always matches the declaration order of branches, regardless of which branch finished first. Index 0 is always the first branch you declared.</Callout>

        <H2 id="errors">Error semantics</H2>
        <P>If any branch throws an exception, the parallel node is considered failed. The first failure (by declaration order) wins. Other branches that are still running are cancelled. The normal retry policy applies to the whole parallel node — all branches re-run from the start on retry.</P>
        <Table
          headers={['Scenario', 'Outcome']}
          rows={[
            ['All branches succeed',                      'Merger runs. Normal edge resolution continues.'],
            ['One branch throws, no retry policy',         'Run fails immediately with the branch exception.'],
            ['One branch throws, retry policy present',    'All branches re-run from the start (attempt N+1).'],
            ['All branches throw',                         'First-declared exception surfaces.'],
          ]}
        />

        <H2 id="executor">Executor lifecycle</H2>
        <Table
          headers={['Executor source', 'Lifecycle']}
          rows={[
            ['Default (virtual-thread-per-task)', 'Created lazily per run; shut down automatically when the run completes.'],
            ['User-supplied via .executor(pool)',  'Never shut down by the graph. Your responsibility to manage its lifecycle.'],
          ]}
        />
        <Cb src={`// share a pool across many graph runs
ExecutorService sharedPool = Executors.newVirtualThreadPerTaskExecutor();

Graph<MyState> graph = Graph.<MyState>builder()
    // ...
    .executor(sharedPool)   // graph uses this, never shuts it down
    .build();

// shut it down at application close
Runtime.getRuntime().addShutdownHook(new Thread(sharedPool::shutdown));`} />
        <P>Inside a node, always use <Code>ctx.executor()</Code> for submitting sub-tasks rather than creating a new executor. This keeps node work aligned with the graph's configured strategy.</P>

        <H2 id="gotchas">Constraints and gotchas</H2>
        <UL items={[
          <>Branches are <strong>anonymous</strong>: they do not appear in <Code>r.path()</Code>, do not fire <Code>NodeListener</Code> callbacks, and do not produce individual trace steps.</>,
          <>Branches must not share mutable state. Each branch gets the same input state value, but they run concurrently.</>,
          <>Per-branch interrupts inside <Code>parallel</Code> are not supported. Interrupt points must be at named nodes outside the parallel block.</>,
          <>Do not use <Code>synchronized</Code> over blocking I/O inside a branch — that pins the carrier thread. Use <Code>ReentrantLock</Code> if coordination is needed.</>,
          <>The <Code>parallel</Code> node itself counts as one step in the path and one OTel span.</>,
        ]} />
      </>
    ),
  },

  retries: {
    crumb: 'RUNTIME / RETRIES',
    title: 'Retries',
    lede: 'Retry policy is part of the graph definition, not runtime configuration. That keeps failure behavior explicit, versioned alongside the graph, and reproducible across environments.',
    toc: [
      { id: 'policy', label: 'RetryPolicy factories' },
      { id: 'pernode', label: 'Per-node vs default' },
      { id: 'wiring', label: 'Wiring examples' },
      { id: 'listener', label: 'Observing retries' },
      { id: 'semantics', label: 'Failure semantics' },
      { id: 'tracing', label: 'Retries in traces' },
    ],
    body: () => (
      <>
        <P>Every node in a TraceGraph is surrounded by the same retry machinery. You attach a policy when declaring a node, or set a graph-wide default as a fallback. Policies are pure data — no threads, no schedulers visible to you. The executor handles backoff and fires listener callbacks on each attempt.</P>

        <H2 id="policy">RetryPolicy factories</H2>
        <Table
          headers={['Factory', 'Parameters', 'Use when']}
          rows={[
            ['RetryPolicy.none()',                              '—',                                     'Disable retries explicitly. Good as a default when you want opt-in only.'],
            ['RetryPolicy.fixed(maxAttempts, delay)',           'int, Duration',                         'Uniform delay. Predictable total wait time.'],
            ['RetryPolicy.exponential(max, init, mult, cap)',   'int, Duration, double, Duration',       'Exponential backoff with a ceiling. Best for transient HTTP or payment errors.'],
          ]}
        />
        <Cb src={`import io.tracegraph.core.RetryPolicy;
import java.time.Duration;

// fixed: retry up to 2 times, wait 500 ms between each
RetryPolicy fixed = RetryPolicy.fixed(2, Duration.ofMillis(500));

// exponential: up to 4 attempts, starting at 100 ms, doubling each time, capped at 4 s
// delays: 100 ms → 200 ms → 400 ms → (would be 800 ms but capped at 400 ms by max)
// total max wait ≈ 700 ms before the 4th attempt
RetryPolicy exponential = RetryPolicy.exponential(
    4,                          // maxAttempts (including the first attempt)
    Duration.ofMillis(100),     // initialDelay
    2.0,                        // multiplier
    Duration.ofSeconds(4)       // maxDelay cap
);`} />

        <H2 id="pernode">Per-node vs graph-wide default</H2>
        <P>Per-node policy always wins over the graph default. This lets you apply conservative defaults everywhere while giving payment, LLM, or third-party API nodes their own tailored behavior.</P>
        <Table
          headers={['Priority', 'How set', 'Wins over']}
          rows={[
            ['1 (highest)', '.node(name, fn, policy)',      'Graph default, library default.'],
            ['2',           '.defaultRetryPolicy(policy)',  'Library default (no retries).'],
            ['3 (lowest)',   'Library default',             'Nothing — this is RetryPolicy.none().'],
          ]}
        />

        <H2 id="wiring">Wiring examples</H2>
        <Cb src={`Graph<OrderState> graph = Graph.<OrderState>builder()

    // pure computation — no retries needed
    .node("validate", OrderNodes::validate)

    // network call — moderate fixed retry
    .node("profile", ProfileClient::fetch,
        RetryPolicy.fixed(2, Duration.ofMillis(300)))

    // payment — aggressive exponential with idempotency key
    .node("charge", OrderNodes::charge,
        RetryPolicy.exponential(4, Duration.ofMillis(200), 2.0, Duration.ofSeconds(5)))

    // LLM call — allow a couple of retries for transient overload
    .node("llm", LlmNodes::call,
        RetryPolicy.exponential(3, Duration.ofMillis(500), 2.0, Duration.ofSeconds(10)))

    // set a default for any node that doesn't have its own policy
    .defaultRetryPolicy(RetryPolicy.fixed(1, Duration.ofMillis(100)))

    .entry("validate")
    // ... edges ...
    .build();`} />

        <H2 id="listener">Observing retries</H2>
        <P><Code>NodeListener.onRetry(nodeName, attemptNumber, cause)</Code> fires before each retry attempt (attempt 2, 3, …). Use it for metrics, circuit breakers, or alert thresholds.</P>
        <Cb src={`NodeListener retryMetrics = new NodeListener() {
    @Override
    public void onRetry(String node, int attempt, Throwable cause) {
        meterRegistry.counter("graph.node.retry",
            "node", node, "attempt", String.valueOf(attempt))
            .increment();
        log.warn("node={} attempt={} cause={}", node, attempt, cause.getMessage());
    }
};

Graph<MyState> graph = Graph.<MyState>builder()
    // ...
    .listener(retryMetrics)
    .build();`} />
        <P>When <Code>OtelNodeListener</Code> is attached, retries appear as span events on the same node span — not as separate spans. Each event carries the attempt number and exception details.</P>

        <H2 id="semantics">Failure semantics</H2>
        <UL items={[
          <>If all attempts are exhausted, the run fails at that node. <Code>r.status()</Code> returns <Code>FAILED</Code> and <Code>r.error()</Code> carries the last exception.</>,
          <><Code>Error</Code> (e.g. <Code>OutOfMemoryError</Code>) always short-circuits retries and propagates immediately.</>,
          <><Code>InterruptedException</Code> always short-circuits retries and propagates immediately.</>,
          <>The node function receives no indication of which attempt it is on. Use <Code>ctx.idempotencyKey()</Code> to make side effects safe across attempts.</>,
        ]} />
        <Callout><strong>At-least-once, not exactly-once.</strong> Retries can re-execute a node's side effects. Forward <Code>ctx.idempotencyKey()</Code> to payment, email, or messaging APIs that support it. The key is stable across all retry attempts for the same node in the same run.</Callout>

        <H2 id="tracing">Retries in trace artifacts</H2>
        <P><Code>TraceStep.attempts()</Code> records how many attempts a node took. You can inspect this after the run without needing to parse logs.</P>
        <Cb src={`ExecutionTrace<OrderState> trace = store.load(r.executionId()).orElseThrow();
trace.steps().forEach(step -> {
    if (step.attempts() > 1) {
        System.out.printf("[%s] took %d attempts%n", step.nodeName(), step.attempts());
    }
});`} />
      </>
    ),
  },

  checkpoints: {
    crumb: 'RUNTIME / CHECKPOINTS',
    title: 'Checkpoints & resume',
    lede: 'Checkpointing turns graph progress into a durable artifact. Resume uses that artifact to continue a run after process restarts, deploys, or intentional interruptions.',
    toc: [
      { id: 'model', label: 'Checkpoint model' },
      { id: 'store', label: 'CheckpointStore' },
      { id: 'jdbc', label: 'JDBC store' },
      { id: 'resume', label: 'Resume behavior' },
      { id: 'idem', label: 'Idempotency' },
      { id: 'ops', label: 'Operational guidance' },
    ],
    body: () => (
      <>
        <P>Without checkpointing, a graph run is transient — if the process dies, all progress is lost. With a durable <Code>CheckpointStore</Code>, each successfully completed node is persisted. A subsequent <Code>graph.resume(executionId)</Code> call picks up from the last saved boundary and continues forward.</P>

        <H2 id="model">The checkpoint model</H2>
        <P>Checkpoints are written at a specific point in the execution cycle: <em>after</em> the node function returns successfully, but <em>before</em> outgoing edges are evaluated. This gives you a clean boundary — if a crash happens inside a node body, the node re-runs from scratch on resume.</P>
        <Table
          headers={['Event', 'Checkpoint written?', 'Notes']}
          rows={[
            ['Node function returns successfully',   'Yes',  'State and lastCompletedNode are persisted.'],
            ['Node function throws (retryable)',      'No',   'Node will retry. Checkpoint not updated.'],
            ['Node function throws (exhausted)',      'No',   'Run fails. Last successful checkpoint remains.'],
            ['Process crash mid-node-body',           'No',   'On resume, the node re-runs from attempt 1.'],
            ['interruptBefore node',                  'Yes',  'Checkpoint carries interruptPending=true.'],
            ['interruptAfter node',                   'Yes',  'Normal checkpoint after the node completes.'],
          ]}
        />
        <Callout><strong>At-least-once guarantee.</strong> If a crash occurs during a node body — after external side effects but before the node returns — the node re-runs on resume. Use <Code>ctx.idempotencyKey()</Code> to make external calls safe.</Callout>

        <H2 id="store">CheckpointStore implementations</H2>
        <Table
          headers={['Class', 'Suitable for']}
          rows={[
            ['(default no-op)',          'No checkpointing. Default when .checkpointStore(…) is not called.'],
            ['InMemoryCheckpointStore',  'Tests, local development, and short-lived process runs.'],
            ['JdbcCheckpointStore',      'Production. Persists across process restarts. Supports any JDBC datasource.'],
          ]}
        />
        <Cb src={`// In-memory — good for tests
.checkpointStore(new InMemoryCheckpointStore())

// Use it in tests
@Test
void resumeAfterChargeFailure() {
    var store = new InMemoryCheckpointStore<OrderState>();
    var graph = buildGraph(store);

    // first run — simulate crash after validate
    ExecutionResult<OrderState> r1 = graph.run(seed);
    // ... assert intermediate state ...

    // resume
    ExecutionResult<OrderState> r2 = graph.resume(r1.executionId());
    assertThat(r2.status()).isEqualTo(Status.COMPLETED);
}`} />

        <H2 id="jdbc">JDBC store — production setup</H2>
        <P><Code>JdbcCheckpointStore</Code> writes a single table. The upsert is wrapped in a transaction using a portable UPDATE-then-INSERT pattern that works on PostgreSQL, MySQL, and H2.</P>
        <Cb src={`import io.tracegraph.runtime.checkpoint.JdbcCheckpointStore;

// call initSchema() once at startup (idempotent)
JdbcCheckpointStore<OrderState> store =
    new JdbcCheckpointStore<>(dataSource, OrderState.class);
store.initSchema();   // creates tracegraph_checkpoint table if absent

Graph<OrderState> graph = Graph.<OrderState>builder()
    // ...
    .checkpointStore(store)
    .build();`} />
        <P>The table schema is a single row per <Code>executionId</Code> with columns for the serialized state, the last completed node name, status, and interrupt flags. Jackson is used for state serialization (optional dependency).</P>

        <H2 id="resume">How resume works, step by step</H2>
        <Steps items={[
          <>Call <Code>graph.resume(executionId)</Code>. The store loads the checkpoint for that ID.</>,
          <>The runtime reads <Code>lastCompletedNode</Code> from the checkpoint.</>,
          <>Outgoing edges of <Code>lastCompletedNode</Code> are re-evaluated against the saved state. This is a re-evaluation, not a re-execution of the node body.</>,
          <>Execution continues forward from the resolved next node.</>,
          <>Each subsequent node writes a new checkpoint on success, advancing the resume boundary.</>,
        ]} />
        <Cb src={`// first run — may be interrupted or killed partway through
ExecutionResult<OrderState> r1 = graph.run(seed);
String id = r1.executionId();

// later — after restart or interrupt resolution
ExecutionResult<OrderState> r2 = graph.resume(id);
System.out.println(r2.status());     // COMPLETED
System.out.println(r2.path());       // only the nodes that ran in r2`} />

        <H2 id="idem">Making resume safe with idempotency keys</H2>
        <P>Because a crash mid-node causes a re-run on resume, any node with external side effects must be idempotent. The idempotency key is the mechanism.</P>
        <Cb src={`public static OrderState charge(OrderState s, Context ctx) {
    // ctx.idempotencyKey() is the same whether this is the first attempt
    // or a re-run caused by resume after a crash
    ChargeResult cr = paymentApi.charge(
        s.amount(),
        Map.of("Idempotency-Key", ctx.idempotencyKey()));
    return s.withCharged(true, cr.chargeId());
}`} />

        <H2 id="ops">Operational guidance</H2>
        <UL items={[
          <>Always use <Code>JdbcCheckpointStore</Code> (or equivalent durable store) for production long-running flows.</>,
          <>Call <Code>store.initSchema()</Code> once at startup. It is idempotent — safe to call every deployment.</>,
          <>Checkpoint rows are not automatically cleaned up. Add a TTL-based purge job for old completed runs.</>,
          <>Edge predicates must be pure functions of state. Resume re-evaluates them — side effects in predicates break resume correctness.</>,
          <>Do not change the graph structure (rename nodes, remove nodes) while runs may be in a resumed state without a migration plan.</>,
        ]} />
      </>
    ),
  },

  memory: {
    crumb: 'RUNTIME / MEMORY',
    title: 'Memory',
    lede: 'MemoryStore is scoped cross-execution key-value storage. Use it when information should outlive one run but does not belong inside the current execution state.',
    toc: [
      { id: 'concept', label: 'Concept' },
      { id: 'api', label: 'API reference' },
      { id: 'impls', label: 'Implementations' },
      { id: 'jdbc', label: 'JDBC store' },
      { id: 'scope', label: 'Scope design' },
      { id: 'spring', label: 'Spring Boot auto-config' },
    ],
    body: () => (
      <>
        <P>Working memory is the state object itself — it flows through nodes and is committed to traces. <Code>MemoryStore</Code> is for data that crosses run boundaries: conversation history, user preferences, cached embeddings, or shared cross-execution artifacts. It is a simple scoped key-value interface that nodes access through <Code>ctx.memory()</Code>.</P>

        <H2 id="concept">When to use memory vs state</H2>
        <Table
          headers={['What', 'Put it in', 'Why']}
          rows={[
            ['Current run decisions',         'State (S)',    'Appears in traces and replay automatically.'],
            ['Prior conversation turns',       'MemoryStore', 'Outlives the current run; loaded at start, not cluttering the trace.'],
            ['User preferences / settings',   'MemoryStore', 'Long-lived, cross-session, user-scoped.'],
            ['Cached LLM embeddings',          'MemoryStore', 'Expensive to recompute; shared across runs.'],
            ['Tenant-level shared config',     'MemoryStore', 'Multi-instance; needs explicit scope isolation.'],
          ]}
        />

        <H2 id="api">API reference</H2>
        <P>All operations are synchronous. <Code>scope</Code> is a namespace string; <Code>key</Code> is the key within that scope. Values are stored and retrieved with type information preserved.</P>
        <Cb src={`// put — store any serializable value
ctx.memory().put("session:user-123", "lastIntent", "book_flight");
ctx.memory().put("session:user-123", "history",    List.of("msg1", "msg2"));

// get — returns Optional<T>; empty if key absent
Optional<String> intent = ctx.memory()
    .get("session:user-123", "lastIntent", String.class);

// delete — remove a single key
ctx.memory().delete("session:user-123", "lastIntent");

// keys — list all keys in a scope
List<String> keys = ctx.memory().keys("session:user-123");`} />
        <Callout><strong>Scope namespacing.</strong> There is no built-in hierarchy. Use composite scope strings like <Code>{"\"user:{id}\""}</Code>, <Code>{"\"tenant:{id}:session:{id}\""}</Code>, or <Code>{"\"workflow:{type}\""}</Code> to create clean isolation.</Callout>

        <H2 id="impls">Implementations</H2>
        <Table
          headers={['Class', 'Module', 'Suitable for']}
          rows={[
            ['MemoryStore.noop()',      'tracegraph-core',   'Default. Discards all writes, returns empty on reads.'],
            ['InMemoryMemoryStore',    'tracegraph-memory', 'Tests and local dev. ConcurrentHashMap per scope.'],
            ['FileMemoryStore',        'tracegraph-memory', 'Single-process persistence. One JSON file per {scope}/{key}.'],
            ['JdbcMemoryStore',        'tracegraph-memory', 'Production. Transactional, multi-instance safe.'],
          ]}
        />
        <Cb src={`// in-memory — good for tests
.memoryStore(new InMemoryMemoryStore())

// file — single process persistence
.memoryStore(FileMemoryStore.of(Path.of("/var/data/memory")))

// JDBC — production
JdbcMemoryStore jdbcStore = new JdbcMemoryStore(dataSource);
jdbcStore.initSchema();   // idempotent — creates tracegraph_memory table
.memoryStore(jdbcStore)`} />

        <H2 id="jdbc">JDBC store details</H2>
        <P><Code>JdbcMemoryStore</Code> uses a single table with a composite <Code>(scope, key_name)</Code> primary key and a <Code>value_json</Code> column. Jackson default-typing-as-property is used so heterogeneous value types round-trip correctly. Writes use a portable UPDATE-then-INSERT upsert inside a transaction.</P>
        <Cb file="application.yml" lang="yaml" src={`# Managed automatically by Spring Boot starter when DataSource is present
tracegraph:
  memory:
    jdbc:
      enabled: true          # default true when DataSource + JdbcMemoryStore on classpath
      init-schema: true      # run initSchema() on startup
      table: tracegraph_memory  # override table name`} />
        <P>Persistence failures surface as <Code>MemoryPersistenceException</Code> — a <Code>RuntimeException</Code> that propagates out of the node and is eligible for retry if a policy is attached.</P>

        <H2 id="scope">Scope design patterns</H2>
        <UL items={[
          <><Code>{"\"user:{userId}\""}</Code> — long-lived per-user preferences and history.</>,
          <><Code>{"\"session:{sessionId}\""}</Code> — short-lived conversational context. Purge after the conversation ends.</>,
          <><Code>{"\"tenant:{tenantId}:config\""}</Code> — shared tenant-level configuration.</>,
          <><Code>{"\"workflow:{executionId}\""}</Code> — per-run scratch space for data too large to put in state.</>,
        ]} />
        <Callout><strong>Path traversal guard.</strong> Scope and key strings are validated by <Code>FileMemoryStore</Code> and <Code>JdbcMemoryStore</Code> against path traversal patterns (<Code>/</Code>, <Code>\</Code>, <Code>..</Code>). Keep scope and key names simple alphanumeric strings or use colons as separators.</Callout>

        <H2 id="spring">Spring Boot auto-configuration</H2>
        <P>The <Code>MemoryAutoConfiguration</Code> in the starter automatically registers a <Code>JdbcMemoryStore</Code> bean when a <Code>DataSource</Code> bean is present and <Code>tracegraph-memory</Code> is on the classpath. It runs before the main <Code>TraceGraphAutoConfiguration</Code> so it wins over the no-op default. Provide your own <Code>@Bean MemoryStore</Code> to override.</P>
      </>
    ),
  },

  interrupts: {
    crumb: 'RUNTIME / INTERRUPTS & HITL',
    title: 'Interrupts & HITL',
    lede: 'Interrupts let a graph pause at named execution boundaries for human approval, content review, or external control, then resume from the checkpointed state later.',
    toc: [
      { id: 'concept', label: 'HITL concept' },
      { id: 'before-after', label: 'interruptBefore vs interruptAfter' },
      { id: 'declare', label: 'Declaring interrupts' },
      { id: 'detect', label: 'Detecting interruption' },
      { id: 'resume', label: 'Resume flow' },
      { id: 'rest', label: 'REST API' },
    ],
    body: () => (
      <>
        <P>Human-in-the-loop (HITL) flows require the graph to pause, store its state durably, surface the pending decision to a human interface, then continue when a decision arrives. TraceGraph models this as a first-class feature: declare interrupt points in the builder, check <Code>Status.INTERRUPTED</Code> at runtime, and call <Code>graph.resume(id)</Code> when ready.</P>

        <H2 id="before-after">interruptBefore vs interruptAfter</H2>
        <Table
          headers={['Method', 'Pauses', 'Checkpoint carries', 'Resume continues from']}
          rows={[
            ['.interruptBefore("node")', 'Before the named node runs', 'interruptPending=true, lastCompletedNode=previous', 'The named node (runs it for the first time)'],
            ['.interruptAfter("node")',  'After the named node runs',  'Normal checkpoint after node exit',                'Next node after edge resolution'],
          ]}
        />
        <P>Use <Code>interruptBefore</Code> when a human should see the state and approve before the node runs (e.g. before calling an external API or sending a message). Use <Code>interruptAfter</Code> when a human should review the node's output before the graph continues.</P>

        <H2 id="declare">Declaring interrupt points</H2>
        <Cb file="ApprovalGraph.java" src={`import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import io.tracegraph.runtime.checkpoint.JdbcCheckpointStore;

// Set up durable store — required for HITL to survive process restarts
JdbcCheckpointStore<ApprovalState> store = new JdbcCheckpointStore<>(dataSource, ApprovalState.class);
store.initSchema();

Graph<ApprovalState> graph = Graph.<ApprovalState>builder()
    .node("analyze",  AnalysisNodes::analyze)
    .node("approve",  ApprovalNodes::recordApproval)  // human sets approved=true before resume
    .node("execute",  ExecutionNodes::execute)
    .node("rejected", RejectionNodes::notify)

    .entry("analyze")
    .edge("analyze", "approve")
    .edge("approve", "execute",  ApprovalState::approved)
    .edge("approve", "rejected", s -> !s.approved())
    .terminal("execute")
    .terminal("rejected")

    // pause before "approve" — human reviews the analysis result first
    .interruptBefore("approve")

    // durable store is mandatory for HITL
    .checkpointStore(store)
    .build();`} />
        <Callout><strong>A durable checkpoint store is mandatory.</strong> Without one, the interrupt state is lost when the process restarts. Always pair interrupt declarations with a <Code>JdbcCheckpointStore</Code> or equivalent in production.</Callout>

        <H2 id="detect">Detecting interruption in your application</H2>
        <Cb src={`ExecutionResult<ApprovalState> r = graph.run(seed);

if (r.status() == Status.INTERRUPTED) {
    String executionId = r.executionId();
    ApprovalState state = r.finalState();

    // surface the state to a human-facing UI or queue
    approvalQueue.enqueue(new PendingApproval(executionId, state.analysisResult()));

    log.info("Execution {} paused for human approval. Analysis: {}",
        executionId, state.analysisResult());
}
// else: COMPLETED or FAILED — handle normally`} />

        <H2 id="resume">Resume flow after human action</H2>
        <P>When the human approves (or rejects), your application updates external state — or simply calls resume. The graph re-evaluates edges from the interrupt boundary and continues.</P>
        <Cb src={`// approval handler — called by UI or webhook
public void handleApproval(String executionId, boolean approved) {
    // optionally update memory or external state the graph will read on resume
    // then resume — graph re-evaluates edges from the checkpoint
    ExecutionResult<ApprovalState> resumed = graph.resume(executionId);

    if (resumed.status() == Status.COMPLETED) {
        log.info("Execution {} completed after approval", executionId);
    }
}`} />
        <P>The resumed execution picks up exactly where the interrupt left it. Nodes before the interrupt boundary do not re-run. The execution path in <Code>r.path()</Code> after resume includes only the nodes that ran in the second phase.</P>

        <H2 id="rest">REST API for HITL workflows</H2>
        <P>The Spring Boot starter exposes resume as an HTTP endpoint when a single <Code>Graph&lt;?&gt;</Code> bean is registered.</P>
        <Table
          headers={['Method', 'Path', 'Response']}
          rows={[
            ['POST', '/tracegraph/traces/{id}/resume', '200 OK with ExecutionResult JSON; 404 if unknown; 409 if not INTERRUPTED'],
          ]}
        />
        <Cb lang="bash" src={`# Resume a paused execution from a CI/CD script, webhook, or admin UI
curl -X POST http://localhost:8080/tracegraph/traces/3fa85f64-5717.../resume

# Response
{
  "executionId": "3fa85f64-5717-...",
  "status": "COMPLETED",
  "path": ["approve", "execute"]
}`} />
        <UL items={[
          <><strong>404</strong>: The execution ID is not found in the checkpoint store.</>,
          <><strong>409</strong>: The execution exists but its status is not <Code>INTERRUPTED</Code> (already completed, failed, or still running).</>,
          <>Per-branch interrupts inside a <Code>parallel</Code> block are not supported. Place interrupt points at named nodes outside parallel blocks.</>,
        ]} />
      </>
    ),
  },

  otel: {
    crumb: 'OBSERVABILITY / OPENTELEMETRY',
    title: 'OpenTelemetry',
    lede: 'OpenTelemetry support lives in the observability module. It keeps tracegraph-core OTel-free while giving you one span per node, rich retry events, state-change attributes, and LLM token tracking.',
    toc: [
      { id: 'wire', label: 'Wiring' },
      { id: 'spans', label: 'Span structure' },
      { id: 'attrs', label: 'Span attributes' },
      { id: 'retries', label: 'Retries in spans' },
      { id: 'state', label: 'State diffs' },
      { id: 'usage', label: 'LLM token tracking' },
      { id: 'compose', label: 'Composing listeners' },
    ],
    body: () => (
      <>
        <P>OTel tracing is wired through the <Code>NodeListener</Code> SPI. <Code>OtelNodeListener</Code> in <Code>tracegraph-observability</Code> translates node lifecycle events into spans. Core stays OTel-free — no OTel dep unless you explicitly add observability.</P>

        <H2 id="wire">Wiring OtelNodeListener</H2>
        <Cb file="GraphConfig.java" src={`import io.tracegraph.observability.OtelNodeListener;
import io.opentelemetry.api.GlobalOpenTelemetry;

// use the global OpenTelemetry SDK (initialized by your OTel agent or SDK setup)
Graph<MyState> graph = Graph.<MyState>builder()
    // ...
    .listener(OtelNodeListener.usingGlobal())
    .build();

// or supply an explicit OpenTelemetry instance
OpenTelemetry otel = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
Graph<MyState> graph = Graph.<MyState>builder()
    // ...
    .listener(OtelNodeListener.using(otel))
    .build();`} />

        <H2 id="spans">Span structure</H2>
        <P>Each named node gets exactly one span. The span name is the node name. Parallel branches inside a <Code>parallel(…)</Code> block do not get individual spans — the whole parallel node is one span.</P>
        <Table
          headers={['Event', 'Span behavior']}
          rows={[
            ['Node enter (onEnter)',       'Span starts. Attributes: tracegraph.execution.id, tracegraph.node.name.'],
            ['Node exit success (onExit)', 'Span ends with StatusCode.OK.'],
            ['Retry attempt (onRetry)',     'Span event added. No new span. Attributes: attempt number, exception class.'],
            ['Node failure (onError)',      'StatusCode.ERROR set. Span.recordException called. Span ends.'],
            ['State change (onState)',      'Span event "state" added with before/after rendered attributes.'],
            ['Usage (onUsage)',             'Span attributes: llm.usage.input_tokens, llm.usage.output_tokens, llm.usage.total_tokens.'],
          ]}
        />

        <H2 id="attrs">Span attributes reference</H2>
        <Table
          headers={['Attribute', 'Value']}
          rows={[
            ['tracegraph.execution.id', 'The execution UUID for the current run.'],
            ['tracegraph.node.name',    'The node name as declared in the builder.'],
            ['tracegraph.retry.attempt','Retry attempt number (on onRetry events).'],
            ['llm.usage.input_tokens',  'Prompt token count (fired via ctx.reportUsage).'],
            ['llm.usage.output_tokens', 'Completion token count.'],
            ['llm.usage.total_tokens',  'Sum of prompt + completion tokens.'],
          ]}
        />

        <H2 id="retries">Retries appear as span events, not new spans</H2>
        <P>This is intentional. One span per node keeps the trace clean. Each retry attempt is visible as a time-stamped event on the same span with the attempt number and the exception that triggered the retry. You can see exactly how many retries occurred without span explosion.</P>
        <Cb lang="json" src={`{
  "name": "charge",
  "status": "OK",
  "events": [
    { "name": "retry", "attributes": { "attempt": 2, "exception.type": "PaymentGatewayTimeoutException" } },
    { "name": "retry", "attributes": { "attempt": 3, "exception.type": "PaymentGatewayTimeoutException" } }
  ]
}`} />

        <H2 id="state">State diffs in spans</H2>
        <P><Code>NodeListener.onState(nodeName, before, after)</Code> fires once per successful node exit (not on failure, not per retry). <Code>OtelNodeListener</Code> binds this as a <Code>"state"</Code> span event with rendered before/after attributes.</P>
        <P>By default state is rendered with <Code>String::valueOf</Code>. Plug in a custom <Code>StateRenderer</Code> when you need structured attributes or want to redact sensitive fields.</P>
        <Cb src={`// custom renderer — e.g. render only the fields that matter for tracing
OtelNodeListener listener = OtelNodeListener.builder()
    .openTelemetry(otel)
    .stateRenderer((node, state) -> state instanceof OrderState s
        ? "OrderState{id=%s, status=%s}".formatted(s.orderId(), s.status())
        : String.valueOf(state))
    .build();`} />

        <H2 id="usage">LLM token tracking</H2>
        <P>When a node calls <Code>ctx.reportUsage(promptTokens, completionTokens)</Code>, <Code>OtelNodeListener</Code> emits the three token attributes on the current span. <Code>LlmCostListener</Code> (also in observability) accumulates per-node and per-execution totals.</P>
        <Cb src={`import io.tracegraph.observability.LlmCostListener;

LlmCostListener cost = new LlmCostListener();

Graph<AgentState> graph = Graph.<AgentState>builder()
    // ...
    .listener(Listeners.compose(OtelNodeListener.usingGlobal(), cost))
    .build();

graph.run(seed);

// after the run
long totalPrompt     = cost.totalPromptTokens();
long totalCompletion = cost.totalCompletionTokens();
Map<String, long[]> perNode = cost.usageByNode();`} />

        <H2 id="compose">Composing multiple listeners</H2>
        <P>Use <Code>Listeners.compose(a, b, c, …)</Code> to attach multiple listeners. All listeners fire in order for every event.</P>
        <Cb src={`import io.tracegraph.core.spi.Listeners;

NodeListener combined = Listeners.compose(
    OtelNodeListener.usingGlobal(),
    new LlmCostListener(),
    new MetricsNodeListener(meterRegistry)
);

Graph<MyState> graph = Graph.<MyState>builder()
    // ...
    .listener(combined)
    .build();`} />
      </>
    ),
  },

  replay: {
    crumb: 'OBSERVABILITY / REPLAY & DIFF',
    title: 'Replay & diff',
    lede: 'Replay turns a saved execution into a debugging artifact. Inspect each step, fork from any point, and compare two runs to see exactly where behavior diverged.',
    toc: [
      { id: 'record', label: 'Recording traces' },
      { id: 'stores', label: 'Trace stores' },
      { id: 'inspect', label: 'Inspecting a trace' },
      { id: 'diff', label: 'Diffing two runs' },
      { id: 'fork', label: 'Forking from a step' },
      { id: 'lineage', label: 'Fork lineage' },
    ],
    body: () => (
      <>
        <P>Replay is the killer differentiator of TraceGraph. Every execution can be saved as an <Code>ExecutionTrace&lt;S&gt;</Code> — an ordered list of steps each carrying state before and after the node ran. Walk it, diff two traces, or fork from any step to re-run with a modified graph or seed.</P>

        <H2 id="record">Recording traces</H2>
        <P>Attach a <Code>RecordingTraceRecorder</Code> and a <Code>TraceStore</Code> at graph definition time. No node code changes needed.</P>
        <Cb src={`TraceStore<OrderState> store = new InMemoryTraceStore<>();

Graph<OrderState> graph = Graph.<OrderState>builder()
    .node("validate", OrderNodes::validate)
    .node("charge",   OrderNodes::charge)
    .entry("validate")
    .edge("validate", "charge", OrderState::valid)
    .terminal("charge")
    .traceRecorder(new RecordingTraceRecorder<>(store))
    .build();

ExecutionResult<OrderState> r = graph.run(seed);
// trace persisted under r.executionId()`} />

        <H2 id="stores">Trace store options</H2>
        <Table
          headers={['Class', 'Suitable for']}
          rows={[
            ['InMemoryTraceStore',  'Tests and local debugging. Lost on process restart.'],
            ['JsonFileTraceStore',  'Local dev and CI. One JSON file per execution; crash-safe via ATOMIC_MOVE.'],
            ['JdbcTraceStore',      'Production. Queryable, retained across restarts. initSchema() is idempotent.'],
          ]}
        />
        <Cb src={`// JSON file store — constructed with Class<S> for deserialization
TraceStore<OrderState> fileStore =
    JsonFileTraceStore.of(Path.of("/var/traces"), OrderState.class);

// JDBC — single table, portable upsert
JdbcTraceStore<OrderState> jdbcStore = new JdbcTraceStore<>(dataSource, OrderState.class);
jdbcStore.initSchema();`} />
        <Callout><strong>Resume + traces.</strong> When using checkpoints and resume together, the recorder appends to the existing trace for the same execution ID. The full history — both phases — is preserved in one trace object.</Callout>

        <H2 id="inspect">Inspecting a trace</H2>
        <Cb src={`ExecutionTrace<OrderState> trace = store.load(r.executionId()).orElseThrow();

System.out.println("Status: " + trace.status());
trace.steps().forEach(step -> {
    System.out.printf("[%s] attempts=%d before=%s after=%s%n",
        step.nodeName(), step.attempts(), step.before(), step.after());
    step.usage().ifPresent(u -> System.out.printf(
        "  tokens: prompt=%d completion=%d%n", u.promptTokens(), u.completionTokens()));
});`} />
        <Table
          headers={['TraceStep field', 'Type', 'Description']}
          rows={[
            ['nodeName()',  'String',          'Node name.'],
            ['before()',    'S',               'State going into the node.'],
            ['after()',     'S',               'State returned by the node.'],
            ['attempts()',  'int',             '1 = succeeded first try; 2+ = had retries.'],
            ['usage()',     'Optional<Usage>', 'LLM tokens if reported via ctx.reportUsage(…).'],
            ['error()',     'Optional<String>','Error class + message on failure (lossy — no stack trace).'],
          ]}
        />

        <H2 id="diff">Diffing two traces</H2>
        <P><Code>TraceDiff.between(left, right)</Code> computes the longest common prefix matched by node name and before/after equality, the divergence index, and per-side remainders.</P>
        <Cb src={`TraceDiff<OrderState> diff = TraceDiff.between(
    store.load(idA).orElseThrow(),
    store.load(idB).orElseThrow());

System.out.println("Identical?        " + diff.identical());
System.out.println("Diverges at step: " + diff.divergenceIndex());
diff.leftRemainder().forEach(s  -> System.out.println("< " + s.nodeName()));
diff.rightRemainder().forEach(s -> System.out.println("> " + s.nodeName()));`} />
        <Table
          headers={['TraceDiff field', 'Description']}
          rows={[
            ['commonPrefix()',    'Steps matched in both traces (name + state equality).'],
            ['divergenceIndex()', 'Index where traces first differ. -1 if identical.'],
            ['leftRemainder()',   'Steps from traceA after divergence.'],
            ['rightRemainder()',  'Steps from traceB after divergence.'],
            ['sameStatus()',      'True when both traces have the same terminal Status.'],
            ['identical()',       'No divergence + same status + same final state.'],
          ]}
        />

        <H2 id="fork">Forking from a step</H2>
        <P>Re-run from any step against a (possibly modified) graph. The seed defaults to the <Code>before</Code> state of the chosen step. Pass <Code>-1</Code> to re-run from entry.</P>
        <Cb src={`ReplayRunner<OrderState> runner = ReplayRunner.of(parent, fixedGraph);

// fork from step 2 using parent's step-2 before-state as seed
ExecutionResult<OrderState> fork = runner.reRunFrom(2);

// fork from entry with a different seed
ExecutionResult<OrderState> altFork = runner.reRunFrom(-1, correctedSeed);`} />

        <H2 id="lineage">Fork lineage</H2>
        <Cb src={`ExecutionTrace<OrderState> forkTrace = store.load(fork.executionId()).orElseThrow();
System.out.println("Forked from: " + forkTrace.forkedFromExecutionId());
System.out.println("At step:     " + forkTrace.forkedFromStepIndex());`} />
        <Callout><strong>No determinism guarantee.</strong> Forking re-executes node code. LLMs, HTTP calls, and other non-deterministic sources may produce different results. Use <Code>TraceDiff</Code> to compare the fork to the parent and understand what changed.</Callout>
      </>
    ),
  },

  spring: {
    crumb: 'INTEGRATION / SPRING BOOT',
    title: 'Spring Boot starter',
    lede: 'The starter wires infrastructure around your graph, not the graph itself. You declare the Graph<S> bean; the starter auto-configures SPI defaults, REST endpoints, LLM clients, and optional JDBC stores.',
    toc: [
      { id: 'dep', label: 'Dependencies' },
      { id: 'autoconfig', label: 'Auto-config model' },
      { id: 'graph', label: 'Define Graph bean' },
      { id: 'trace', label: 'Enable traces' },
      { id: 'props', label: 'Property reference' },
      { id: 'conditions', label: 'Condition table' },
    ],
    body: () => (
      <>
        <P>The Spring Boot starter is four auto-configurations in one artifact. Each is guarded by conditions so unused features add zero overhead. You declare your own <Code>Graph&lt;S&gt;</Code> bean; the starter injects the surrounding infrastructure.</P>

        <H2 id="dep">Dependencies</H2>
        <Cb file="pom.xml" lang="xml" src={`<!-- core starter: SPI defaults + REST endpoints -->
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-spring-boot-starter</artifactId>
    <version>0.3.0</version>
</dependency>

<!-- add observability for traces, replay, and diff endpoints -->
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-observability</artifactId>
    <version>0.3.0</version>
</dependency>

<!-- add connectors for LLM auto-wiring -->
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-connectors</artifactId>
    <version>0.3.0</version>
</dependency>`} />

        <H2 id="autoconfig">The auto-configuration model</H2>
        <P>The starter ships four auto-configurations. They are independent and guarded by <Code>@ConditionalOn*</Code> annotations — only what you need activates.</P>
        <Table
          headers={['Auto-config class', 'Registers', 'Activates when']}
          rows={[
            ['TraceGraphAutoConfiguration',  'No-op SPI beans (NodeListener, CheckpointStore, TraceRecorder, MemoryStore)', 'Always (lowest priority — user beans win via @ConditionalOnMissingBean)'],
            ['TraceWebAutoConfiguration',     'TraceController, TraceReplayController, TraceStreamController',               'TraceStore bean present + web application + tracegraph.web.enabled=true'],
            ['MemoryAutoConfiguration',       'JdbcMemoryStore bean',                                                        'DataSource + JdbcMemoryStore + Jackson on classpath + no MemoryStore bean'],
            ['LlmAutoConfiguration',          'OpenAiLlmClient or AnthropicLlmClient bean',                                  'LlmClient on classpath + tracegraph.llm.provider set + no LlmClient bean'],
          ]}
        />

        <H2 id="graph">Define your Graph bean</H2>
        <P>The starter never guesses <Code>Graph&lt;?&gt;</Code>. Declare it as a <Code>@Bean</Code> and inject the SPI beans the starter provides. The injected <Code>NodeListener</Code> and <Code>CheckpointStore</Code> are no-ops by default; declare your own beans to override them.</P>
        <Cb file="AppConfiguration.java" src={`@Configuration
public class AppConfiguration {

    @Bean
    public Graph<AgentState> agentGraph(
            NodeListener listener,
            CheckpointStore checkpoints,
            TraceRecorder<AgentState> recorder,
            MemoryStore memory) {

        return Graph.<AgentState>builder()
            .node("ingest",    AgentNodes::ingest)
            .node("llm",       AgentNodes::callLlm)
            .node("respond",   AgentNodes::respond)
            .entry("ingest")
            .edge("ingest", "llm")
            .edge("llm", "respond", AgentState::hasResponse)
            .edge("llm", "llm",     s -> !s.hasResponse() && s.attempts() < 3)
            .terminal("respond")
            .listener(listener)
            .checkpointStore(checkpoints)
            .traceRecorder(recorder)
            .memoryStore(memory)
            .build();
    }
}`} />
        <Callout><strong>Generic type.</strong> Spring cannot auto-register <Code>Graph&lt;?&gt;</Code> because it is generic in <Code>S</Code>. Your application owns the state type and the graph definition. The starter provides the infrastructure around it.</Callout>

        <H2 id="trace">Enable traces and REST inspection</H2>
        <P>Declare a <Code>TraceStore</Code> bean and the web endpoints activate automatically. For production, use <Code>JdbcTraceStore</Code> initialized at startup.</P>
        <Cb file="TraceConfig.java" src={`@Configuration
public class TraceConfig {

    // In-memory for dev/test
    @Bean
    @Profile("!prod")
    public TraceStore<AgentState> devTraceStore() {
        return new InMemoryTraceStore<>();
    }

    // JDBC for production
    @Bean
    @Profile("prod")
    public TraceStore<AgentState> prodTraceStore(DataSource ds) {
        var store = new JdbcTraceStore<>(ds, AgentState.class);
        store.initSchema();
        return store;
    }

    @Bean
    public TraceRecorder<AgentState> traceRecorder(TraceStore<AgentState> store) {
        return new RecordingTraceRecorder<>(store);
    }
}`} />

        <H2 id="props">Full property reference</H2>
        <Cb file="application.yml" lang="yaml" src={`tracegraph:
  web:
    enabled: true           # set false to disable all /tracegraph/* REST endpoints

  memory:
    jdbc:
      enabled: true         # auto-wire JdbcMemoryStore when DataSource present
      init-schema: true     # run initSchema() at startup
      table: tracegraph_memory  # override table name

  llm:
    enabled: true           # set false to disable LlmAutoConfiguration
    provider: openai        # openai | anthropic
    openai:
      api-key: \${OPENAI_API_KEY}
      endpoint: https://api.openai.com/v1
      model: gpt-4o-mini
      timeout: 30s
    anthropic:
      api-key: \${ANTHROPIC_API_KEY}
      model: claude-sonnet-4-6
      version: 2023-06-01`} />
        <Table
          headers={['Property', 'Default', 'Description']}
          rows={[
            ['tracegraph.web.enabled',                 'true',                       'Toggle all REST endpoints.'],
            ['tracegraph.memory.jdbc.enabled',         'true',                       'Toggle JdbcMemoryStore auto-wiring.'],
            ['tracegraph.memory.jdbc.init-schema',     'true',                       'Run initSchema() at startup.'],
            ['tracegraph.memory.jdbc.table',           'tracegraph_memory',          'Override the memory table name.'],
            ['tracegraph.llm.enabled',                 'true',                       'Toggle LlmAutoConfiguration.'],
            ['tracegraph.llm.provider',                '(none — LlmClient not registered)', 'Set to openai or anthropic to register a client.'],
            ['tracegraph.llm.openai.api-key',          '(required when provider=openai)',   'OpenAI API key.'],
            ['tracegraph.llm.anthropic.api-key',       '(required when provider=anthropic)', 'Anthropic API key.'],
          ]}
        />

        <H2 id="conditions">Auto-config condition summary</H2>
        <Table
          headers={['Bean / endpoint', 'Requires']}
          rows={[
            ['No-op NodeListener, CheckpointStore, TraceRecorder, MemoryStore', '@ConditionalOnMissingBean — replaced by any user-declared bean.'],
            ['GET/DELETE /tracegraph/traces/**',         'TraceStore bean + web app + tracegraph.web.enabled=true.'],
            ['POST /tracegraph/traces/{id}/replay',      'Above + single Graph<?> candidate bean.'],
            ['POST /tracegraph/traces/{id}/resume',      'Above + single Graph<?> candidate bean.'],
            ['POST /tracegraph/traces/stream',           'Above + single Graph<?> candidate bean.'],
            ['JdbcMemoryStore',                          'DataSource + JdbcMemoryStore + Jackson on classpath, no MemoryStore bean, enabled=true.'],
            ['OpenAiLlmClient or AnthropicLlmClient',   'LlmClient on classpath, tracegraph.llm.provider set, no LlmClient bean, enabled=true.'],
          ]}
        />
      </>
    ),
  },

  llm: {
    crumb: 'INTEGRATION / LLM CONNECTORS',
    title: 'LLM connectors',
    lede: 'The connectors module provides low-level, testable Java types for LLM calls, a ChatNode adapter that bridges LLM responses into graph state, and a ReActAgent factory for tool-use loops.',
    toc: [
      { id: 'client', label: 'LlmClient interface' },
      { id: 'openai', label: 'OpenAI adapter' },
      { id: 'anthropic', label: 'Anthropic adapter' },
      { id: 'mock', label: 'MockLlmClient for tests' },
      { id: 'chat', label: 'ChatNode' },
      { id: 'streaming', label: 'Streaming' },
      { id: 'react', label: 'ReActAgent' },
    ],
    body: () => (
      <>
        <P>LLM adapters live in <Code>tracegraph-connectors</Code>. The design is intentionally low-level: <Code>LlmClient</Code> is a single-method interface, request and response are plain records, and the bridge between LLM output and graph state is an explicit lambda you write. No magic prompt templates or hidden state management.</P>

        <H2 id="client">LlmClient interface</H2>
        <P><Code>LlmClient</Code> is a <Code>@FunctionalInterface</Code> with one method: <Code>complete(LlmRequest)</Code>. Swap providers by injecting a different implementation.</P>
        <Cb src={`// build a request
LlmRequest req = LlmRequest.builder()
    .model("gpt-4o-mini")
    .message(ChatMessage.system("You are a helpful assistant."))
    .message(ChatMessage.user("Summarize this order: " + state.orderId()))
    .temperature(0.2)
    .maxTokens(256)
    .build();

// call the provider
LlmResponse res = client.complete(req);

// extract the result
String text   = res.content();      // concatenated content blocks
String reason = res.finishReason(); // "stop", "length", "tool_use", etc.
int promptTok = res.promptTokens();
int compTok   = res.completionTokens();`} />
        <Table
          headers={['LlmRequest field', 'Type', 'Description']}
          rows={[
            ['model()',        'String',            'Model name as the provider expects it.'],
            ['messages()',     'List<ChatMessage>', 'Conversation history. system, user, and assistant roles.'],
            ['temperature()',  'Double',            'Sampling temperature (0.0–2.0 for most providers).'],
            ['maxTokens()',    'Integer',           'Maximum completion tokens.'],
          ]}
        />
        <Table
          headers={['LlmResponse field', 'Type', 'Description']}
          rows={[
            ['content()',           'String', 'Concatenated text from all content blocks.'],
            ['finishReason()',      'String', 'Provider-specific finish reason.'],
            ['promptTokens()',      'int',    'Input token count.'],
            ['completionTokens()',  'int',    'Output token count.'],
          ]}
        />

        <H2 id="openai">OpenAI-compatible adapter</H2>
        <P><Code>OpenAiLlmClient</Code> targets the <Code>POST /v1/chat/completions</Code> endpoint. It works with OpenAI and any OpenAI-compatible API (Groq, Together, local Ollama, etc.).</P>
        <Cb src={`LlmClient openai = OpenAiLlmClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .model("gpt-4o-mini")
    .endpoint("https://api.openai.com/v1")   // override for compatible APIs
    .timeout(Duration.ofSeconds(30))
    .build();

// use with a local Ollama instance
LlmClient local = OpenAiLlmClient.builder()
    .apiKey("ignored")
    .model("llama3")
    .endpoint("http://localhost:11434/v1")
    .build();`} />
        <P>Non-2xx responses surface as <Code>LlmHttpException(statusCode, body)</Code>. Wire a retry policy on any node using this client to handle transient overload (429) or timeout errors.</P>

        <H2 id="anthropic">Anthropic adapter</H2>
        <P><Code>AnthropicLlmClient</Code> targets the <Code>POST /v1/messages</Code> endpoint with <Code>x-api-key</Code> and <Code>anthropic-version</Code> headers. System messages are lifted into the top-level <Code>system</Code> field automatically.</P>
        <Cb src={`LlmClient anthropic = AnthropicLlmClient.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .model("claude-sonnet-4-6")
    .version("2023-06-01")   // anthropic-version header
    .timeout(Duration.ofSeconds(60))
    .build();`} />

        <H2 id="mock">MockLlmClient for tests</H2>
        <P>Use <Code>MockLlmClient</Code> in unit tests to avoid real HTTP calls. Three modes: constant response, echo, or custom lambda.</P>
        <Cb src={`// constant response
LlmClient constant = MockLlmClient.constant("APPROVED");

// echo the last user message back
LlmClient echo = MockLlmClient.echo();

// custom lambda — full control
LlmClient custom = MockLlmClient.of(req -> {
    String lastMsg = req.messages().getLast().content();
    return LlmResponse.of("Response to: " + lastMsg, "stop", 10, 5);
});

// use it exactly like a real client
Node<AgentState> node = ChatNode.<AgentState>builder()
    .client(custom)
    .requestFactory(s -> LlmRequest.builder().model("test").message(ChatMessage.user(s.query())).build())
    .responseFolder((s, r) -> s.withResponse(r.content()))
    .build();`} />

        <H2 id="chat">ChatNode — bridging LLM responses into state</H2>
        <P><Code>ChatNode&lt;S&gt;</Code> adapts any <Code>LlmClient</Code> to a <Code>Node&lt;S&gt;</Code>. You supply a <Code>requestFactory</Code> (state → LlmRequest) and a <Code>responseFolder</Code> (state + response → new state). It calls <Code>ctx.reportUsage(…)</Code> automatically so token counts appear in traces and OTel spans.</P>
        <Cb src={`import io.tracegraph.connectors.llm.ChatNode;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.ChatMessage;

Node<AgentState> llmNode = ChatNode.<AgentState>builder()
    .client(openaiClient)
    // requestFactory: build the LLM request from current state
    .requestFactory(s -> LlmRequest.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.system("You process customer support tickets."))
        .message(ChatMessage.user(s.ticketText()))
        .temperature(0.1)
        .maxTokens(512)
        .build())
    // responseFolder: fold the LLM response back into state
    .responseFolder((s, r) -> s.withDraft(r.content()))
    .build();

// add it to the graph like any other node
Graph<AgentState> graph = Graph.<AgentState>builder()
    .node("classify", AgentNodes::classify)
    .node("draft", llmNode)   // ChatNode implements Node<S>
    .node("send",  AgentNodes::sendReply)
    .entry("classify")
    .edge("classify", "draft")
    .edge("draft", "send", AgentState::draftApproved)
    .terminal("send")
    .build();`} />
        <Callout><strong>Explicit bridging.</strong> Keeping <Code>requestFactory</Code> and <Code>responseFolder</Code> explicit gives you testable, swappable, and replay-transparent LLM integration. The prompt and the state mapping are code you own, not hidden framework behavior.</Callout>

        <H2 id="streaming">Streaming LLM responses</H2>
        <P><Code>LlmClient.stream(request)</Code> returns a <Code>Flow.Publisher&lt;LlmStreamChunk&gt;</Code>. Providers with native streaming support override this method; others get a default single-chunk implementation that wraps <Code>complete()</Code>.</P>
        <Cb src={`// stream token deltas
client.stream(req).subscribe(new Flow.Subscriber<LlmStreamChunk>() {
    public void onNext(LlmStreamChunk chunk) {
        System.out.print(chunk.delta());
        if (chunk.isLast()) System.out.println(); // terminal chunk
    }
    // ... onSubscribe, onError, onComplete
});`} />

        <H2 id="react">ReActAgent — Reason + Act loop</H2>
        <P><Code>ReActAgent&lt;S&gt;</Code> produces a complete <Code>Graph&lt;S&gt;</Code> implementing the Reason + Act loop: LLM routing node → tool execution node → back to LLM, terminating at <Code>done</Code> when no tool calls are returned.</P>
        <Cb src={`import io.tracegraph.connectors.agent.ReActAgent;
import io.tracegraph.connectors.agent.Tool;
import io.tracegraph.connectors.agent.ToolDefinition;

// define tools
Tool webSearch = args -> webSearchClient.search(args);
Tool calculator = args -> String.valueOf(eval(args));

// build the ReActAgent graph
Graph<AgentState> agentGraph = ReActAgent.<AgentState>builder()
    .client(openaiClient)
    // tool definitions tell the LLM what tools are available
    .tool(new ToolDefinition("web_search",   "Search the web",       "{\"query\": \"string\"}"), webSearch)
    .tool(new ToolDefinition("calculator",   "Evaluate math",        "{\"expr\":  \"string\"}"), calculator)
    // requestFactory: build the LLM request from current state (includes tool calls history)
    .requestFactory(s -> LlmRequest.builder()
        .model("gpt-4o")
        .message(ChatMessage.user(s.userMessage()))
        .build())
    // responseFolder: fold LLM response text back into state
    .responseFolder((s, r) -> s.withAssistantMessage(r.content()))
    // toolResultFolder: fold a tool result back into state for the next LLM call
    .toolResultFolder((s, name, result) -> s.withToolResult(name, result))
    .build()
    .buildGraph();

ExecutionResult<AgentState> r = agentGraph.run(
    new AgentState("What is the capital of France and 2+2?"));
System.out.println(r.finalState().assistantMessage());`} />
        <P>The graph structure produced by <Code>ReActAgent</Code> has three named nodes: <Code>llm</Code>, <Code>tools</Code>, and <Code>done</Code>. The loop terminates at <Code>done</Code> when the LLM returns no tool calls in its response.</P>
      </>
    ),
  },

  rest: {
    crumb: 'INTEGRATION / REST API',
    title: 'REST API',
    lede: 'The Spring Boot starter exposes a focused HTTP surface for traces, replay, resume, streaming, and diff. Endpoint availability depends on which modules and beans are present.',
    toc: [
      { id: 'conditions', label: 'Availability conditions' },
      { id: 'traces', label: 'Trace CRUD' },
      { id: 'diff', label: 'Diff endpoint' },
      { id: 'replay', label: 'Replay endpoint' },
      { id: 'resume', label: 'Resume endpoint' },
      { id: 'stream', label: 'SSE streaming' },
      { id: 'errors', label: 'Error responses' },
    ],
    body: () => (
      <>
        <P>All endpoints are under the <Code>/tracegraph</Code> prefix. They are registered by <Code>TraceWebAutoConfiguration</Code> — guarded by <Code>@ConditionalOnWebApplication</Code>, <Code>@ConditionalOnBean(TraceStore)</Code>, and <Code>tracegraph.web.enabled=true</Code>. Replay and resume additionally require a single <Code>Graph&lt;?&gt;</Code> bean.</P>

        <H2 id="conditions">Availability conditions</H2>
        <Table
          headers={['Endpoint group', 'Required condition']}
          rows={[
            ['GET/DELETE /tracegraph/traces/**',       'TraceStore bean + web app + web.enabled=true'],
            ['POST /tracegraph/traces/{id}/replay',    'Above + single Graph<?> bean'],
            ['POST /tracegraph/traces/{id}/resume',    'Above + single Graph<?> bean'],
            ['POST /tracegraph/traces/stream',         'Above + single Graph<?> bean'],
            ['GET /tracegraph/traces/{a}/diff/{b}',    'TraceStore bean + web app + web.enabled=true'],
          ]}
        />

        <H2 id="traces">Trace CRUD</H2>
        <Table
          headers={['Method', 'Path', 'Query params', 'Response']}
          rows={[
            ['GET',    '/tracegraph/traces',      'limit, offset',  '200 JSON array of executionIds; X-Total-Count header.'],
            ['GET',    '/tracegraph/traces/{id}', '—',              '200 full ExecutionTrace JSON; 404 if not found.'],
            ['DELETE', '/tracegraph/traces/{id}', '—',              '204 on success; 404 if not found.'],
          ]}
        />
        <Cb lang="bash" src={`# list the 10 most recent traces
curl "http://localhost:8080/tracegraph/traces?limit=10&offset=0"
# X-Total-Count: 47

# fetch a full trace
curl "http://localhost:8080/tracegraph/traces/3fa85f64-5717-4562-b3fc-2c963f66afa6"

# delete a trace
curl -X DELETE "http://localhost:8080/tracegraph/traces/3fa85f64-5717-4562-b3fc-2c963f66afa6"`} />
        <Cb lang="json" src={`// GET /tracegraph/traces/{id} response shape
{
  "executionId":  "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status":       "COMPLETED",
  "startedAt":    "2025-05-07T12:00:00.000Z",
  "completedAt":  "2025-05-07T12:00:01.234Z",
  "steps": [
    {
      "nodeName":  "validate",
      "attempts":  1,
      "before":    { "orderId": "ord-1", "valid": false },
      "after":     { "orderId": "ord-1", "valid": true },
      "usage":     null
    },
    {
      "nodeName":  "charge",
      "attempts":  2,
      "before":    { "orderId": "ord-1", "valid": true, "charged": false },
      "after":     { "orderId": "ord-1", "valid": true, "charged": true },
      "usage":     null
    }
  ]
}`} />

        <H2 id="diff">Diff endpoint</H2>
        <P>Returns a <Code>TraceDiff</Code> JSON object comparing two stored executions. 404 if either ID is not found.</P>
        <Cb lang="bash" src={`curl "http://localhost:8080/tracegraph/traces/id-a/diff/id-b"`} />
        <Cb lang="json" src={`{
  "divergenceIndex": 2,
  "identical":       false,
  "sameStatus":      true,
  "sameFinalState":  false,
  "commonPrefix":    [
    { "nodeName": "validate", "attempts": 1 },
    { "nodeName": "enrich",   "attempts": 1 }
  ],
  "leftRemainder":  [{ "nodeName": "charge-v1", "attempts": 3 }],
  "rightRemainder": [{ "nodeName": "charge-v2", "attempts": 1 }]
}`} />

        <H2 id="replay">Replay endpoint</H2>
        <P>Re-runs a saved trace from a chosen step index. Returns the new execution ID and fork lineage. <Code>step=-1</Code> (default) means re-run from entry.</P>
        <Cb lang="bash" src={`# replay from entry
curl -X POST "http://localhost:8080/tracegraph/traces/3fa85f64.../replay"

# replay from step 2
curl -X POST "http://localhost:8080/tracegraph/traces/3fa85f64.../replay?step=2"`} />
        <Cb lang="json" src={`// response
{
  "executionId":           "9c1e4a77-...",
  "status":                "COMPLETED",
  "forkedFromExecutionId": "3fa85f64-...",
  "forkedFromStepIndex":   2
}`} />
        <Table
          headers={['Status code', 'Meaning']}
          rows={[
            ['200', 'Fork completed. Body contains ExecutionResult with fork lineage.'],
            ['400', 'step query param is out of range for the trace.'],
            ['404', 'The parent trace ID was not found.'],
          ]}
        />

        <H2 id="resume">Resume endpoint</H2>
        <P>Continues an interrupted execution. The execution must be in <Code>Status.INTERRUPTED</Code> state.</P>
        <Cb lang="bash" src={`curl -X POST "http://localhost:8080/tracegraph/traces/3fa85f64.../resume"`} />
        <Table
          headers={['Status code', 'Meaning']}
          rows={[
            ['200', 'Run continued and completed. Body contains ExecutionResult.'],
            ['404', 'Execution ID not found in checkpoint store.'],
            ['409', 'Execution exists but is not INTERRUPTED (already completed or failed).'],
          ]}
        />

        <H2 id="stream">SSE streaming</H2>
        <P>Start a graph run and stream node lifecycle events as Server-Sent Events. Each event is a JSON <Code>NodeEvent&lt;S&gt;</Code> — one of <Code>NodeEnter</Code>, <Code>NodeExit</Code>, <Code>NodeRetry</Code>, <Code>Failed</Code>, or <Code>Complete</Code>.</P>
        <Cb lang="bash" src={`# POST triggers a new run; events stream back as SSE
curl -N -X POST "http://localhost:8080/tracegraph/traces/stream" \
     -H "Content-Type: application/json" \
     -d '{"orderId":"ord-1","valid":false,"charged":false}'`} />
        <Cb lang="javascript" src={`// browser EventSource
const es = new EventSource('/tracegraph/traces/stream?execution=' + id);
es.addEventListener('NodeEnter',  e => console.log('enter',  JSON.parse(e.data).nodeName));
es.addEventListener('NodeExit',   e => console.log('exit',   JSON.parse(e.data).nodeName));
es.addEventListener('NodeRetry',  e => console.log('retry',  JSON.parse(e.data)));
es.addEventListener('Failed',     e => { console.error(JSON.parse(e.data)); es.close(); });
es.addEventListener('Complete',   e => { console.log('done',   JSON.parse(e.data)); es.close(); });`} />
        <Callout><strong>Backpressure.</strong> The SSE publisher uses a <Code>SubmissionPublisher</Code>. When the consumer is slow, overflow drops oldest events. Durable state lives in the <Code>TraceStore</Code> — use the trace API to recover any missed steps after the run completes.</Callout>

        <H2 id="errors">Common error responses</H2>
        <Table
          headers={['HTTP status', 'When']}
          rows={[
            ['400 Bad Request',  'Invalid limit/offset values (negative); step out of range.'],
            ['404 Not Found',    'Execution ID not in store.'],
            ['409 Conflict',     'Resume called on an execution that is not INTERRUPTED.'],
            ['500 Server Error', 'Unexpected runtime failure. Check server logs for the executionId.'],
          ]}
        />
      </>
    ),
  },
}

const DOC_IDS = DOCS_TREE.flatMap((group) => group.items.map((item) => item.id))

function DocPage({ id }: { id: string }) {
  const page = DOC_PAGES[id] ?? DOC_PAGES.quickstart
  const currentIndex = DOC_IDS.indexOf(id in DOC_PAGES ? id : 'quickstart')
  const prevId = currentIndex > 0 ? DOC_IDS[currentIndex - 1] : null
  const nextId = currentIndex >= 0 && currentIndex < DOC_IDS.length - 1 ? DOC_IDS[currentIndex + 1] : null

  return (
    <div>
      <div className="mono text-[11px] uppercase tracking-[0.14em] text-ink-500 mb-4">
        {page.crumb}
      </div>
      <div className="flex items-center gap-2 mb-4">
        <Badge tone="neutral">Docs</Badge>
        <Badge tone="accent">Expanded</Badge>
      </div>
      <h1 className="display-tight text-[44px] text-ink-950 dark:text-white">{page.title}</h1>
      <p className="mt-4 text-[16px] text-ink-600 dark:text-ink-400 leading-relaxed max-w-3xl">{page.lede}</p>
      <div className="mt-10">{page.body()}</div>

      <div className="mt-16 pt-8 border-t hairline flex items-center justify-between gap-4 mono text-[12px] text-ink-500 flex-wrap">
        <div className="flex items-center gap-4">
          {prevId ? <a href={`/docs/${prevId}`} className="hover:text-ink-950 dark:hover:text-white inline-flex items-center gap-1.5">← Previous</a> : <span />}
          {nextId ? <a href={`/docs/${nextId}`} className="hover:text-ink-950 dark:hover:text-white inline-flex items-center gap-1.5">Next →</a> : <span />}
        </div>
        <a
          href="https://github.com/tracegraph/tracegraph"
          target="_blank"
          rel="noreferrer"
          className="hover:text-ink-950 dark:hover:text-white"
        >
          Edit on GitHub →
        </a>
      </div>
    </div>
  )
}
