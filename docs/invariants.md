# Invariants — Field Ownership & Stable Identity Contracts

> **Status:** ENFORCED — agents MUST check this before modifying state, identity, or cross-system code.
> **Owner:** @kingsk (sole maintainer)
> **Last updated:** 2026-06-23
> **Validation triggers:** Architecture change, new shared state, new cache, new provider
> **Related ADRs:** ADR-004, ADR-005, ADR-006
> **Related Failure Modes:** SM-001, CI-001, PC-001

---

## 1. Field Ownership Map

Every piece of mutable state has exactly ONE owner. Only the owner may write. Everyone else reads.

### Server — State Ownership

| Field / State | Owner | Source of Truth | Readers | Write Methods |
|---|---|---|---|---|
| `compiler` (GrailsCompiler) | `GrailsService` | Groovy CompilationUnit | All T1 providers (read-only) | `compileAndVisitAST()`, `setupWorkspace()` |
| `visitor` (GrailsASTVisitor) | `GrailsService` | AST traversal output | All T1 providers (read-only) | `visitAST()`, `compileAndVisitAST()` |
| `fileTracker` (FileContentTracker) | `GrailsService` | In-memory document buffers | All T1 providers (read-only) | `didOpen()`, `didChange()`, `didClose()` |
| `astService` (ASTService) | `GrailsService` | Artifact classification caches | Discovery, completion | `clearUri()` via visitor pipeline |
| `projectIndex` (ProjectIndex) | IndexManager | Derived from AST | Hover, Definition, References | Index rebuild on AST change |
| `methodScopeCache` (MethodScopeCache) | Per-file scope | Local variable/param resolution | Completion, signature help | Evicted on document change |
| `groovydocCache` (GroovydocCache) | GroovydocCache (LRU) | Lazy extraction from AST | Hover, completion | LRU eviction + AST change |
| `config` (GrailsLspConfig) | `GrailsService` | VS Code settings via LSP | All providers (read-only) | `didChangeConfiguration()` |
| `cancellationService` | `GrailsService` | Request lifecycle | All T1 providers | `createCancellationToken()`, `didClose()` auto-cancel |
| `healthService` | `GrailsService` | Provider latency/success metrics | Observability | `recordHealth()` in provider finally blocks |
| `providerRegistry` | `GrailsService` | Lazy provider instances | `GrailsTextDocumentService` | `getProvider()` (lazy init) |
| `discoveryService` | `GrailsService` | ClassGraph workspace scan | Completion strategies | `clearSymbolCaches()` on AST cycle end |
| Cross-file caches | `GrailsService` | Derived from visitor + compiler | All providers | `clearCrossFileCaches()` at end of incremental cycle |

### Client — State Ownership

| Field / State | Owner | Source of Truth | Readers |
|---|---|---|---|
| Service instances | `ServiceContainer` | Constructor wiring | Commands via UseCase |
| LSP client lifecycle | `LanguageServerManager` | VS Code LanguageClient API | Commands, status bar |
| Configuration values | `ConfigurationService` | `workspace.getConfiguration('grails')` | All services |
| Error state | `ErrorService` | Error events | Status bar, output channel |
| Server status | `LanguageServerManager` | LSP connection state | Status bar, commands |
| Webview panels | Per-service (Dashboard, DepGraph, SQL) | VS Code WebviewPanel API | Commands |

### Ownership Rules

| ID | Severity | Rule | Related ADR | Related Failure Mode |
|---|---|---|---|---|
| `INV-OWN-001` | 🔴 CRITICAL | **Single writer.** Only owner writes. Providers NEVER write to `visitor`, `compiler`, `fileTracker`. EVER. | ADR-004 | PC-001 |
| `INV-OWN-002` | 🔴 CRITICAL | **No shared mutable state outside GrailsService.** Server shared state lives in GrailsService only. | ADR-004 | — |
| `INV-OWN-003` | 🟡 HIGH | **No ServiceContainer inside services.** Client services receive deps via constructor injection only. | ADR-001 | — |
| `INV-OWN-004` | 🔴 CRITICAL | **Derived state must have invalidation trigger.** Every cache documents: what invalidates it, who, what scope. | ADR-002 | SM-001, CI-001 |
| `INV-OWN-005` | 🔴 CRITICAL | **No ASTNode references in ProjectIndex.** All data extracted to primitive/LSP types at index-build time. | ADR-006 | — |

---

## 2. Stable Identifier Contracts

### What Is Durable vs. Mutable

