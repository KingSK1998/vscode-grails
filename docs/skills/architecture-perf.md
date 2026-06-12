# Architecture for Performance — Grails Extension
> Agent-agnostic skill. Any AI agent can read this.
> Referenced from: `AGENTS.md` §7 (L135-L155)

## Context

Extension host → shared → blocking = editor freeze.
Server → own JVM → throughput over latency.
Client lightweight logic → acceptable. Client Grails/AST/domain logic → wrong side.
Before any change → flag finding → ask permission.

## Where Does Logic Belong?

- Needs VS Code API only → client
- Needs AST / ClassNode / Grails domain → server
- Needs both → client triggers, server processes, client displays
- Lightweight, no VS Code API, no AST → either, prefer server

Current placement wrong → flag, ask permission.

## Performance Flow

- Assumed slow → measure first, do nothing yet
- Not slow → already best, stop
- Confirmed slow → root cause?
  - Wrong architecture → fix architecture first
  - Right architecture, sync work → would async help?
  - Right architecture, right async → cannot improve, document why

## Reasoning Dimensions

For any problem, reason across all three:
- Placement → right time? right side? right frequency?
- Lifecycle → fully cleaned up? restarts safe?
- Scope → as narrow as possible?

## Init Order (see client/RULES.md §2 L34-L70)

| Layer | Services |
|-------|----------|
| 0 | OutputChannelService, GrailsLspConfig |
| 1 | ConfigurationService, StatusBarService, ErrorService |
| 2 | ProjectService, GradleService, LogStreamingService |
| 3 | LanguageServerManager |
| 4 | ArtifactService, GrailsTestService, DebugService |
| 5 | DashboardService, DependencyGraphService, GormSqlPreviewService (lazy only) |

Blocks user before Layer 3? No → Layer 4+. UI-only → Layer 5.

## Budget

| Metric | Target | Red Flag |
|--------|--------|----------|
| Activation | < 100ms | > 500ms |
| First completion | < 500ms | > 2s |
| didChange → server | debounced 300ms | no debounce |
| Collections / logs | capped | unbounded |
| Services at startup | Layers 0–3 | all 13 |

## Diagnose

- Slow activation → which layer initializes eagerly that blocks user?
- Slow completion → debounce missing? wrong TIER? full reindex on single file?
- Editor freeze → what holds extension host thread that could yield?
- Memory grows → which collection uncapped? which service never disposes?
