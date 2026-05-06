import { useEffect, useState } from 'react'
import { Badge, Button, GraphCanvas, Icon, Panel } from '@/components'
import { EDGES, STUDIO_NODES } from '@/data/mock'
import { api } from '@/lib/api'
import type { Edge, GraphComplexity } from '@/types'

type Selection =
  | { type: 'node'; nodeName: string }
  | { type: 'edge'; edgeIndex: number }

type LensMode = 'topology' | 'relations' | 'execution'

const NODE_META: Record<
  string,
  {
    tier: string
    accent: string
    insight: string
    context: string[]
  }
> = {
  validate: {
    tier: 'entry',
    accent: '#0d8f63',
    insight: 'Normalizes incoming state and gates invalid work before the graph expands.',
    context: ['schema', 'guardrails', 'contract'],
  },
  enrich: {
    tier: 'context',
    accent: '#3276ff',
    insight: 'Pulls side knowledge into the working state and merges evidence back together.',
    context: ['parallel', 'profile', 'inventory'],
  },
  score: {
    tier: 'decision',
    accent: '#6d4aff',
    insight: 'Calculates routing confidence and decides whether the order is safe to charge.',
    context: ['risk', 'async', 'usage'],
  },
  charge: {
    tier: 'action',
    accent: '#ff7a59',
    insight: 'Executes the payment boundary with retries and idempotent recovery semantics.',
    context: ['payment', 'retry', 'mutation'],
  },
  ship: {
    tier: 'terminal',
    accent: '#111827',
    insight: 'Hands off to fulfillment once the transactional path is complete.',
    context: ['carrier', 'dispatch', 'exit'],
  },
}

