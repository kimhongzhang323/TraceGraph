# TraceGraph :: Eval

## 📖 Introduction to Evaluation
Welcome to `tracegraph-eval`! When you write a normal program, you know exactly what the output should be for a given input (e.g. `2 + 2 = 4`). However, when you use Large Language Models (LLMs), the output is *non-deterministic* (it changes slightly every time) and often in natural language.

How do you know if your agent is actually getting better when you tweak its prompt or its graph logic? 

The `tracegraph-eval` module provides tools to scientifically **score** and **evaluate** your agents against a dataset of expected answers.

### Key Concepts
- **Dataset**: A list of `(Input, Expected Output)` pairs.
- **Scorer**: A function that compares the agent's actual output to the expected output (e.g., using exact match, regex, or even asking another LLM to score it!).
- **Evaluation Run**: Running your agent across the entire dataset and generating an aggregate score (like 85% accuracy).

## 🏗️ Evaluation Workflow

```mermaid
flowchart TD
    Data[(Test Dataset)] --> Runner[Eval Runner]
    Agent[TraceGraph Agent] --> Runner
    
    Runner -->|1. Feed Input| Agent
    Agent -->|2. Return Actual Output| Runner
    Runner -->|3. Send Expected & Actual| Scorer[Evaluation Scorer]
    
    Scorer -->|4. Return Score (0.0 to 1.0)| Report[Final Eval Report]
```

## 🚀 How to Use It

### 1. Creating a Scorer
You can create a custom scorer to define how an output is graded.

```java
import site.tracegraph.eval.Scorer;

public class ExactMatchScorer implements Scorer {
    @Override
    public double score(String expected, String actual) {
        // Returns 1.0 if they match perfectly, 0.0 otherwise
        return expected.trim().equalsIgnoreCase(actual.trim()) ? 1.0 : 0.0;
    }
}
```

### 2. Running an Evaluation
Use the `EvalRunner` to execute your agent against a dataset.

```java
import site.tracegraph.eval.EvalRunner;
import site.tracegraph.eval.Dataset;

public class MyAgentEvaluation {
    public static void main(String[] args) {
        TraceGraph myAgent = // ... initialize your agent
        
        // Load your test questions and answers
        Dataset dataset = Dataset.fromCsv("src/test/resources/eval-data.csv");
        
        EvalRunner runner = new EvalRunner(myAgent, new ExactMatchScorer());
        
        // Run the evaluation!
        EvalReport report = runner.evaluate(dataset);
        
        System.out.println("Agent Accuracy: " + report.getAverageScore() * 100 + "%");
    }
}
```
