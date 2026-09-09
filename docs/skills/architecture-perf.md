# Architecture for performance

**Updated:** 2026-09-08. Apply the [performance contract](../specs/performance.md) and the selected task card. This skill supports diagnosis and implementation decisions; it does not create another permission gate for already authorized work.

## Workflow

1. State the observable requirement or reproduced bottleneck. Read the owning source, existing tests and applicable invariants.
2. Separate extension-host work, server request latency, queue wait, compiler/Gradle work and resource retention. A separate JVM does not remove latency requirements.
3. Measure the relevant fixture with environment/input metadata. For a correctness/resource-bound violation, add a deterministic reproducer even when a wall-clock benchmark is inappropriate.
4. Identify ownership, frequency, affected scope, admission limits and disposal. Compare the simplest existing mechanism against the measured requirement.
5. Make a bounded fix in the owner, preserving coherent state and regression behavior. Significant architecture changes need an ADR with alternatives/costs; routine changes do not need fresh user approval.
6. Run relevant correctness checks and compare the same workload. Record improvements, costs and limits in the task record. If a change brings no material benefit, simplify it within the task's diff.

## Decision prompts

| Observation | Inspect |
|---|---|
| Slow activation | Eager service work, synchronous I/O, JVM start versus model wait |
| Slow completion/hover | Queue/lock wait, live activating getters, repeated scans and uncapped output |
| Edit storm backlog | Coalescing after text application, count/byte bounds and fairness |
| Heap keeps growing | Retained generations/classloaders, static maps, observers and owned handles |
| Large disk cache | Duplicate artifacts, missing schema/size limits and failed eviction |
| Repeated full rebuilds | Actual affected-input dependencies and cache invalidation scope |

Use one budget authority: [roadmap scorecard](../product-roadmap.md#8-acceptance-scorecard-and-evaluation) plus its reproducible performance protocol. Do not copy independent debounce/latency constants into this skill. Asynchronous code can still be slow, unbounded or incorrect; 'already async' is not an optimization verdict.