| ID | Identifier | Durable? | System | Notes |
|---|---|---|---|---|
| `INV-ID-001` | File URI (`file:///...`) | ✅ Durable | LSP protocol | Canonical key for all document state. Normalized before use. |
| `INV-ID-002` | Class fully-qualified name | ✅ Durable (within compile) | Groovy compiler | Stable within a compilation unit. Changes if user renames class. |
| `INV-ID-003` | `SymbolInfo.id` | ✅ Durable (within snapshot) | ProjectIndex | Unique within index version. Regenerated on rebuild. |
| `INV-ID-004` | AST node identity (`ClassNode`, `MethodNode`) | ❌ Mutable | Groovy compiler | Destroyed and recreated on every recompilation. NEVER cache. |
| `INV-ID-005` | Line/column positions | ❌ Mutable | Editor content | Shift with every keystroke. Always resolve fresh from AST. |
| `INV-ID-006` | `GrailsService` field references | ❌ Mutable | Runtime | Providers read live via getters. Never cache reference. |
| `INV-ID-007` | Config values (`codeLensMode`, etc.) | ❌ Mutable | User settings | Can change anytime via `didChangeConfiguration`. Always read live. |
| `INV-ID-008` | Gradle dependency coordinates | ✅ Durable (within sync) | build.gradle | Stable until next Gradle sync. |
| `INV-ID-009` | Workspace folder URI | ✅ Durable (within session) | VS Code API | Stable for extension lifetime. Changes on folder add/remove. |

### Cross-System Key Rules

| ID | Severity | Rule | Related ADR |
|---|---|---|---|
| `INV-KEY-001` | 🔴 CRITICAL | **URI is canonical cross-system key.** All document state keys on normalized URI. | — |
| `INV-KEY-002` | 🔴 CRITICAL | **Never use mutable state as lookup key.** Line numbers, AST node identity — all mutable. Resolve fresh. | ADR-006 |
| `INV-KEY-003` | 🟡 HIGH | **SymbolInfo IDs are snapshot-scoped.** Valid only within one index version. Do not persist across rebuilds. | ADR-006 |
| `INV-KEY-004` | 🟠 MEDIUM | **Gradle coordinates are sync-scoped.** Valid only until next `build.gradle` change triggers re-sync. | — |
| `INV-KEY-005` | 🟡 HIGH | **Provider state is request-scoped.** Providers hold no state between requests. Each request starts fresh. | ADR-005 |

### Identity Resolution Chain

```
User action → URI + Position
  → FileContentTracker.getContent(uri) → fresh buffer
  → GrailsASTVisitor.getClassNodes(uri) → fresh AST nodes
  → ProjectIndex.getSymbol(fqcn) → SymbolInfo (immutable value)
  → Provider transforms SymbolInfo → LSP response
```

Every step re-resolves from source. No stale references carried forward.

---

## 3. State Consistency Invariants

These are non-negotiable. Violation = bug.

| ID | Severity | Rule | Related Failure Mode |
|---|---|---|---|
| `INV-STATE-001` | 🔴 CRITICAL | **Single-writer principle.** Only GrailsService write-path methods mutate shared state. | PC-001 |
| `INV-STATE-002` | 🔴 CRITICAL | **Read-never-write.** Providers read via BaseProvider getters. Never call mutating methods. | PC-001 |
| `INV-STATE-003` | 🔴 CRITICAL | **Atomic invalidation.** `clearCrossFileCaches()` at END of every incremental AST cycle, not middle. | CI-001 |
| `INV-STATE-004` | 🟡 HIGH | **No partial visibility.** Providers never observe half-built ASTs or partially updated indexes. | — |
| `INV-STATE-005` | 🟡 HIGH | **Cancellation-safe.** `CancellationException` propagates cleanly. No partial results committed. | — |
| `INV-STATE-006` | 🟡 HIGH | **Fail-safe defaults.** Missing data → empty list / null. Never throw from provider. | — |
| `INV-STATE-007` | 🟠 MEDIUM | **URI normalization.** All URI comparisons use normalized form. Raw string comparison = bug. | — |

---

## 4. Invariant Review Checklist

> Agents MUST run this checklist when reviewing code that touches state, caches, identifiers, or providers.
> Reference: `docs/skills/review.md §3`

```text
State Change?
□ INV-OWN-001 — Single writer preserved
□ INV-OWN-002 — No new shared mutable state outside GrailsService
□ INV-STATE-001 — Write-path methods unchanged or justified

New Cache?
□ INV-OWN-004 — Invalidation trigger documented (what, who, scope)
□ INV-OWN-005 — No ASTNode references stored
□ INV-STATE-003 — clearCrossFileCaches() updated if cross-file

New Identifier?
□ INV-ID-* — Durable/mutable classification added to §2 table
□ INV-KEY-002 — Not using mutable value as lookup key

New Shared State?
□ INV-OWN-001 — Ownership assigned in §1 table
□ INV-OWN-002 — Lives in GrailsService (server) or ServiceContainer (client)

New Architecture?
□ ADR required — document in docs/adr/decisions.md
```
