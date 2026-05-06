import { SectionLabel, StatusDot, Button, Code, CodeBlock, Badge, Icon } from '@/components'
import { Seo } from '@/components/Seo'
import { MODULES } from '@/data/mock'
import { highlightJava } from '@/lib/highlight'

export function Home() {
  return (
    <div className="fade-up">
      <Seo
        title="Typed agent runtime for the JVM"
        description="TraceGraph is a typed execution-graph runtime for the JVM. Replay any run, fork from any step, diff executions, and keep agents production-ready."
        path="/"
      />
      <Hero />
      <KeyStats />
      <FeatureGrid />
      <ModelSection />
      <ReplaySection />
      <ModulesSection />
      <CtaSection />
    </div>
  )
}

function Hero() {
  return (
    <section className="hero-gradient relative overflow-hidden">
      <div className="max-w-[1320px] mx-auto px-6 lg:px-8 pt-20 pb-24 lg:pt-28 lg:pb-32 text-center">
        <div className="inline-flex items-center gap-2 mono text-[11px] uppercase tracking-[0.14em] text-ink-600 dark:text-ink-400 mb-8">
          <StatusDot tone="ok" pulse />
          JVM-native · v0.3.0 · Apache-2.0
        </div>
        <h1 className="display text-[58px] sm:text-[78px] lg:text-[112px] text-ink-950 dark:text-white max-w-[1100px] mx-auto">
          Agents that<br />
          <span className="text-ink-400 dark:text-ink-500">survive production.</span>
        </h1>
        <p className="mt-8 text-[18px] lg:text-[20px] text-ink-600 dark:text-ink-400 max-w-2xl mx-auto leading-relaxed">
          TraceGraph is a typed execution-graph runtime for the JVM. Replay any run, fork from any step, diff two executions — all from plain Java records.
        </p>
        <div className="mt-10 flex items-center justify-center gap-3 flex-wrap">
          <Button as="a" href="/docs" size="lg" variant="primary" iconRight="arrow-right">Get started</Button>
          <Button as="a" href="/trace" size="lg" variant="ghost">Open trace explorer</Button>
        </div>
        <div className="mt-12 mono text-[12px] text-ink-500 flex items-center justify-center gap-2">
          <span className="opacity-60">$</span>
          <span>mvn dependency:get -Dartifact=site.tracegraph:tracegraph-core:0.3.0</span>
        </div>
      </div>
      <div className="max-w-[1320px] mx-auto px-6 lg:px-8 pb-20">
        <HeroViz />
      </div>
    </section>
  )
}

