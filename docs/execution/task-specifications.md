# Roadmap task acceptance cards

**Updated:** 2026-09-08. These cards define behavior to prove, not implementation status. [task-queue.json](task-queue.json) is the only task-status/dependency ledger. Existing source should be reused and repaired; a card is not an instruction to rewrite its feature.

All cards inherit the [execution guide](../agent-execution.md), [invariants](../invariants.md) and [validation gates](../specs/validation.md). Each numbered item is an acceptance case, identified as `<task-id>/<number>` in its record. Entry points identify where to start reading; follow actual call paths before editing. Broader phase gates in the roadmap still apply. Tasks after R0 intentionally leave class/method design to evidence from preceding tasks.

## R0-01

**Repair the interrupted document scheduler and publication changes.** Read the [handoff's exact files, failures and commands](../implementation-handoff.md#r0-01--the-next-coding-task), [state contract](../state-and-lifecycle-specification.md) and [performance contract](../specs/performance.md). Required invariants: INV-OWN-001, INV-STATE-002/004/005/010, INV-PERF-001/002.

1. Reproduce and repair the saved `ProjectState`/`AtomicReference` exception while keeping static compilation; all `GrailsIncrementalCompilerSpec` cases pass on the changed tree.
2. Repair map-emptiness assertions without weakening the observable queue tests; `DocumentCompilationSpec` passes, including nonblocking notifications, copied input and coalescing while compilation is blocked.
3. Empty opens, superseding edits, close and shutdown cannot publish obsolete overlay state/diagnostics. Closing a hibernated project does not activate its compiler through a getter; disk-backed state remains recoverable.
4. Document the queue/replay interface for a not-yet-registered root, with a focused test. R0-02 owns discovery integration; do not implement a new discovery framework here. Record targeted/lifecycle/full server checks and remaining broader R1 isolation gaps honestly.

**Boundary:** restore a verifiable baseline; no global AST redesign, new agent API or speculative optimization.

## R0-02

**Implement startup discovery and root lifecycle.** Start at `GrailsLanguageServer.initialize`, `GrailsWorkspaceService.didChangeWorkspaceFolders`, `GradleService.getGrailsProjectAsync` and `WorkspaceManager`. Read the [state contract](../state-and-lifecycle-specification.md). Invariants: INV-OWN-007/008, INV-STATE-005/007/008, INV-PERF-001.

1. Initialize returns capabilities without waiting for Gradle; discovery starts after `initialized`, including zero roots and multiple roots.
2. Root addition/removal/re-add during a blocked discovery uses generations; a late result never resurrects removed state. Windows paths, encoded file URIs and nested roots route correctly; unmatched URIs do not inherit a default Grails project.
3. Register results and notify the client; replay only latest still-open buffers in the matching root. Close-before-discovery and shutdown-before-completion release pending work.
4. Build-watch debounce unions affected roots and covers the client/server supported inputs consistently, including Kotlin settings and version catalogs; failure/cancel produces actionable status and bounded retry.

## R0-03

**Validate the real bundled stdio/custom protocol.** Start at `GrailsLanguageServer.main`, custom client interface wiring, `logback.xml`, `LanguageServerManager` and `scripts/smoke-server.js`. Read [validation](../specs/validation.md). Invariants: INV-PERF-001, INV-STATE-005/006.

1. A rebuilt JAR emits only correctly framed protocol traffic on stdout; logs go to stderr/file with bounded retention.
2. The remote client proxy supports the actual custom Grails notification interface. Client handlers are attached before startup can emit project information.
3. Real smoke initializes a fixture, discovers a project, opens an unsaved document, receives diagnostics/symbols and exits cleanly. Record command, Java/JAR fingerprint and result.
4. Startup failure and shutdown leave no owned child process/stream/timeout alive. A previous old-JAR failure or mocked test does not satisfy this card.

## R0-04

**Validate deterministic build and local packaging.** Start at package scripts, `scripts/gradle.js`, `scripts/copy-server.js`, `serverConfig.ts`, `.vscodeignore` and CI. Read [validation](../specs/validation.md).

1. Documented build produces/copies the intended single server artifact; ambiguous candidates fail explicitly. Configured Java, JAVA_HOME fallback and paths with spaces work.
2. Script/client tests and full build pass; package contents include required assets and the current JAR without obsolete selection ambiguity or accidental caches/logs.
3. A locally installed VSIX starts through the packaged entry point on the available platform, with a recorded fixture workflow. Record remaining platforms for R5-05 rather than claiming they passed.
4. CI uses the same artifact path/commands. Prepare local package only; publishing is a separate authorized action.

## R0-05

**Reconcile baseline evidence and KB.** Start at [system map](../architecture/system-map.md), STATUS, handoff, invariants, ADRs and task records.

1. R0-01 through R0-04 acceptance evidence describes the actual current tree; absent or failed checks are repaired/verified before this gate closes.
2. Current architecture descriptions agree with source; target guarantees and known gaps are clearly separated. Do not call shallow AST copies immutable or project contexts isolated classpaths.
3. Failure registry and records retain unresolved R1/R2 work with owning tasks. Run selector validation; no dangling active review references or contradictory current task statuses.

**Existing contribution:** September documentation reconciliation is partial work toward this card, not its baseline acceptance.

## R1-01

**Bound scheduling and establish fairness.** Start at document queue, worker ownership and workspace admission. Read [performance](../specs/performance.md) and [state](../state-and-lifecycle-specification.md). Invariants: INV-PERF-001/002, INV-STATE-005.

1. A blocked compiler plus a large edit storm retains only bounded active work/latest pending inputs, with byte limits and fair admission across roots.
2. Every text edit is applied even if its compile job is coalesced; the final input eventually compiles after the blocker clears. Large-file behavior is explicit.
3. Cancellation/root removal/shutdown drains owned tasks; no unbounded executor queues or abandoned futures. Record queue counts/bytes and rejected/deferred work behavior.

## R1-02

**Preserve revision/order and reject stale publication.** Start at `FileContentTracker`, document generation tracking and candidate validation. Read [state](../state-and-lifecycle-specification.md). Invariants: INV-ID-001/005, INV-STATE-004/005/007, INV-EDIT-001.

1. Sequential incremental changes, full replacement, empty text, CRLF/UTF-16 and obsolete versions produce correct final content/locations.
2. Deterministic edit/close/reopen, root remove/re-add and dependency-change races cannot commit stale candidates; reset document versions cannot reuse an old open generation.
3. Candidate validation and publication are ordered atomically with invalidating inputs; delayed diagnostics do not overwrite a newer buffer's state. Closing an overlay and deleting a file have distinct correct results.

## R1-03

**Bound Gradle synchronization and retain usable state.** Start at Gradle connection/cancellation ownership and `ProjectContextImpl.triggerGradleSync`. Read [state](../state-and-lifecycle-specification.md), [discovery](../specs/library-discovery.md), [performance](../specs/performance.md). Invariants: INV-OWN-007/008, INV-STATE-005/008.

1. Timeout, cancellation, offline/broken build and superseded sync release owned connections/work exactly once and cannot replace usable committed state.
2. During a simulated 30-second sync stall, existing reads complete without waiting for Gradle or acquiring its locks; stale state is labeled.
3. Retry has a trigger and bound, debounce preserves all affected roots, and disposal rejects late completion. Actual coherent classpath replacement is completed under R2-02.

## R1-04

**Prove publication and lifecycle ownership.** Start at project locks, `SnapshotManager`, `VersionedSnapshot`, `BaseProvider`, visitor `copyFrom`, discovery retention and workspace LRU. Read [state](../state-and-lifecycle-specification.md) and [performance](../specs/performance.md). Invariants: INV-OWN-001/005/006/009, INV-STATE-001/002/003/004/010/011.

1. Audit all reader/writer paths and reproduce a deterministic concurrent-read case; choose/document the smallest mechanism satisfying coherent, responsive reads.
2. Readers retained during compile/remove observe stable generation facts; no mixed AST/index/model/cache publication or mutable AST references in forbidden index/cache values.
3. Hibernation and disposal have an explicit request-lease/retention strategy; active/LKG safe facts remain usable while heavy compiler/scan/classloader paths release after leases drain.
4. Routing/read getters do not synchronously activate/hibernate or wait behind compilation. Lifecycle transitions, activation ownership, cross-project lock order and resource reachability have tests; major representation changes get an ADR with costs.

## R1-05

**Publish reproducible latency and resource baselines.** Read [performance](../specs/performance.md). Invariants: INV-PERF-001/002/003, INV-STATE-010.

1. Produce cold/warm, blocked-build, edit-storm and repeated lifecycle measurements for declared fixtures/hardware, with sample counts/variance and failures.
2. Establish justified numeric heap/disk/queue-byte/large-file limits and behavior at each limit; measure peak/settled use and classloader retention.
3. Compare to the roadmap targets and repair material violations or record an explicit evidence-backed budget decision. Add reproducible benchmark commands/artifacts and inexpensive regression checks; fabricated p95 numbers do not pass.

## R2-01

**Isolate classpaths and source-set membership.** Start at `GrailsCompiler.updateClassLoader`, discovery project selection and Gradle source-set modeling. Read [discovery](../specs/library-discovery.md). Invariants: INV-DISC-001/002, INV-OWN-004/005.

1. Two roots selecting different versions of the same library return only their own applicable APIs/origins; include identical FQNs and similarly prefixed root names.
2. Main/test/generated source membership and ordered classpath precedence follow the resolved model; test-only APIs do not leak to production.
3. Shared immutable artifact facts may be reused, but compiler instances, visibility membership and mutable caches remain scoped; remove/re-add frees old membership.

## R2-02

**Refresh all dependency-derived state coherently.** Start at Gradle success callback, compiler/discovery invalidation and index publication. Read [discovery](../specs/library-discovery.md) and [state](../state-and-lifecycle-specification.md). Invariants: INV-STATE-003/004/005, INV-DISC-001/002.

1. Add, remove and change a dependency: compiler, declaration discovery, docs, configuration/capability facts and index converge on the same dependency revision without restart.
2. A changed artifact at the same coordinates invalidates by content; unchanged artifacts are reused within resource limits.
3. Failed/cancelled refresh preserves stale last-good state; simultaneous edits/removal cannot publish a mismatched candidate. Test actual disappearance, not just cache-clear calls.

## R2-03

**Use actual Gradle project edges.** Start at `WorkspaceManager.projectDependsOn`/propagation and the Gradle model builder. Read [discovery](../specs/library-discovery.md). Invariants: INV-OWN-008, INV-STATE-009, INV-DISC-001.

1. Replace name/JAR-prefix guesses with resolved build/project/source-set relationships; test renamed modules, composites and unrelated same-name/prefix projects.
2. Cross-project visibility follows permitted edges and records origin/revisions. Cycles terminate with one visit per propagation.
3. Edge add/remove updates downstream dirty state without waiting on upstream compilation or acquiring nested project writer locks.

## R2-04

**Discover declarations and explain their origins.** Start at `DiscoveryService`, compiler symbol resolution and completion strategies. Read [discovery](../specs/library-discovery.md). Invariants: INV-DISC-001/002/003, INV-OWN-004/005.

1. Real fixture signatures for inherited/generic/overloaded members, traits and Groovy extension modules match resolved declarations; source attachments are optional enrichment.
2. Each result has source-set/artifact/signature provenance. Wrong-scope and missing-module negative cases do not invent fallback APIs.
3. Inventory hardcoded lists by category; replace framework/library fallback inventories with evidence-backed resolution or explicit unavailable status. Preserve legitimate grammar/snippet/protocol constants with tests.

## R2-05

**Implement capability adapters for Grails conventions.** Start at existing GORM/injection/delegate/TagLib providers and completion strategies. Read [discovery](../specs/library-discovery.md). Invariants: INV-DISC-001/003, INV-STATE-003/004.

1. Each admitted adapter documents detection, inputs, derivation, applicability, invalidation, limits and actual artifact fixtures.
2. Cover current-domain GORM finders/properties, injection/delegates and required plugin conventions, with no-capability and renamed/deleted-domain negative cases.
3. Unsupported/runtime-dependent behavior stays explicit; adapters do not execute an application for ordinary reads or accumulate a manual method inventory.

## R2-06

**Establish tested compatibility evidence.** Read [discovery](../specs/library-discovery.md) and [validation](../specs/validation.md). Invariants: INV-DISC-001/002/003.

1. Create a runnable matrix with pinned real Grails 7/Groovy 4 combinations, exact JDK/Gradle versions, dependencies/fingerprints and fixture provenance. Select exact supported combinations from verified available artifacts.
2. Include dependency addition/removal/change, version skew, multiple roots and offline replay; preserve at least one real-resolution integration check.
3. Product capabilities/documentation distinguish tested, experimental and unsupported combinations. New versions do not become supported merely because their major version matches.

## R3-01

**Define shared typed analysis operations.** Start at existing custom protocol and shared schemas; inspect consumers before extending them. Read [agent contract](../specs/agent-tools.md). Invariants: INV-TOOL-001/003, INV-STATE-004/006, INV-DISC-003.

1. Define schemas/internal operations for project capabilities, symbol search/explanation and diagnostics with scope, revisions, per-item provenance and current/stale/partial/unavailable states.
2. Validate inputs, errors, cancellation, count/byte caps and revision-bound cursors; distinguish unavailable from a successful empty result.
3. Tests read the existing analysis state through these operations, including unsaved inputs and cross-project revision vectors where applicable; no second agent index.

## R3-02

**Expose first VS Code agent reads.** Start at client composition/lifecycle and the R3-01 operations. Read [agent contract](../specs/agent-tools.md). Invariants: INV-OWN-003, INV-TOOL-001/003.

1. Register capabilities, symbol search/explanation and diagnostics tools with typed bounded inputs/results, activation/disposal and supported-version handling.
2. Tool answers agree with editor answers for identical project/document/dependency revisions; wrong-project and stale/unavailable cases remain explicit.
3. Read calls do not execute builds/applications implicitly; validate workspace scope/trust and keep the deterministic IDE functional without a model.

## R3-03

**Measure whether the tools help agents.** Read the roadmap [evaluation method](../product-roadmap.md#evaluate-the-ai-value-instead-of-assuming-it) and [agent contract](../specs/agent-tools.md).

1. Establish a fixed, versioned task set covering wrong-version APIs, Grails relationships and diagnosis, with independent acceptance checks and held-out cases.
2. Compare the same model/settings with search/build alone versus with tools; report successes, wrong changes, time, output/token volume where measurable and variability.
3. Publish limitations and concrete improvements/removals justified by results. A disappointing result is useful evidence; unproven product claims must be revised before acceptance.

## R3-04

**Add the MCP adapter after operation contracts stabilize.** Read [agent contract](../specs/agent-tools.md). Invariants: INV-TOOL-001/003.

1. A second client exercises the same typed read operations/error semantics without duplicating project models or indexes.
2. Define connection/session lifecycle, scope, editor-versus-disk availability and cancellation/output limits; do not pretend external clients automatically see unsaved editor buffers.
3. Enforce validation at the operation boundary independent of MCP annotations, and test malformed requests, stale cursors and disconnect cleanup.

## R4-01

**Make ordinary language features agree.** Start at completion/hover/definition/reference/signature providers and shared resolution helpers. Read [IDE contract](../specs/ide-workflows.md). Invariants: INV-ID-003/004/005, INV-KEY-003/005, INV-STATE-004.

1. Cross-feature fixtures resolve the same symbols/signatures for overloads, locals/shadowing, generics, closures/delegates, traits, properties and inherited/extension methods.
2. Unsaved changes and dependency revisions cannot mix locations/types; missing/ambiguous dynamic bindings are explicit.
3. Remove divergent per-provider inference only where the shared resolver demonstrably preserves behavior and latency; include regression cases for prior failure modes.

## R4-02

**Expose evidenced Grails relationships.** Start at domain/controller/service/TagLib/view/config providers. Read [IDE](../specs/ide-workflows.md) and [discovery](../specs/library-discovery.md). Invariants: INV-DISC-001/003, INV-OWN-004/005.

1. Queries connect routes/actions, injection, domain associations, tags, views/templates and configuration using proven rules/declarations.
2. Every relationship has scoped identities, origin/derivation and input revisions; dynamic unknowns remain visible.
3. Source/dependency deletion removes edges. Shared compact facts serve both UI and tools with bounded traversal; no AST-retaining parallel graph.

## R4-03

**Deliver GSP embedded-language editing.** Start at `GspToGroovyConverter`, document selectors and source-position mapping. Read [IDE](../specs/ide-workflows.md). Invariants: INV-EDIT-001, INV-STATE-004/005, INV-PERF-002.

1. Route HTML/CSS/JS/Groovy regions through supported language services/providers with original/virtual version mapping and recoverable malformed input.
2. Test nesting, interpolation/directives/comments, CRLF/UTF-16/astral characters, and generated helper regions.
3. Map diagnostics, navigation, formatting, completion replacements and additional edits correctly; reject unmappable edits and clean up virtual documents on close/removal.

## R4-04

**Integrate configuration and i18n intelligence.** Start at YAML intelligence, config metadata, JSON/properties support and client selectors. Read [IDE](../specs/ide-workflows.md) and [discovery](../specs/library-discovery.md). Invariants: INV-DISC-001/003, INV-EDIT-001.

1. Nested YAML/JSON/properties, profiles/overrides, arrays/dotted keys, placeholders and i18n references resolve from applicable metadata/project facts.
2. Adding/removing plugin metadata changes keys/docs/defaults without hardcoded framework catalogs; incomplete metadata does not produce certain false-invalid diagnostics.
3. Correct provider registration/source mapping and dependency refresh are exercised through editor-level tests; secret values are not exposed in tool/view logs.

## R4-05

**Finish nested source/artifact exploration.** Start at project/artifact tree services and Gradle source-set model. Read [IDE](../specs/ide-workflows.md). Invariants: INV-DISC-001, INV-OWN-003/004, INV-PERF-002.

1. Multi-module/nested roots, main/test/generated sources, resources and assets preserve scoped node identities and navigate to correct files.
2. Children load lazily with bounded work, cancellation and loading/empty/error states; refresh/removal disposes old nodes/watchers.
3. Large-tree, keyboard and focus tests pass; no hardcoded directory depth or whole-tree materialization on activation.

## R4-06

**Validate run/debug/test workflows.** Start at existing task/debug/test services and commands. Read [IDE](../specs/ide-workflows.md), [agent contract](../specs/agent-tools.md). Invariants: INV-OWN-003, INV-TOOL-002, INV-PERF-002.

1. Selected project/wrapper/environment determines the operation; test discovery and actual selected execution agree, including no-tests and skipped cases.
2. Cancellation/restart/disposal release owned processes/listeners; missing Java, wrapper failure and port conflicts give actionable output.
3. Debug/test launch works in the declared fixture/platform setup and respects workspace trust; report unsupported configurations explicitly.

## R4-07

**Integrate useful views and accessibility.** Start at dashboard, dependency graph and GORM SQL preview services. Read [IDE](../specs/ide-workflows.md) and [performance](../specs/performance.md). Invariants: INV-OWN-003/004, INV-DISC-003, INV-PERF-001/002.

1. Overview/relationship/dependency views show actual model state, origins and stale/partial/error states; static SQL approximation is labeled accurately.
2. Large results use filters/paging/lazy layout and bounded messages; webview CSP/input validation/disposal are verified.
3. Keyboard/screen-reader/focus/theme/high-contrast/zoom/reduced-motion checks pass. Views do not start heavy work on activation merely to look populated.

## R5-01

**Implement conservative change impact.** Read [agent](../specs/agent-tools.md), [IDE](../specs/ide-workflows.md), and the R4-02 relationship implementation. Invariants: INV-DISC-003, INV-TOOL-001/003.

1. Scope traversal to evidenced relationships and revision vectors; include explicit runtime/dynamic unknowns and truncation.
2. Evaluate known true and false relationships on held-out changes; report false negatives and precision limitations rather than claiming complete safety.
3. Editor/tool results agree, dependency/source removals invalidate impact, and traversal/resource bounds hold on cycles/large graphs.

## R5-02

**Deliver version-aware rename previews.** Start at existing rename provider and shared symbol identity. Read [agent](../specs/agent-tools.md), [IDE](../specs/ide-workflows.md), [state](../state-and-lifecycle-specification.md). Invariants: INV-KEY-003, INV-TOOL-002, INV-EDIT-001.

1. Correctly scoped, unambiguous cross-file references produce reviewable edits with evidence and document/version preconditions; uncertain dynamic matches are excluded/listed.
2. Changed buffers, rename collisions, removed files, embedded edits and obsolete snapshots invalidate/recompute previews.
3. Apply through supported workspace-edit semantics and report rejected/partially applied changes with recovery. Tests do not assume unsupported filesystem atomicity.

## R5-03

**Expose scoped compile/test checks and records.** Start at existing Gradle/task execution, R3 operation boundary and R4-06 flows. Read [agent contract](../specs/agent-tools.md). Invariants: INV-TOOL-001/002/003, INV-PERF-002.

1. Explicit project/task/environment selections use validated structured arguments, execution permission/trust and bounded concurrency/output/time.
2. Cancel/timeout/disconnect cleans up owned execution; success, failure, no-tests, skipped and partial checks remain distinct.
3. Result records identify exact input state/fingerprints and executed checks. Independent acceptance rejects a change that a narrow passing test missed.

## R5-04

**Present the same change review to humans and agents.** Read [agent](../specs/agent-tools.md) and [IDE](../specs/ide-workflows.md). Invariants: INV-TOOL-001/002/003, INV-DISC-003.

1. Review connects proposed/applied edits, source evidence, impact, checks actually run and unchecked assumptions using one change/input identity.
2. A new buffer/dependency generation invalidates stale review/check claims; partial application and failed checks remain visible with recovery actions.
3. Navigation/accessibility and bounded output work for both editor and tool consumers; no claimed approval or safe-change status merely from generated prose.

## R5-05

**Verify and prepare the 1.0 release.** Read the roadmap's [finished experience](../product-roadmap.md#2-what-the-finished-10-product-looks-like) and [validation](../specs/validation.md).

1. All admitted R0-R5 task dependencies are accepted with durable evidence; compatibility claims match the pinned matrix and actual artifacts.
2. Cross-platform fresh VSIX install, real stdio, daily IDE/agent/change workflows, long-session resource behavior and accessibility pass the release procedure.
3. External workflow acceptance and agent comparisons support the product claims; release notes describe limitations/migration/recovery and local artifacts are reviewable. Publishing requires the applicable authorization.

## R6-01

**Candidate: expand runtime compatibility.** Read [discovery](../specs/library-discovery.md) and [validation](../specs/validation.md).

1. Admission records requested real projects and the specific older/newer combinations worth supporting.
2. Compare isolated compiler/runtime options against memory/startup/storage costs before choosing a strategy; never load incompatible project runtimes into shared mutable compiler state casually.
3. Add exact real-artifact matrix tests and degradation labels before claiming support. No universal past/future promise.

## R6-02

**Candidate: compare dependency upgrades.** Read [discovery](../specs/library-discovery.md) and [agent](../specs/agent-tools.md).

1. Admission demonstrates repeated migration work users need to reduce.
2. Compare signatures/capabilities/config evidence from both exact artifact sets without silently changing the user's build; show uncertainty and adapter limits.
3. Verify suggested migrations with independent compile/tests and report measured usefulness.

## R6-03

**Candidate: contributor capability/schema adapters.** Read [discovery](../specs/library-discovery.md).

1. Admission identifies real external contributors/use cases after the built-in boundary stabilizes.
2. Define versioned inputs/outputs, registration, scope, resource/trust/lifecycle constraints and compatibility tests; extension-provided code is not loaded implicitly from an arbitrary dependency.
3. A separately authored adapter passes positive/negative/removal tests without modifying core inventory lists.

## R6-04

**Candidate: scoped runtime observations.** Read [agent](../specs/agent-tools.md) and [discovery](../specs/library-discovery.md).

1. Admission demonstrates information static analysis cannot economically provide and an explicit runtime session/permission model.
2. Observations carry process/session/environment/time scope, bounded collection and disposal; no secrets or universalizing environment-specific facts.
3. Disconnect/restart/stale observations behave correctly; measured workflow value justifies runtime overhead.

## R6-05

**Candidate: headless CI/team workflows.** Read [agent contract](../specs/agent-tools.md).

1. Admission identifies active users requiring reproducible checks outside an editor.
2. Reuse the analysis engine with explicit checkout/overlay/unsaved semantics, input fingerprints, schema compatibility and bounded output.
3. Independent CI executions agree on declared inputs without editor-only APIs/private caches; measure operation cost and maintenance value.

## R6-06

**Candidate: richer refactoring and test-impact selection.** Read [agent](../specs/agent-tools.md), [IDE](../specs/ide-workflows.md) and [validation](../specs/validation.md).

1. Admission selects a concrete valuable transformation/selection problem and expected precision, not an unlimited refactoring promise.
2. Version-aware previews, ambiguity handling, partial-failure recovery and held-out correctness cases pass.
3. Compare selected tests with independent full-suite outcomes; report missed impacts and limits before using selection to skip validation.

R6 candidates require admission, an updated acceptance card and dependency review before changing `deferred` to `queued`. A new model release alone is not evidence that a particular feature should be built or removed.
