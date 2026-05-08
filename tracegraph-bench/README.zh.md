# TraceGraph :: Benchmark (基准测试)

本模块包含针对 TraceGraph 核心组件（执行引擎、状态管理、图路由）的性能基准测试。它使用 JMH (Java Microbenchmark Harness) 框架，用于检测执行时的吞吐量瓶颈、锁争用和并发限制。

## 运行基准测试

```bash
mvn clean install -DskipTests
cd tracegraph-bench
java -jar target/benchmarks.jar
```
