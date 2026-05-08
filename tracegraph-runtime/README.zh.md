# tracegraph-runtime（运行时）

`tracegraph-runtime` 提供检查点（checkpoint）与恢复（resume）相关的实现，以及运行时执行器的支持代码。它负责在长期执行或中断后恢复执行，并与核心执行语义集成。

核心功能：

- `CheckpointStore` SPI 与 `InMemoryCheckpointStore` 实现。
- 恢复流程：恢复时从上次完成的节点继续，重新评估出边并继续执行。
- 与重试/中断的语义交互需要注意：节点可能会在恢复后以 at-least-once 的形式重新执行。
