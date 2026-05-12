import { useEffect, useState } from 'react'
import { Panel, Badge, Button } from '@/components'
import { Seo } from '@/components/Seo'
import { MOCK_TRACE, MOCK_TRACE_LIST } from '@/data/mock'
import { api } from '@/lib/api'
import { useBotDetected } from '@/hooks/useBotDetected'
import type { ExecutionTrace, TraceDiff, TraceStep, TraceSummary } from '@/types'

type ViewMode = 'inspect' | 'diff'

function normaliseTrace(raw: unknown): ExecutionTrace {
  if (!raw || typeof raw !== 'object') return MOCK_TRACE
  const r = raw as Record<string, unknown>

  const steps: TraceStep[] = Array.isArray(r.steps)
    ? (r.steps as Record<string, unknown>[]).map((s, idx) => {
        const nodeName = (s.nodeName as string) ?? (s.name as string) ?? `step-${idx}`
        const attempts = typeof s.attempts === 'number' ? s.attempts : 1
        const dur = typeof s.duration === 'string'
          ? parseFloat(s.duration.replace('PT', '').replace('S', '')) * 1000
          : typeof s.dur === 'number' ? s.dur : 0
        const hasErr = !!s.error
        const status = hasErr ? 'err' : attempts > 1 ? 'retry' : Array.isArray(s.children) && (s.children as unknown[]).length > 0 ? 'parallel' : 'ok'
        const errMsg = s.error && typeof s.error === 'object'
          ? `${(s.error as Record<string, string>).className}: ${(s.error as Record<string, string>).message}`
          : typeof s.error === 'string' ? s.error : undefined
        return {
          i: typeof s.index === 'number' ? s.index : idx,
          name: nodeName,
          kind: 'sync',
          status,
          attempts,
          async: false,
          dur: Math.round(dur),
          t0: idx * 100,
          before: (s.before as Record<string, unknown>) ?? null,
          after: (s.after as Record<string, unknown>) ?? null,
          diff: [],
          err: errMsg,
          index: typeof s.index === 'number' ? s.index : idx,
          nodeName,
        }
      })
    : []

  const startedAt = (r.startedAt as string) ?? new Date().toISOString()
  const completedAt = r.completedAt as string | undefined
  const durationMs = completedAt
    ? new Date(completedAt).getTime() - new Date(startedAt).getTime()
    : typeof r.duration === 'number' ? r.duration : steps.reduce((acc, s) => acc + s.dur, 0)

  return {
    executionId: (r.executionId as string) ?? 'unknown',
    graph: (r.graph as string) ?? 'graph',
    initialState: r.initialState as Record<string, unknown> | null,
    finalState: r.finalState as Record<string, unknown> | null,
    status: (r.status as ExecutionTrace['status']) ?? 'COMPLETED',
    startedAt,
    completedAt,
    duration: durationMs,
    steps,
    logs: Array.isArray(r.logs) ? (r.logs as unknown as ExecutionTrace['logs']) : [],
    forkedFromExecutionId: r.forkedFromExecutionId as string | null,
    forkedFromStepIndex: r.forkedFromStepIndex as number,
  }
}

