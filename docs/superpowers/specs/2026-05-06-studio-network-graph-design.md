# Studio Network Graph — Design Spec

**Date:** 2026-05-06  
**Status:** Approved

## Overview

Replace the static SVG canvas in the Studio page with an interactive force-directed network graph using React Flow. Nodes are small filled circles with monospace name labels; edges are curved arrows. The graph is pannable, zoomable, and node-draggable.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Library | `@xyflow/react` (React Flow v12) | Native React, MIT, best DAG support, built-in pan/zoom/drag/minimap |
| Layout algorithm | `d3-force` force simulation | Organic clustering layout matching yFiles knowledge-graph aesthetic |
| Node style | Small filled circle + name label below | Compact, knowledge-graph style; accent color per node tier |
| Edge style | Curved arrows, label pill on conditional edges | Matches current Studio visual language |

## Scope

### What changes

- `StudioCanvas` component replaced by `GraphCanvas` (new file: `src/components/GraphCanvas.tsx`)
- `NODE_LAYOUT` record deleted — hardcoded positions replaced by force simulation
- Helper functions `buildEdgePath`, `getEdgeVisualState`, `getNodeVisualState` deleted
- React Flow stylesheet import added to `main.tsx` or `App.tsx`
- Three new npm dependencies: `@xyflow/react`, `d3-force`, `@types/d3-force`

### What stays the same

- Left panel: node list + edge list with selection state
- Right panel: `StudioInspector` (node/edge detail)
- Lens mode switcher (topology / relations / execution) — drives visual emphasis via node/edge opacity and color, same logic as today
- Selection sync: clicking a node/edge in the graph updates the inspector and left panel highlight
- Canvas background (radial gradients + grid)
- `CanvasOverlay` (knowledge lens card + overview panel with minimap)
- `MiniMap` component — React Flow provides its own minimap but the existing overlay minimap stays as-is in the overlay panel

## Architecture

### `GraphCanvas` component

```
GraphCanvas
  props:
    nodes: StudioNode[]          // from STUDIO_NODES mock
    edges: Edge[]                // from EDGES mock
    selectedNodeName: string | null
    selectedEdgeIndex: number | null
    lensMode: LensMode
    onSelectNode: (name: string) => void
    onSelectEdge: (index: number) => void

  internals:
    - Converts nodes/edges to React Flow node/edge objects on mount
    - Runs d3-force simulation once to compute x/y positions
    - Passes computed positions to React Flow as initialNodes (layouted: true)
    - Registers custom node type: 'circleNode' → CircleNode component
    - React Flow handles all pan/zoom/drag after initial layout
```

### `CircleNode` (custom React Flow node type)

- Renders a filled `<circle>` via SVG or a `<div>` styled as a circle
- Radius: 22px, fill: `NODE_META[name].accent`
- Label: node name in monospace, centered below the circle
- Selected state: white outer ring + drop shadow glow
- Neighborhood state (lens-driven): full opacity vs dimmed (0.3)

### Force simulation setup

```
d3-force simulation:
  - forceLink (edges, distance 120)
  - forceManyBody (strength -300)
  - forceCenter (canvas width/2, canvas height/2)
  - forceCollide (radius 40)
  - Run for 300 ticks synchronously before render (no animation delay)
```

### Lens mode → visual state

Same logic as current `getNodeVisualState` / `getEdgeVisualState` but applied as React Flow node/edge `data` props on each re-render when `lensMode` or `selectedNodeName` changes. No re-layout on lens change.

### Edge rendering

React Flow's default `BezierEdge` for unconditional edges. Conditional edges use a custom `LabeledEdge` that renders the existing label pill at the midpoint. Arrow markers via React Flow's built-in `MarkerType.ArrowClosed`.

## File changes

| File | Action |
|---|---|
| `web/src/components/GraphCanvas.tsx` | Create |
| `web/src/pages/Studio.tsx` | Replace `<StudioCanvas …>` with `<GraphCanvas …>`; delete `StudioCanvas`, `buildEdgePath`, `getEdgeVisualState`, `getNodeVisualState`, `NODE_LAYOUT` import |
| `web/src/data/mock.ts` | Remove `NODE_LAYOUT` export (check it's not used elsewhere) |
| `web/src/types.ts` | Remove `NodeLayout` type if only used by `NODE_LAYOUT` |
| `web/package.json` | Add `@xyflow/react`, `d3-force`, `@types/d3-force` |
| `web/src/main.tsx` or `App.tsx` | Import `@xyflow/react/dist/style.css` |

## Out of scope

- Real graph data from the backend API (still uses mock data)
- Per-node execution trace animation (Phase 3 feature)
- Saving user-dragged positions across sessions
- Branch-level parallel node fan-out rendering changes
