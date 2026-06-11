// All static data + Java syntax highlighter.

window.MODULES = [
  { name: 'tracegraph-core',                     desc: 'Graph<S>, builder, executor, retries, parallel, async. Zero deps beyond SLF4J.', status: 'stable',       cov: '94%' },
  { name: 'tracegraph-observability',            desc: 'OtelNodeListener, RecordingTraceRecorder, replay, diff. Optional OpenTelemetry.', status: 'stable',       cov: '88%' },
  { name: 'tracegraph-memory-jdbc',              desc: 'JDBC-backed MemoryStore + CheckpointStore. PostgreSQL & H2 tested.',              status: 'stable',       cov: '82%' },
  { name: 'tracegraph-memory-file',              desc: 'File-system backed stores for local dev and small deployments.',                  status: 'stable',       cov: '90%' },
  { name: 'tracegraph-llm',                      desc: 'Vendor-neutral LlmClient SPI. Token usage flows into traces.',                    status: 'stable',       cov: '79%' },
  { name: 'tracegraph-llm-openai',               desc: 'OpenAI-compatible adapter (OpenAI, Together, vLLM, …).',                          status: 'stable',       cov: '74%' },
  { name: 'tracegraph-llm-anthropic',            desc: 'Anthropic Messages API adapter with streaming.',                                  status: 'stable',       cov: '71%' },
  { name: 'tracegraph-spring-boot-starter',      desc: 'Auto-config for the four SPIs, REST inspector at /tracegraph/traces, SSE.',       status: 'stable',       cov: '83%' },
  { name: 'tracegraph-eval',                     desc: 'Test harness for graphs: deterministic seeds, golden traces, CI assertions.',    status: 'experimental', cov: '61%' },
  { name: 'tracegraph-mcp',                      desc: 'Expose graphs as MCP tools and consume MCP servers as nodes.',                    status: 'experimental', cov: '52%' },
  { name: 'tracegraph-bom',                      desc: 'Maven BOM for version alignment across all modules.',                             status: 'stable',       cov: 'n/a'  },
];

window.TRACE = {
  executionId: 'e9c4f1a2-8b40-4e15-9c2d-0a4e7d6b5e21',
  graph: 'order-pipeline',
  status: 'FAILED',
  startedAt: '2026-05-06 10:42:18.221',
  duration: 1041,
  steps: [
    { i: 0, name: 'validate',  kind: 'node',     status: 'ok',       attempts: 1, async: false, dur: 12,  t0: 0,
      before: { id:'o-91', valid:false, charged:false, shipped:false },
      after:  { id:'o-91', valid:true,  charged:false, shipped:false },
      diff: ['  id: o-91','- valid: false','+ valid: true','  charged: false'] },
    { i: 1, name: 'enrich',    kind: 'parallel', status: 'parallel', attempts: 1, async: false, dur: 184, t0: 14, branches: 3,
      before: { id:'o-91', valid:true, profile:null, fraud:null, inventory:null },
      after:  { id:'o-91', valid:true, profile:'PRO', fraud:0.04, inventory:'IN_STOCK' },
      diff: ['  id: o-91','+ profile: PRO','+ fraud: 0.04','+ inventory: IN_STOCK'] },
    { i: 2, name: 'score',     kind: 'async',    status: 'ok',       attempts: 1, async: true, dur: 312, t0: 200,
      usage: { prompt: 482, completion: 96 },
      before: { id:'o-91', riskScore:null },
      after:  { id:'o-91', riskScore:0.18 },
      diff: ['  id: o-91','+ riskScore: 0.18'] },
    { i: 3, name: 'charge',    kind: 'node',     status: 'retry',    attempts: 2, async: false, dur: 421, t0: 514,
      before: { id:'o-91', charged:false, attempts:0 },
      after:  { id:'o-91', charged:true,  attempts:2 },
      diff: ['  attempt 1: NetworkTimeoutException','! retrying after 200ms (exponential)','  attempt 2: ok','+ charged: true','+ attempts: 2 (recovered)'] },
    { i: 4, name: 'ship',      kind: 'node',     status: 'err',      attempts: 1, async: false, dur: 92,  t0: 938,
      err: 'CarrierUnavailableException: gateway returned 503',
      before: { id:'o-91', shipped:false },
      after:  null,
      diff: ['  id: o-91','! ship attempt 1: CarrierUnavailableException','! gateway returned 503','! retries exhausted (max=1)'] },
  ],
  logs: [
    { t: 0,    lv: 'evt',  msg: 'execution started · entry=validate · idem=ord-91-v2' },
    { t: 12,   lv: 'info', msg: 'validate completed · 12ms' },
    { t: 14,   lv: 'evt',  msg: 'parallel(enrich) fan-out · 3 branches' },
    { t: 198,  lv: 'info', msg: 'parallel(enrich) merged · 184ms' },
    { t: 201,  lv: 'evt',  msg: 'async(score) submitted to virtual-thread executor' },
    { t: 512,  lv: 'info', msg: 'async(score) completed · 312ms · usage{p=482,c=96}' },
    { t: 514,  lv: 'info', msg: 'charge attempt 1 of 3' },
    { t: 717,  lv: 'warn', msg: 'charge attempt 1 failed · NetworkTimeoutException · retrying in 200ms' },
    { t: 919,  lv: 'info', msg: 'charge attempt 2 succeeded · 218ms' },
    { t: 938,  lv: 'info', msg: 'ship attempt 1 of 1' },
    { t: 1030, lv: 'err',  msg: 'ship failed · CarrierUnavailableException: gateway 503 · no retries left' },
    { t: 1041, lv: 'evt',  msg: 'execution finished · status=FAILED · path=[validate, enrich, score, charge]' },
  ],
};

