# Implementation handoff — R0-01 baseline

**Updated:** 2026-09-08. **Purpose:** continue the interrupted implementation without repeating the project audit. The latest user request prioritizes a complete roadmap and a durable handoff; no runtime fixes were attempted during this documentation update.

Read [AGENTS.md](../AGENTS.md), then the [execution guide](agent-execution.md). Run `node scripts/roadmap.js next` for the current task; this handoff records the September interrupted baseline, not a permanently fixed next-task instruction. Its [R0-01 record](execution/records/R0-01.md) carries resumption evidence. The first compatibility target is Grails 7+ / Groovy 4. Do not treat historical “Phase 3 complete” labels as a passing release gate.

## Current state

The working tree contains substantial pre-existing work plus unfinished changes from the September implementation sessions. Preserve it. Do not reset files or infer ownership from staged/unstaged state; staging changed across the interrupted sessions without an agent index operation being recorded.

| Area | What exists | Verification status |
|---|---|---|
| Client startup | Production default, configured Java executable, separate application/LSP development ports, stable bundled-JAR selection | Five headless client tests passed in the preceding session |
| Client event ordering | Project notification handlers registered before `LanguageClient.start()` | Early project-discovery regression reproduced, then passed |
| Build/package plumbing | Portable Gradle launcher; build/copy/package scripts; stable JAR name; exclusions and CI adjustments | Unit coverage and package-file listing checked; final build/VSIX not verified |
| Document compilation | Scheduled single worker, URI coalescing, copied revisions, pending completion future, deferred dependency processing | Partial implementation; latest saved targeted test run has failures |
| Project write transaction | Compile/index/publication grouped under project lock; close cleanup added | Regression currently fails with `ProjectState`/`AtomicReference` cast error |
| Initial workspace discovery | Initial loop remains TODO; folder changes remain a no-op | Unimplemented; startup agent stopped at the usage limit |
| Stdio logging | `logback.xml` console target changed to `System.err` | Old bundled JAR reproduced protocol corruption; rebuilt JAR not yet retested |
| Product direction | R0–R6 roadmap, finished-product definition, AI workflows and release gates | Documentation, not a claim of implementation |

The previous clean incremental-compiler baseline passed **before** the background-scheduling changes. It does not validate the current working tree.

## Latest saved server test evidence

Read on 2026-09-08 from `server/build/test-results/test/`; report timestamps are 2026-09-07 around 07:13 UTC. No server test was rerun during this documentation task.

| Suite | Tests | Failures | Observed cause |
|---|---|---|---|
| `GrailsIncrementalCompilerSpec` | 7 | 7 | `ExecutionException` wrapping `ClassCastException`: `ProjectState` cannot be cast to `AtomicReference` |
| `DocumentCompilationSpec` | 6 | 3 | `document.@pendingChanges.empty` evaluates to a map-key lookup (`null`) even when the map is `[:]` |

The map assertion failures do not by themselves demonstrate a nonempty queue. Correct the assertion without weakening the behavior test. The project exception is a runtime regression: inspect property dispatch within the newly introduced Groovy closures. The interrupted writer identified `state` resolving through `getState()` rather than the atomic field; verify the exact stack before fixing it. Keep `@CompileStatic`.

## R0-01 — the next coding task

**Outcome:** opening/editing a document returns promptly, newest pending revisions compile correctly, close/shutdown cannot publish obsolete work, and existing incremental behavior remains correct.

### Files to inspect

- [GrailsTextDocumentService](../server/src/main/groovy/kingsk/grails/lsp/services/GrailsTextDocumentService.groovy): `scheduleCompilation`, `runPendingChange`, `compilationFinished`, `didClose`, `shutdown`.
- [ProjectContextImpl](../server/src/main/groovy/kingsk/grails/lsp/context/ProjectContextImpl.groovy): closure field access, `compileAndVisitAST`, `commitSnapshotLocked`, `closeDocument`, lifecycle getters.
- [FileContentTracker](../server/src/main/groovy/kingsk/grails/lsp/services/FileContentTracker.groovy): optional deferred dependency work, copied input, empty documents and incremental edit ordering.
- [GrailsService](../server/src/main/groovy/kingsk/grails/lsp/GrailsService.groovy): document worker shutdown order.
- [DocumentCompilationSpec](../server/src/test/groovy/kingsk/grails/lsp/services/DocumentCompilationSpec.groovy) and [GrailsIncrementalCompilerSpec](../server/src/test/groovy/kingsk/grails/lsp/core/compiler/GrailsIncrementalCompilerSpec.groovy).
- [BaseLspSpec](../server/src/test/groovy/kingsk/grails/lsp/test/BaseLspSpec.groovy): helper waits introduced for asynchronous notifications.

### Remaining checks identified before interruption

1. Resolve the saved test failures using their actual stack traces.
2. Verify closing a hibernated project does not call activating getters from `commitSnapshotLocked`; add a behavior test.
3. Verify an empty open document is accepted (`didOpenFile` currently rejects falsy text).
4. Verify an active compile superseded by an edit or close cannot publish stale state/diagnostics.
5. Add root-scoped replay for documents opened before project discovery; coordinate its contract with R0-02 rather than dropping those documents.
6. Review URI normalization and current-project routing. Unrelated files must not silently use the default project.