export function TraceExplorer() {
  const isBot = useBotDetected()
  if (isBot) return <div className="flex items-center justify-center h-[60vh] text-ink-400 mono text-[13px]">Access denied.</div>

  const [activeIdx, setActiveIdx] = useState(0)
  const [trace, setTrace] = useState<ExecutionTrace>(MOCK_TRACE)
  const [traceList, setTraceList] = useState<TraceSummary[]>(MOCK_TRACE_LIST)
  const [isLive, setIsLive] = useState(false)
  const [viewMode, setViewMode] = useState<ViewMode>('inspect')
  const [diffIdA, setDiffIdA] = useState('')
  const [diffIdB, setDiffIdB] = useState('')
  const [diffResult, setDiffResult] = useState<TraceDiff | null>(null)
  const [diffLoading, setDiffLoading] = useState(false)
  const [liveEvents, setLiveEvents] = useState<{ t: number; type: string; node: string }[]>([])

  useEffect(() => {
    api.traces
      .list(20)
      .then((data) => {
        const ids: string[] = Array.isArray(data)
          ? (data as string[])
          : ((data as { items: string[] }).items ?? [])
        if (ids.length > 0) {
          setIsLive(true)
          setTraceList(ids.map((id) => ({ id, graph: 'graph', status: 'COMPLETED' })))
          loadTrace(ids[0])
        }
      })
      .catch(() => {
        setIsLive(false)
      })
  }, [])

  useEffect(() => {
    const es = api.traces.stream()
    if (!es) return
    const t0 = Date.now()
    es.onmessage = (e) => {
      try {
        const payload = JSON.parse(e.data) as Record<string, unknown>
        const type = (payload.type as string) ?? e.type ?? 'event'
        const node = (payload.nodeName as string) ?? ''
        setLiveEvents((prev) => [...prev.slice(-49), { t: Date.now() - t0, type, node }])
      } catch { /* non-json event */ }
    }
    return () => es.close()
  }, [])

  function loadTrace(id: string) {
    api.traces
      .get(id)
      .then((raw) => {
        if (raw && typeof raw === 'object') {
          setTrace(normaliseTrace(raw))
          setActiveIdx(0)
        }
      })
      .catch(() => {})
  }

  function handleDelete(id: string) {
    if (!confirm(`Delete trace ${id.slice(0, 12)}…?`)) return
    api.traces
      .delete(id)
      .then(() => {
        api.traces.list(20).then((data) => {
          const ids: string[] = Array.isArray(data) ? (data as string[]) : []
          setTraceList(ids.map((i) => ({ id: i, graph: 'graph', status: 'COMPLETED' })))
          if (ids.length > 0) loadTrace(ids[0])
        }).catch(() => {})
      })
      .catch(() => alert(`DELETE /tracegraph/traces/${id} failed`))
  }

  function handleDiff() {
    if (!diffIdA || !diffIdB) return
    setDiffLoading(true)
    api.traces
      .diff(diffIdA, diffIdB)
      .then((r) => { setDiffResult(r as TraceDiff); setDiffLoading(false) })
      .catch(() => { alert('Diff failed'); setDiffLoading(false) })
  }

  const step = trace.steps[activeIdx] ?? trace.steps[0]

  return (
    <div className="max-w-[1500px] mx-auto px-4 lg:px-6 py-6 fade-up">
      <Seo
        title="Trace explorer"
        description="Inspect execution traces, replay forks, and compare state changes in the TraceGraph trace explorer."
        path="/trace"
        noindex
      />

      <BackendBanner isLive={isLive} />

      <div className="mt-3 flex items-center gap-2">
        {(['inspect', 'diff'] as ViewMode[]).map((m) => (
          <button key={m}
            onClick={() => setViewMode(m)}
            className={`mono text-[11px] uppercase tracking-wider px-3 py-1.5 rounded-full transition ${
              viewMode === m ? 'bg-accent-500 text-white' : 'border hairline text-ink-500 hover:text-ink-950 dark:hover:text-white'
            }`}>
            {m === 'inspect' ? 'Inspect' : 'Diff / Compare'}
          </button>
        ))}
      </div>

      {viewMode === 'diff' ? (
        <DiffView
          traceList={traceList}
          diffIdA={diffIdA} setDiffIdA={setDiffIdA}
          diffIdB={diffIdB} setDiffIdB={setDiffIdB}
          diffResult={diffResult}
          loading={diffLoading}
          onDiff={handleDiff}
        />
      ) : (
        <>
          <div className="mt-3">
            <TopBar
              trace={trace}
              traceList={traceList}
              activeIdx={activeIdx}
              onLoad={loadTrace}
              onDelete={handleDelete}
            />
          </div>
          <div className="mt-4 grid grid-cols-1 lg:grid-cols-[300px_1fr_360px] gap-3 h-[calc(100vh-320px)] min-h-[640px]">
            <StepList trace={trace} activeIdx={activeIdx} setActiveIdx={setActiveIdx} />
            <GraphPanel trace={trace} activeIdx={activeIdx} setActiveIdx={setActiveIdx} />
            <Inspector step={step} />
          </div>
          <Timeline trace={trace} activeIdx={activeIdx} setActiveIdx={setActiveIdx} />
          <Logs trace={trace} liveEvents={liveEvents} />
        </>
      )}
    </div>
  )
}

