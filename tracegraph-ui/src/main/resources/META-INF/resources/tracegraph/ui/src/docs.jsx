// Docs page with sidebar nav and content panes.

const Docs = () => {
  const [active, setActive] = React.useState(() => {
    const m = window.location.hash.match(/^#\/docs\/(.+)$/);
    return (m && m[1]) || 'quickstart';
  });
  React.useEffect(() => {
    const onHash = () => {
      const m = window.location.hash.match(/^#\/docs\/(.+)$/);
      if (m) setActive(m[1]);
      else setActive('quickstart');
    };
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);

  return (
    <div className="max-w-[1320px] mx-auto px-6 lg:px-8 py-12 grid grid-cols-1 lg:grid-cols-[240px_1fr_200px] gap-12 fade-up">
      <aside className="lg:sticky lg:top-20 lg:self-start lg:max-h-[calc(100vh-100px)] overflow-y-auto scroll-thin pr-2">
        {window.DOCS_TREE.map(group => (
          <div key={group.section} className="mb-6">
            <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 dark:text-ink-500 mb-3">{group.section}</h4>
            <ul className="space-y-0.5">
              {group.items.map(it => (
                <li key={it.id}>
                  <a href={`#/docs/${it.id}`}
                     className={`block px-3 py-1.5 rounded-md text-[13.5px] transition-colors ${
                       active === it.id
                         ? 'bg-ink-100 dark:bg-ink-900 text-ink-950 dark:text-white font-medium'
                         : 'text-ink-600 dark:text-ink-400 hover:text-ink-950 dark:hover:text-white'
                     }`}>{it.label}</a>
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
        <div className="space-y-2 border-l hairline pl-4">
          <a className="block text-ink-500 hover:text-ink-950 dark:hover:text-white" href="#install">Install</a>
          <a className="block text-ink-500 hover:text-ink-950 dark:hover:text-white" href="#state">Define state</a>
          <a className="block text-ink-500 hover:text-ink-950 dark:hover:text-white" href="#build">Build the graph</a>
          <a className="block text-ink-500 hover:text-ink-950 dark:hover:text-white" href="#run">Run it</a>
        </div>
      </aside>
    </div>
  );
};

const DocPage = ({ id }) => {
  const page = DOC_PAGES[id] || DOC_PAGES.quickstart;
  return (
    <div>
      <div className="mono text-[11px] uppercase tracking-[0.14em] text-ink-500 mb-4">DOCS / {page.crumb}</div>
      <h1 className="display-tight text-[44px] text-ink-950 dark:text-white">{page.title}</h1>
      <p className="mt-4 text-[16px] text-ink-600 dark:text-ink-400 leading-relaxed max-w-2xl">{page.lede}</p>
      <div className="mt-10">{page.body()}</div>
      <div className="mt-16 pt-8 border-t hairline flex items-center justify-between mono text-[12px] text-ink-500">
        <a href="#/docs" className="hover:text-ink-950 dark:hover:text-white inline-flex items-center gap-1.5">
          <Icon name="arrow-left" size={12} /> Back to docs
        </a>
        <span>Edit on GitHub →</span>
      </div>
    </div>
  );
};

const H2 = ({ children, id }) => <h2 id={id} className="font-display text-[26px] text-ink-950 dark:text-white tracking-tight font-medium mt-12 mb-3">{children}</h2>;
const P  = ({ children }) => <p className="text-[15px] text-ink-700 dark:text-ink-300 leading-relaxed mb-4">{children}</p>;
const Cb = ({ src, file, lang = 'java' }) => <div className="my-4"><CodeBlock filename={file} language={lang}>{highlightJava(src)}</CodeBlock></div>;
const Callout = ({ children }) => (
  <div className="my-6 p-4 pl-5 rounded-xl border-l-2 border-accent-500 bg-accent-50/60 dark:bg-accent-700/10 text-[14.5px] text-ink-700 dark:text-ink-300">{children}</div>
);
const Table = ({ headers, rows }) => (
  <div className="my-6 rounded-xl border hairline overflow-hidden">
    <table className="w-full text-[13.5px]">
      <thead className="bg-ink-50 dark:bg-ink-900">
        <tr>{headers.map(h => <th key={h} className="text-left mono text-[11px] uppercase tracking-wider text-ink-500 px-4 py-3 font-medium">{h}</th>)}</tr>
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
);

const DOC_PAGES = {
  quickstart: {
    crumb: 'GETTING STARTED / QUICKSTART',
    title: 'Quickstart',
    lede: 'Get a typed graph running on the JVM in under a minute. This walkthrough builds the smallest useful TraceGraph — one entry, one terminal — and layers on retries, tracing, and replay.',
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
      <P>Three nodes, two edges, one entry, one terminal. The fluent builder is the contract.</P>
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
    body: () => (<>
      <H2>Requirements</H2>
      <Table headers={['Dependency','Version']} rows={[
        ['JDK','21+ (records, pattern matching, virtual threads)'],
        ['Maven','3.9+'],
        ['SLF4J binding','any (logback, log4j2, jul)'],
      ]}/>
      <H2>Maven</H2>
      <Cb file="pom.xml" lang="xml" src={`<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-core</artifactId>
    <version>0.3.0</version>
</dependency>`} />
      <H2>Gradle</H2>
      <Cb file="build.gradle.kts" lang="kotlin" src={`implementation("site.tracegraph:tracegraph-core:0.3.0")
implementation("site.tracegraph:tracegraph-observability:0.3.0")
implementation("site.tracegraph:tracegraph-spring-boot-starter:0.3.0")`} />
    </>),
  },
  firstgraph: {
    crumb: 'GETTING STARTED / YOUR FIRST GRAPH',
    title: 'Your first graph',
    lede: 'A five-node order pipeline with retries, parallel enrichment, and an async LLM call.',
    body: () => (<>
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
    .edge("enrich", "score")
    .edge("score", "charge", s -> s.riskScore() < 0.5)
    .edge("charge", "ship", OrderState::charged)
    .terminal("ship")
    .build();`} />
    </>),
  },
  graphs: {
    crumb: 'CONCEPTS / GRAPHS & NODES',
    title: 'Graphs & nodes',
    lede: 'Graph<S> is the main runtime abstraction. State type S threads through every node.',
    body: () => (<>
      <H2>The four node types</H2>
      <Table headers={['Builder method','Use when']} rows={[
        ['node','Synchronous transitions. The 80% case.'],
        ['asyncNode','Network calls, LLM completions.'],
        ['parallel','Fan-out enrichment, multi-source fetches.'],
        ['routingNode','Dynamic next-node selection or sendAll fan-out.'],
      ]}/>
      <Callout><strong>Single type parameter.</strong> Use state composition (sub-records inside <Code>S</Code>) for sub-results. <Code>Node&lt;S, R&gt;</Code> is intentionally not a thing.</Callout>
    </>),
  },
  state:    { crumb:'CONCEPTS / STATE & CONTEXT', title:'State & context', lede:'State threads through your graph. Context is the per-execution toolkit handed to every node.', body: () => <P>Prefer records. Nodes return the next state; they don't mutate. The <Code>ctx.idempotencyKey()</Code> is the lever for at-least-once safety on resume.</P> },
  edges:    { crumb:'CONCEPTS / EDGES & ROUTING', title:'Edges & routing', lede:'Edges are first-class records. Predicates are pure functions of state.', body: () => <Cb src={`.edge("score", "approve", s -> s.risk() < 0.3)
.edge("score", "review",  s -> s.risk() < 0.7)
.edge("score", "reject")  // no predicate = fallthrough`} /> },
  parallel: { crumb:'CONCEPTS / ASYNC & PARALLEL', title:'Async & parallel', lede:'Default executor is virtual-thread-per-task. Branches inside parallel(...) are anonymous.', body: () => <P>Branches receive the same input state and merge in declaration order. First-by-declaration-order failure wins. Parallel collapses to a single trace step.</P> },
  retries:  { crumb:'RUNTIME / RETRIES', title:'Retries', lede:'Retry policy is a graph-definition concern, not runtime config.', body: () => <Cb src={`RetryPolicy policy = RetryPolicy.exponential(
    3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(2));`} /> },
  checkpoints: { crumb:'RUNTIME / CHECKPOINTS', title:'Checkpoints & resume', lede:'Checkpoints write after node exit, before edge resolution.', body: () => <Callout><strong>At-least-once on resume.</strong> Use <Code>ctx.idempotencyKey()</Code> to make side effects safe.</Callout> },
  memory:   { crumb:'RUNTIME / MEMORY', title:'Memory', lede:'MemoryStore is a scoped key-value store for cross-execution data.', body: () => <P>Implementations: <Code>InMemoryMemoryStore</Code>, <Code>FileMemoryStore</Code>, <Code>JdbcMemoryStore</Code>.</P> },
  interrupts: { crumb:'RUNTIME / INTERRUPTS & HITL', title:'Interrupts & HITL', lede:'Pause executions for human-in-the-loop approvals.', body: () => <Cb src={`.interruptBefore("issueRefund")
.checkpointStore(jdbcStore)`} /> },
  otel:     { crumb:'OBSERVABILITY / OPENTELEMETRY', title:'OpenTelemetry', lede:'One span per node. Retries are span events. Errors set StatusCode.ERROR.', body: () => <Cb src={`.listener(OtelNodeListener.usingGlobal())`} /> },
  replay:   { crumb:'OBSERVABILITY / REPLAY & DIFF', title:'Replay & diff', lede:'Plug a TraceRecorder in once, and every run becomes a deterministic replay artifact.', body: () => <Cb src={`Replayer<OrderState> replay = Replayer.of(trace);
ReplayRunner<OrderState> runner = ReplayRunner.of(trace, fixedGraph);
ExecutionResult<OrderState> fork = runner.reRunFrom(2);
TraceDiff<OrderState> d = TraceDiff.between(left, right);`} /> },
  spring:   { crumb:'INTEGRATION / SPRING BOOT', title:'Spring Boot starter', lede:'Auto-config registers no-op beans for the four SPIs. Your beans always win.', body: () => <Cb file="application.yml" lang="yaml" src={`tracegraph:
  web:
    enabled: true
  memory:
    jdbc:
      enabled: true
  llm:
    provider: openai`} /> },
  llm:      { crumb:'INTEGRATION / LLM CONNECTORS', title:'LLM connectors', lede:'Vendor-neutral LlmClient SPI. OpenAI and Anthropic adapters ship.', body: () => <Cb src={`LlmClient client = OpenAiLlmClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .build();`} /> },
  rest:     { crumb:'INTEGRATION / REST API', title:'REST API', lede:'Endpoints exposed by the Spring Boot starter when tracegraph.web.enabled is true.', body: () => <P>See the <a href="#/api" className="text-accent-600 dark:text-accent-100 underline underline-offset-4">API reference</a> for the full surface.</P> },
};

window.Docs = Docs;
