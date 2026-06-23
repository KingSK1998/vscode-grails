# System Map — Architecture at a Glance

> **Purpose:** First file a new agent reads. Understand the system BEFORE reading constraints.
> **Owner:** @kingsk (sole maintainer)
> **Last updated:** 2026-06-23
> **Last validated:** 2026-06-23

---

## The Big Picture

```
┌──────────────────────────────────────────────────────────┐
│                       VS Code                            │
│  ┌────────────────────────────────────────────────────┐  │
│  │              Client (TypeScript)                   │  │
│  │                                                    │  │
│  │  extension.ts → ServiceContainer                   │  │
│  │       │              │                             │  │
│  │       ▼              ▼                             │  │
│  │  Commands ─→ UseCases ─→ Services                  │  │
│  │       │                     │                      │  │
│  │       │    ┌────────────────┤                      │  │
│  │       ▼    ▼                ▼                      │  │
│  │  StatusBar  ErrorService  LanguageServerManager    │  │
│  │                               │                    │  │
│  └───────────────────────────────┼────────────────────┘  │
│                                  │ LSP (stdio JSON-RPC)  │
│  ┌───────────────────────────────┼────────────────────┐  │
│  │              Server (Groovy 4)│                    │  │
│  │                               ▼                    │  │
│  │  GrailsLanguageServer                              │  │
│  │       │                                            │  │
│  │       ▼                                            │  │
│  │  GrailsService ◄── composition root                │  │
│  │       │                                            │  │
│  │       ├── compiler (GrailsCompiler)                │  │
│  │       ├── visitor (GrailsASTVisitor)               │  │
│  │       ├── fileTracker (FileContentTracker)         │  │
│  │       ├── projectIndex (ProjectIndex)              │  │
│  │       ├── discoveryService (DiscoveryService)      │  │
│  │       ├── astService (ASTService)                  │  │
│  │       ├── cancellationService                      │  │
│  │       ├── healthService                            │  │
│  │       ├── providerRegistry ──────────────────┐     │  │
│  │       │                                      │     │  │
│  │       ▼                                      ▼     │  │
│  │  GrailsTextDocumentService    T1 Providers         │  │
│  │  GrailsWorkspaceService       ├── Completion       │  │
│  │                               ├── Hover            │  │
│  │                               ├── Definition       │  │
│  │                               ├── References       │  │
│  │                               ├── Diagnostics      │  │
│  │                               ├── CodeLens         │  │
│  │                               ├── SignatureHelp    │  │
│  │                               └── DocumentSymbol   │  │
│  │                                                    │  │
│  │  T2 Utilities (static, no state)                   │  │
│  │       GrailsUtils, ASTUtils, CompletionUtil, ...   │  │
│  │                                                    │  │
│  │  T3 Modules (own deps, no GrailsService)           │  │
│  │       GrailsYamlIntelligenceProvider(config)       │  │
│  │       GrailsLspConfig                              │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
         │
         ▼
    User's Grails Project
    ├── build.gradle (Gradle Tooling API)
    ├── src/**/*.groovy (CompilationUnit)
    └── grails-app/**/*.gsp (GspToGroovyConverter)
```

---

## Data Flow — What Happens When User Types

```
1. User edits file
   ↓
2. VS Code sends didChange notification
   ↓
3. Client forwards via LanguageClient (LSP)
   ↓
4. Server: GrailsTextDocumentService.didChange()
   ↓
5. FileContentTracker updates buffer
   ↓
6. GrailsService.compileAndVisitAST(file)  ← WRITE PATH
   ├── GrailsCompiler.compileSourceFile()
   ├── GrailsASTVisitor.visitSourceUnit()
   ├── ProjectIndex rebuild (partial)
   └── clearCrossFileCaches()
   ↓
7. GrailsDiagnosticService.publishDiagnostics()
   ↓
8. Next LSP request (hover, completion, etc.)
   → Provider reads fresh state via BaseProvider getters
   → Returns LSP response
```

