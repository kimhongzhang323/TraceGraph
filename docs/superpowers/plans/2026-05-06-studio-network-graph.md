# Studio Network Graph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the static SVG canvas in the Studio page with an interactive force-directed network graph using React Flow, where nodes are small accent-colored circles with monospace name labels.

**Architecture:** Install `@xyflow/react` and `d3-force`. Create a self-contained `GraphCanvas` component that converts `STUDIO_NODES`/`EDGES` mock data into React Flow nodes/edges, runs a d3-force simulation synchronously to compute initial positions, registers a `CircleNode` custom node type, and wires selection/lens-mode props through to the parent `Studio` page. The existing left panel, right inspector, lens switcher, and canvas overlay remain untouched.

**Tech Stack:** React 18, TypeScript, `@xyflow/react` v12, `d3-force`, Tailwind CSS (existing)

---

## File Map

| File | Action |
|---|---|
| `web/package.json` | Add `@xyflow/react`, `d3-force`, `@types/d3-force` |
| `web/src/main.tsx` | Import React Flow stylesheet |
| `web/src/components/GraphCanvas.tsx` | **Create** — force layout + React Flow canvas |
| `web/src/components/index.ts` | Export `GraphCanvas` |
| `web/src/pages/Studio.tsx` | Replace `<StudioCanvas>` with `<GraphCanvas>`; delete `StudioCanvas`, `buildEdgePath`, `getEdgeVisualState`, `getNodeVisualState` |
| `web/src/data/mock.ts` | Remove `NODE_LAYOUT` export |
| `web/src/types/index.ts` | Remove `NodeLayout` interface |

---

## Task 1: Install dependencies and import stylesheet

**Files:**
- Modify: `web/package.json`
- Modify: `web/src/main.tsx`

- [ ] **Step 1: Install React Flow and d3-force**

```bash
cd web
npm install @xyflow/react d3-force
npm install --save-dev @types/d3-force
```

Expected output: packages added, no peer-dep errors.

- [ ] **Step 2: Import React Flow stylesheet in `web/src/main.tsx`**

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import '@xyflow/react/dist/style.css'
import { App } from './App'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
```

- [ ] **Step 3: Verify dev server starts without errors**

```bash
npm run dev
```

Expected: Vite starts, no TypeScript or module-not-found errors.

- [ ] **Step 4: Commit**

```bash
git add web/package.json web/package-lock.json web/src/main.tsx
git commit -m "feat(web): install @xyflow/react and d3-force"
```

---

## Task 2: Create the `GraphCanvas` component

**Files:**
- Create: `web/src/components/GraphCanvas.tsx`

This component owns all React Flow logic. It accepts the same props as the old `StudioCanvas` and is a drop-in replacement.

- [ ] **Step 1: Create `web/src/components/GraphCanvas.tsx`**

```tsx
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
```

- [ ] **Step 2: Export `GraphCanvas` from the component barrel**

Open `web/src/components/index.ts` and add:

```ts
export { GraphCanvas } from './GraphCanvas'
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd web && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add web/src/components/GraphCanvas.tsx web/src/components/index.ts
git commit -m "feat(web): add GraphCanvas with force-directed React Flow layout"
```

---

## Task 3: Wire `GraphCanvas` into `Studio.tsx`

**Files:**
- Modify: `web/src/pages/Studio.tsx`

Replace the `<StudioCanvas>` block and delete the old SVG helpers. The left panel, right inspector, canvas background, and `CanvasOverlay` are untouched.

- [ ] **Step 1: Update imports at the top of `Studio.tsx`**

Replace the existing import block with:

```tsx
import { useEffect, useState } from 'react'
import { Badge, Button, GraphCanvas, Icon, Panel } from '@/components'
import { EDGES, STUDIO_NODES } from '@/data/mock'
import { api } from '@/lib/api'
import type { Edge, GraphComplexity } from '@/types'
```

(Remove `NODE_LAYOUT` from the import; remove `NodeLayout` from the type import.)

- [ ] **Step 2: Replace the Canvas panel content in `Studio.tsx`**

Find the Canvas `<Panel>` block (the one containing `<StudioCanvas …>`). The inner `<div>` currently contains the background gradients, `<CanvasOverlay>`, and `<StudioCanvas>`. Replace `<StudioCanvas … />` with:

```tsx
<div className="absolute inset-0">
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
```

The full Canvas `<Panel>` children should now look like:

```tsx
<div className="relative h-full overflow-hidden bg-[radial-gradient(circle_at_18%_18%,rgba(13,143,99,0.10),transparent_24%),radial-gradient(circle_at_80%_10%,rgba(50,118,255,0.10),transparent_24%),radial-gradient(circle_at_78%_78%,rgba(109,74,255,0.12),transparent_24%),linear-gradient(180deg,#fcfcfc_0%,#f3f6f8_100%)] dark:bg-[radial-gradient(circle_at_18%_18%,rgba(13,143,99,0.16),transparent_24%),radial-gradient(circle_at_80%_10%,rgba(50,118,255,0.14),transparent_24%),radial-gradient(circle_at_78%_78%,rgba(109,74,255,0.18),transparent_24%),linear-gradient(180deg,#0c1014_0%,#090b0f_100%)]">
  <CanvasOverlay
    lensMode={lensMode}
    focusNode={focusNode}
    selectedEdgeIndex={selectedEdgeIndex}
    highlightedEdges={highlightedEdges}
    onSelectEdge={(edgeIndex) => setSelection({ type: 'edge', edgeIndex })}
  />
  <div className="absolute inset-0">
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
```

- [ ] **Step 3: Delete the dead code from `Studio.tsx`**

Delete the following functions entirely (they are replaced by `GraphCanvas`):

- `StudioCanvas` function (lines ~259–420)
- `buildEdgePath` function
- `getEdgeVisualState` function
- `getNodeVisualState` function

Also delete the `focusNode`, `neighborhood`, and `highlightedEdges` locals inside `Studio()` **only if** `CanvasOverlay` doesn't need them. Check: `CanvasOverlay` uses `focusNode` and `highlightedEdges` — keep those locals. Remove only the functions above.

- [ ] **Step 4: Verify TypeScript compiles**

```bash
cd web && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 5: Open the Studio page in the browser and verify**

