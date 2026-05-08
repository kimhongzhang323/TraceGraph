# TraceGraph :: UI

## 📖 Introduction to TraceGraph UI
Welcome to `tracegraph-ui`! Building graph-based agents can get complicated. When an agent loops back on itself or takes an unexpected path, debugging via standard text logs can be a nightmare.

The UI module provides a **visual, real-time dashboard** for your TraceGraph applications. It allows developers and end-users to see the graph structure, watch the agent move from node to node, and inspect the state data at every step.

### Why Do I Need This?
- **Debugging**: Easily see exactly *why* your agent made a specific decision.
- **State Inspection**: Click on a node execution to see exactly what JSON data was in the state at that moment.
- **Demonstration**: Show off your complex AI workflows to non-technical stakeholders in a visual way.

## 🏗️ UI Architecture

The UI module hooks into the Observability layer of TraceGraph, streaming events to a web frontend.

```mermaid
flowchart LR
    subgraph Backend
        Core[TraceGraph Execution] --> Obs[Event Publisher]
        Obs --> Controller[Spring WebSockets / REST]
    end
    
    subgraph Frontend (Browser)
        Controller -->|State Events| Dashboard[React / Vue Dashboard]
        Dashboard --> Vis[Node Visualization Graph]
        Dashboard --> Inspector[JSON State Inspector]
    end
```

## 🚀 How to Use It

Because this is built specifically for Spring Boot, enabling the UI is incredibly easy via Spring's Auto-Configuration.

### 1. Add the Dependency
Include this module in your application's `pom.xml`:

```xml
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-ui</artifactId>
</dependency>
```

### 2. Enable in Properties
In your `application.yml` or `application.properties`, simply enable the UI endpoint:

```yaml
tracegraph:
  ui:
    enabled: true
    port: 8081 # Optional: Run UI on a separate port
```

### 3. Access the Dashboard
Once you start your Spring Boot application, open your web browser and navigate to:
`http://localhost:8080/tracegraph-ui`

You will immediately see a visualization of any currently running agents!