window.TRACE_LIST = [
  { id: 'e9c4f1a2', graph: 'order-pipeline', status: 'FAILED'    },
  { id: '3a82d019', graph: 'order-pipeline', status: 'COMPLETED' },
  { id: '7f1d92cb', graph: 'rag-agent',      status: 'COMPLETED' },
  { id: 'b40e87aa', graph: 'react-agent',    status: 'INTERRUPTED' },
];

window.NODE_LAYOUT = {
  validate: { x: 320, y: 70,  w: 120, h: 56, kind: 'node' },
  enrich:   { x: 320, y: 180, w: 120, h: 56, kind: 'parallel ∥' },
  score:    { x: 320, y: 290, w: 120, h: 56, kind: 'async' },
  charge:   { x: 200, y: 400, w: 120, h: 56, kind: 'node · retry(3)' },
  ship:     { x: 440, y: 400, w: 120, h: 56, kind: 'node' },
};

window.EDGES = [
  { from: 'validate', to: 'enrich', label: 'valid' },
  { from: 'enrich',   to: 'score'                  },
  { from: 'score',    to: 'charge', label: 'risk<.5' },
  { from: 'charge',   to: 'ship',   label: 'charged' },
];

window.STUDIO_NODES = [
  { name: 'validate', kind: 'node',     fn: 'OrderNodes::validate',  retry: '—',                  entry: true,  terminal: false },
  { name: 'enrich',   kind: 'parallel', fn: 'List.of(profile, fraud, inv)', retry: '—',           entry: false, terminal: false },
  { name: 'score',    kind: 'async',    fn: 'OrderNodes::scoreAsync',retry: '—',                  entry: false, terminal: false },
  { name: 'charge',   kind: 'node',     fn: 'OrderNodes::charge',    retry: 'exponential(3)',     entry: false, terminal: false },
  { name: 'ship',     kind: 'node',     fn: 'OrderNodes::ship',      retry: '—',                  entry: false, terminal: true  },
];

window.DOCS_TREE = [
  { section: 'Getting started', items: [
    { id:'quickstart',  label:'Quickstart' },
    { id:'install',     label:'Installation' },
    { id:'firstgraph',  label:'Your first graph' },
  ]},
  { section: 'Concepts', items: [
    { id:'graphs',   label:'Graphs & nodes' },
    { id:'state',    label:'State & context' },
    { id:'edges',    label:'Edges & routing' },
    { id:'parallel', label:'Async & parallel' },
  ]},
  { section: 'Runtime', items: [
    { id:'retries',     label:'Retries' },
    { id:'checkpoints', label:'Checkpoints & resume' },
    { id:'memory',      label:'Memory' },
    { id:'interrupts',  label:'Interrupts & HITL' },
  ]},
  { section: 'Observability', items: [
    { id:'otel',   label:'OpenTelemetry' },
    { id:'replay', label:'Replay & diff' },
  ]},
  { section: 'Integration', items: [
    { id:'spring', label:'Spring Boot starter' },
    { id:'llm',    label:'LLM connectors' },
    { id:'rest',   label:'REST API' },
  ]},
];

