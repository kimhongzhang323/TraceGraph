package io.tracegraph.examples.hitl;

import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import io.tracegraph.runtime.InMemoryCheckpointStore;

public class HitlApprovalMain {

    record ApprovalState(String action, String status) {}

    public static void main(String[] args) {
        var checkpointStore = new InMemoryCheckpointStore();

        Graph<ApprovalState> graph = Graph.<ApprovalState>builder()
                .node("prepare", (state, ctx) ->
                        new ApprovalState(state.action(), "pending approval for: " + state.action()))
                .node("execute", (state, ctx) ->
                        new ApprovalState(state.action(), "executed: " + state.action()))
                .entry("prepare")
                .edge("prepare", "execute")
                .interruptBefore("execute")
                .checkpointStore(checkpointStore)
                .build();

        // First run — will pause before "execute"
        var interrupted = graph.run(new ApprovalState("deploy-to-prod", null));
        System.out.println("Status: " + interrupted.status());
        System.out.println("State at interrupt: " + interrupted.finalState());
        System.out.println("Path so far: " + interrupted.path());

        if (interrupted.status() == Status.INTERRUPTED) {
            System.out.println("Simulating human approval...");
            var resumed = graph.resume(interrupted.executionId());
            resumed.ifPresent(result -> {
                System.out.println("Resumed status: " + result.status());
                System.out.println("Final state: " + result.finalState());
            });
        }
    }
}
