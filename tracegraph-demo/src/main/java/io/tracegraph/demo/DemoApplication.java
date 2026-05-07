package io.tracegraph.demo;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoApplication {

    private static final Logger LOG = LoggerFactory.getLogger(DemoApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedTraces(Graph<OrderState> graph) {
        return args -> {
            seed(graph, "ord-001", 49.99);
            seed(graph, "ord-002", 1500.00);
            seed(graph, "ord-003", 200.00);
            seed(graph, "ord-004", 999.0);
            seed(graph, "ord-005", 75.00);
        };
    }

    private static void seed(Graph<OrderState> graph, String orderId, double amount) {
        try {
            ExecutionResult<OrderState> result = graph.run(OrderState.of(orderId, amount));
            LOG.info("execution {} status={} path={}", result.executionId(), result.status(), result.path());
        } catch (Exception ex) {
            LOG.warn("execution for orderId={} raised exception: {}", orderId, ex.getMessage());
        }
    }
}
