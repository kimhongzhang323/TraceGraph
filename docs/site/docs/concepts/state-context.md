# State and Context

In TraceGraph, execution is driven by the state of the graph. The state is a data structure that flows through the nodes, being read and mutated at each step.

## Graph State

You define your graph's state by creating a class (e.g., `MyState`). Each node in the graph takes this state as input and returns a mutated version or a difference to be applied. This ensures that the state is passed safely between nodes.

## Context

While State represents the data being processed, Context provides execution metadata, environment variables, or dependency injection hooks that are constant or specific to a single run. Context can be accessed within nodes to fetch configurations, API keys, or logger instances without polluting the business State.

## Thread Safety

Since graphs can be executed concurrently, TraceGraph ensures that state mutations are managed properly. When designing your state class, consider using immutable data structures to prevent side-effects in concurrent branches.