function HeroViz() {
  const steps = [
    { i: 0, name: 'validate', dur: 12 },
    { i: 1, name: 'enrich', dur: 184 },
    { i: 2, name: 'score', dur: 312 },
    { i: 3, name: 'charge', dur: 421 },
    { i: 4, name: 'ship', dur: 92 },
  ]

  return (
    <div className="rounded-3xl border hairline bg-white dark:bg-ink-950 shadow-card overflow-hidden">
      <div className="flex items-center justify-between px-5 py-3 border-b hairline">
        <div className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full bg-ink-200 dark:bg-ink-800" />
          <span className="w-2.5 h-2.5 rounded-full bg-ink-200 dark:bg-ink-800" />
          <span className="w-2.5 h-2.5 rounded-full bg-ink-200 dark:bg-ink-800" />
          <span className="mono text-[11px] text-ink-500 ml-3">order-pipeline · execution e9c4f1a2</span>
        </div>
        <div className="flex items-center gap-1.5 mono text-[11px] text-emerald-600 dark:text-emerald-400">
          <StatusDot tone="ok" pulse /> running
        </div>
      </div>
      <div className="grid lg:grid-cols-[1fr_360px] min-h-[460px]">
        <div className="grid-bg p-8 flex items-center justify-center border-r hairline">
          <svg viewBox="0 0 720 480" className="w-full max-w-[680px]">
            <defs>
              <marker id="hv-arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
                <path d="M0,0 L10,5 L0,10 z" fill="currentColor" />
              </marker>
            </defs>
            <g className="text-ink-950 dark:text-white" stroke="currentColor" strokeWidth="1.5" fill="none" markerEnd="url(#hv-arr)">
              <path d="M360 70 L360 130" /><path d="M360 180 L210 240" /><path d="M360 180 L510 240" />
              <path d="M210 290 L360 350" /><path d="M510 290 L360 350" /><path d="M360 400 L360 440" />
            </g>
            {([
              { x: 300, y: 32, w: 120, h: 38, label: 'entry', solid: true, accent: false, pulse: false, sub: '' },
              { x: 300, y: 130, w: 120, h: 50, label: 'validate', solid: false, accent: true, pulse: false, sub: '12ms · ok' },
              { x: 150, y: 240, w: 120, h: 50, label: 'enrich', solid: false, accent: false, pulse: false, sub: 'parallel · 3' },
              { x: 450, y: 240, w: 120, h: 50, label: 'score', solid: false, accent: false, pulse: false, sub: 'async · llm' },
              { x: 300, y: 350, w: 120, h: 50, label: 'charge', solid: false, accent: true, pulse: true, sub: 'retry 1/3' },
              { x: 320, y: 438, w: 80, h: 32, label: 'terminal', solid: true, accent: false, pulse: false, sub: '' },
            ] as const).map((n, i) => (
              <g key={i}>
                <rect
                  x={n.x}
                  y={n.y}
                  width={n.w}
                  height={n.h}
                  rx="8"
                  fill={n.solid ? 'currentColor' : (n.accent ? 'rgba(13,143,99,0.08)' : 'white')}
                  stroke={n.accent ? '#0d8f63' : 'currentColor'}
                  strokeWidth={n.accent ? 1.6 : 1.4}
                  className={n.solid ? '' : 'dark:fill-ink-950'}
                />
                <text
                  x={n.x + n.w / 2}
                  y={n.y + (n.sub ? 22 : n.h / 2 + 4)}
                  textAnchor="middle"
                  fill={n.solid ? 'white' : 'currentColor'}
                  className="mono fill-ink-950 dark:fill-white"
                  fontSize="12"
                  fontWeight="500"
                >
                  {n.label}
                </text>
                {n.sub && (
                  <text
                    x={n.x + n.w / 2}
                    y={n.y + 38}
                    textAnchor="middle"
                    className="mono"
                    fill={n.accent ? '#0d8f63' : '#6b7280'}
                    fontSize="10"
                  >
                    {n.sub}
                  </text>
                )}
              </g>
            ))}
          </svg>
        </div>
        <div className="p-6 space-y-5 bg-ink-50/40 dark:bg-ink-950/60">
          <div>
            <SectionLabel>State diff · charge</SectionLabel>
            <div className="mt-3 mono text-[12px] space-y-1">
              <div className="text-ink-500">  riskScore: 0.18</div>
              <div className="text-rose-600 dark:text-rose-400">- charged: false</div>
              <div className="text-emerald-600 dark:text-emerald-400">+ charged: true</div>
              <div className="text-amber-600 dark:text-amber-400">  attempts: 2 (recovered)</div>
            </div>
          </div>
          <div className="border-t hairline pt-4">
            <SectionLabel>Trace · 5 steps</SectionLabel>
            <div className="mt-3 space-y-1.5 mono text-[11.5px]">
              {steps.map((s) => (
                <div key={s.i} className="flex items-center justify-between text-ink-700 dark:text-ink-300">
                  <span>#{String(s.i).padStart(2, '0')} {s.name}</span>
                  <span className="text-ink-500">{s.dur}ms</span>
                </div>
              ))}
            </div>
          </div>
          <a href="/trace" className="arrow-link mono text-[12px] text-accent-600 dark:text-accent-100 inline-flex items-center gap-1.5 mt-2">
            Open in trace explorer <span className="arrow">→</span>
          </a>
        </div>
      </div>
    </div>
  )
}

