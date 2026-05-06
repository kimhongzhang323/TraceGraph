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
      { id: 'next', label: 'Next steps' },
    ],
    body: () => (
      <>
        <H2 id="install">1. Install</H2>
        <P>Start with the smallest module that solves the problem you have today. Most teams should begin with <Code>tracegraph-core</Code> and add durability or observability only when they need it.</P>
        <Table
          headers={['Module', 'Add it when you need']}
          rows={[
            ['tracegraph-core', 'Typed graph execution, retries, edges, async, and parallel nodes.'],
            ['tracegraph-runtime', 'Checkpoint persistence and resume across process boundaries.'],
            ['tracegraph-observability', 'Trace recording, replay, diff, and OpenTelemetry listeners.'],
            ['tracegraph-spring-boot-starter', 'Auto-configuration and REST endpoints inside Spring Boot.'],
          ]}
        />
        <Cb file="pom.xml" lang="xml" src={`<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-core</artifactId>
    <version>0.3.0</version>
</dependency>`} />

        <H2 id="state">2. Define your state</H2>
        <P>State is the entire contract between nodes. TraceGraph is easiest to reason about when state is immutable and explicit, which is why Java records are the happy path.</P>
        <Cb file="OrderState.java" src={`record OrderState(String id, boolean valid, boolean charged) {
    OrderState withValid(boolean v)   { return new OrderState(id, v, charged); }
    OrderState withCharged(boolean v) { return new OrderState(id, valid, v); }
}`} />
        <UL items={[
          <>Use one record for the execution state instead of scattering transient fields across services.</>,
          <>Return a new value from each node. Avoid mutating collections inside the existing state instance.</>,
          <>Prefer adding explicit fields over burying intermediate results in maps or JSON blobs.</>,
        ]} />

        <H2 id="build">3. Build the graph</H2>
        <P>The builder is the public contract. You name nodes, wire edges, declare a single entry node, and mark one or more terminals. There is no hidden scheduler beyond what you define.</P>
        <Cb file="Pipeline.java" src={`Graph<OrderState> graph = Graph.<OrderState>builder()
    .node("validate", (s, ctx) -> s.withValid(true))
    .node("charge",   (s, ctx) -> s.withCharged(true))
    .entry("validate")
    .edge("validate", "charge", OrderState::valid)
    .terminal("charge")
    .build();`} />
        <Callout><strong>Execution model.</strong> After every node exits, TraceGraph evaluates outgoing edges against the post-node state. If a terminal is reached, the run completes. If a node throws and no retry policy applies, the run fails immediately.</Callout>

        <H2 id="run">4. Run it</H2>
        <Cb file="Main.java" src={`ExecutionResult<OrderState> r = graph.run(new OrderState("o-1", false, false));
System.out.println(r.status());     // COMPLETED
System.out.println(r.finalState()); // OrderState[id=o-1, valid=true, charged=true]
System.out.println(r.path());       // [validate, charge]
System.out.println(r.error());      // Optional.empty()`} />
        <H3>What the result tells you</H3>
        <UL items={[
          <><Code>status()</Code> tells you whether the run completed, failed, or was interrupted.</>,
          <><Code>finalState()</Code> is the last committed state returned by the graph.</>,
          <><Code>path()</Code> is the ordered list of node names that executed.</>,
          <><Code>executionId()</Code> is the correlation handle you use for traces, replay, and resume.</>,
        ]} />

        <H2 id="next">5. Next steps</H2>
        <Steps items={[
          <>Add <Code>RetryPolicy</Code> to side-effecting nodes like payment or LLM calls.</>,
          <>Wire a <Code>RecordingTraceRecorder</Code> when you want deterministic replay and diff.</>,
          <>Add a <Code>CheckpointStore</Code> before you depend on crash-safe long-running flows.</>,
          <>Move to the Spring Boot starter when you want the graph embedded in an application with HTTP endpoints.</>,
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
    lede: 'This example moves past the toy two-node flow and shows how a real graph reads: validation, enrichment, async scoring, retries, and a terminal shipping step.',
    toc: [
      { id: 'state', label: 'State' },
      { id: 'graph', label: 'Graph' },
      { id: 'flow', label: 'Flow semantics' },
      { id: 'run', label: 'Run' },
    ],
    body: () => (
      <>
        <H2 id="state">Define the state record</H2>
        <Cb file="OrderState.java" src={`record OrderState(
    String id, boolean valid,
    String profile, double fraud, String inventory,
    double riskScore, boolean charged, boolean shipped
) {}`} />
        <P>Even when a graph gets more complex, the rule stays the same: the graph threads one typed value through every transition.</P>

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

        <H2 id="flow">How this graph executes</H2>
        <Steps items={[
          <>The run starts at <Code>validate</Code>. If validation fails, the edge to <Code>enrich</Code> never fires.</>,
          <>The <Code>parallel("enrich", ...)</Code> node fans out to anonymous branches, then merges results into one new <Code>OrderState</Code>.</>,
          <>The <Code>asyncNode("score")</Code> stage returns a <Code>CompletableFuture&lt;OrderState&gt;</Code>, but from the rest of the graph&apos;s perspective it behaves like any other node.</>,
          <>The <Code>charge</Code> node has its own retry policy, so transient failures are retried before the run is marked failed.</>,
          <>If <Code>charge</Code> succeeds, the <Code>charged</Code> predicate routes the run to the terminal <Code>ship</Code> node.</>,
        ]} />

        <H2 id="run">Run and inspect</H2>
        <Cb src={`ExecutionResult<OrderState> r = graph.run(seed);
r.path();       // [validate, enrich, score, charge, ship]
r.finalState(); // fully enriched + charged + shipped
r.status();     // COMPLETED`} />
        <Callout><strong>Debugging tip.</strong> As soon as you care about inspecting intermediate state, add <Code>tracegraph-observability</Code> and record traces. That gives you replay and diff without changing your node code.</Callout>
      </>
    ),
  },

  graphs: {
    crumb: 'CONCEPTS / GRAPHS & NODES',
    title: 'Graphs & nodes',
    lede: 'A graph is compiled control flow. It declares execution boundaries explicitly, and each node is a named transition over the same state type.',
    toc: [
      { id: 'kinds', label: 'Node kinds' },
      { id: 'entry', label: 'Entry & terminal' },
      { id: 'validation', label: 'Build-time validation' },
      { id: 'immutability', label: 'Immutability' },
    ],
    body: () => (
      <>
        <H2 id="kinds">The supported node kinds</H2>
        <Table headers={['Builder method', 'Use when']} rows={[
          ['node', 'Synchronous transitions. The default and most common case.'],
          ['asyncNode', 'Network calls or long-running operations that already compose as CompletableFuture<S>.'],
          ['parallel', 'Fan-out enrichment where several branches read the same input state and merge back.'],
          ['routingNode', 'A node needs to choose the next destination dynamically at runtime.'],
        ]} />
        <P>These are not separate runtimes. They all compile into the same graph model and share the same retry, listener, trace, and checkpoint behavior.</P>

        <H2 id="entry">Entry and terminal nodes</H2>
        <P>Every graph has exactly one entry node declared with <Code>.entry("name")</Code>. A graph can have one or more terminals declared with <Code>.terminal("name")</Code>. A run completes the moment execution reaches any terminal.</P>
        <UL items={[
          <>If a graph has no reachable terminal, it is structurally suspicious and usually a bug.</>,
          <>Entry and terminal declarations are part of the graph definition, not runtime options.</>,
          <>The path in <Code>ExecutionResult</Code> is simply the ordered list of executed node names.</>,
        ]} />

        <H2 id="validation">What gets validated at build time</H2>
        <UL items={[
          <>Duplicate node names are rejected.</>,
          <>Missing entry or terminal declarations are rejected.</>,
          <>Unreachable terminal paths should be caught before the first run, not after deploying.</>,
          <>Retry, listener, checkpoint, and trace wiring are additive infrastructure concerns around the same graph definition.</>,
        ]} />

        <H2 id="immutability">Nodes return state, never mutate it</H2>
        <Callout><strong>Single type parameter.</strong> Keep the graph state explicit and whole. If a node produces a “sub-result”, fold it back into <Code>S</Code> rather than inventing a separate per-node result type.</Callout>
      </>
    ),
  },

  state: {
    crumb: 'CONCEPTS / STATE & CONTEXT',
    title: 'State & context',
    lede: 'State is the execution value that flows through the graph. Context is the toolbox around the run: IDs, memory, executors, and production safety hooks.',
    toc: [
      { id: 'state', label: 'State record' },
      { id: 'ctx', label: 'NodeContext' },
      { id: 'memory', label: 'State vs memory' },
      { id: 'testing', label: 'Testing style' },
    ],
    body: () => (
      <>
        <H2 id="state">The state record</H2>
        <P>Prefer Java records. They serialize cleanly, diff naturally in traces, and push you toward immutable updates that are easy to reason about.</P>
        <Cb src={`record AppState(String userId, boolean authenticated, List<String> roles) {
    AppState withAuthenticated(boolean v) {
        return new AppState(userId, v, roles);
    }
}`} />

        <H2 id="ctx">NodeContext</H2>
        <Table headers={['Method', 'What it gives you']} rows={[
          ['ctx.executionId()', 'The execution correlation ID for logs, traces, and resume workflows.'],
          ['ctx.idempotencyKey()', 'A stable key for at-least-once-safe side effects.'],
          ['ctx.memory()', 'Scoped cross-run storage for things that do not belong in the current execution state.'],
          ['ctx.executor()', 'The run-scoped executor, especially useful inside async or parallel work.'],
        ]} />
        <P>Keep business decisions in state. Use context for infrastructure concerns: correlation, external memory, safe retries, or delegating asynchronous work.</P>

        <H2 id="memory">State vs memory</H2>
        <UL items={[
          <>Put data in <Code>S</Code> when it affects current execution decisions and should appear in traces and replay.</>,
          <>Use <Code>MemoryStore</Code> for user preferences, prior conversations, cache entries, or shared cross-execution artifacts.</>,
          <>Do not treat memory as a hidden side channel for core graph behavior unless you also accept weaker replay determinism.</>,
        ]} />

        <H2 id="testing">How to test nodes cleanly</H2>
        <P>The easiest testing seam is usually the node function itself. Feed it an input state and a minimal context, assert on the returned state, then separately test graph wiring with a small integration-style graph test.</P>
      </>
    ),
  },

  edges: {
    crumb: 'CONCEPTS / EDGES & ROUTING',
    title: 'Edges & routing',
    lede: 'Edges are first-class graph records. They are where conditional control flow becomes explicit, inspectable, and replayable.',
    toc: [
      { id: 'edges', label: 'Edge basics' },
      { id: 'predicates', label: 'Predicates' },
      { id: 'routing', label: 'Dynamic routing' },
      { id: 'order', label: 'Ordering' },
    ],
    body: () => (
      <>
        <H2 id="edges">Edge basics</H2>
        <P>An edge connects two named nodes. Without a predicate it always fires. With a predicate it fires only when the predicate returns true on the post-node state.</P>
        <Cb src={`.edge("score", "approve", s -> s.risk() < 0.3)
.edge("score", "review",  s -> s.risk() < 0.7)
.edge("score", "reject")`} />

        <H2 id="predicates">Predicates must stay pure</H2>
        <P>Predicates are part of route resolution, so they must be deterministic functions of state. Avoid I/O, clocks, random values, or external service checks inside them.</P>
        <Callout><strong>Replay implication.</strong> If a predicate depends only on state, replay and resume can re-evaluate the exact same rule safely. If it depends on the outside world, your execution path becomes harder to reproduce.</Callout>

        <H2 id="routing">Dynamic routing with routing nodes</H2>
        <Cb src={`.routingNode("router", (s, ctx) -> {
    if (s.needsReview()) return NodeResult.goTo("review", s);
    return NodeResult.of(s);
})`} />
        <P>Use a routing node when the destination itself is dynamic. Use regular edges when simple predicates on the existing state are enough.</P>

        <H2 id="order">Ordering and fallthrough</H2>
        <P>Keep routing readable by making your branch order intentional. More specific predicates should come before broader fallthrough paths, and unconditional “else” edges should be obvious in the definition.</P>
      </>
    ),
  },

  parallel: {
    crumb: 'CONCEPTS / ASYNC & PARALLEL',
    title: 'Async & parallel',
    lede: 'TraceGraph uses virtual-thread-per-task execution by default. Async and parallel stages behave like normal nodes from the graph’s perspective, but they let you overlap external work safely.',
    toc: [
      { id: 'async', label: 'asyncNode' },
      { id: 'parallel', label: 'parallel' },
      { id: 'executor', label: 'Executor lifecycle' },
      { id: 'gotchas', label: 'Gotchas' },
    ],
    body: () => (
      <>
        <H2 id="async">asyncNode</H2>
        <P>An async node returns <Code>CompletableFuture&lt;S&gt;</Code>. Retry, checkpoint, and listener behavior still wrap the node as one graph step.</P>
        <Cb src={`.asyncNode("score", (s, ctx) ->
    CompletableFuture.supplyAsync(() -> callLlm(s), ctx.executor()))`} />

        <H2 id="parallel">parallel</H2>
        <P>Parallel branches all receive the same input state. They run concurrently, then collapse into one merged output using the reducer you provide.</P>
        <Cb src={`.parallel("enrich",
    List.of(
        (s, ctx) -> fetchProfile(s),
        (s, ctx) -> fetchFraudScore(s),
        (s, ctx) -> fetchInventory(s)),
    (input, branches) -> branches.stream().reduce(input, AppState::merge))`} />

        <H2 id="executor">Executor lifecycle</H2>
        <UL items={[
          <>The default executor is created lazily per run.</>,
          <>Default executors are shut down when the run completes.</>,
          <>User-supplied executors via <Code>.executor(myPool)</Code> are never shut down by the graph.</>,
          <>Using <Code>ctx.executor()</Code> keeps node-internal async work aligned with the graph’s configured runtime strategy.</>,
        ]} />

        <H2 id="gotchas">Important gotchas</H2>
        <Callout><strong>Branches are anonymous.</strong> Parallel branches do not become individual trace path elements and do not fire individual node listener callbacks. A parallel block appears as one logical trace step.</Callout>
      </>
    ),
  },

  retries: {
    crumb: 'RUNTIME / RETRIES',
    title: 'Retries',
    lede: 'Retry policy belongs in the graph definition. That keeps failure behavior explicit, versioned with the graph, and reproducible across environments.',
    toc: [
      { id: 'policy', label: 'RetryPolicy' },
      { id: 'pernode', label: 'Per-node vs default' },
      { id: 'listener', label: 'Observing retries' },
      { id: 'failures', label: 'Failure semantics' },
    ],
    body: () => (
      <>
        <H2 id="policy">RetryPolicy</H2>
        <Table headers={['Factory', 'Parameters']} rows={[
          ['RetryPolicy.fixed(n, delay)', 'Retry up to n times with a constant delay.'],
          ['RetryPolicy.exponential(n, initial, mult, max)', 'Retry with capped exponential backoff.'],
          ['RetryPolicy.none()', 'Disable retries explicitly.'],
        ]} />
        <Cb src={`RetryPolicy policy = RetryPolicy.exponential(
    3,
    Duration.ofMillis(100),
    2.0,
    Duration.ofSeconds(2));`} />

        <H2 id="pernode">Per-node vs graph-wide defaults</H2>
        <Cb src={`.node("charge", chargeFn, RetryPolicy.exponential(3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(2)))
.defaultRetryPolicy(RetryPolicy.fixed(2, Duration.ofMillis(500)))`} />
        <P>Per-node policy wins over the graph default. That lets you keep conservative defaults while still treating payment, LLM, or network-heavy nodes specially.</P>

        <H2 id="listener">Observing retries</H2>
        <P><Code>NodeListener.onRetry(name, attempt, cause)</Code> fires before each retry. When you use <Code>OtelNodeListener</Code>, retries appear as events on the same node span rather than separate spans.</P>

        <H2 id="failures">Failure semantics</H2>
        <UL items={[
          <>If all attempts are exhausted, the run fails at that node.</>,
          <>Use <Code>ctx.idempotencyKey()</Code> for at-least-once-safe side effects.</>,
          <>Treat <Code>Error</Code> and interruption as short-circuit conditions rather than “just retry it” cases.</>,
        ]} />
      </>
    ),
  },

  checkpoints: {
    crumb: 'RUNTIME / CHECKPOINTS',
    title: 'Checkpoints & resume',
    lede: 'Checkpointing turns graph progress into a durable artifact. Resume uses that artifact to continue a run after process restarts, deploys, or intentional interruptions.',
    toc: [
      { id: 'store', label: 'CheckpointStore' },
      { id: 'resume', label: 'Resume behavior' },
      { id: 'idem', label: 'Idempotency' },
      { id: 'ops', label: 'Operational guidance' },
    ],
    body: () => (
      <>
        <H2 id="store">CheckpointStore</H2>
        <P>The default store is a no-op. You opt in explicitly, which keeps the core library lightweight for teams that do not need durability.</P>
        <Cb src={`// In-memory (test/dev only)
.checkpointStore(new InMemoryCheckpointStore())

// JDBC (production)
JdbcCheckpointStore<OrderState> store =
    new JdbcCheckpointStore<>(dataSource, OrderState.class);
store.initSchema();
.checkpointStore(store)`} />

        <H2 id="resume">What resume actually does</H2>
        <Cb src={`ExecutionResult<OrderState> resumed = graph.resume(executionId);`} />
        <UL items={[
          <>Checkpoints are written after node exit and before edge resolution.</>,
          <>Resume starts from the last completed checkpoint, not from the middle of an in-flight node body.</>,
          <>If the process crashes mid-node, that node is re-executed from attempt 1 on resume.</>,
        ]} />

        <H2 id="idem">Idempotency keys</H2>
        <Cb src={`String key = ctx.idempotencyKey();
httpClient.post("/charge", body, Map.of("Idempotency-Key", key));`} />
        <P>Checkpointing gives you at-least-once semantics, not exactly-once semantics. The idempotency key is how you keep retries and resume safe when a node performs external side effects.</P>

        <H2 id="ops">Operational guidance</H2>
        <Callout><strong>Production rule of thumb.</strong> Use JDBC or another durable store before you rely on resume for business-critical runs. In-memory stores are great for tests, demos, and local debugging, but not for surviving real process boundaries.</Callout>
      </>
    ),
  },

  memory: {
    crumb: 'RUNTIME / MEMORY',
    title: 'Memory',
    lede: 'MemoryStore is scoped cross-execution storage. Use it when information should outlive one run, but does not belong inside the current execution state itself.',
    toc: [
      { id: 'api', label: 'API' },
      { id: 'impls', label: 'Implementations' },
      { id: 'scope', label: 'Scope design' },
    ],
    body: () => (
      <>
        <H2 id="api">API</H2>
        <Cb src={`ctx.memory().put("session", "lastIntent", intent);
Optional<String> last = ctx.memory().get("session", "lastIntent", String.class);
ctx.memory().delete("session", "lastIntent");
List<String> keys = ctx.memory().keys("session");`} />

        <H2 id="impls">Implementations</H2>
        <Table headers={['Class', 'When to use']} rows={[
          ['MemoryStore.noop()', 'Default when you do not want memory yet.'],
          ['InMemoryMemoryStore', 'Tests and local development.'],
          ['FileMemoryStore', 'Single-process local persistence.'],
          ['JdbcMemoryStore', 'Production, transactional, multi-instance friendly setups.'],
        ]} />

        <H2 id="scope">How to pick scopes</H2>
        <UL items={[
          <>Use user ID for long-lived preferences.</>,
          <>Use session ID for short-lived conversational context.</>,
          <>Use tenant or workspace prefixes when data isolation matters.</>,
          <>Avoid dumping everything into one broad scope; explicit namespacing keeps cleanup and debugging sane.</>,
        ]} />
      </>
    ),
  },

  interrupts: {
    crumb: 'RUNTIME / INTERRUPTS & HITL',
    title: 'Interrupts & HITL',
    lede: 'Interrupts let a graph pause at named execution boundaries for human approval, review, or external control, then resume from the checkpointed state later.',
    toc: [
      { id: 'declare', label: 'Declare interrupts' },
      { id: 'resume', label: 'Resume' },
      { id: 'api', label: 'REST API' },
    ],
    body: () => (
      <>
        <H2 id="declare">Declaring interrupt points</H2>
        <Cb src={`Graph<ApprovalState> graph = Graph.<ApprovalState>builder()
    .node("analyze", fn)
    .node("approve", approveFn)
    .node("execute", executeFn)
    .entry("analyze")
    .edge("analyze", "approve")
    .edge("approve", "execute", ApprovalState::approved)
    .terminal("execute")
    .interruptBefore("approve")
    .checkpointStore(jdbcStore)
    .build();`} />
        <Callout><strong>Durability is not optional here.</strong> Interrupt flows only make operational sense when a durable checkpoint store is present.</Callout>

        <H2 id="resume">Resuming after human action</H2>
        <Cb src={`ExecutionResult<ApprovalState> r = graph.run(seed);
if (r.status() == Status.INTERRUPTED) {
    graph.resume(r.executionId());
}`} />

        <H2 id="api">Spring Boot REST surface</H2>
        <P>When using the Spring Boot starter, <Code>POST /tracegraph/traces/{'{id}'}/resume</Code> provides the HTTP resume path. It returns 409 when the execution exists but is not currently interrupted.</P>
      </>
    ),
  },

  otel: {
    crumb: 'OBSERVABILITY / OPENTELEMETRY',
    title: 'OpenTelemetry',
    lede: 'OpenTelemetry support lives in the observability module. It keeps core free of OTel dependencies while still giving you one span per node, rich retry events, and state-change visibility.',
    toc: [
      { id: 'wire', label: 'Wiring' },
      { id: 'spans', label: 'Span structure' },
      { id: 'state', label: 'State diffs' },
    ],
    body: () => (
      <>
        <H2 id="wire">Wiring OtelNodeListener</H2>
        <Cb src={`.listener(OtelNodeListener.usingGlobal())
// or
.listener(OtelNodeListener.using(openTelemetry))`} />

        <H2 id="spans">Span structure</H2>
        <Table headers={['Event', 'Span behavior']} rows={[
          ['Node enter', 'Span starts with execution and node attributes.'],
          ['Node exit', 'Span completes successfully.'],
          ['Retry', 'Event is added to the same span with attempt metadata.'],
          ['Node failure', 'StatusCode.ERROR is set and exception data is recorded.'],
          ['State change', 'A state event captures before/after rendering.'],
        ]} />

        <H2 id="state">State diffs in traces</H2>
        <P>State rendering flows through <Code>NodeListener.onState(name, before, after)</Code>. Plug in a custom <Code>StateRenderer</Code> if <Code>String::valueOf</Code> is too noisy or not structured enough for your telemetry backend.</P>
      </>
    ),
  },

  replay: {
    crumb: 'OBSERVABILITY / REPLAY & DIFF',
    title: 'Replay & diff',
    lede: 'Replay turns a saved execution into a debugging artifact. You can inspect each step, fork from any step, and compare two runs to see exactly where behavior changed.',
    toc: [
      { id: 'record', label: 'Recording' },
      { id: 'replay', label: 'Replay' },
      { id: 'diff', label: 'Diff' },
      { id: 'fork', label: 'Fork lineage' },
    ],
    body: () => (
      <>
        <H2 id="record">Recording traces</H2>
        <Cb src={`TraceStore<OrderState> store = new InMemoryTraceStore<>();
.traceRecorder(new RecordingTraceRecorder<>(store))`} />
        <P>Pick the store based on how you plan to use traces: in-memory for tests, JSON files for local debugging, JDBC for production retention and inspection workflows.</P>

        <H2 id="replay">Walking a trace</H2>
        <Cb src={`ExecutionTrace<OrderState> trace = store.load(id).orElseThrow();
Replayer<OrderState> replay = Replayer.of(trace);
for (int i = 0; i < replay.stepCount(); i++) {
    TraceStep<OrderState> step = replay.stepAt(i);
    System.out.printf("Step %d: %s -> %s%n", i, step.before(), step.after());
}`} />

        <H2 id="diff">Diffing two runs</H2>
        <Cb src={`TraceDiff<OrderState> diff = TraceDiff.between(left, right);
diff.commonPrefix();
diff.divergenceIndex();
diff.leftRemainder();
diff.rightRemainder();
diff.identical();`} />

        <H2 id="fork">Fork lineage</H2>
        <Cb src={`ReplayRunner<OrderState> runner = ReplayRunner.of(trace, fixedGraph);
ExecutionResult<OrderState> fork = runner.reRunFrom(2);`} />
        <Callout><strong>Lineage matters.</strong> A fork gets a fresh execution ID, but still carries its source execution and source step index. That makes prompt iteration, bug-fix verification, and incident analysis much easier to reason about.</Callout>
      </>
    ),
  },

  spring: {
    crumb: 'INTEGRATION / SPRING BOOT',
    title: 'Spring Boot starter',
    lede: 'The starter wires infrastructure around your graph, not the graph itself. You still define the state type and the Graph<S> bean; the starter supplies no-op defaults, HTTP endpoints, and optional auto-configuration around it.',
    toc: [
      { id: 'dep', label: 'Dependencies' },
      { id: 'graph', label: 'Define Graph bean' },
      { id: 'trace', label: 'Enable traces' },
      { id: 'props', label: 'Properties' },
      { id: 'conditions', label: 'Auto-config conditions' },
    ],
    body: () => (
      <>
        <H2 id="dep">Dependencies</H2>
        <Cb file="pom.xml" lang="xml" src={`<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-spring-boot-starter</artifactId>
    <version>0.3.0</version>
</dependency>`} />
        <P>Add <Code>tracegraph-observability</Code> as well when you want trace endpoints, replay, diff, or the embedded UI.</P>

        <H2 id="graph">Define your Graph bean</H2>
        <Cb file="AppConfiguration.java" src={`@Configuration
public class AppConfiguration {
    @Bean
    public Graph<MyState> graph(NodeListener listener, CheckpointStore checkpoints) {
        return Graph.<MyState>builder()
            .node("ingest",  (s, ctx) -> s.normalize())
            .node("respond", (s, ctx) -> s.reply())
            .entry("ingest")
            .edge("ingest", "respond")
            .terminal("respond")
            .listener(listener)
            .checkpointStore(checkpoints)
            .build();
    }
}`} />
        <P>The starter does not attempt to guess <Code>Graph&lt;?&gt;</Code> because your state type is application-specific. It only auto-wires the surrounding SPI beans.</P>

        <H2 id="trace">Enable traces and REST endpoints</H2>
        <Cb file="TraceConfig.java" src={`@Bean
TraceStore<MyState> traceStore() {
    return new InMemoryTraceStore<>();
}

@Bean
TraceRecorder<MyState> traceRecorder(TraceStore<MyState> store) {
    return new RecordingTraceRecorder<>(store);
}`} />
        <UL items={[
          <>When a <Code>TraceStore</Code> is present, trace inspection endpoints become available.</>,
          <>Replay and resume endpoints require a single candidate <Code>Graph&lt;?&gt;</Code> bean.</>,
          <>The embedded UI depends on both the starter and the UI module being on the classpath.</>,
        ]} />

        <H2 id="props">Key properties</H2>
        <Cb file="application.yml" lang="yaml" src={`tracegraph:
  web:
    enabled: true
  ui:
    enabled: true
  memory:
    jdbc:
      enabled: true
      init-schema: false
      table: tracegraph_memory
  llm:
    provider: openai
    openai:
      api-key: \${OPENAI_API_KEY}
      model: gpt-4o-mini`} />

        <H2 id="conditions">What the starter auto-configures</H2>
        <Table headers={['Concern', 'Condition']} rows={[
          ['NodeListener / CheckpointStore / TraceRecorder / MemoryStore', 'No-op defaults only when you do not declare your own bean.'],
          ['Trace REST controllers', 'Require starter web config plus observability pieces.'],
          ['Replay / resume endpoints', 'Require a single Graph<?> candidate.'],
          ['LlmClient', 'Requires connectors on the classpath and tracegraph.llm.provider set.'],
        ]} />
      </>
    ),
  },

  llm: {
    crumb: 'INTEGRATION / LLM CONNECTORS',
    title: 'LLM connectors',
    lede: 'The connectors module gives you low-level, testable Java types for LLM calls. It intentionally avoids pretending providers are all identical.',
    toc: [
      { id: 'client', label: 'LlmClient' },
      { id: 'openai', label: 'OpenAI' },
      { id: 'anthropic', label: 'Anthropic' },
      { id: 'chat', label: 'ChatNode' },
    ],
    body: () => (
      <>
        <H2 id="client">LlmClient interface</H2>
        <Cb src={`LlmResponse res = client.complete(LlmRequest.builder()
    .model("gpt-4o")
    .message(ChatMessage.user("Summarize this order: " + state))
    .temperature(0.2)
    .maxTokens(256)
    .build());
String text = res.content();`} />

        <H2 id="openai">OpenAI-compatible adapter</H2>
        <Cb src={`LlmClient client = OpenAiLlmClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .model("gpt-4o-mini")
    .endpoint("https://api.openai.com/v1")
    .build();`} />

        <H2 id="anthropic">Anthropic adapter</H2>
        <Cb src={`LlmClient client = AnthropicLlmClient.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .model("claude-sonnet-4-6")
    .build();`} />

        <H2 id="chat">Folding responses back into state</H2>
        <Cb src={`Node<AgentState> scoreNode = ChatNode.<AgentState>builder()
    .client(client)
    .requestFactory(s -> LlmRequest.builder().model("gpt-4o").message(ChatMessage.user(prompt(s))).build())
    .responseFolder((s, r) -> s.withScore(parseScore(r.content())))
    .build();`} />
        <Callout><strong>Design advice.</strong> Keep the provider-facing request small and keep the response-folder explicit. That gives you better tests, easier provider swaps, and cleaner replay artifacts.</Callout>
      </>
    ),
  },

  rest: {
    crumb: 'INTEGRATION / REST API',
    title: 'REST API',
    lede: 'The Spring Boot starter exposes a focused HTTP surface for traces, replay, resume, streaming, and UI support. The exact endpoints depend on which modules and beans are present.',
    toc: [
      { id: 'conditions', label: 'Endpoint conditions' },
      { id: 'traces', label: 'Traces' },
      { id: 'replay', label: 'Replay & resume' },
      { id: 'stream', label: 'Streaming' },
      { id: 'ui', label: 'UI endpoints' },
    ],
    body: () => (
      <>
        <H2 id="conditions">When endpoints exist</H2>
        <Table headers={['Endpoint group', 'Needs']} rows={[
          ['Trace list/get/delete', 'Starter web config plus observability backing store.'],
          ['Replay and resume', 'Everything above plus a single Graph<?> bean.'],
          ['SSE stream', 'A graph bean and stream controller registration.'],
          ['UI graph / complexity', 'tracegraph-ui on the classpath and UI enabled.'],
        ]} />

        <H2 id="traces">Trace endpoints</H2>
        <Table headers={['Method', 'Path', 'Description']} rows={[
          ['GET', '/tracegraph/traces', 'List executions, usually paginated via limit/offset.'],
          ['GET', '/tracegraph/traces/{id}', 'Fetch a full ExecutionTrace payload.'],
          ['DELETE', '/tracegraph/traces/{id}', 'Delete a stored execution.'],
          ['GET', '/tracegraph/traces/{a}/diff/{b}', 'Diff two executions structurally.'],
        ]} />
        <Cb lang="json" src={`{
  "executionId": "a1b2c3d4-...",
  "status": "COMPLETED",
  "steps": [
    {
      "nodeName": "fetch",
      "attempts": 1,
      "before": { "input": "hello" },
      "after":  { "input": "hello", "result": "world" }
    }
  ]
}`} />

        <H2 id="replay">Replay and resume endpoints</H2>
        <Cb lang="bash" src={`curl -X POST "http://localhost:8080/tracegraph/traces/e9c4f1a2/replay?step=2"
curl -X POST "http://localhost:8080/tracegraph/traces/e9c4f1a2/resume"`} />
        <UL items={[
          <>Replay returns a fresh execution ID and fork lineage.</>,
          <>Resume returns 409 if the run is not currently interrupted.</>,
          <>Both surfaces are operationally safest when traces and checkpoints are backed by durable stores.</>,
        ]} />

        <H2 id="stream">Streaming execution events</H2>
        <P>The SSE endpoint emits <Code>NodeEnter</Code>, <Code>NodeExit</Code>, <Code>NodeRetry</Code>, <Code>Failed</Code>, and <Code>Complete</Code> events as JSON.</P>
        <Cb lang="javascript" src={`const es = new EventSource('/tracegraph/traces/stream?execution=' + id);
es.onmessage = (e) => {
  const event = JSON.parse(e.data);
  console.log(event.type, event.nodeName);
};`} />

        <H2 id="ui">Trace UI support endpoints</H2>
        <Table headers={['Method', 'Path', 'Description']} rows={[
          ['GET', '/tracegraph/ui/graph', 'Returns the structural graph description.'],
          ['GET', '/tracegraph/ui/complexity', 'Returns GraphComplexity metrics for the active graph.'],
        ]} />
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
