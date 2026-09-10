# State, revision and lifecycle contract

**Updated:** 2026-09-09. **Status:** required behavior for R0/R1/R2, with implementation gaps recorded below. This replaces the old Phase 3 target text; it does not assert that snapshot safety has been achieved. [Invariants](invariants.md) define stable rule IDs, [ADR-009](adr/decisions.md#adr-009-project-ownership-and-executable-contracts-2026-09-08) explains corrected ownership, and the [task queue](execution/task-queue.json) controls acceptance.

## 1. Ownership and current evidence

`GrailsService` remains the server composition root. `WorkspaceManager` owns registered project contexts and routing. Each `ProjectContextImpl` owns its compiler, visitor, project index/IndexManager, lifecycle state and publication coordination; its `SnapshotManager` owns lineage. `GrailsTextDocumentService` owns document-work admission and delegates buffer state to `FileContentTracker`. Providers are readers through context interfaces, never substitute writers.

Current source contains `VersionedSnapshot`, a bounded document scheduler, per-project contexts and lineage. R1-01 targeted scheduler/lifecycle tests pass; its [execution record](execution/records/R1-01.md) contains the commands and limits. A record holding an AST accessor, an atomic pointer or a shallow map copy does not prove transitive immutability. Current classpath aggregation, provider lock coverage, getter-triggered activation and retained classloaders require validation/repair. No documentation label closes those tasks.

## 2. Revision identities

An analysis input must identify all state capable of invalidating its output:

| Identity | Required scope/lifetime |
|---|---|
| Root/build identity | Canonical URI plus registered root generation; remove/re-add changes the generation |
| Document identity | Canonical URI plus open generation; a reopened file can restart its LSP version |
| Document version | Monotonic within one open generation; not a globally durable ID |
| Dependency revision | Resolved model, source sets and ordered classpath fingerprints |
| Analysis configuration revision | Relevant compiler/adapter/mapping settings captured with the candidate; changes invalidate affected derived facts |
| Analysis revision | Committed project generation; includes document inputs and dependency revision used |
| Symbol identity | Project/source-set scope, declaration locator/signature and analysis revision as needed; index IDs are snapshot-local |

A candidate captures immutable input values. Never queue a mutable `TextFile` that later edits overwrite. If several documents form a compilation unit, record/check their input revisions as a set or an equivalent project input generation. A single file version cannot certify a whole-project result.

URI normalization must preserve filesystem semantics: path segments, encoding, Windows drive/UNC behavior and platform case sensitivity. Do not lowercase every URI or compare bare string prefixes. Define symlink identity consistently within a workspace and test it before claiming support. Paths and URIs are different input forms; convert deliberately at boundaries.

## 3. Document synchronization

Apply every `contentChanges` entry in received order to the text produced by the preceding entry. Do not sort change ranges against the original text. Range-less replacement replaces the full current buffer. Empty text is a valid open document. Reject/ignore obsolete versions within the same open generation and record the diagnostic reason without inventing missing content. This follows the incremental synchronization contract in the [LSP 3.17 document synchronization specification](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didChange).

After applying changes, capture the latest input and enqueue bounded background work. Queue coalescing may discard obsolete compile jobs, never the text transformations needed to construct the final buffer. Position conversion must use the negotiated encoding; test UTF-16 explicitly for the first supported client/server path.

The current provisional admission policy allows one active document candidate, 256 pending tickets, 32 ordinary pending tickets per root, 256 KiB of conservatively estimated ticket metadata and a 4 MiB automatic source input. Pending tickets contain URI/version metadata; only the active candidate copies source text. Ready roots alternate. Overflow retains one process-level recovery marker and re-reads current buffers on the compiler worker in fair batches capped at 256 candidates. Successfully handled URI/version pairs prevent recovery from recompiling unchanged open buffers. R1-05 owns workload measurement and tuning of these numeric values.

`didClose` ends the overlay generation, cancels pending overlay work and prevents active old work from publishing. Closing is not deleting a file from the project: discard unsaved overlay facts and restore/reindex disk-backed facts when appropriate. Clear obsolete diagnostics without synchronously activating a hibernated compiler. A deleted file must disappear from the committed index after deletion is processed. Closing a never-saved file removes its overlay-only symbols.

Close tickets can use the global queue beyond the ordinary per-root limit. If the global count or byte limit is exhausted, repeated closes collapse to one close-reconciliation marker. The worker compares each project's committed overlay URI set with the current tracker view and removes stale overlays before clearing that marker. This keeps close recovery reconstructable without retaining an unbounded tombstone list.

Documents opened before project discovery remain in the tracked-buffer store with bounded replay bookkeeping. When a root is registered, enqueue the latest still-open inputs belonging to that root generation. Do not retain an unbounded separate copy of every pre-discovery buffer, replay closed inputs or use the default project for unrelated files.

## 4. Candidate, commit and read protocol

```text
receive -> apply text -> capture input identity -> admit/coalesce background work
  -> build private candidate -> validate coherent output + all input identities
  -> publish one committed generation -> send still-current diagnostics/events
read -> capture committed generation once -> query it -> return scoped result
```

The publication unit comprises every component consumed together: symbol/index facts, relevant AST access, dependency model and derived framework/cache facts. Revisions may share immutable values; they must not expose mutable objects that a later compile changes. Cache invalidation is part of the transition, before new readers can see a generation with old derived facts.

Check root generation, document/open versions, dependency revision, cancellation and disposal immediately before publication. The check and pointer/index publication must be ordered with input changes so an edit cannot slip between them unnoticed. Checking a flag once before a lengthy compiler operation is insufficient. Publishing dependent indexes separately from the project snapshot also requires an explicit coherent-read design.

Capture the request's committed view once. Do not alternate between that view and live compiler/visitor getters. Track unsaved current buffers separately; range-sensitive answers from an older version must be mapped/revalidated or withheld as unavailable. Across projects, capture a revision vector and label dependent dirty/stale facts; never pretend independently committed projects form one simultaneous workspace snapshot.

### Choosing an isolation mechanism

R1-04 must first reproduce the race and audit every reader/writer path. Prefer compact immutable extracted facts for common reads and existing index boundaries. AST-dependent reads require proof that their generation cannot be mutated while retained, or a narrowly scoped, bounded alternative that returns unavailable instead of waiting behind compilation. A lock held only until a future is created does not protect that future's work; a different writer lock does not protect it either.

Do not deep-clone the entire Groovy AST, create a compiler per keystroke or migrate every provider to a speculative semantic database by default. Record alternatives and measured costs in an ADR for a substantial ownership/publication change. Whatever mechanism is chosen must pass the same observable consistency and responsiveness tests.

An index's origin link is a value locator (URI, source/signature, revision), **not an AST object reference**. `ProjectIndex`, `SymbolInfo` and `MethodScopeCache` retain no ASTNode references after construction, per INV-OWN-005 and ADR-006.

## 5. Failure, partial syntax and cancellation

Ordinary syntax errors are expected while typing. A validated partial analysis may commit diagnostics and the facts whose validity is known, with partial/current status. Remove facts invalidated by the edit rather than silently presenting old symbols as current. Last-known-good (LKG) data can remain available as explicitly stale evidence.

A fatal compiler/model failure discards the incoherent candidate and preserves the prior committed usable state. Do not publish a failed candidate merely because `SnapshotManager.commit(snapshot, false)` can store it. The writer determines whether output is a valid partial analysis or unusable failure. Cancellation/timeout/shutdown cannot turn either into a success.

Providers convert missing state to protocol-appropriate empty/null/error results and expose unavailable/degraded capability status where the protocol permits. Never report an unavailable index as an authoritative 'no references' for rename or impact. Expected cancellation remains cancellation, not an internal crash or successful empty result. Diagnostics must be tied to the input document version when supported and suppressed once superseded.

## 6. Lifecycle transitions and resource ownership

The current `ProjectState` enum is INITIALIZING, READY, HIBERNATED, REACTIVATING, FAILED, DISPOSING. Dirty dependency state is separate; do not introduce alternate enum names from an old diagram without a justified migration.

| Event | Required behavior |
|---|---|
| Register root | INITIALIZING; return capabilities promptly, resolve asynchronously after initialized |
| Initial usable model/analysis | READY; replay current buffered inputs and publish scoped status |
| Initial/fatal recovery failure | FAILED with reason and retry trigger; no spinning retries |
| Hibernation | Owner coordinates quiescence/release; reads use retained safe facts or unavailable, never trigger blocking activation |
| Edit/explicit refresh requiring compiler | HIBERNATED/FAILED -> one REACTIVATING attempt; other jobs share its result through admission |
| Activation succeeds/fails | READY/FAILED; only that attempt may complete/clear its activation ownership |
| Dependency dirty | Mark freshness, propagate over explicit graph once per project, schedule work without blocking readers |
| Root remove/shutdown | DISPOSING becomes terminal before returning from removal; queued work is cancelled, the active candidate is invalidated, and resource release runs after its drain barrier without holding the notification path |
| Root re-add | New context/root generation; no resurrection of a disposed instance |

Do not wait for activation/Gradle on notification or provider threads. Do not perform synchronous LRU hibernation while routing a request. Avoid acquiring another project's write lock while holding one project's write lock; publish dirty propagation outside the writer critical section. Define a lock order for any remaining multi-lock operation and test it.

### Retention and hibernation

`SnapshotManager` remains the single lineage owner. Active/LKG facts are strongly reachable while usable; optional history is capped (current policy: at most three historical entries). Soft references are optional cache retention, never correctness or a memory budget.

A retained snapshot may not keep a hibernated compiler/classloader alive indefinitely. Existing AST-bearing snapshots therefore require an explicit release strategy: detach safe value facts/metadata, invalidate AST access for new reads, let bounded in-flight request leases finish/cancel, then release compiler/scan/classloader references. Keep active/LKG **usable facts**, not a promise to preserve every heavy AST forever. Do not mutate a snapshot under an existing reader to clear it. R1-04 must document the chosen representation and lease/disposal behavior before claiming INV-STATE-010 compliance.

Transient in-flight leases may retain an old generation until they drain; after hibernation completion no owned retention path may keep the released generation alive. Root removal and shutdown must also clean discovery maps, executor tasks, connections, virtual documents and observers. Tests inspect reachability/owned handles; setting two fields to null is not sufficient evidence. Real JVM exhaustion may defeat recovery; test controlled resource-limit rejection and cleanup without promising every OutOfMemoryError is recoverable.

## 7. Required race and lifecycle tests

1. Block compilation after capturing input, edit again, finish the old job: old state/diagnostics cannot publish; final input eventually commits.
2. Repeat with close/reopen and reset document version, root removal/re-add, dependency refresh and shutdown: generation checks distinguish all cases.
3. Block a writer mid-index update: a read either sees the complete prior generation promptly or explicit unavailable status, never partial/mixed state.
4. Recompile/remove a file while retaining an older request view: old results remain stable during its permitted lease; new results contain no removed symbols.
5. Open an empty buffer and send a multi-change notification whose second range depends on the first change: final content and locations match protocol order.
6. Close an unsaved overlay for a disk-backed file: old overlay facts vanish and disk state remains recoverable without reactivating on the callback thread.
7. Simulate discovery timeout/removal and activation races: one owner completes; no late resurrection or discarded still-open input.
8. Repeatedly refresh/hibernate/dispose roots: owned work drains and old classloaders/AST generations are no longer retained after leases end.

Use deterministic barriers and assert externally visible behavior. Map each test to the affected INV-OWN/INV-ID/INV-KEY/INV-STATE rules and the task card; the presence of a lock or snapshot class is not a substitute for these observations.