export function Studio() {
  const [selection, setSelection] = useState<Selection>({ type: 'node', nodeName: 'charge' })
  const [lensMode, setLensMode] = useState<LensMode>('relations')
  const [mermaid, setMermaid] = useState<string | null>(null)
  const [complexity, setComplexity] = useState<GraphComplexity | null>(null)

  useEffect(() => {
    api.graph.mermaid().then(setMermaid).catch(() => {})
    api.graph.complexity().then(setComplexity).catch(() => {})
  }, [])

  const selectedNodeName = selection.type === 'node' ? selection.nodeName : null
  const selectedEdgeIndex = selection.type === 'edge' ? selection.edgeIndex : null

  const focusNode = selectedNodeName ?? (selectedEdgeIndex != null ? EDGES[selectedEdgeIndex]?.from : STUDIO_NODES[0]?.name) ?? null
  const neighborhood = new Set<string>()
  const highlightedEdges = new Set<number>()

  if (focusNode) {
    neighborhood.add(focusNode)
    EDGES.forEach((edge, index) => {
      if (edge.from === focusNode || edge.to === focusNode) {
        neighborhood.add(edge.from)
        neighborhood.add(edge.to)
        highlightedEdges.add(index)
      }
    })
  }

  if (selectedEdgeIndex != null) {
    const edge = EDGES[selectedEdgeIndex]
    if (edge) {
      neighborhood.add(edge.from)
      neighborhood.add(edge.to)
      highlightedEdges.add(selectedEdgeIndex)
    }
  }

  return (
    <div className="max-w-[1500px] mx-auto px-4 lg:px-6 py-6 fade-up">
      <StudioHeader complexity={complexity} mermaid={mermaid} />

      <div className="mt-4 grid grid-cols-1 lg:grid-cols-[290px_1fr_340px] gap-3 h-[calc(100vh-200px)] min-h-[640px]">
        <Panel title="Knowledge Graph">
          <div className="py-1.5">
            <SectionHeader label="Nodes" count={STUDIO_NODES.length} />
            {STUDIO_NODES.map((node) => {
              const active = selection.type === 'node' && selection.nodeName === node.name
              const meta = NODE_META[node.name]
              return (
                <button
                  key={node.name}
                  onClick={() => setSelection({ type: 'node', nodeName: node.name })}
                  className={`w-full text-left px-3.5 py-3 border-l-2 transition-colors ${
                    active
                      ? 'bg-accent-50 dark:bg-accent-700/15 border-accent-500'
                      : 'border-transparent hover:bg-ink-50 dark:hover:bg-ink-900/60'
                  }`}
                >
                  <div className="flex items-start gap-2">
                    <span className="mt-1 h-2.5 w-2.5 rounded-full" style={{ backgroundColor: meta.accent }} />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="mono text-[12.5px] text-ink-950 dark:text-white">{node.name}</span>
                        <Badge tone="neutral">{node.kind}</Badge>
                        {node.entry && <Badge tone="dark">entry</Badge>}
                        {node.terminal && <Badge tone="dark">terminal</Badge>}
                      </div>
                      <div className="mt-1 text-[11.5px] text-ink-600 dark:text-ink-300">{meta.insight}</div>
                      <div className="mt-2 flex gap-1 flex-wrap">
                        {meta.context.map((item) => (
                          <span
                            key={item}
                            className="mono rounded-full border border-black/8 bg-white/75 px-2 py-0.5 text-[10px] uppercase tracking-wider text-ink-500 dark:border-white/10 dark:bg-white/5"
                          >
                            {item}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                </button>
              )
            })}

            <SectionHeader label="Relationships" count={EDGES.length} className="mt-5" />
            {EDGES.map((edge, edgeIndex) => {
              const active = selection.type === 'edge' && selection.edgeIndex === edgeIndex
              return (
                <button
                  key={`${edge.from}-${edge.to}-${edgeIndex}`}
                  onClick={() => setSelection({ type: 'edge', edgeIndex })}
                  className={`w-full text-left px-3.5 py-3 border-l-2 transition-colors ${
                    active
                      ? 'bg-accent-50 dark:bg-accent-700/15 border-accent-500'
                      : 'border-transparent hover:bg-ink-50 dark:hover:bg-ink-900/60'
                  }`}
                >
                  <div className="mono text-[12px] text-ink-950 dark:text-white">{`${edge.from} -> ${edge.to}`}</div>
                  <div className="mt-0.5 text-[11px] text-ink-500">
                    {edge.label ? `predicate: ${edge.label}` : 'unconditional transition'}
                  </div>
                </button>
              )
            })}
          </div>
        </Panel>

        <Panel
          title="Canvas"
          action={
            <div className="flex items-center gap-1.5">
              {(['topology', 'relations', 'execution'] as LensMode[]).map((mode) => (
                <button
                  key={mode}
                  onClick={() => setLensMode(mode)}
                  className={`rounded-full px-2.5 py-1 mono text-[10px] uppercase tracking-wider transition ${
                    lensMode === mode
                      ? 'bg-accent-50 text-accent-700 dark:bg-accent-700/20 dark:text-accent-100'
                      : 'text-ink-500 hover:bg-ink-100 dark:hover:bg-ink-900'
                  }`}
                >
                  {mode}
                </button>
              ))}
            </div>
          }
        >
          <div className="relative h-full overflow-hidden bg-[radial-gradient(circle_at_18%_18%,rgba(13,143,99,0.10),transparent_24%),radial-gradient(circle_at_80%_10%,rgba(50,118,255,0.10),transparent_24%),radial-gradient(circle_at_78%_78%,rgba(109,74,255,0.12),transparent_24%),linear-gradient(180deg,#fcfcfc_0%,#f3f6f8_100%)] dark:bg-[radial-gradient(circle_at_18%_18%,rgba(13,143,99,0.16),transparent_24%),radial-gradient(circle_at_80%_10%,rgba(50,118,255,0.14),transparent_24%),radial-gradient(circle_at_78%_78%,rgba(109,74,255,0.18),transparent_24%),linear-gradient(180deg,#0c1014_0%,#090b0f_100%)]">
            <CanvasOverlay
              lensMode={lensMode}
              focusNode={focusNode}
              selectedEdgeIndex={selectedEdgeIndex}
              highlightedEdges={highlightedEdges}
              onSelectEdge={(edgeIndex) => setSelection({ type: 'edge', edgeIndex })}
            />
            <div className="absolute inset-0 z-0">
              <GraphCanvas
                studioNodes={STUDIO_NODES}
                edges={EDGES}
                selectedNodeName={selectedNodeName}
                selectedEdgeIndex={selectedEdgeIndex}
                lensMode={lensMode}
                onSelectNode={(nodeName) => setSelection({ type: 'node', nodeName })}
                onSelectEdge={(edgeIndex) => setSelection({ type: 'edge', edgeIndex })}
              />
            </div>
          </div>
        </Panel>

        <Panel
          title={
            selection.type === 'node'
              ? `node | ${selection.nodeName}`
              : `edge | ${selectedEdgeIndex != null ? selectedEdgeIndex + 1 : ''}`
          }
        >
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
        <span className="mono text-ink-950 dark:text-white whitespace-nowrap">graph | order-pipeline.java</span>
        <span className="text-ink-300 dark:text-ink-700">|</span>
        {complexity ? (
          <span className="mono whitespace-nowrap">
            {complexity.nodeCount} nodes | {complexity.edgeCount} edges | 1 entry | 1 terminal
          </span>
        ) : (
          <span className="mono whitespace-nowrap">5 nodes | 4 edges | 1 entry | 1 terminal</span>
        )}
        <span className="text-ink-300 dark:text-ink-700">|</span>
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
        <Button size="sm" variant="primary" icon="play">
          Run
        </Button>
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


function CanvasOverlay({
  lensMode,
  focusNode,
  selectedEdgeIndex,
  highlightedEdges,
  onSelectEdge,
}: {
  lensMode: LensMode
  focusNode: string | null
  selectedEdgeIndex: number | null
  highlightedEdges: Set<number>
  onSelectEdge: (edgeIndex: number) => void
}) {
  const overviewEdges = EDGES.map((edge, index) => ({
    edge,
    index,
    active: selectedEdgeIndex === index || highlightedEdges.has(index),
  }))

  return (
    <>
      <div className="absolute left-4 top-4 z-10 rounded-2xl border border-white/65 bg-white/82 px-4 py-3 shadow-card backdrop-blur dark:border-white/10 dark:bg-ink-950/75">
        <div className="mono text-[10px] uppercase tracking-[0.16em] text-ink-500">Knowledge lens</div>
        <div className="mt-1 text-[12.5px] text-ink-700 dark:text-ink-300">
          {lensMode === 'topology' && 'Topology emphasizes structure, entry, exit, and fan-out.'}
          {lensMode === 'relations' && 'Relations emphasize the local neighborhood around the current focus.'}
          {lensMode === 'execution' && 'Execution emphasizes decision flow and the hot path through the graph.'}
        </div>
        <div className="mt-2 text-[11px] text-ink-500">
          Focus node: <span className="mono text-ink-950 dark:text-white">{focusNode ?? 'none'}</span>
        </div>
      </div>

      <div className="absolute right-4 top-4 z-10 w-[220px] rounded-2xl border border-white/65 bg-white/82 p-3 shadow-card backdrop-blur dark:border-white/10 dark:bg-ink-950/75">
        <div className="mono text-[10px] uppercase tracking-[0.16em] text-ink-500">Overview</div>
        <div className="mt-2 h-[74px] rounded-xl border border-black/5 bg-white/70 dark:border-white/10 dark:bg-white/5">
          <MiniMap focusNode={focusNode} />
        </div>
        <div className="mt-3 space-y-1.5">
          {overviewEdges.map(({ edge, index, active }) => (
            <button
              key={`${edge.from}-${edge.to}-${index}`}
              onClick={() => onSelectEdge(index)}
              className={`w-full rounded-lg px-2.5 py-2 text-left transition ${
                active
                  ? 'bg-accent-50 text-accent-700 dark:bg-accent-700/20 dark:text-accent-100'
                  : 'hover:bg-ink-50 dark:hover:bg-ink-900/60'
              }`}
            >
              <div className="mono text-[10.5px]">{`${edge.from} -> ${edge.to}`}</div>
              <div className="text-[10.5px] text-ink-500">{edge.label ?? 'fallthrough'}</div>
            </button>
          ))}
        </div>
      </div>
    </>
  )
}

function MiniMap({ focusNode }: { focusNode: string | null }) {
  const cols = 3
  const fw = 210 / cols
  const fh = 74 / Math.ceil(STUDIO_NODES.length / cols)
  const nodeIndex = new Map(STUDIO_NODES.map((n, i) => [n.name, i]))

  return (
    <svg viewBox="0 0 210 74" className="h-full w-full">
      {EDGES.map((edge, index) => {
        const fromIdx = nodeIndex.get(edge.from)
        const toIdx = nodeIndex.get(edge.to)
        if (fromIdx == null || toIdx == null) return null
        return (
          <line
            key={index}
            x1={(fromIdx % cols) * fw + fw / 2}
            y1={Math.floor(fromIdx / cols) * fh + fh / 2}
            x2={(toIdx % cols) * fw + fw / 2}
            y2={Math.floor(toIdx / cols) * fh + fh / 2}
            stroke="rgba(15,23,42,0.25)"
            strokeWidth="1.4"
          />
        )
      })}
      {STUDIO_NODES.map((node, index) => {
        const meta = NODE_META[node.name]
        return (
          <circle
            key={node.name}
            cx={(index % cols) * fw + fw / 2}
            cy={Math.floor(index / cols) * fh + fh / 2}
            r={focusNode === node.name ? 5 : 3.5}
            fill={meta?.accent ?? '#6b7280'}
            opacity={focusNode === node.name ? 1 : 0.72}
          />
        )
      })}
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
  const node = STUDIO_NODES.find((item) => item.name === nodeName)
  if (!node) return null

  const incoming = EDGES.filter((edge) => edge.to === node.name)
  const outgoing = EDGES.filter((edge) => edge.from === node.name)
  const meta = NODE_META[node.name]

  const Field = ({ k, v }: { k: string; v: string }) => (
    <div className="grid grid-cols-[100px_1fr] gap-3 py-2 border-t hairline first:border-t-0 text-[12.5px]">
      <span className="mono text-[11px] text-ink-500">{k}</span>
      <span className="mono text-[12px] text-ink-950 dark:text-white break-all">{v}</span>
    </div>
  )

  return (
    <div className="p-4 pb-6">
      <div className="mb-4 rounded-2xl border hairline bg-[linear-gradient(135deg,rgba(13,143,99,0.08),rgba(50,118,255,0.05))] px-4 py-3 dark:bg-[linear-gradient(135deg,rgba(13,143,99,0.12),rgba(50,118,255,0.10))]">
        <div className="mono text-[10px] uppercase tracking-[0.16em] text-ink-500">{meta.tier}</div>
        <div className="mt-1 text-[12.5px] leading-relaxed text-ink-700 dark:text-ink-300">{meta.insight}</div>
      </div>

      <Field k="name" v={node.name} />
      <Field k="kind" v={node.kind} />
      <Field k="function" v={node.fn} />
      <Field k="retry" v={node.retry} />
      <Field k="entry" v={node.entry ? 'true' : 'false'} />
      <Field k="terminal" v={node.terminal ? 'true' : 'false'} />

      <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-6 mb-2">Incoming</h4>
      <div className="rounded-lg bg-ink-50 dark:bg-ink-900 p-3 mono text-[11.5px]">
        {incoming.length === 0 ? (
          <span className="text-ink-500">(entry - no incoming)</span>
        ) : (
          incoming.map((edge, index) => (
            <div key={index} className="text-ink-700 dark:text-ink-300">
              {'<- '}
              <span className="text-emerald-600">{edge.from}</span>
              {edge.label ? ` if (${edge.label})` : ''}
            </div>
          ))
        )}
      </div>

      <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-5 mb-2">Outgoing</h4>
      <div className="rounded-lg bg-ink-50 dark:bg-ink-900 p-3 mono text-[11.5px]">
        {outgoing.length === 0 ? (
          <span className="text-ink-500">(terminal - no outgoing)</span>
        ) : (
          outgoing.map((edge, index) => (
            <div key={index} className="text-ink-700 dark:text-ink-300">
              {'-> '}
              <span className="text-emerald-600">{edge.to}</span>
              {edge.label ? ` if (${edge.label})` : ''}
            </div>
          ))
        )}
      </div>

      <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-5 mb-2">Builder snippet</h4>
      <pre className="rounded-lg bg-ink-950 dark:bg-black/60 border border-white/5 p-3 mono text-[11px] text-white/85 overflow-x-auto scroll-thin">{`.${node.kind === 'parallel' ? 'parallel' : node.kind === 'async' ? 'asyncNode' : 'node'}("${node.name}", ${node.fn}${node.retry !== '-' ? ',\n    RetryPolicy.exponential(...)' : ''})`}</pre>
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
      <div className="mb-4 rounded-2xl border hairline bg-[linear-gradient(135deg,rgba(50,118,255,0.08),rgba(109,74,255,0.05))] px-4 py-3 dark:bg-[linear-gradient(135deg,rgba(50,118,255,0.12),rgba(109,74,255,0.10))]">
        <div className="mono text-[10px] uppercase tracking-[0.16em] text-ink-500">Relationship</div>
        <div className="mt-1 text-[12.5px] leading-relaxed text-ink-700 dark:text-ink-300">
          {edge.label
            ? `This edge carries the ${edge.label} predicate from ${edge.from} into ${edge.to}.`
            : `This edge is the default transition from ${edge.from} into ${edge.to}.`}
        </div>
      </div>

      <Field k="index" v={`#${edgeIndex + 1}`} />
      <Field k="from" v={edge.from} />
      <Field k="to" v={edge.to} />
      <Field k="label" v={edge.label ?? '(unconditional)'} />
      <Field k="type" v={edge.label ? 'predicate edge' : 'fallthrough edge'} />

      <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-6 mb-2">Endpoints</h4>
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
      <pre className="rounded-lg bg-ink-950 dark:bg-black/60 border border-white/5 p-3 mono text-[11px] text-white/85 overflow-x-auto scroll-thin">
        {edge.label ? `.edge("${edge.from}", "${edge.to}", s -> ${edge.label})` : `.edge("${edge.from}", "${edge.to}")`}
      </pre>
    </div>
  )
}
