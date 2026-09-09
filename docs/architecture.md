# Architecture

**Updated:** 2026-09-08. The extension has a TypeScript VS Code client and Groovy/LSP4J JVM server over stdio JSON-RPC. See the [system map](architecture/system-map.md) for source-backed current structure and open gaps; this overview does not certify runtime behavior.

## Component boundaries

| Component | Responsibility | Rules |
|---|---|---|
| Client | VS Code activation/settings, commands, UseCases, tasks/debug/testing presentation, trees and webviews | [client/RULES.md](../client/RULES.md) |
| Server composition | GrailsService wires services; WorkspaceManager coordinates registered projects | [server/RULES.md](../server/RULES.md), [invariants](invariants.md) |
| Project analysis | ProjectContextImpl owns compiler/visitor/index/publication and lifecycle; SnapshotManager owns lineage | [State contract](state-and-lifecycle-specification.md) |
| Discovery | Resolve actual project/source-set artifacts and capabilities; attach origins and invalidate by inputs | [Discovery spec](specs/library-discovery.md) |
| Language providers | Request-scoped consumers of coherent analysis; no hidden compile/scan/activation side effects | [Performance spec](specs/performance.md) |
| Embedded editing/UI | Region/source mapping, configuration metadata, native IDE workflows and bounded views | [IDE spec](specs/ide-workflows.md) |
| Agent operations | Proposed typed analysis/change/check boundary reused by editor and MCP adapters | [Agent spec](specs/agent-tools.md) |

## Startup and data flow

Client activation wires services and starts the configured JVM. LSP initialize advertises capabilities without waiting for Gradle. Initialized-time background project discovery, root registration and buffer replay are R0-02 work; the current initial root loop is incomplete. The client/server transport and final bundled artifact require R0-03/R0-04 verification.

Document callbacks apply input changes and queue analysis. Project writers build and validate candidate state, then publish a coherent generation for readers. This is the required contract; current shallow AST copies and mismatched lock coverage do not prove it. Read operations must stay available or explicitly unavailable during a blocked build.

The current client selects Groovy/GSP file documents and has expanded Gradle build-input watchers. Full YAML/JSON and embedded-language service routing is R4 work. Context registration exists, but project classpath isolation and dependency edges still need repair; neither 'single-root only' nor 'complete multi-root support' accurately describes the current source.

## Caches and storage

Keep exact scoped lookup maps and compact extracted index facts; prefer incremental affected-file updates and lazy bounded documentation/metadata loading. Every cache records ownership, revisions, bounds, invalidation and disposal. ProjectIndex, SymbolInfo and MethodScopeCache must not retain ASTNode references. Shared immutable artifact facts are separate from per-project visibility. Disk data is reproducible/schema-versioned and never a substitute for current dependency evidence.

The existing cache/snapshot classes are implementation to audit, not a mandate for duplicate caches or full AST persistence. Resource budgets and candidate DSA choices are in the performance spec; R1-05 supplies actual measurements.

## Decisions and delivery

[ADR-009](adr/decisions.md#adr-009-project-ownership-and-executable-contracts-2026-09-08) preserves GrailsService composition and single-writer boundaries while correcting obsolete physical ownership/wiring clauses. Context segregation, static compilation and the no-AST index boundary remain active. Use the [execution guide](agent-execution.md) and task queue to continue; historical phase documents do not select work.