```bash
npm run dev
```

Navigate to the Studio page. You should see:
- Force-directed circle nodes arranged organically on the dark canvas
- Node name labels below each circle in monospace
- Curved edges with arrow markers between nodes
- Clicking a node highlights it (glowing ring) and updates the right inspector
- Pan and zoom work (drag the canvas, scroll to zoom)
- Lens mode switcher changes edge/node opacity

- [ ] **Step 6: Commit**

```bash
git add web/src/pages/Studio.tsx
git commit -m "feat(web): replace SVG canvas with React Flow force-directed graph in Studio"
```

---

## Task 4: Remove `NODE_LAYOUT` and `NodeLayout`

**Files:**
- Modify: `web/src/data/mock.ts`
- Modify: `web/src/types/index.ts`

- [ ] **Step 1: Remove `NODE_LAYOUT` from `web/src/data/mock.ts`**

Delete the entire `NODE_LAYOUT` export block:

```ts
// DELETE this block:
export const NODE_LAYOUT: Record<string, NodeLayout> = {
  validate:{x:320,y:70, w:120,h:56,kind:'node'},
  enrich:  {x:320,y:180,w:120,h:56,kind:'parallel'},
  score:   {x:320,y:290,w:120,h:56,kind:'async'},
  charge:  {x:200,y:400,w:120,h:56,kind:'node | retry(3)'},
  ship:    {x:440,y:400,w:120,h:56,kind:'node'},
}
```

Also remove `NodeLayout` from the import at the top of `mock.ts`:

```ts
// Change from:
import type {
  ExecutionTrace, TraceSummary, NodeLayout, Edge, StudioNode,
  Module, ChangelogEntry, ApiGroup,
} from '@/types'

// To:
import type {
  ExecutionTrace, TraceSummary, Edge, StudioNode,
  Module, ChangelogEntry, ApiGroup,
} from '@/types'
```

- [ ] **Step 2: Remove `NodeLayout` from `web/src/types/index.ts`**

Delete the interface:

```ts
// DELETE this block:
export interface NodeLayout {
  x: number
  y: number
  w: number
  h: number
  kind: string
}
```

- [ ] **Step 3: Verify TypeScript compiles with no errors**

```bash
cd web && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add web/src/data/mock.ts web/src/types/index.ts
git commit -m "chore(web): remove NODE_LAYOUT and NodeLayout now that GraphCanvas owns layout"
```

---

## Self-Review Notes

- **Spec coverage:** All spec requirements are covered — React Flow, d3-force, circle nodes, force layout, selection sync, lens mode, deleted helpers, removed `NODE_LAYOUT`. ✓
- **No placeholders:** All steps include full code. ✓
- **Type consistency:** `CircleNodeData`, `GraphCanvasProps`, `LensMode` defined in Task 2 and used consistently in Task 3. `NODE_ACCENTS` drives accent colors consistently. ✓
- **`CanvasOverlay` preservation:** `focusNode`, `neighborhood`, `highlightedEdges` locals in `Studio()` are retained because `CanvasOverlay` still consumes them. ✓
