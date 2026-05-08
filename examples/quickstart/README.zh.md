# 快速开始（中文示例）

本示例演示如何使用 TraceGraph 构建一个简单的订单处理流程（下单 → 验证 → 扣款 → 发货）。

先决条件：JDK 21、Maven。确保 `JAVA_HOME` 指向 JDK 21。

本地运行：

```bash
mvn -f examples/quickstart/pom.xml -DskipTests exec:java
```

示例文件：

- `examples/quickstart/src/main/java/zh_examples/OrderStateExample.java`：订单状态示例。
- `examples/quickstart/src/main/java/zh_examples/ExerciseImplementations.java`：练习实现（充当演示替身）。

练习：修改 `OrderStateExample` 中的节点实现，模拟网络延迟并观察重试行为。
# Quickstart 示例（中文参考实现）

本目录包含与教程配套的简易参考实现（目的是演示概念，而非完整生产代码）：

- `zh_examples/OrderStateExample.java`：展示不可变 `OrderState` record。
- `zh_examples/ExerciseImplementations.java`：包含教程练习的伪实现（条件流、异步收款、sendAll 模拟）。

运行方法（示例）：

```bash
# 在项目根目录
mvn -f examples/quickstart/pom.xml -DskipTests exec:java -Dexec.mainClass=zh_examples.ExerciseImplementations
```

说明：这些参考实现不依赖完整 TraceGraph runtime，只用于帮助理解教程中的代码片段。