---

## State Flow — Who Owns What

```
                    WRITE PATH (4 methods only)
                    ┌──────────────────────────────┐
                    │ setupWorkspace()             │
                    │ refreshAndReindexWorkspace() │
                    │ compileAndVisitAST(file)     │
                    │ visitAST(file)               │
                    └─────────┬────────────────────┘
                              │ mutates
                              ▼
┌──────────────────────────────────────────────────┐
│              GrailsService (owner)               │
│                                                  │
│  compiler ──► AST ──► visitor ──► ProjectIndex   │
│     ▲                                    │       │
│     │         fileTracker                │       │
│     │         config                     ▼       │
│     │         cancellationService   SymbolInfo   │
│     │         healthService        (immutable)   │
└─────┼────────────────────────────────────────────┘
      │                                    │
      │                              READ PATH
      │                                    │
      │         ┌──────────────────────────┘
      │         ▼
      │   ┌──────────────┐
      │   │  Providers   │ ── read via BaseProvider getters
      │   │  (stateless) │ ── NEVER write
      │   └──────────────┘
      │
  Gradle Tooling API
  (external, async)
```

---

## Provider Tiers — Decision Tree

```
Does your class use visitor/compiler/fileTracker at runtime?
  │
  ├── YES → TIER 1: extends BaseProvider
  │         Constructor: (ProviderContext, CompilationContext, ProjectContext)
  │         Created by: ProviderRegistry only
  │         Must: cancellation token + health recording
  │
  └── NO
       │
       ├── Pure function, no state? → TIER 2: static utility
       │   All methods static. No constructor. No fields.
       │
       └── Own lifecycle, minimal deps? → TIER 3: plug-n-play
           Constructor takes only what's needed (e.g., config).
           Never full GrailsService.
```

---

## High Risk Areas — Where Mistakes Are Expensive

| Area | Risk | Key Invariants | Why Expensive |
|---|---|---|---|
| **GrailsService write path** | Shared mutable state, invalidation logic, concurrency | `INV-OWN-001`, `INV-STATE-001` | Corrupts ALL downstream reads. Every provider affected. |
| **ProjectIndex** | Identity contracts, AST boundary enforcement | `INV-OWN-005`, `INV-ID-003`, `INV-KEY-002` | Stale index = wrong completions, wrong hover, wrong navigation. Silent. |
| **Incremental compilation pipeline** | Cache invalidation ordering, cross-file consistency | `INV-STATE-003`, `INV-OWN-004` | Stale caches produce wrong results that look correct. Hard to reproduce. |
| **ProviderRegistry** | Context boundary enforcement, constructor wiring | `INV-STATE-002`, `INV-OWN-001` | Wrong context = provider can mutate state. `@CompileStatic` is only safety net. |
| **clearCrossFileCaches()** | Must run at END of cycle, must cover ALL cross-file caches | `INV-STATE-003` | Missing one cache = stale data until next full rebuild. Users won't know. |

When touching these areas:
1. Read `docs/invariants.md` — full document, not just relevant section
2. Run invariant review checklist (`docs/skills/review.md §3`)
3. Verify with existing tests before modifying

---

## Reading Order For New Agents

| Step | File | What You Learn |
|---|---|---|
| 1 | **This file** (`docs/architecture/system-map.md`) | How the system works |
| 2 | `docs/architecture.md` | Detailed component responsibilities |
| 3 | `docs/invariants.md` | What you must never break |
| 4 | `docs/failure-modes.md` | What has broken before |
| 5 | `docs/adr/decisions.md` | Why things are the way they are |
| 6 | `docs/architecture/change-triggers.md` | When to update KB |
| 7 | `CODING_STANDARDS.md` | How to write code here |
| 8 | `server/RULES.md` or `client/RULES.md` | Component-specific rules |

