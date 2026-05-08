# LangGraph Spring Boot Starter (Legacy)

## 📖 Introduction
Welcome to `langgraph-spring-boot-starter`. 

> **⚠️ IMPORTANT NOTE**: This module is considered **legacy** or **experimental**. The primary, actively maintained starter for this project is `tracegraph-spring-boot-starter`. Please use that one for all new projects.

This module was originally created to bridge the concepts of Python's `LangGraph` into the Java Spring Boot ecosystem via Auto-Configuration. It allows developers to inject graph agents as Spring Beans without writing boilerplate wiring code.

## 🏗️ How Auto-Configuration Works

Spring Boot Starters work by scanning the classpath. When you include this module, it automatically looks for tracegraph/langgraph components and registers them in the Application Context.

```mermaid
flowchart TD
    Starter[LangGraph Starter JAR] --> AutoConfig[@AutoConfiguration Class]
    AutoConfig --> Condition1[Check if TraceGraph is on classpath]
    AutoConfig --> Condition2[Check if application.yml is configured]
    
    Condition1 & Condition2 --> BeanFactory[Create Spring Beans]
    BeanFactory --> Bean1[Graph Bean]
    BeanFactory --> Bean2[Memory Store Bean]
    
    Bean1 & Bean2 --> App[Your Custom App Code]
```

## 🚀 Example Usage (If you must use it)

### 1. Add the Dependency
```xml
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>langgraph-spring-boot-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 2. Configure Properties
```yaml
langgraph:
  enabled: true
  checkpoint:
    type: postgres # Auto-configures PostgreSQL memory saving
```

### 3. Inject the Graph
Because the starter configures everything for you, you can simply `@Autowire` the components you need:

```java
import org.springframework.stereotype.Service;

@Service
public class MyService {
    
    // This is created automatically by the starter!
    private final TraceGraph traceGraph;
    
    public MyService(TraceGraph traceGraph) {
        this.traceGraph = traceGraph;
    }
}
```