function KeyStats() {
  const stats = [
    ['Virtual threads', '1·per node', 'Default executor: virtual-thread-per-task. Lazy, scoped to the run.'],
    ['Retry policies', 'fixed · exp', 'Per-node or graph-wide. Idempotency keys exposed via ctx.'],
    ['Replay fidelity', 'step·level', 'Re-execute from any saved step. Forks track lineage automatically.'],
    ['Heavy core deps', 'zero', 'SLF4J only. No Spring, no Jackson, no OpenTelemetry in core.'],
  ] as const

  return (
    <section className="border-t hairline">
      <div className="max-w-[1320px] mx-auto px-6 lg:px-8 py-20 grid md:grid-cols-4 gap-px bg-ink-100 dark:bg-ink-900 rounded-2xl overflow-hidden">
        {stats.map(([label, num, sub], i) => (
          <div key={i} className="bg-white dark:bg-ink-950 p-7">
            <div className="mono text-[11px] uppercase tracking-wider text-ink-500">{label}</div>
            <div className="display-tight text-[36px] mt-3 text-ink-950 dark:text-white">{num}</div>
            <p className="mt-3 text-[13.5px] text-ink-600 dark:text-ink-400 leading-relaxed">{sub}</p>
          </div>
        ))}
      </div>
    </section>
  )
}

