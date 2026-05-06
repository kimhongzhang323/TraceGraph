import { useCallback, useMemo } from 'react'
import {
  ReactFlow,
  ReactFlowProvider,
  Background,
  BackgroundVariant,
  useNodesState,
  useEdgesState,
  type Node,
  type Edge as RFEdge,
  type NodeTypes,
  MarkerType,
  Handle,
  Position,
  type NodeProps,
} from '@xyflow/react'
import * as d3Force from 'd3-force'
import type { Edge, StudioNode } from '@/types'

type LensMode = 'topology' | 'relations' | 'execution'

interface GraphCanvasProps {
  studioNodes: StudioNode[]
  edges: Edge[]
  selectedNodeName: string | null
  selectedEdgeIndex: number | null
  lensMode: LensMode
  onSelectNode: (name: string) => void
  onSelectEdge: (index: number) => void
}

interface CircleNodeData extends Record<string, unknown> {
  label: string
  accent: string
  selected: boolean
  dimmed: boolean
  entry: boolean
  terminal: boolean
}

const NODE_ACCENTS: Record<string, string> = {
  validate: '#0d8f63',
  enrich:   '#3276ff',
  score:    '#6d4aff',
  charge:   '#ff7a59',
  ship:     '#111827',
}

function CircleNode({ data }: NodeProps) {
  const d = data as CircleNodeData
  const r = 22
  const size = r * 2

  return (
    <div style={{ position: 'relative', width: size, height: size + 20 }}>
      <Handle type="target" position={Position.Top} style={{ opacity: 0 }} />

      <svg
        width={size}
        height={size}
        style={{ overflow: 'visible', display: 'block' }}
      >
        {d.selected && (
          <circle
            cx={r}
            cy={r}
            r={r + 7}
            fill="none"
            stroke={d.accent}
            strokeWidth={2}
            opacity={0.5}
          />
        )}
        <circle
          cx={r}
          cy={r}
          r={r}
          fill={d.accent}
          opacity={d.dimmed ? 0.25 : 1}
        />
        {d.entry && (
          <circle cx={r} cy={r} r={r - 5} fill="none" stroke="white" strokeWidth={1.5} opacity={0.6} />
        )}
      </svg>

      <div
        style={{
          position: 'absolute',
          top: size + 4,
          left: '50%',
          transform: 'translateX(-50%)',
          fontFamily: 'monospace',
          fontSize: 11,
          color: d.dimmed ? 'rgba(255,255,255,0.3)' : 'rgba(255,255,255,0.9)',
          whiteSpace: 'nowrap',
          pointerEvents: 'none',
        }}
      >
        {d.label}
      </div>

      <Handle type="source" position={Position.Bottom} style={{ opacity: 0 }} />
    </div>
  )
}

const NODE_TYPES: NodeTypes = { circleNode: CircleNode }

function computeForceLayout(
  nodeIds: string[],
  edgePairs: { source: string; target: string }[],
  width: number,
  height: number,
): Map<string, { x: number; y: number }> {
  const simNodes = nodeIds.map((id) => ({ id, x: width / 2, y: height / 2 }))
  const simLinks = edgePairs.map(({ source, target }) => ({ source, target }))

  const simulation = d3Force
    .forceSimulation(simNodes)
    .force('link', d3Force.forceLink(simLinks).id((d: { id: string }) => d.id).distance(130))
    .force('charge', d3Force.forceManyBody().strength(-400))
    .force('center', d3Force.forceCenter(width / 2, height / 2))
    .force('collide', d3Force.forceCollide(50))
    .stop()

  simulation.tick(300)

  const positions = new Map<string, { x: number; y: number }>()
  simNodes.forEach((n) => positions.set(n.id, { x: n.x, y: n.y }))
  return positions
}

function buildRFNodes(
  studioNodes: StudioNode[],
  positions: Map<string, { x: number; y: number }>,
  selectedNodeName: string | null,
  selectedEdgeIndex: number | null,
  edges: Edge[],
  lensMode: LensMode,
): Node[] {
  const focusNode = selectedNodeName ?? (selectedEdgeIndex != null ? edges[selectedEdgeIndex]?.from : studioNodes[0]?.name)
  const neighborhood = new Set<string>()
  if (focusNode) {
    neighborhood.add(focusNode)
    edges.forEach((e) => {
      if (e.from === focusNode || e.to === focusNode) {
        neighborhood.add(e.from)
        neighborhood.add(e.to)
      }
    })
  }

  return studioNodes.map((n) => {
    const pos = positions.get(n.name) ?? { x: 0, y: 0 }
    const isSelected = n.name === selectedNodeName
    const inNeighborhood = neighborhood.has(n.name)
    const dimmed = lensMode === 'execution' ? !inNeighborhood : !inNeighborhood && lensMode === 'relations'

    return {
      id: n.name,
      type: 'circleNode',
      position: { x: pos.x - 22, y: pos.y - 22 },
      data: {
        label: n.name,
        accent: NODE_ACCENTS[n.name] ?? '#6b7280',
        selected: isSelected,
        dimmed,
        entry: n.entry,
        terminal: n.terminal,
      } satisfies CircleNodeData,
    }
  })
}

