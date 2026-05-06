import { useEffect, useState } from 'react'
import { Badge, Button, Icon, Panel } from '@/components'
import { EDGES, NODE_LAYOUT, STUDIO_NODES } from '@/data/mock'
import { api } from '@/lib/api'
import type { Edge, GraphComplexity, StudioNode } from '@/types'

type Selection =
  | { type: 'node'; nodeName: string }
  | { type: 'edge'; edgeIndex: number }

export function Studio() {
  const [selection, setSelection] = useState<Selection>({ type: 'node', nodeName: 'charge' })
  const [mermaid, setMermaid] = useState<string | null>(null)
  const [complexity, setComplexity] = useState<GraphComplexity | null>(null)

  useEffect(() => {
    api.graph.mermaid().then(setMermaid).catch(() => {})
    api.graph.complexity().then(setComplexity).catch(() => {})
  }, [])

  const selectedNodeName = selection.type === 'node' ? selection.nodeName : null
  const selectedEdgeIndex = selection.type === 'edge' ? selection.edgeIndex : null

  return (
    <div className="max-w-[1500px] mx-auto px-4 lg:px-6 py-6 fade-up">
      <StudioHeader complexity={complexity} mermaid={mermaid} />

      <div className="mt-4 grid grid-cols-1 lg:grid-cols-[280px_1fr_340px] gap-3 h-[calc(100vh-200px)] min-h-[640px]">
        <Panel title="Knowledge Graph">
          <div className="py-1.5">
            <SectionHeader label="Nodes" count={STUDIO_NODES.length} />
            {STUDIO_NODES.map((n) => {
              const isActive = selection.type === 'node' && selection.nodeName === n.name
              return (
                <button
                  key={n.name}
                  onClick={() => setSelection({ type: 'node', nodeName: n.name })}
                  className={`w-full text-left grid grid-cols-[20px_1fr_auto] gap-2 items-center px-3.5 py-2.5 border-l-2 transition-colors ${
                    isActive
                      ? 'bg-accent-50 dark:bg-accent-700/15 border-accent-500'
                      : 'border-transparent hover:bg-ink-50 dark:hover:bg-ink-900/60'
                  }`}
                >
                  <span className="text-ink-500 text-[10px]">
                    {n.kind === 'parallel' ? '∥' : n.kind === 'async' ? '⚡' : '●'}
                  </span>
                  <span className="min-w-0">
                    <span className="mono text-[12.5px] text-ink-950 dark:text-white">{n.name}</span>
                    <span className="flex items-center gap-1 mt-0.5 flex-wrap">
                      {n.entry && <Badge tone="dark">entry</Badge>}
                      {n.terminal && <Badge tone="dark">terminal</Badge>}
                      <Badge tone="neutral">{n.kind}</Badge>
                    </span>
                  </span>
                </button>
              )
            })}

            <SectionHeader label="Edges" count={EDGES.length} className="mt-5" />
            {EDGES.map((edge, edgeIndex) => {
              const isActive = selection.type === 'edge' && selection.edgeIndex === edgeIndex
              return (
                <button
                  key={`${edge.from}-${edge.to}-${edgeIndex}`}
                  onClick={() => setSelection({ type: 'edge', edgeIndex })}
                  className={`w-full text-left grid grid-cols-[20px_1fr] gap-2 items-center px-3.5 py-2.5 border-l-2 transition-colors ${
                    isActive
                      ? 'bg-accent-50 dark:bg-accent-700/15 border-accent-500'
                      : 'border-transparent hover:bg-ink-50 dark:hover:bg-ink-900/60'
                  }`}
                >
                  <span className="text-ink-400 text-[11px]">→</span>
                  <span className="min-w-0">
                    <span className="mono text-[12px] text-ink-950 dark:text-white">{edge.from} → {edge.to}</span>
                    <span className="block mt-0.5 text-[11px] text-ink-500">
                      {edge.label ? `predicate: ${edge.label}` : 'unconditional transition'}
                    </span>
                  </span>
                </button>
              )
            })}
          </div>
        </Panel>

        <Panel
          title="Canvas"
          action={
            <>
              <button className="w-6 h-6 rounded inline-flex items-center justify-center text-ink-500 hover:bg-ink-100 dark:hover:bg-ink-900 text-[10px]">+</button>
              <button className="w-6 h-6 rounded inline-flex items-center justify-center text-ink-500 hover:bg-ink-100 dark:hover:bg-ink-900 text-[10px]">−</button>
            </>
          }
        >
          <div className="grid-bg h-full p-2">
            <StudioGraphSvg
              selectedNodeName={selectedNodeName}
              selectedEdgeIndex={selectedEdgeIndex}
              onSelectNode={(nodeName) => setSelection({ type: 'node', nodeName })}
              onSelectEdge={(edgeIndex) => setSelection({ type: 'edge', edgeIndex })}
            />
          </div>
        </Panel>

        <Panel title={selection.type === 'node' ? `node · ${selection.nodeName}` : `edge · ${selectedEdgeIndex != null ? selectedEdgeIndex + 1 : ''}`}>
          <StudioInspector selection={selection} />
        </Panel>
      </div>

      {complexity && (
        <div className="mt-3 rounded-xl border hairline bg-white dark:bg-ink-950 px-6 py-4">
          <div className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mb-3">Graph complexity</div>
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-4">
            {([
              ['Nodes', complexity.nodeCount],
              ['Edges', complexity.edgeCount],
              ['Max depth', complexity.maxDepth],
              ['Max fan-out', complexity.maxFanOut],
              ['Cyclomatic', complexity.cyclomaticComplexity],
              ['Parallel branches', complexity.parallelBranches],
              ['Subgraph depth', complexity.subgraphDepth],
              ['Hotspots', complexity.hotspots.length],
            ] as [string, number][]).map(([label, val]) => (
              <div key={label}>
                <div className="mono text-[10px] text-ink-500 uppercase tracking-wider">{label}</div>
                <div className="display-tight text-[24px] text-ink-950 dark:text-white">{val}</div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function StudioHeader({ complexity, mermaid }: { complexity: GraphComplexity | null; mermaid: string | null }) {
  return (
    <div className="rounded-[20px] border hairline bg-white dark:bg-ink-950 px-4 py-2.5 flex items-center gap-3 flex-wrap">
      <div className="flex items-center gap-2.5 min-w-0 text-[12px] text-ink-700 dark:text-ink-300">
        <Icon name="search" size={14} className="text-ink-500" />
        <span className="mono text-ink-950 dark:text-white whitespace-nowrap">graph · order-pipeline.java</span>
        <span className="text-ink-300 dark:text-ink-700">·</span>
        {complexity
          ? <span className="mono whitespace-nowrap">{complexity.nodeCount} nodes · {complexity.edgeCount} edges · 1 entry · 1 terminal</span>
          : <span className="mono whitespace-nowrap">5 nodes · 4 edges · 1 entry · 1 terminal</span>}
        <span className="text-ink-300 dark:text-ink-700">·</span>
        <Badge tone="ok">VALID</Badge>
      </div>

      <div className="flex-1" />

      <div className="flex items-center gap-2 ml-auto">
        <select
          className="h-8 min-w-[150px] rounded-lg border hairline bg-ink-50 dark:bg-ink-900 px-3 text-[12.5px] text-ink-950 dark:text-white"
          defaultValue="react-agent"
        >
          <option value="react-agent">react-agent</option>
        </select>
        <Button size="sm" variant="ghost" icon="file-code" onClick={() => mermaid && alert(mermaid)}>
          Mermaid
        </Button>
        <Button size="sm" variant="ghost" icon="file-code">
          PlantUML
        </Button>
        <Button size="sm" variant="primary" icon="play">Run</Button>
      </div>
    </div>
  )
}

function SectionHeader({ label, count, className = '' }: { label: string; count: number; className?: string }) {
  return (
    <div className={`px-3.5 pb-1.5 flex items-center justify-between ${className}`}>
      <span className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500">{label}</span>
      <span className="mono text-[10.5px] text-ink-400">{count}</span>
    </div>
  )
}

function StudioGraphSvg({
  selectedNodeName,
  selectedEdgeIndex,
  onSelectNode,
  onSelectEdge,
}: {
  selectedNodeName: string | null
  selectedEdgeIndex: number | null
  onSelectNode: (nodeName: string) => void
  onSelectEdge: (edgeIndex: number) => void
}) {
  return (
    <svg viewBox="0 0 760 580" preserveAspectRatio="xMidYMid meet" className="w-full h-full text-ink-950 dark:text-white">
      <defs>
        <marker id="st-arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
          <path d="M0,0 L10,5 L0,10 z" fill="currentColor" />
        </marker>
      </defs>
      <rect x={NODE_LAYOUT.validate.x + 15} y="30" width={NODE_LAYOUT.validate.w - 30} height="22" rx="4" fill="currentColor" />
      <text x={NODE_LAYOUT.validate.x + NODE_LAYOUT.validate.w / 2} y="45" textAnchor="middle" fill="white" className="mono" fontSize="10">▼ entry</text>

      {EDGES.map((e, i) => {
        const a = NODE_LAYOUT[e.from]
        const b = NODE_LAYOUT[e.to]
        if (!a || !b) return null
        const x1 = a.x + a.w / 2
        const y1 = a.y + a.h
        const x2 = b.x + b.w / 2
        const y2 = b.y
        const mx = (x1 + x2) / 2
        const my = (y1 + y2) / 2
        const isActive = selectedEdgeIndex === i
        const stroke = isActive ? '#0d8f63' : 'currentColor'

        return (
          <g key={i} style={{ cursor: 'pointer' }} onClick={() => onSelectEdge(i)}>
            <path
              d={`M ${x1} ${y1} C ${x1} ${y1 + 30}, ${x2} ${y2 - 30}, ${x2} ${y2}`}
              fill="none"
              stroke={stroke}
              strokeWidth={isActive ? 2.4 : 1.4}
              markerEnd="url(#st-arr)"
            />
            {e.label && (
              <g>
                <rect
                  x={mx - 38}
                  y={my - 10}
                  width="76"
                  height="20"
                  rx="4"
                  fill="white"
                  stroke={stroke}
                  strokeOpacity={isActive ? '0.8' : '0.3'}
                  className="dark:fill-ink-900"
                />
                <text x={mx} y={my + 4} textAnchor="middle" className="mono fill-ink-700 dark:fill-ink-300" fontSize="10">{e.label}</text>
              </g>
            )}
          </g>
        )
      })}

      {Object.entries(NODE_LAYOUT).map(([name, n]) => {
        const isActive = name === selectedNodeName
        return (
          <g key={name} style={{ cursor: 'pointer' }} onClick={() => onSelectNode(name)}>
            <rect
              x={n.x}
              y={n.y}
              width={n.w}
              height={n.h}
              rx="10"
              fill={isActive ? 'rgba(13,143,99,0.10)' : 'white'}
              stroke={isActive ? '#0d8f63' : 'currentColor'}
              strokeWidth={isActive ? 2.2 : 1.4}
              className={isActive ? '' : 'dark:fill-ink-900'}
            />
            <text x={n.x + n.w / 2} y={n.y + 22} textAnchor="middle" className="mono fill-ink-950 dark:fill-white" fontSize="13" fontWeight="500">{name}</text>
            <text x={n.x + n.w / 2} y={n.y + 39} textAnchor="middle" className="mono fill-ink-500" fontSize="10">{n.kind}</text>
          </g>
        )
      })}

      <line
        x1={NODE_LAYOUT.ship.x + NODE_LAYOUT.ship.w / 2}
        y1={NODE_LAYOUT.ship.y + NODE_LAYOUT.ship.h}
        x2={NODE_LAYOUT.ship.x + NODE_LAYOUT.ship.w / 2}
        y2={NODE_LAYOUT.ship.y + NODE_LAYOUT.ship.h + 24}
        stroke="currentColor"
        strokeWidth="1.4"
      />
      <rect x={NODE_LAYOUT.ship.x + 15} y={NODE_LAYOUT.ship.y + NODE_LAYOUT.ship.h + 24} width={NODE_LAYOUT.ship.w - 30} height="22" rx="4" fill="currentColor" />
      <text x={NODE_LAYOUT.ship.x + NODE_LAYOUT.ship.w / 2} y={NODE_LAYOUT.ship.y + NODE_LAYOUT.ship.h + 39} textAnchor="middle" fill="white" className="mono" fontSize="10">▲ terminal</text>
    </svg>
  )
}

function StudioInspector({ selection }: { selection: Selection }) {
  if (selection.type === 'edge') {
    return <StudioEdgeInspector edge={EDGES[selection.edgeIndex]} edgeIndex={selection.edgeIndex} />
  }
  return <StudioNodeInspector nodeName={selection.nodeName} />
}

function StudioNodeInspector({ nodeName }: { nodeName: string }) {
  const n: StudioNode | undefined = STUDIO_NODES.find((x) => x.name === nodeName)
  if (!n) return null

  const incoming = EDGES.filter((e) => e.to === n.name)
  const outgoing = EDGES.filter((e) => e.from === n.name)

  const Field = ({ k, v }: { k: string; v: string }) => (
    <div className="grid grid-cols-[100px_1fr] gap-3 py-2 border-t hairline first:border-t-0 text-[12.5px]">
      <span className="mono text-[11px] text-ink-500">{k}</span>
      <span className="mono text-[12px] text-ink-950 dark:text-white break-all">{v}</span>
    </div>
  )

  return (
    <div className="p-4 pb-6">
      <Field k="name" v={n.name} />
      <Field k="kind" v={n.kind} />
      <Field k="function" v={n.fn} />
      <Field k="retry" v={n.retry} />
      <Field k="entry" v={n.entry ? 'true' : 'false'} />
      <Field k="terminal" v={n.terminal ? 'true' : 'false'} />

      <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-6 mb-2">Incoming</h4>
      <div className="rounded-lg bg-ink-50 dark:bg-ink-900 p-3 mono text-[11.5px]">
        {incoming.length === 0
          ? <span className="text-ink-500">(entry — no incoming)</span>
          : incoming.map((e, i) => <div key={i} className="text-ink-700 dark:text-ink-300">← <span className="text-emerald-600">{e.from}</span>{e.label ? ` if (${e.label})` : ''}</div>)}
      </div>

      <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-5 mb-2">Outgoing</h4>
      <div className="rounded-lg bg-ink-50 dark:bg-ink-900 p-3 mono text-[11.5px]">
        {outgoing.length === 0
          ? <span className="text-ink-500">(terminal — no outgoing)</span>
          : outgoing.map((e, i) => <div key={i} className="text-ink-700 dark:text-ink-300">→ <span className="text-emerald-600">{e.to}</span>{e.label ? ` if (${e.label})` : ''}</div>)}
      </div>

      <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-5 mb-2">Builder snippet</h4>
      <pre className="rounded-lg bg-ink-950 dark:bg-black/60 border border-white/5 p-3 mono text-[11px] text-white/85 overflow-x-auto scroll-thin">{`.${n.kind === 'parallel' ? 'parallel' : n.kind === 'async' ? 'asyncNode' : 'node'}("${n.name}", ${n.fn}${n.retry !== '—' ? ',\n    RetryPolicy.exponential(...)' : ''})`}</pre>
    </div>
  )
}

function StudioEdgeInspector({ edge, edgeIndex }: { edge: Edge | undefined; edgeIndex: number }) {
  if (!edge) return null

  const sourceNode = STUDIO_NODES.find((node) => node.name === edge.from)
  const targetNode = STUDIO_NODES.find((node) => node.name === edge.to)

  const Field = ({ k, v }: { k: string; v: string }) => (
    <div className="grid grid-cols-[100px_1fr] gap-3 py-2 border-t hairline first:border-t-0 text-[12.5px]">
      <span className="mono text-[11px] text-ink-500">{k}</span>
      <span className="mono text-[12px] text-ink-950 dark:text-white break-all">{v}</span>
    </div>
  )

  return (
    <div className="p-4 pb-6">
      <Field k="index" v={`#${edgeIndex + 1}`} />
      <Field k="from" v={edge.from} />
      <Field k="to" v={edge.to} />
      <Field k="label" v={edge.label ?? '(unconditional)'} />
      <Field k="type" v={edge.label ? 'predicate edge' : 'fallthrough edge'} />

      <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-6 mb-2">Semantics</h4>
      <div className="rounded-lg bg-ink-50 dark:bg-ink-900 p-3 text-[12.5px] text-ink-700 dark:text-ink-300 leading-relaxed">
        {edge.label
          ? <>After <span className="mono text-ink-950 dark:text-white">{edge.from}</span> exits, this edge is taken when predicate <span className="mono text-ink-950 dark:text-white">{edge.label}</span> evaluates to true on the post-node state.</>
          : <>After <span className="mono text-ink-950 dark:text-white">{edge.from}</span> exits, this edge is an unconditional transition to <span className="mono text-ink-950 dark:text-white">{edge.to}</span>.</>}
      </div>

      <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-5 mb-2">Endpoints</h4>
      <div className="space-y-2">
        <div className="rounded-lg border hairline bg-white dark:bg-ink-950 px-3 py-2.5">
          <div className="mono text-[10.5px] text-ink-500 uppercase tracking-[0.14em]">Source</div>
          <div className="mt-1 flex items-center gap-2 flex-wrap">
            <span className="mono text-[12.5px] text-ink-950 dark:text-white">{edge.from}</span>
            {sourceNode && <Badge tone="neutral">{sourceNode.kind}</Badge>}
            {sourceNode?.entry && <Badge tone="dark">entry</Badge>}
          </div>
        </div>
        <div className="rounded-lg border hairline bg-white dark:bg-ink-950 px-3 py-2.5">
          <div className="mono text-[10.5px] text-ink-500 uppercase tracking-[0.14em]">Target</div>
          <div className="mt-1 flex items-center gap-2 flex-wrap">
            <span className="mono text-[12.5px] text-ink-950 dark:text-white">{edge.to}</span>
            {targetNode && <Badge tone="neutral">{targetNode.kind}</Badge>}
            {targetNode?.terminal && <Badge tone="dark">terminal</Badge>}
          </div>
        </div>
      </div>

      <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-5 mb-2">Builder snippet</h4>
      <pre className="rounded-lg bg-ink-950 dark:bg-black/60 border border-white/5 p-3 mono text-[11px] text-white/85 overflow-x-auto scroll-thin">{edge.label
        ? `.edge("${edge.from}", "${edge.to}", s -> ${edge.label})`
        : `.edge("${edge.from}", "${edge.to}")`}</pre>
    </div>
  )
}
