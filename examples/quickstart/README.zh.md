# Quickstart 示例（中文参考实现）

本目录包含与教程配套的简易参考实现（目的是演示概念，而非完整生产代码）：

- `zh_examples/OrderStateExample.java`：展示不可变 `OrderState` record。
- `zh_examples/ExerciseImplementations.java`：包含教程练习的伪实现（条件流、异步收款、sendAll 模拟）。

运行方法（示例）：

```bash
# 在项目根目录
mvn -f examples/quickstart/pom.xml -DskipTests exec:java -Dexec.mainClass=zh_examples.ExerciseImplementations
```

说明：这些参考实现不依赖完整 TraceGraph runtime，只用于帮助理解教程中的代码片段。如需将它们编译为真实可运行示例，我可以把 `examples/quickstart/pom.xml` 更新为包含必要的依赖并实现真实的 `Graph` 构建与运行。
