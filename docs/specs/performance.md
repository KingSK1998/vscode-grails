# Responsiveness, resource limits and optimization contract

**Status:** required engineering process and candidate budgets; measured compliance is pending R1-05. **Updated:** 2026-09-08.
**Status:** measured baseline compliance established in R1-05 (`reports/performance-baseline.json`). **Updated:** 2026-09-10.

Correctness is a constraint on optimization. The goal is short interactive waits with bounded background CPU, heap and disk, including during failure. A separate JVM protects the extension host from direct compiler execution but can still starve requests or consume the machine's resources.

## Work placement and scheduling

| Work | Placement and rule |
|---|---|
| VS Code commands/settings/views | Client; return/yield promptly; no synchronous builds or large file scans |
| LSP document notification | Apply all edits in protocol order to tracked text, capture revision, enqueue; no compile/Gradle/scan |
| Completion/hover/navigation | Bounded read of a captured committed generation; never wait for build/activation |
| Compile/index/discovery | Owned background workers, bounded concurrency and admission, per-project writer serialization |
| Gradle model/tasks | Separate bounded background admission; timeout/cancel; never under AST/publication locks |
| View graph layout/docs extraction | Lazy, cancellable, bounded input/output; only when requested/visible |

Compilation requests may coalesce **after every text change is applied**. Keep at most one latest pending input per open URI, plus bounded active jobs. A per-URI bound alone is insufficient for many files: use a global/project admission limit and a compact dirty set for excess work, then recover final revisions fairly. Do not silently drop text updates or allow one busy root to starve all others. Keep queued payloads bounded by bytes as well as item count.

Debounce controls when work starts, not total queue growth. Cancellation is cooperative; a compiler that ignores interruption may finish, but its obsolete output must not commit. Use revision/generation rejection and avoid scheduling unlimited replacements. Do not repeatedly kill a shared Gradle daemon or spawn a JVM for every edit.

## Candidate acceptance budgets

Use the roadmap's [scorecard](../product-roadmap.md#8-acceptance-scorecard-and-evaluation) as the product budget authority. Initial measurements must distinguish queue time, work time, transport time and JVM cold start.

| Measurement | Initial target or required property |
|---|---|
| Ordinary document notification | p95 handler time below 10 ms on declared fixture/hardware; large files reported separately |
| Warm completion/hover | p95 below 100 ms end-to-end under stated normal load |
| Initialize | Capability response does not wait for Gradle; separately report process/activation/model/index times |
| Blocked Gradle | During a 30-second simulated stall, already available reads still complete |
| Edit storm | Bounded active/pending work and bytes; final revision eventually wins |
| Memory after repeated open/change/close/remove cycles | Stabilizes within a documented envelope after settling; no monotonically retained generations/classloaders |
| Disk | Configured/documented maximum with eviction, corrupt-entry recovery and no growing duplicate artifact copies |

These are targets, not current benchmark results or guarantees for every machine. R1-05 must publish actual baseline numbers and propose justified numeric heap, disk, queue-byte and large-file limits before resource-dependent features are accepted. Define behavior at every limit: degrade with status, evict reproducible metadata, cancel low-priority work or reject new work; never corrupt input state. Any budget revision requires before/after evidence and a recorded decision, not hiding a regression by raising a constant.

## Measurement protocol

Record commit plus dirty-tree scope, OS/CPU/RAM, JDK/Node/Gradle, JVM flags, fixture fingerprints, number/size of files and symbols, dependency JAR count/bytes, root count, cache state, concurrency and command. Use monotonic elapsed time. Report sample count, p50/p95/p99 and maxima for interactive requests, failures/cancellations, queued work/bytes, CPU, peak/settled heap, retained classloaders and disk footprint.

Separate cold and warm runs; warm up the JVM before steady-state comparisons. Keep raw machine-readable results as artifacts and summarize them in the task record. Repeat enough to expose variance; do not certify a p95 from a handful of requests or a single favorable run. A proposed fixture ladder is small (100 files), medium (1,000), large (10,000), plus a real Grails project; record actual contents and treat generated fixtures as load tests, not semantic compatibility evidence.

Use deterministic latches/barriers for race tests. Tests must prove a read completes while a writer is deliberately blocked, not rely on a short sleep happening to win. Performance timing tests run in a declared benchmark environment; fast CI checks assert bounded queue counts and publication behavior without brittle millisecond thresholds.

## Choosing data structures

| Access pattern | Starting point | Evidence needed to replace it |
|---|---|---|
| Exact symbol/artifact/URI lookup | Scoped hash map with stable keys | Profiles showing collisions/allocation or locality problem |
| Prefix completion over mostly static names | Sorted names and binary-search range, cap results | Measured latency/memory comparison before adding a trie |
| Project/dependency/semantic relationships | Adjacency lists + visited-set BFS/DFS | Proven reachability workload before precomputing transitive closure |
| Revision scheduling | URI-keyed latest-work map + bounded scheduler | Fairness and retained-byte results before a more complex scheduler |
| Large documentation strings | Lazy bounded LRU | Hit-rate/retention evidence for a different policy |
| Reused external declarations | Immutable artifact metadata + separate membership | Measured fingerprint/load cost before specialized storage |

Specify `N`, number of affected files/edges, output size and memory per entry when discussing complexity. An O(1) lookup is not a win if it duplicates huge ASTs, hides stale state or requires expensive rebuilding. Avoid a custom database, persistent tree, event bus or lock-free design without an observed requirement.

## Every cache needs this record

```text
Owner and scope:
Key and all input revisions:
Value representation (AST/classloader references forbidden where invariant says so):
Maximum entries AND bytes / measured estimate:
Population trigger and thread/executor:
Hit/miss/stale behavior:
Invalidation on edit, close, config, dependency, root remove, schema change:
Cancellation and concurrent publication:
Eviction, disposal and corrupt-disk recovery:
Metrics and regression tests:
```

Make resource release testable through owned handles and reachability. `compiler = null`, a `SoftReference`, or `System.gc()` is not proof of reclamation. Hibernation must honor request leases and remove AST/classloader paths from retained metadata, as specified in the [state contract](../state-and-lifecycle-specification.md). Never force GC on the interactive path or depend on catching every real JVM out-of-memory failure.

## Optimization acceptance

Reproduce the bottleneck, locate it, change the smallest owning unit, rerun correctness/negative cases, compare the same workload, and record gains and costs. A change with no material measured benefit should be simplified or reverted within the task's own diff. Avoid unrelated cleanup. Required invariants: `INV-OWN-004/008`, `INV-STATE-004/005/010`, `INV-PERF-001/002/003`.