function buildRFEdges(
  edges: Edge[],
  selectedEdgeIndex: number | null,
  selectedNodeName: string | null,
  lensMode: LensMode,
): RFEdge[] {
  const focusNeighborEdges = new Set<number>()
  if (selectedNodeName) {
    edges.forEach((e, i) => {
      if (e.from === selectedNodeName || e.to === selectedNodeName) focusNeighborEdges.add(i)
    })
  }

  return edges.map((e, i) => {
    const isSelected = i === selectedEdgeIndex
    const inNeighborhood = focusNeighborEdges.has(i)

    let stroke = 'rgba(255,255,255,0.25)'
    let strokeWidth = 1.5
    let opacity = 0.6

    if (isSelected) {
      stroke = '#0d8f63'; strokeWidth = 2.5; opacity = 1
    } else if (lensMode === 'relations' && inNeighborhood) {
      stroke = '#3276ff'; strokeWidth = 2; opacity = 0.9
    } else if (lensMode === 'execution') {
      stroke = '#6d4aff'; strokeWidth = inNeighborhood ? 2 : 1.2; opacity = inNeighborhood ? 0.85 : 0.25
    } else if (inNeighborhood) {
      strokeWidth = 2; opacity = 0.8
    }

    return {
      id: `e-${i}`,
      source: e.from,
      target: e.to,
      label: e.label ?? undefined,
      type: 'default',
      markerEnd: { type: MarkerType.ArrowClosed, color: stroke },
      style: { stroke, strokeWidth, opacity },
      labelStyle: { fontFamily: 'monospace', fontSize: 10, fill: 'rgba(255,255,255,0.75)' },
      labelBgStyle: { fill: 'rgba(15,17,23,0.85)', stroke: 'rgba(255,255,255,0.12)' },
      labelBgBorderRadius: 8,
      labelBgPadding: [4, 8] as [number, number],
    }
  })
}

function GraphCanvasInner({
  studioNodes,
  edges,
  selectedNodeName,
  selectedEdgeIndex,
  lensMode,
  onSelectNode,
  onSelectEdge,
}: GraphCanvasProps) {
  const positions = useMemo(
    () =>
      computeForceLayout(
        studioNodes.map((n) => n.name),
        edges.map((e) => ({ source: e.from, target: e.to })),
        700,
        500,
      ),
    // positions are computed once on mount — studioNodes and edges are stable mock data
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  )

  const initialNodes = useMemo(
    () => buildRFNodes(studioNodes, positions, selectedNodeName, selectedEdgeIndex, edges, lensMode),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [positions],
  )

  const initialEdges = useMemo(
    () => buildRFEdges(edges, selectedEdgeIndex, selectedNodeName, lensMode),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [positions],
  )

  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes)
  const [rfEdges, , onEdgesChange] = useEdgesState(initialEdges)

  // Sync visual state (selection + lens) into node/edge data without re-running layout
  useMemo(() => {
    setNodes((prev) =>
      buildRFNodes(studioNodes, positions, selectedNodeName, selectedEdgeIndex, edges, lensMode).map((next, i) => ({
        ...prev[i],
        data: next.data,
      })),
    )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedNodeName, selectedEdgeIndex, lensMode])

  const onNodeClick = useCallback(
    (_: React.MouseEvent, node: Node) => onSelectNode(node.id),
    [onSelectNode],
  )

  const onEdgeClick = useCallback(
    (_: React.MouseEvent, edge: RFEdge) => {
      const idx = rfEdges.findIndex((e) => e.id === edge.id)
      if (idx !== -1) onSelectEdge(idx)
    },
    [rfEdges, onSelectEdge],
  )

  return (
    <ReactFlow
      nodes={nodes}
      edges={rfEdges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onNodeClick={onNodeClick}
      onEdgeClick={onEdgeClick}
      nodeTypes={NODE_TYPES}
      fitView
      fitViewOptions={{ padding: 0.3 }}
      minZoom={0.3}
      maxZoom={2.5}
      proOptions={{ hideAttribution: true }}
    >
      <Background variant={BackgroundVariant.Dots} gap={24} size={1} color="rgba(255,255,255,0.06)" />
    </ReactFlow>
  )
}

export function GraphCanvas(props: GraphCanvasProps) {
  return (
    <ReactFlowProvider>
      <GraphCanvasInner {...props} />
    </ReactFlowProvider>
  )
}