function BackendBanner({ isLive }: { isLive: boolean }) {
  return (
    <div className={`rounded-xl border px-4 py-2 flex items-center gap-2.5 mono text-[11.5px] mb-1 ${
      isLive
        ? 'bg-emerald-50 dark:bg-emerald-900/15 border-emerald-200 dark:border-emerald-800 text-emerald-700 dark:text-emerald-300'
        : 'bg-amber-50 dark:bg-amber-900/15 border-amber-200 dark:border-amber-800 text-amber-700 dark:text-amber-300'
    }`}>
      <span className={`inline-block w-2 h-2 rounded-full ${isLive ? 'bg-emerald-500 animate-pulse' : 'bg-amber-400'}`} />
      {isLive
        ? 'Live — connected to backend at localhost:8082'
        : 'Demo data — backend not reachable. Start tracegraph-demo on port 8082 to see real traces.'}
    </div>
  )
}

function DiffView({
  traceList, diffIdA, setDiffIdA, diffIdB, setDiffIdB, diffResult, loading, onDiff
}: {
  traceList: TraceSummary[]
  diffIdA: string; setDiffIdA: (v: string) => void
  diffIdB: string; setDiffIdB: (v: string) => void
  diffResult: TraceDiff | null
  loading: boolean
  onDiff: () => void
}) {
  const idOpts = traceList.map((t) => t.id)
  return (
    <div className="mt-4 rounded-xl border hairline bg-white dark:bg-ink-950 p-5">
      <h3 className="mono text-[11px] uppercase tracking-[0.14em] text-ink-500 mb-4">Compare two executions</h3>
      <div className="flex items-center gap-3 flex-wrap">
        <select value={diffIdA} onChange={(e) => setDiffIdA(e.target.value)}
          className="mono text-[12px] h-9 px-3 rounded-lg border hairline bg-ink-50 dark:bg-ink-900 text-ink-950 dark:text-white min-w-[200px]">
          <option value="">— select trace A —</option>
          {idOpts.map((id) => <option key={id} value={id}>{id.slice(0, 24)}…</option>)}
        </select>
        <span className="mono text-ink-400">vs</span>
        <select value={diffIdB} onChange={(e) => setDiffIdB(e.target.value)}
          className="mono text-[12px] h-9 px-3 rounded-lg border hairline bg-ink-50 dark:bg-ink-900 text-ink-950 dark:text-white min-w-[200px]">
          <option value="">— select trace B —</option>
          {idOpts.map((id) => <option key={id} value={id}>{id.slice(0, 24)}…</option>)}
        </select>
        <Button size="sm" variant="primary" onClick={onDiff}>
          {loading ? 'Comparing…' : 'Compare'}
        </Button>
      </div>
      {diffResult && (
        <div className="mt-6 space-y-4">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            {([
              ['Common prefix', diffResult.commonPrefix.length],
              ['Divergence at', diffResult.divergenceIndex === -1 ? 'none' : `step ${diffResult.divergenceIndex}`],
              ['Left remainder', diffResult.leftRemainder.length],
              ['Right remainder', diffResult.rightRemainder.length],
            ] as [string, string | number][]).map(([k, v]) => (
              <div key={k} className="rounded-xl border hairline bg-ink-50 dark:bg-ink-900 px-4 py-3">
                <div className="mono text-[10px] text-ink-500 uppercase tracking-wider">{k}</div>
                <div className="text-[20px] text-ink-950 dark:text-white mt-1">{v}</div>
              </div>
            ))}
          </div>
          <div className="flex items-center gap-4 mono text-[12px]">
            <span className={diffResult.sameStatus ? 'text-emerald-600' : 'text-rose-500'}>
              {diffResult.sameStatus ? '✓ same status' : '✕ different status'}
            </span>
            <span className={diffResult.sameFinalState ? 'text-emerald-600' : 'text-rose-500'}>
              {diffResult.sameFinalState ? '✓ same final state' : '✕ different final state'}
            </span>
          </div>
          {diffResult.commonPrefix.length > 0 && (
            <div>
              <div className="mono text-[10.5px] uppercase text-ink-500 tracking-wider mb-2">Shared path</div>
              <div className="flex gap-2 flex-wrap">
                {diffResult.commonPrefix.map((s, i) => (
                  <span key={i} className="mono text-[11.5px] px-2.5 py-1 rounded-full bg-ink-100 dark:bg-ink-800 text-ink-700 dark:text-ink-300">{s.nodeName ?? s.name}</span>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function TopBar({ trace, traceList, activeIdx, onLoad, onDelete }: {
  trace: ExecutionTrace
  traceList: TraceSummary[]
  activeIdx: number
  onLoad: (id: string) => void
  onDelete: (id: string) => void
}) {
  const step = trace.steps[activeIdx] ?? trace.steps[0]
  const statusTone = trace.status === 'FAILED' ? 'err' : trace.status === 'COMPLETED' ? 'ok' : 'warn'

  function handleFork() {
    api.traces
      .replay(trace.executionId, activeIdx)
      .then((r) => alert(`Forked → new executionId: ${(r as { executionId: string }).executionId}`))
      .catch(() => alert(`POST /tracegraph/traces/${trace.executionId}/replay?step=${activeIdx}`))
  }

  return (
    <div className="rounded-xl border hairline bg-white dark:bg-ink-950 px-4 py-2.5 flex items-center gap-3 mono text-[12px] text-ink-700 dark:text-ink-300 flex-wrap">
      <span className="text-ink-950 dark:text-white">execution {trace.executionId.slice(0, 18)}…</span>
      <span className="text-ink-300 dark:text-ink-700">·</span>
      <Badge tone={statusTone}>{trace.status}</Badge>
      <span className="text-ink-300 dark:text-ink-700">·</span>
      <span>graph <strong className="text-ink-950 dark:text-white">{trace.graph ?? 'graph'}</strong></span>
      <span className="text-ink-300 dark:text-ink-700">·</span>
      <span>{trace.startedAt ? new Date(trace.startedAt).toLocaleTimeString() : ''}</span>
      <span className="text-ink-300 dark:text-ink-700">·</span>
      <span>{trace.duration ?? 0}ms · {trace.steps.length} steps</span>
      {trace.forkedFromExecutionId && (
        <>
          <span className="text-ink-300 dark:text-ink-700">·</span>
          <span className="text-accent-600 dark:text-accent-300">↪ fork from {trace.forkedFromExecutionId.slice(0, 12)}…</span>
        </>
      )}
      <div className="flex-1" />
      <select
        className="mono text-[12px] px-2.5 h-8 rounded-lg border hairline bg-ink-50 dark:bg-ink-900 text-ink-950 dark:text-white"
        onChange={(e) => {
          const id = e.target.value.split(' · ')[0]
          if (id) onLoad(id)
        }}
      >
        {traceList.map((t) => (
          <option key={t.id}>{t.id} · {t.graph} · {t.status}</option>
        ))}
      </select>
      <Button size="sm" variant="ghost" icon="trash-2" onClick={() => onDelete(trace.executionId)}>
        Delete
      </Button>
      <Button size="sm" variant="primary" icon="redo-2" onClick={handleFork}>
        Fork from step #{String(activeIdx).padStart(2, '0')} · {step?.name}
      </Button>
    </div>
  )
}

function StepList({ trace, activeIdx, setActiveIdx }: {
  trace: ExecutionTrace
  activeIdx: number
  setActiveIdx: (i: number) => void
}) {
  return (
    <Panel title={`Steps · ${trace.steps.length}`}>
      <div className="py-1.5">
        {trace.steps.map((s) => (
          <button
            key={s.i}
            onClick={() => setActiveIdx(s.i)}
            className={`w-full text-left grid grid-cols-[24px_1fr_auto] gap-2 items-center px-3.5 py-2.5 border-l-2 transition-colors ${
              activeIdx === s.i
                ? 'bg-accent-50 dark:bg-accent-700/15 border-accent-500'
                : 'border-transparent hover:bg-ink-50 dark:hover:bg-ink-900/60'
            }`}
          >
            <span className={`mono text-[10.5px] ${activeIdx === s.i ? 'text-accent-700 dark:text-accent-100' : 'text-ink-400'}`}>
              #{String(s.i).padStart(2, '0')}
            </span>
            <span className="min-w-0">
              <span className={`mono text-[12.5px] block truncate ${s.status === 'err' ? 'text-rose-600 dark:text-rose-400' : 'text-ink-950 dark:text-white'}`}>{s.name}</span>
              <span className="flex items-center gap-1 mt-0.5">
                {s.status === 'parallel' && s.branches && <Badge tone="neutral">∥ {s.branches}</Badge>}
                {s.async && <Badge tone="neutral">async</Badge>}
                {s.attempts > 1 && <Badge tone="warn">↻ {s.attempts}</Badge>}
                {s.status === 'err' && <Badge tone="err">FAIL</Badge>}
                {s.status === 'ok' && <Badge tone="ok">ok</Badge>}
              </span>
            </span>
            <span className="mono text-[10.5px] text-ink-500">{s.dur}ms</span>
          </button>
        ))}
      </div>
    </Panel>
  )
}

const NODE_LAYOUT_FIXED = {
  validate: { x: 320, y: 70,  w: 120, h: 56 },
  enrich:   { x: 320, y: 180, w: 120, h: 56 },
  score:    { x: 320, y: 290, w: 120, h: 56 },
  charge:   { x: 200, y: 400, w: 120, h: 56 },
  ship:     { x: 440, y: 400, w: 120, h: 56 },
}
const EDGES_FIXED = [
  { from: 'validate', to: 'enrich',  label: 'valid' },
  { from: 'enrich',   to: 'score' },
  { from: 'score',    to: 'charge',  label: 'risk<.5' },
  { from: 'charge',   to: 'ship',    label: 'charged' },
]

function GraphPanel({ trace, activeIdx, setActiveIdx }: {
  trace: ExecutionTrace
  activeIdx: number
  setActiveIdx: (i: number) => void
}) {
  const stepFor = (name: string) => trace.steps.find((s) => s.name === name)

  const nodeStyle = (name: string) => {
    const step = stepFor(name)
    const isActive = step?.i === activeIdx
    if (!step) return { fill: 'white', stroke: 'currentColor', sw: 1.4, sub: '' }
    const sw = isActive ? 2.4 : 1.8
    if (step.status === 'err')     return { fill: 'rgba(244,63,94,0.06)',  stroke: '#e11d48', sw, sub: '✕ FAIL',          subColor: '#e11d48' }
    if (step.status === 'retry')   return { fill: 'rgba(245,158,11,0.07)', stroke: '#d97706', sw, sub: `↻ ${step.attempts}`, subColor: '#d97706' }
    if (step.status === 'parallel') return { fill: 'rgba(0,0,0,0.02)',     stroke: 'currentColor', sw: isActive ? 2.4 : 1.4, sub: `∥ ${step.branches ?? ''}`, subColor: '#6b7280' }
    if (step.status === 'ok')      return { fill: 'rgba(13,143,99,0.08)', stroke: '#0d8f63', sw, sub: `✓ ${step.dur}ms`,   subColor: '#0d8f63' }
    return { fill: 'white', stroke: 'currentColor', sw: 1.4, sub: '' }
  }

  const edgeStroke = (from: string, to: string) => {
    const f = stepFor(from), t = stepFor(to)
    if (f?.status === 'err' || t?.status === 'err') return '#e11d48'
    if (f && t) return '#0d8f63'
    return 'currentColor'
  }

  return (
    <Panel title={`graph · ${trace.graph ?? 'graph'}`}>
      <div className="grid-bg h-full p-2">
        <svg viewBox="0 0 760 580" preserveAspectRatio="xMidYMid meet" className="w-full h-full text-ink-950 dark:text-white">
          <defs>
            <marker id="tg-arr-acc" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#0d8f63" /></marker>
            <marker id="tg-arr-err" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#e11d48" /></marker>
            <marker id="tg-arr-def" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="currentColor" /></marker>
          </defs>

          <rect x={NODE_LAYOUT_FIXED.validate.x + 15} y="30" width={NODE_LAYOUT_FIXED.validate.w - 30} height="22" rx="4" fill="currentColor" />
          <text x={NODE_LAYOUT_FIXED.validate.x + NODE_LAYOUT_FIXED.validate.w / 2} y="45" textAnchor="middle" fill="white" className="mono" fontSize="10">▼ entry</text>

          {EDGES_FIXED.map((e, i) => {
            const a = NODE_LAYOUT_FIXED[e.from as keyof typeof NODE_LAYOUT_FIXED]
            const b = NODE_LAYOUT_FIXED[e.to as keyof typeof NODE_LAYOUT_FIXED]
            if (!a || !b) return null
            const x1 = a.x + a.w / 2, y1 = a.y + a.h
            const x2 = b.x + b.w / 2, y2 = b.y
            const mx = (x1 + x2) / 2, my = (y1 + y2) / 2
            const stroke = edgeStroke(e.from, e.to)
            const marker = stroke === '#e11d48' ? 'tg-arr-err' : stroke === '#0d8f63' ? 'tg-arr-acc' : 'tg-arr-def'
            return (
              <g key={i}>
                <path d={`M ${x1} ${y1} C ${x1} ${y1 + 30}, ${x2} ${y2 - 30}, ${x2} ${y2}`}
                  fill="none" stroke={stroke} strokeWidth="1.6" markerEnd={`url(#${marker})`} />
                {e.label && (
                  <g>
                    <rect x={mx - 32} y={my - 10} width="64" height="20" rx="4" fill="white" stroke={stroke} strokeOpacity="0.4" className="dark:fill-ink-950" />
                    <text x={mx} y={my + 4} textAnchor="middle" className="mono fill-ink-700 dark:fill-ink-300" fontSize="10">{e.label}</text>
                  </g>
                )}
              </g>
            )
          })}

          {Object.entries(NODE_LAYOUT_FIXED).map(([name, n]) => {
            const s = nodeStyle(name)
            return (
              <g key={name} style={{ cursor: 'pointer' }}
                onClick={() => { const st = stepFor(name); if (st) setActiveIdx(st.i) }}>
                <rect x={n.x} y={n.y} width={n.w} height={n.h} rx="10"
                  fill={s.fill} stroke={s.stroke} strokeWidth={s.sw}
                  className={s.fill === 'white' ? 'dark:fill-ink-900' : ''} />
                <text x={n.x + n.w / 2} y={n.y + 22} textAnchor="middle" className="mono fill-ink-950 dark:fill-white" fontSize="13" fontWeight="500">{name}</text>
                {'sub' in s && s.sub && (
                  <text x={n.x + n.w / 2} y={n.y + 39} textAnchor="middle" className="mono" fontSize="10" fill={'subColor' in s ? s.subColor : '#6b7280'}>{s.sub}</text>
                )}
              </g>
            )
          })}

          <line x1={NODE_LAYOUT_FIXED.ship.x + NODE_LAYOUT_FIXED.ship.w / 2} y1={NODE_LAYOUT_FIXED.ship.y + NODE_LAYOUT_FIXED.ship.h}
            x2={NODE_LAYOUT_FIXED.ship.x + NODE_LAYOUT_FIXED.ship.w / 2} y2={NODE_LAYOUT_FIXED.ship.y + NODE_LAYOUT_FIXED.ship.h + 24}
            stroke="currentColor" strokeWidth="1.4" />
          <rect x={NODE_LAYOUT_FIXED.ship.x + 15} y={NODE_LAYOUT_FIXED.ship.y + NODE_LAYOUT_FIXED.ship.h + 24} width={NODE_LAYOUT_FIXED.ship.w - 30} height="22" rx="4" fill="currentColor" />
          <text x={NODE_LAYOUT_FIXED.ship.x + NODE_LAYOUT_FIXED.ship.w / 2} y={NODE_LAYOUT_FIXED.ship.y + NODE_LAYOUT_FIXED.ship.h + 39} textAnchor="middle" fill="white" className="mono" fontSize="10">▲ terminal</text>
        </svg>
      </div>
    </Panel>
  )
}

function Inspector({ step }: { step: TraceStep }) {
  const Field = ({ k, v, tone }: { k: string; v: string; tone?: 'ok' | 'err' }) => (
    <div className="grid grid-cols-[100px_1fr] gap-3 py-2 border-t hairline first:border-t-0 text-[12.5px]">
      <span className="mono text-[11px] text-ink-500">{k}</span>
      <span className={`mono text-[12px] break-all ${tone === 'err' ? 'text-rose-600 dark:text-rose-400' : tone === 'ok' ? 'text-emerald-600 dark:text-emerald-400' : 'text-ink-950 dark:text-white'}`}>{v}</span>
    </div>
  )
  return (
    <Panel title={`step · ${step.name}`}>
      <div className="p-4 pb-6">
        <Field k="node" v={step.name} />
        <Field k="index" v={`#${String(step.i).padStart(2, '0')}`} />
        <Field k="status" v={step.status} tone={step.status === 'err' ? 'err' : step.status === 'ok' ? 'ok' : undefined} />
        <Field k="duration" v={`${step.dur} ms`} />
        <Field k="attempts" v={String(step.attempts)} />
        <Field k="async" v={step.async ? 'true' : 'false'} />
        {step.usage && <Field k="usage" v={`p=${step.usage.prompt} c=${step.usage.completion}`} />}
        {step.err && <Field k="error" v={step.err} tone="err" />}

        <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-6 mb-2">State diff</h4>
        <div className="rounded-lg bg-ink-950 dark:bg-black/60 border border-white/5 p-3.5 mono text-[11.5px] leading-[1.65]">
          {step.diff && step.diff.length > 0 ? step.diff.map((line, i) => {
            const cls = line.startsWith('+') ? 'text-emerald-300'
              : line.startsWith('-') || line.startsWith('!') ? 'text-rose-300'
              : 'text-ink-400'
            return <div key={i} className={cls}>{line}</div>
          }) : (
            <span className="text-ink-500">(no diff recorded)</span>
          )}
        </div>

        <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-5 mb-2">State · before</h4>
        <pre className="rounded-lg bg-ink-50 dark:bg-ink-900 border hairline p-3.5 mono text-[11px] text-ink-700 dark:text-ink-300 overflow-x-auto scroll-thin">
          {JSON.stringify(step.before, null, 2)}
        </pre>

        <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-5 mb-2">State · after</h4>
        {step.after
          ? <pre className="rounded-lg bg-ink-50 dark:bg-ink-900 border hairline p-3.5 mono text-[11px] text-ink-700 dark:text-ink-300 overflow-x-auto scroll-thin">
              {JSON.stringify(step.after, null, 2)}
            </pre>
          : <div className="rounded-lg border border-rose-200 dark:border-rose-900/40 bg-rose-50 dark:bg-rose-900/20 p-3.5 mono text-[11px] text-rose-700 dark:text-rose-300">(no exit; node failed)</div>}
      </div>
    </Panel>
  )
}

function Timeline({ trace, activeIdx, setActiveIdx }: {
  trace: ExecutionTrace
  activeIdx: number
  setActiveIdx: (i: number) => void
}) {
  const total = (trace.duration ?? 1000) + 60
  const step = trace.steps[activeIdx] ?? trace.steps[0]
  if (!step) return null
  const cursorPos = ((step.t0 + step.dur / 2) / total) * 100

  return (
    <div className="mt-3 rounded-xl border hairline bg-white dark:bg-ink-950 px-4 py-3.5">
      <div className="flex items-center justify-between mono text-[11px] text-ink-500 mb-2.5">
        <span>timeline · {((trace.duration ?? 0) / 1000).toFixed(2)}s · virtual-thread executor</span>
        <div className="flex items-center gap-3">
          {[['ok','bg-accent-500/70'],['parallel','bg-amber-300'],['retry','bg-amber-500'],['err','bg-rose-500']].map(([label, color]) => (
            <span key={label} className="inline-flex items-center gap-1.5">
              <span className={`w-2.5 h-2.5 rounded-sm ${color}`} />{label}
            </span>
          ))}
        </div>
      </div>
      <div className="relative h-12 bg-ink-50 dark:bg-ink-900 rounded-lg overflow-hidden">
        {Array.from({ length: 12 }).map((_, i) => (
          <div key={i} className="absolute top-0 bottom-0 w-px bg-black/5 dark:bg-white/5" style={{ left: `${(i / 11) * 100}%` }} />
        ))}
        {trace.steps.map((s) => {
          const bg = s.status === 'parallel' ? 'bg-amber-200 text-ink-950'
            : s.status === 'retry' ? 'bg-amber-400 text-ink-950'
            : s.status === 'err' ? 'bg-rose-500 text-white'
            : 'bg-accent-200 text-ink-950'
          return (
            <button key={s.i} onClick={() => setActiveIdx(s.i)}
              className={`absolute top-2 h-8 rounded mono text-[10.5px] px-2 flex items-center overflow-hidden whitespace-nowrap ${bg} ${activeIdx === s.i ? 'ring-2 ring-ink-950 dark:ring-white' : ''}`}
              style={{ left: `${(s.t0 / total) * 100}%`, width: `${Math.max((s.dur / total) * 100, 3)}%` }}
              title={`${s.name} · ${s.dur}ms`}>
              {s.name}
            </button>
          )
        })}
        <div className="absolute top-0 bottom-0 w-0.5 bg-ink-950 dark:bg-white pointer-events-none" style={{ left: `${cursorPos}%` }} />
      </div>
    </div>
  )
}

function Logs({ trace, liveEvents }: { trace: ExecutionTrace; liveEvents: { t: number; type: string; node: string }[] }) {
  const lvColors: Record<string, string> = {
    info: 'text-ink-500',
    warn: 'text-amber-600 dark:text-amber-400',
    err:  'text-rose-600 dark:text-rose-400',
    evt:  'text-accent-600 dark:text-accent-100',
  }
  const logs = trace.logs ?? []
  return (
    <div className="mt-3 rounded-xl border hairline bg-white dark:bg-ink-950 overflow-hidden">
      <div className="px-4 py-2.5 border-b hairline flex items-center justify-between bg-ink-50/50 dark:bg-ink-900/40">
        <span className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500">
          logs · execution {trace.executionId.slice(0, 8)}
        </span>
        {liveEvents.length > 0 && (
          <span className="mono text-[10.5px] text-accent-600 dark:text-accent-300 flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-accent-500 animate-pulse" />
            {liveEvents.length} live events
          </span>
        )}
      </div>
      <div className="max-h-[200px] overflow-auto scroll-thin mono text-[11.5px] leading-[1.65]">
        {logs.map((l, i) => (
          <div key={i} className="grid grid-cols-[60px_50px_1fr] gap-3 px-4 py-1 border-t hairline first:border-t-0 text-ink-700 dark:text-ink-300">
            <span className="text-ink-400">{String(l.t).padStart(4, '0')}ms</span>
            <span className={`font-medium ${lvColors[l.lv] ?? ''}`}>{l.lv.toUpperCase()}</span>
            <span>{l.msg}</span>
          </div>
        ))}
        {liveEvents.map((e, i) => (
          <div key={`live-${i}`} className="grid grid-cols-[60px_50px_1fr] gap-3 px-4 py-1 border-t hairline text-ink-700 dark:text-ink-300 bg-accent-50/50 dark:bg-accent-900/10">
            <span className="text-ink-400">{String(e.t).padStart(4, '0')}ms</span>
            <span className="font-medium text-accent-600 dark:text-accent-300">EVT</span>
            <span>{e.type}{e.node ? ` · ${e.node}` : ''}</span>
          </div>
        ))}
        {logs.length === 0 && liveEvents.length === 0 && (
          <div className="px-4 py-3 text-ink-400">(no logs)</div>
        )}
      </div>
    </div>
  )
}
