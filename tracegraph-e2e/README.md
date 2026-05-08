# TraceGraph :: End-to-End (E2E) Tests

## 📖 What is End-to-End Testing?
Welcome to `tracegraph-e2e`! Unit tests (like checking if 2+2=4) are great, but they don't prove that your entire system works when deployed to production. 

End-to-End (E2E) tests spin up your *entire application*, including real databases and simulated external APIs, and test the flow from start to finish. We use **Testcontainers** to spin up real PostgreSQL databases inside Docker, and **WireMock** to fake responses from LLM providers (so we don't spend money on API calls during tests).

### Why is this important?
- Prevents "It works on my machine" bugs.
- Verifies that database schemas and SQL queries are correct.
- Ensures the application handles network timeouts and API errors gracefully.

## 🏗️ Testing Architecture

```mermaid
sequenceDiagram
    participant JUnit as E2E Test Suite
    participant App as Spring Boot App
    participant DB as Testcontainers (PostgreSQL)
    participant Mock as WireMock (Mock LLM)

    JUnit->>DB: 1. Spin up Docker Postgres
    JUnit->>Mock: 2. Start Mock HTTP Server
    JUnit->>App: 3. Start Spring Context
    JUnit->>App: 4. Send Test HTTP Request
    App->>DB: 5. Read/Write State
    App->>Mock: 6. Request LLM Completion
    Mock-->>App: 7. Return Fake LLM Response
    App-->>JUnit: 8. Return Final Result
    JUnit->>JUnit: 9. Assert Result is Correct
```

## 🚀 How to Run E2E Tests

Because these tests require Docker and take longer to run, they are typically skipped during standard builds and run explicitly.

### Prerequisites
- **Docker** must be installed and running on your machine (for Testcontainers).

### Running the Tests
```bash
# Run only integration tests
mvn verify -pl tracegraph-e2e
```

### Example Test Snippet
Here is how we use Testcontainers and WireMock in a test:

```java
@SpringBootTest
@Testcontainers
public class AgentFlowE2ETest {

    // Spin up a real Postgres DB in Docker
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Test
    public void testFullAgentFlow() {
        // 1. Setup WireMock to fake OpenAI
        stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"choices\": [{\"message\": {\"content\": \"Hello!\"}}]}")));

        // 2. Execute the TraceGraph application flow
        // 3. Assert that the database saved the state correctly
    }
}
```
