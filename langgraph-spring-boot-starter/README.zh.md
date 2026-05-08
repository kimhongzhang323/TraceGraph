# LangGraph Spring Boot Starter (传统遗留模块)

## 📖 模块简介
欢迎使用 `langgraph-spring-boot-starter` 模块。

> **⚠️ 重要提示**: 该模块被视为**传统遗留（Legacy）**或**实验性**模块。该项目主要的、积极维护的 Starter 是 `tracegraph-spring-boot-starter`。对于所有新项目，请务必使用那一个。

该模块最初的创建目的是通过自动配置（Auto-Configuration）将 Python `LangGraph` 的概念桥接到 Java Spring Boot 生态系统中。它允许开发人员将图代理作为 Spring Bean 注入，而无需编写冗长的手动装配代码。

## 🏗️ 自动配置如何工作

Spring Boot Starters 通过扫描类路径来工作。当您引入此模块时，它会自动寻找 tracegraph/langgraph 组件并将它们注册到 Spring 应用上下文中。

```mermaid
flowchart TD
    Starter[LangGraph Starter JAR 包] --> AutoConfig[@AutoConfiguration 自动配置类]
    AutoConfig --> Condition1[检查 TraceGraph 是否在 classpath 中]
    AutoConfig --> Condition2[检查 application.yml 是否已配置]
    
    Condition1 & Condition2 --> BeanFactory[创建 Spring Beans]
    BeanFactory --> Bean1[Graph Bean 图组件]
    BeanFactory --> Bean2[Memory Store Bean 内存存储组件]
    
    Bean1 & Bean2 --> App[您的自定义应用代码]
```

## 🚀 示例用法 (如果必须使用)

### 1. 添加依赖
```xml
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>langgraph-spring-boot-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 2. 配置属性
```yaml
langgraph:
  enabled: true
  checkpoint:
    type: postgres # 自动配置 PostgreSQL 内存保存机制
```

### 3. 注入图 (Graph)
因为 starter 自动为您配置了一切，所以您只需 `@Autowire` 您需要的组件：

```java
import org.springframework.stereotype.Service;

@Service
public class MyService {
    
    // 此组件由 starter 自动创建并注入！
    private final TraceGraph traceGraph;
    
    public MyService(TraceGraph traceGraph) {
        this.traceGraph = traceGraph;
    }
}
```