Run the affected tests first from the repository root:

```sh
node scripts/gradle.js test --tests "*DocumentCompilationSpec" --tests "*GrailsIncrementalCompilerSpec" --console=plain
```

After those pass, run the required server build and suite:

```sh
npm run build:server
npm run test:server
```

Do not run Gradle builds concurrently in this checkout. Do not run `clean` repeatedly to hide failures. Record the actual result and relevant report before choosing another task.

**Acceptance:** targeted tests pass, relevant lifecycle tests pass, server validation is recorded, and the changed concurrency/ownership behavior is reflected in STATUS, invariants, architecture and failure-mode documents. New APIs must have lifecycle and cancellation coverage.

## R0-02 and R0-03 — validated findings, implementation still needed

- [GrailsLanguageServer](../server/src/main/groovy/kingsk/grails/lsp/GrailsLanguageServer.groovy) has a TODO-only initial workspace loop and no completed asynchronous discovery path. Return capabilities without waiting for Gradle; begin discovery after `initialized`.
- [GrailsWorkspaceService](../server/src/main/groovy/kingsk/grails/lsp/services/GrailsWorkspaceService.groovy) does not implement workspace add/remove. Track pending discovery by root and reject completion after removal/shutdown.
- Use existing `GradleService.getGrailsProjectAsync`, then register the result and publish project information. Do not use `GrailsService.getProjectInfo`'s default-project fallback to resolve unrelated new roots.
- Check URI/path conversion: the async Gradle method constructs a `File` from a URI; some existing callers pass plain Windows paths. Normalize deliberately and test both path forms.
- The launcher currently creates a `LanguageClient` proxy while the service expects custom `GrailsLanguageClient` notifications. Verify and fix the remote interface wiring.
- Replay still-open documents belonging to a newly registered root through the document queue. Never replay closed or removed-root documents.
- Build-change debounce currently captures only the last event batch; edits in different roots can drop earlier roots. Union affected roots until flush.
- Client watchers now include Kotlin settings and `gradle/libs.versions.toml`; server file classification still needs matching support.

### Real transport smoke test

[scripts/smoke-server.js](../scripts/smoke-server.js) launches only its own JVM, validates LSP framing, initializes a workspace, awaits project discovery, opens an in-memory Groovy document, checks diagnostics and symbols, then requests shutdown/exit. It does not write the smoke source file; normal Gradle/LSP caches may be written.

```sh
npm run build
npm run test:smoke
```

An optional workspace and JAR can be supplied directly:

```sh
node scripts/smoke-server.js PATH_TO_WORKSPACE PATH_TO_JAR
```

The previous run against `client/server/grails-language-server-0.4.1-SNAPSHOT-all.jar` failed immediately with **Non-protocol output on stdout**. The stderr source fix is present, but the stable rebuilt JAR has not passed this test. Passing this headless test still does not replace a fresh VSIX/editor acceptance test.

## Broader findings to preserve for R1/R2

- `GrailsCompiler.updateClassLoader()` combines dependencies from all projects, violating the intended isolation.
- Successful Gradle sync updates project metadata without fully refreshing the active compiler/classpath/index.
- Dependency edges currently use names/path containment rather than the explicit resolved Gradle graph.
- Historical documents overstate snapshot immutability and classloader release. Copying visitor maps does not prove all referenced AST objects are immutable.
- YAML is absent from the client LSP document selector; GSP embedded-language support is incomplete.
- Agent operations in the new roadmap are proposals. No MCP or language-model tool integration is implemented.

## Environment and validation notes

The project uses PowerShell on Windows. `npm.cmd` avoids PowerShell execution-policy ambiguity. The default WindowsApps `pwsh` launcher intermittently failed with access denied; explicit `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe` with no profile worked.

Gradle needed permission to use the existing cache under the user's home directory outside the workspace. This was an execution/cache boundary, not a code failure. Avoid changing system home variables. Set `CI=true` only for an individual test process when quieter test logging is useful.

The headless client runner avoids per-test process spawning, which failed in the restricted environment. Prior executed client checks: compile, type checking and lint passed; the final headless run had five passing tests. Some checks predate later unrelated edits; rerun the applicable validation before accepting a release.

No commit, push, Marketplace publish or successful final VSIX validation was performed. After each completed task, append evidence here and update the component status. Preserve previous decisions in MEMORY, with explicit corrections where older completion claims conflict with current code.

## Copyable next-session request

> Read AGENTS.md, docs/product-roadmap.md and docs/implementation-handoff.md. Resume R0-01: repair the interrupted document scheduler and project publication changes. Preserve existing work. Start with the saved XML failures and affected source/tests; avoid repeating the full repository audit. Reproduce any new bug before fixing it, keep @CompileStatic, run the targeted tests and required server checks, and update STATUS, failure modes, relevant invariants and MEMORY with actual results. Do not expand to workspace startup or new features until R0-01 passes.
