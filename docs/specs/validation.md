# Verification and release evidence

**Updated:** 2026-09-08. This contract applies to every queue task. It defines evidence required for acceptance; it does not assert that any current release gate passes.

## Checks appropriate to the change

Run from the repository root unless stated otherwise. Use the existing wrapper scripts rather than assuming a globally installed Gradle.

| Change | Required checks |
|---|---|
| Markdown/task queue | Relative links/anchors, unique task IDs, dependency DAG, source/requirement consistency; `node scripts/roadmap.js validate` |
| Selector/helper script | `node scripts/roadmap.test.js` for the selector; focused tests for other changed helpers. No JVM build for documentation tooling alone |
| Client TypeScript | `npm run compile`, `npm run check-types`, `npm run lint`, and affected behavior tests / `npm run test:client` |
| Server Groovy | Smallest affected Spock tests first; then `npm run build:server` and `npm run test:server` |
| Shared protocol | Both components' affected checks, schema compatibility tests and real custom-protocol transport smoke |
| Launch/Gradle/package | Relevant script tests, full `npm run build`, `npm run test:smoke`, package contents and fresh-installed VSIX workflow |
| Release candidate | R5-05 matrix and end-to-end acceptance below |

For a server bug, first reproduce it in an existing/failing targeted test or add the smallest new regression. Prefer a pure Spock test; use `BaseLspSpec` only when the running service is needed. Do not turn a concurrency test into a synchronous mock test that bypasses the race. No repeated `clean` or parallel Gradle builds in the same checkout. Inspect current XML/stack traces before rerunning a failed command.

Build checks validate compilation/package plumbing, not semantics. Mock-based resolver tests do not prove actual Grails compatibility. A passing source test does not prove the bundled JAR contains that source.

## Fixture requirements

Keep a fixture manifest with exact Grails/Groovy/JDK/Gradle versions, dependency coordinates/fingerprints, wrapper version, source-set/build layout, expected capabilities and rationale. R2-06 creates the executable compatibility matrix after checking available real artifacts; a family label such as “Grails 7+” is not a matrix entry.

| Fixture class | Proves |
|---|---|
| Small synthetic unit inputs | Algorithms, mapping boundaries, empty/invalid input and deterministic concurrency |
| Pinned real Grails 7/Groovy 4 build | Framework declarations, conventions and startup behavior |
| Two real dependency versions | Added/removed/changed APIs and origin changes without hardcoded inventories |
| Two roots and multiple source sets | Isolation and correct test/production visibility |
| Multi-project/composite build | Explicit module edges, renamed projects and propagation |
| Large generated workspace | Scaling/resource behavior, not real framework semantics |
| Held-out application workflows | Product correctness beyond the fixtures used to implement it |

Record fixture provenance/licenses before redistribution. Resolve artifacts once where practical and allow deterministic offline replay; keep at least one declared real-resolution integration check. Do not depend on mutable latest versions or a developer's private home-cache state for release acceptance.

## Required failure/negative cases

Empty document; sequential edits and out-of-order versions; edit while compiling; close/reopen with reset version; root removal/re-add during discovery; shutdown during compile/Gradle; syntax error versus fatal failure; stale cursor/preview; missing/offline/corrupt dependency; same coordinates with changed bytes; wrong-project API; dependency removal; closed/deleted file; classloader/cache retention; partial index visibility; UTF-16 and mixed newline mappings; untrusted workspace execution; packaging an obsolete JAR.

Each task card selects the applicable cases. Record invariant ID -> behavioral test -> result. An inspection can establish wiring/ownership but cannot prove race freedom, garbage collection or measured latency. Avoid tests that merely mirror an implementation's private collection layout.

## Release candidate acceptance

1. Build from the documented environment with the intended server artifact. Inspect the VSIX: exactly the intended bundled server, required runtime assets, no accidental caches/logs/test outputs or obsolete JAR selection ambiguity.
2. Fresh-install on Windows, Linux and macOS for the declared support matrix. Verify configured Java including paths with spaces, startup, project discovery, document diagnostics/navigation and clean shutdown through real stdio. Record stderr separately from framed stdout.
3. Run daily workflows: unsaved edits, Grails relationships, mixed GSP regions/config, run/debug/tests, conservative rename preview and verified checks, agent reads. Use independent expected results, not “no exceptions” alone.
4. Execute the [performance protocol](performance.md) and a long editing/open-close/dependency-refresh session; inspect resources and failure recovery.
5. Complete keyboard/screen-reader/theme acceptance and at least the roadmap's proposed external-user workflow validation. Record unresolved support gaps in release notes and capability labels.
6. Produce reviewable local artifacts and release notes. Actual publishing follows the user's authorization; queue completion is not permission to publish.

Unavailable OS/toolchain/network checks stay `needs_verification` or `blocked` with exact conditions; do not relabel them passed. If an unrelated baseline failure blocks the required suite, record a separate repair task or repair it within a justified scope. A waiver cannot silently satisfy a correctness invariant or release gate.

## Evidence durability

Task records store commands, exit codes, timestamps, environment and concise results. Link full reports/artifacts where available; transient `build/` output may be overwritten, so preserve the meaningful result summary. Record whether checks used the final relevant dirty tree. Never copy old PASS results forward after changing the behavior they tested.

The task selector validates dependency bookkeeping and an acceptance marker; reviews and executable tests enforce the contract. Documentation reduces ambiguity but does not itself prevent runtime invariant violations.
