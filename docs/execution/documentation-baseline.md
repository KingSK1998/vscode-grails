# Documentation and execution-tool baseline

**Date:** 2026-09-08. **Scope:** the user requested specs/handoff documents so other agents can continue the existing roadmap. This is not a completed runtime roadmap task and does not advance R0-05.

## Delivered

- [Execution guide](../agent-execution.md), one dependency/status queue, 38 numbered acceptance cards and a resumable record template/checkpoint.
- Contracts for installed-library/capability discovery, state/lifecycle/publication, performance/resources, IDE/embedded editing, agent operations and release validation.
- Reconciled designated ownership, actual ProviderContext/WorkspaceManager provider wiring, AST retention versus safe index values, and legacy phase/proposal status. ADR-009 records the correction; withdrawn ADR-008 stays reserved.
- Read-only selector/validator and focused Node tests. No client/server runtime code was changed for this documentation task; pre-existing partial source work remains untouched by it.

## Verification evidence

| Check | Observed result |
|---|---|
| `node scripts/roadmap.test.js` | 12 tests passed after the CLI template-path correction |
| `node --test scripts/roadmap.test.js` | Child test-worker spawn was blocked by environment EPERM; direct invocation above passed |
| `node scripts/roadmap.js validate` | 38 tasks valid, with matching roadmap/card IDs, acyclic dependencies, paths and required evidence records |
| `node scripts/roadmap.js next` | R0-01 selected; queued, owner unassigned, correct acceptance card/checkpoint |
| Read-only Markdown check | Final check covered 32 core/added documents and 230 relative links/heading anchors; no missing target/anchor or unclosed fence |
| Focused tracked-file `git diff --check` | Passed for the revised core documentation/rules; unrelated pre-existing Markdown trailing spaces were outside that focused check |
| Independent review | Found and corrected captured-view wording, freshness/completeness distinction, regression reopening/dependent revalidation, and operation-name alignment |

The selector tests use synthetic queue/file inputs; CLI checks use this actual repository. Validation markers prove bookkeeping, not that runtime acceptance tests were run. The prior saved server failures remain the next coding task's evidence to reproduce. No full client/server build, server suite, new JVM smoke or VSIX install was run during this documentation work.

## Continue

Read [agent execution](../agent-execution.md) and run the selector. Repair/verify R0-01 against its full card, update its record and the affected KB, then continue the dependency queue. The current queue admits R0-R5 for 1.0; R6 remains deferred candidate work requiring evidence before admission.
