package zh_examples;

import java.util.concurrent.CompletableFuture;

public class ExerciseImplementations {

    // 练习 1: 条件流（伪实现）
    public static void conditionalFlow() {
        System.out.println("练习1: 未通过验证进入 reject 节点（伪实现）");
    }

    // 练习 2: 异步 charge 模拟
    public static CompletableFuture<Void> asyncCharge() {
        return CompletableFuture.runAsync(() -> {
            try { Thread.sleep(200); } catch (InterruptedException e) { /* ignore */ }
            System.out.println("模拟异步收款完成");
        });
    }

    // 练习 3: sendAll 模拟
    public static void sendAllDemo() {
        System.out.println("练习3: 模拟 sendAll 并行分发到多个仓库");
    }

    public static void main(String[] args) throws Exception {
        conditionalFlow();
        asyncCharge().join();
        sendAllDemo();
    }
}