function FeatureGrid() {
  const features = [
    { icon: 'box', title: 'Typed graphs, plain Java', body: 'Build graphs from records and lambdas. Compile-time safety; no DSL, no annotations, no reflection.' },
    { icon: 'history', title: 'Replay any execution', body: 'Plug in a TraceRecorder. Re-run a saved trace step by step against the same graph — or a modified one.' },
    { icon: 'refresh-cw', title: 'Retries with backoff', body: 'Fixed or exponential, per-node or graph-default. Errors short-circuit. onRetry fires on each attempt.' },
    { icon: 'database', title: 'Checkpoints & resume', body: 'Pluggable CheckpointStore writes after each node exit. Survive process boundaries; resume by id.' },
    { icon: 'activity', title: 'OpenTelemetry, native', body: 'One span per node, retries as span events, errors set StatusCode.ERROR. State diffs as events.' },
    { icon: 'package', title: 'Spring Boot starter', body: 'Auto-config for all four SPIs, REST inspector at /tracegraph/traces, SSE streaming.' },
  ]

  return (
    <section className="border-t hairline">
      <div className="max-w-[1320px] mx-auto px-6 lg:px-8 py-24">
        <div className="grid md:grid-cols-[280px_1fr] gap-12 mb-16">
          <SectionLabel num="01">Why</SectionLabel>
          <div>
            <h2 className="display-tight text-[44px] lg:text-[56px] text-ink-950 dark:text-white text-balance">
              Six features that make production agents survivable.
            </h2>
            <p className="mt-5 text-[16px] text-ink-600 dark:text-ink-400 max-w-2xl">
              No hidden scheduler. No prompt-driven control flow. The graph definition <em>is</em> the runtime, and every transition is observable.
            </p>
          </div>
        </div>
        <div className="grid md:grid-cols-3 gap-px bg-ink-100 dark:bg-ink-900 rounded-2xl overflow-hidden border hairline">
          {features.map((f, i) => (
            <div key={i} className="bg-white dark:bg-ink-950 p-8 min-h-[220px]">
              <div className="w-9 h-9 rounded-lg bg-ink-100 dark:bg-ink-900 flex items-center justify-center text-ink-700 dark:text-ink-300 mb-5">
                <Icon name={f.icon} size={18} />
              </div>
              <div className="mono text-[10px] tracking-widest text-ink-400 dark:text-ink-500 mb-2">F·0{i + 1}</div>
              <h3 className="text-[16px] font-semibold text-ink-950 dark:text-white tracking-tight">{f.title}</h3>
              <p className="mt-2 text-[14px] text-ink-600 dark:text-ink-400 leading-relaxed">{f.body}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

function ModelSection() {
  const code = `// The shape of a real production graph.
record OrderState(String id, boolean valid,
                  boolean charged, boolean shipped) {}

Graph<OrderState> graph = Graph.<OrderState>builder()
    .node("validate", (s, ctx) -> s.withValid(true))
    .asyncNode("score", (s, ctx) ->
        CompletableFuture.supplyAsync(() -> score(s)))
    .parallel("enrich", List.of(
            (s, ctx) -> withProfile(s),
            (s, ctx) -> withFraudCheck(s),
            (s, ctx) -> withInventory(s)),
        (input, branches) -> merge(input, branches))
    .node("charge", chargeFn,
        RetryPolicy.exponential(3, Duration.ofMillis(100),
                                  2.0, Duration.ofSeconds(2)))
    .node("ship", shipFn)
    .entry("validate")
    .edge("validate", "enrich", OrderState::valid)
    .edge("enrich",   "score")
    .edge("score",    "charge")
    .edge("charge",   "ship",  OrderState::charged)
    .terminal("ship")
    .traceRecorder(new RecordingTraceRecorder(store))
    .listener(OtelNodeListener.usingGlobal())
    .build();

ExecutionResult<OrderState> r = graph.run(seed);`

  return (
    <section className="border-t hairline bg-ink-50/60 dark:bg-ink-950/40">
      <div className="max-w-[1320px] mx-auto px-6 lg:px-8 py-24">
        <div className="grid md:grid-cols-[280px_1fr] gap-12 mb-14">
          <SectionLabel num="02">Model</SectionLabel>
          <h2 className="display-tight text-[44px] lg:text-[56px] text-ink-950 dark:text-white text-balance">
            One entry node. Typed state.<br />No hidden control flow.
          </h2>
        </div>
        <div className="grid lg:grid-cols-[1fr_1.2fr] gap-12 items-start">
          <div>
            <h3 className="text-[28px] text-ink-950 dark:text-white tracking-tight font-medium">What a graph actually does</h3>
            <p className="mt-4 text-[15px] text-ink-600 dark:text-ink-400 leading-relaxed">
              A graph starts at a single named entry node, threads typed state through deterministic transitions,
              and stops at one of its terminals. There is no scheduler whispering instructions to your nodes.
            </p>
            <ul className="mt-8 space-y-0">
              {([
                ['node', 'Pure function: (state, ctx) → state. Sync, async, or parallel branches.'],
                ['edge', 'First-class record. Predicate is a pure function of state — replay-safe.'],
                ['context', 'Carries idempotency key, listener hooks, memory, executor. Never mutates state.'],
                ['result', 'ExecutionResult<S> — id, finalState, path, status, error.'],
              ] as const).map(([k, v]) => (
                <li key={k} className="grid grid-cols-[100px_1fr] gap-4 py-3 border-t hairline last:border-b text-[14.5px]">
                  <span className="mono text-[12px] text-ink-500">{k}</span>
                  <span className="text-ink-700 dark:text-ink-300">{v}</span>
                </li>
              ))}
            </ul>
          </div>
          <CodeBlock filename="OrderPipeline.java" language="java">{highlightJava(code)}</CodeBlock>
        </div>
      </div>
    </section>
  )
}

function ReplaySection() {
  const code = `// Load a saved trace and walk it.
ExecutionTrace<OrderState> trace = store.load(id).orElseThrow();

Replayer<OrderState> replay = Replayer.of(trace);
for (int i = 0; i < replay.stepCount(); i++) {
    TraceStep<OrderState> step = replay.stepAt(i);
    // step.before(), step.after(), step.attempts()
}

// Fork: re-run from step 1 against a modified graph.
ReplayRunner<OrderState> runner = ReplayRunner.of(trace, fixedGraph);
ExecutionResult<OrderState> fork = runner.reRunFrom(1);

// Compare two runs.
TraceDiff<OrderState> d = TraceDiff.between(trace, fork);
if (!d.identical()) {
    System.out.println("diverged at " + d.divergenceIndex());
}`

  return (
    <section className="border-t border-white/5 bg-ink-950 text-white">
      <div className="max-w-[1320px] mx-auto px-6 lg:px-8 py-24">
        <div className="grid md:grid-cols-[280px_1fr] gap-12 mb-14">
          <SectionLabel num="03" className="text-ink-400">Replay</SectionLabel>
          <h2 className="display-tight text-[44px] lg:text-[56px] text-balance">
            The killer feature: re-run any agent execution, with full state diff.
          </h2>
        </div>
        <div className="grid lg:grid-cols-[1.2fr_1fr] gap-12 items-start">
          <div className="rounded-2xl border border-white/10 overflow-hidden bg-black">
            <div className="flex items-center justify-between px-4 py-2.5 border-b border-white/10">
              <span className="mono text-[11px] text-white/50">Replay.java</span>
              <span className="mono text-[10px] text-white/30 uppercase tracking-wider">JAVA</span>
            </div>
            <pre className="code-block p-6 overflow-x-auto scroll-thin text-white/85"
              dangerouslySetInnerHTML={{ __html: highlightJava(code) }} />
          </div>
          <div>
            <h3 className="text-[26px] font-medium tracking-tight">Lineage. Diffs. Forks.</h3>
            <p className="mt-3 text-[15px] text-ink-400 leading-relaxed">
              Every replay produces a fresh executionId but carries forkedFromExecutionId and the step it branched from.
            </p>
            <ul className="mt-8 space-y-0">
              {([
                ['store', 'In-memory · JSON file · JDBC. All ship in observability.'],
                ['resume', 'One trace per executionId; resume appends to the prior trace.'],
                ['retries', "Don't add steps; attempts tracks the count."],
              ] as const).map(([k, v]) => (
                <li key={k} className="grid grid-cols-[100px_1fr] gap-4 py-3 border-t border-white/10 text-[14.5px]">
                  <span className="mono text-[12px] text-ink-500">{k}</span>
                  <span className="text-white/80">{v}</span>
                </li>
              ))}
            </ul>
            <div className="mt-8">
              <Button as="a" href="/trace" variant="ghost" size="md" iconRight="arrow-right"
                className="!border-white/20 !text-white hover:!bg-white/10">
                Open the trace explorer
              </Button>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

function ModulesSection() {
  return (
    <section className="border-t hairline">
      <div className="max-w-[1320px] mx-auto px-6 lg:px-8 py-24">
        <div className="grid md:grid-cols-[280px_1fr] gap-12 mb-12">
          <SectionLabel num="04">Modules</SectionLabel>
          <div>
            <h2 className="display-tight text-[44px] lg:text-[56px] text-ink-950 dark:text-white text-balance">
              Eleven modules. Add only what you need.
            </h2>
            <p className="mt-5 text-[16px] text-ink-600 dark:text-ink-400 max-w-2xl">
              Start with <Code>core</Code>, add <Code>observability</Code> second, then storage and connectors slice by slice.
            </p>
          </div>
        </div>
        <div className="rounded-2xl border hairline overflow-hidden bg-white dark:bg-ink-950">
          {MODULES.map((m, i) => (
            <div key={m.name} className={`grid grid-cols-[280px_1fr_auto] gap-6 items-center px-6 py-4 ${i > 0 ? 'border-t hairline' : ''}`}>
              <div className="mono text-[13px] font-medium text-ink-950 dark:text-white">{m.name}</div>
              <div className="text-[14px] text-ink-600 dark:text-ink-400">{m.desc}</div>
              <div className="flex items-center gap-3">
                <Badge tone={m.status === 'stable' ? 'ok' : m.status === 'experimental' ? 'warn' : 'neutral'}>
                  <StatusDot tone={m.status === 'stable' ? 'ok' : m.status === 'experimental' ? 'warn' : 'neutral'} />
                  {m.status}
                </Badge>
                <span className="mono text-[11px] text-ink-500 w-12 text-right">{m.cov}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

function CtaSection() {
  return (
    <section className="border-t hairline bg-ink-50/60 dark:bg-ink-950/40">
      <div className="max-w-[1320px] mx-auto px-6 lg:px-8 py-28 text-center">
        <SectionLabel num="05" className="mb-6 inline-block">Start</SectionLabel>
        <h2 className="display-tight text-[48px] lg:text-[68px] text-ink-950 dark:text-white max-w-3xl mx-auto text-balance">
          Three commands and you have a graph that runs, retries, and replays.
        </h2>
        <p className="mt-6 text-[17px] text-ink-600 dark:text-ink-400 max-w-xl mx-auto">
          No SaaS. No agent. No daemon. <Code>mvn install</Code>, then write Java.
        </p>
        <div className="mt-10 flex items-center justify-center gap-3 flex-wrap">
          <Button as="a" href="/docs" size="lg" variant="primary" iconRight="arrow-right">Quickstart guide</Button>
          <Button as="a" href="/studio" size="lg" variant="ghost">Try the graph studio</Button>
        </div>
      </div>
    </section>
  )
}
