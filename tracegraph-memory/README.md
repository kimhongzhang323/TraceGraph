# TraceGraph Memory

`tracegraph-memory` empowers agents with durable memory to remember facts, constraints, and semantics across multi-turn sessions or long-running tasks.

## Features
- **Layered / Scoped Memory**: Store key-value facts per execution, per user, or per workspace via explicit context scopes.
- **Implementations**:
  - `InMemoryMemoryStore` (volative `ConcurrentHashMap`)
  - `FileMemoryStore` (JSON-backed filesystem structure)
  - `JdbcMemoryStore` (SQL persistence layer via RDBMS)
- **Serialization Guarantee**: Jackson-based polymorphic typing safeguards heterogeneous objects being round-tripped effortlessly.

## Memory Layer Architecture

```mermaid
graph LR
    subgraph Execution Layer
        Context[Node Context]
    end
    
    subgraph Stores
        MemAuth{MemoryStore<br/>Router}
        Temp[(In-Memory)]
        File[(File System)]
        RDBMS[(JDBC Store)]
    end
    
    Context -->|ctx.memory().put()| MemAuth
    Context -->|ctx.memory().get()| MemAuth
    
    MemAuth --> Temp
    MemAuth --> File
    MemAuth --> RDBMS
```