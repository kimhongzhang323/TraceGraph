# TraceGraph :: Demo Application

## 📖 Introduction to the Demo App
Welcome to the `tracegraph-demo` module! This is a standalone, pre-configured Spring Boot application that serves as a complete end-to-end showcase of TraceGraph in a production-like environment.

If you want to demonstrate TraceGraph to your team or want to understand how execution graphs, memory stores, auto-configuration, REST endpoints, and the UI dashboard all wire together, this is the place to look.

### Core Highlights
- **Out-of-the-Box**: A self-contained Spring Boot application with a zero-configuration startup experience.
- **Full-Stack Integration**: It bundles and enables `tracegraph-spring-boot-starter`, `tracegraph-memory`, and the visual frontend `tracegraph-ui`.
- **Multiple Agent Examples**: It registers multiple graph beans, including a RAG Agent for question-answering and a ReAct agent for complex tool use.

## 🏗️ Demo Architecture

```mermaid
flowchart TD
    subgraph Spring Boot Container
        DemoApp[DemoApplication] --> Starter[TraceGraph Auto-Config]
        Starter --> Mem[Memory Store (JdbcMemoryStore)]
        Starter --> UI[UI Endpoints]
        
        DemoApp --> Agent1[RAG Agent Bean]
        DemoApp --> Agent2[ReAct Agent Bean]
    end
    
    User((End User)) -->|HTTP POST| REST[API Controller]
    REST --> Agent1
    REST --> Agent2
```

## 🚀 How to Run and Experience

This module is designed to be runnable instantly with a couple of commands.

### 1. Start the Application
You can start this demo directly using the Maven `spring-boot:run` plugin.
```bash
cd tracegraph-demo
mvn spring-boot:run
```

### 2. Access the Console & UI
Once the application starts (defaulting to port 8080):

- **UI Dashboard**: Open your browser and visit `http://localhost:8080/tracegraph-ui` to view the graphical tracing interface.
- **REST API Endpoints**: The application exposes APIs to trigger Agent execution. You can call them using curl or Postman:
  ```bash
  curl -X POST http://localhost:8080/api/agent/rag \
       -H "Content-Type: application/json" \
       -d '{"query": "What databases does TraceGraph support?"}'
  ```

### 3. View the Traces
After sending the REST request, refresh your UI dashboard, and you will see the complete flow of the agent execution. You can click on specific nodes to inspect the state changes in memory!