window.CHANGELOG = [
  { v: '0.3.0', date: '2026-05-04', tag: 'latest', notes: [
    { kind:'feat',     text:'Replay & diff: TraceRecorder, Replayer, ReplayRunner, TraceDiff. Fork from any step against a modified graph.' },
    { kind:'feat',     text:'JDBC checkpoint store with at-least-once resume semantics.' },
    { kind:'feat',     text:'Anthropic LLM connector (streaming + tool use).' },
    { kind:'breaking', text:'Node SPI: signature is now (S, NodeContext) → S. Sub-result generic R was removed; compose state instead.' },
    { kind:'fix',      text:'Parallel branches no longer leak threads when a sibling branch fails fast.' },
  ]},
  { v: '0.2.1', date: '2026-04-09', notes: [
    { kind:'fix',     text:'OtelNodeListener no longer holds a hard reference to GlobalOpenTelemetry on hot reload.' },
    { kind:'improve', text:'Builder validates entry/terminal reachability at build() time, not first run().' },
    { kind:'improve', text:'Better error messages on unreachable terminals.' },
  ]},
  { v: '0.2.0', date: '2026-03-22', notes: [
    { kind:'feat',     text:'Spring Boot starter with REST inspector and SSE trace stream.' },
    { kind:'feat',     text:'parallel(...) builder method with anonymous branches and merge function.' },
    { kind:'breaking', text:'Edge predicates are now Predicate<S> instead of BiPredicate<S, NodeContext>. Use ctx via state composition.' },
  ]},
  { v: '0.1.0', date: '2026-02-14', notes: [
    { kind:'feat', text:'First public release. Graph<S>, sync nodes, edges, retries, in-memory checkpoint store.' },
  ]},
];

window.API_GROUPS = [
  { group: 'Traces', endpoints: [
    { m:'GET',    p:'/tracegraph/traces',                          desc:'List recent executions, paginated. Filter by graph or status.', returns:'Page<ExecutionSummary>' },
    { m:'GET',    p:'/tracegraph/traces/{executionId}',            desc:'Fetch a full ExecutionTrace<S> document, including all steps.',  returns:'ExecutionTrace<JsonNode>' },
    { m:'GET',    p:'/tracegraph/traces/{executionId}/steps/{i}',  desc:'Fetch a single step and its before/after state.',                 returns:'TraceStep<JsonNode>' },
    { m:'DELETE', p:'/tracegraph/traces/{executionId}',            desc:'Delete a stored execution. Lineage links are preserved.',         returns:'204 No Content' },
  ]},
  { group: 'Replay', endpoints: [
    { m:'POST', p:'/tracegraph/traces/{executionId}/replay',           desc:'Re-run a saved trace step by step against the current graph.',   returns:'ExecutionResult<JsonNode>' },
    { m:'POST', p:'/tracegraph/traces/{executionId}/replay?step={n}',  desc:'Fork: re-run from step n against the current graph.',           returns:'ExecutionResult<JsonNode>' },
    { m:'POST', p:'/tracegraph/traces/diff',                           desc:'Diff two executions; body is { left, right }.',                  returns:'TraceDiff<JsonNode>' },
  ]},
  { group: 'Stream', endpoints: [
    { m:'GET',  p:'/tracegraph/stream',                                desc:'SSE: live node enter/exit/retry/error events for every execution (requires a LiveTraceFeed wired via traceRecorder).', returns:'text/event-stream' },
    { m:'GET',  p:'/tracegraph/stream?execution={id}',                 desc:'SSE filtered to a single execution.',                            returns:'text/event-stream' },
    { m:'POST', p:'/tracegraph/traces/stream',                         desc:'Run-and-watch: starts a new execution with the posted initial state and streams its NodeEvents.', returns:'text/event-stream' },
  ]},
  { group: 'Graph definitions', endpoints: [
    { m:'GET',  p:'/tracegraph/graphs',                                desc:'List registered graphs and their node/edge counts.',             returns:'List<GraphDescriptor>' },
    { m:'GET',  p:'/tracegraph/graphs/{name}',                         desc:'Mermaid + JSON descriptor for a registered graph.',              returns:'GraphDescriptor' },
  ]},
];

// --- Java syntax highlighter (regex-based; kept in scope so docs/home use it) ---
window.highlightJava = function(src) {
  const escape = s => s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  let s = escape(src);

  // line comments
  s = s.replace(/(\/\/[^\n]*)/g, '<span class="cm">$1</span>');
  // strings
  s = s.replace(/("(?:\\.|[^"\\])*")/g, '<span class="st">$1</span>');
  // keywords
  const kws = ['record','class','interface','enum','public','private','protected','static','final','abstract','new','return','if','else','for','while','do','switch','case','break','continue','throw','throws','try','catch','finally','import','package','extends','implements','this','super','void','true','false','null','var'];
  s = s.replace(new RegExp(`\\b(${kws.join('|')})\\b`,'g'), '<span class="kw">$1</span>');
  // types: capitalized identifiers
  s = s.replace(/\b([A-Z][A-Za-z0-9_]+)\b/g, '<span class="ty">$1</span>');
  // numbers
  s = s.replace(/\b(\d+(?:\.\d+)?)\b/g, '<span class="nm">$1</span>');
  return s;
};
