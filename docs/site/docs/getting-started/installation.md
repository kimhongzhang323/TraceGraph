# Installation

TraceGraph requires **JDK 21**.

## Maven

Add the modules you need:

```xml
<!-- Core graph engine (always required) -->
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>langgraph-core</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>

<!-- LLM connectors (OpenAI, Anthropic, Gemini, Ollama) -->
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>langgraph-connectors</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>

<!-- RAG: vector stores, embeddings, retriever -->
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>langgraph-rag</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>

<!-- Spring Boot starter (auto-config for all SPIs) -->
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>langgraph-spring-boot-starter</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

## Snapshots

SNAPSHOT artifacts are published to Maven Central Snapshots:

```xml
<repository>
    <id>central-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <snapshots><enabled>true</enabled></snapshots>
</repository>
```
