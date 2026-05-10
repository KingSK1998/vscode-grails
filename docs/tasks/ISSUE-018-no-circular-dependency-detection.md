# ISSUE-018 · No Circular Dependency Detection

## Problem
`ServiceContainer` initializes services in a hard-coded order. If a circular dependency is introduced between services, it can lead to `undefined` references or stack overflow errors during initialization without a clear explanation.

## Proposed Solution
Implement a dependency graph and cycle detection during the initialization phase.

### Implementation Plan
1. **Dependency Mapping**: Allow services to declare their dependencies explicitly.
2. **Graph Construction**: Build a directed graph of services during the `initialize()` call.
3. **Cycle Detection**: Use Depth-First Search (DFS) to detect cycles in the graph before attempting instantiation.
4. **Descriptive Errors**: Throw a `CircularDependencyException` that clearly lists the chain of services causing the cycle (e.g., `ServiceA -> ServiceB -> ServiceA`).

## Tradeoffs
- **Initialization Overhead**: Adds a small amount of time to the activation sequence.
