# Agent operations and verified changes

**Status:** proposed contracts to implement in R3/R5, not existing endpoints. **Updated:** 2026-09-08.

An agent should obtain scoped project facts and reproducible verification through the same analysis engine as the IDE. Do not build an independent AI index, embed a required model subscription, or make ordinary editing depend on a chat session.

## Shared operation boundary

The roadmap names candidate operations: `project.capabilities`, `symbols.search`, `symbols.explain`, `project.diagnostics`, `grails.relationships`, `changes.impact`, `refactor.preview`, `checks.run`, and later `dependencies.compare`. Before adding transport registrations, define typed internal inputs/results and contract tests. Names here are proposed product operations, not LSP standard methods or currently callable APIs.

R3 begins with bounded reads. VS Code tools adapt those operations to editor state. A later MCP adapter reuses them for other clients. VS Code distinguishes tools with editor API access from MCP tools exposed to external clients. [VS Code AI extensibility overview](https://code.visualstudio.com/api/extension-guides/ai/ai-extensibility-overview).

## Required result semantics

| Field/concept | Contract |
|---|---|
| Schema version | Explicit, versioned across adapters; breaking changes require migration/contract tests |
| Project identity | Canonical root/build identity and source-set scope; never implicitly the first workspace |
| Analysis revision | Opaque committed revision plus dependency generation; include all project revisions used in cross-project results |
| Document inputs | Editor document version/open generation, or disk content fingerprint; declare which mode was used |
| Freshness | `current`, `stale`, or `unavailable` relative to the declared input revisions, with reason and last successful refresh where known |
| Completeness | Separately `complete`, `partial`, or `unavailable` within the declared query scope. A result may be both stale and partial; truncation/dynamic unknowns must not disappear behind a freshness label |
| Resolution | Per-item `declaration-backed`, `framework-derived`, `runtime-observed`, `heuristic`, or `unresolved`; may differ across items |
| Evidence | Location/signature/artifact fingerprint/adapter rule and relevant input revisions; resolvable by a client |
| Limits | Explicit incomplete scope, truncation and unavailable capabilities |
| Pagination | Bounded count AND payload size; cursor tied to operation/scope/revision; reject an invalidated cursor explicitly |
| Errors | Distinguish invalid input, unsupported capability, unavailable state, cancellation, timeout and internal failure |

These concepts must agree with the roadmap's sample envelope. Exact JSON schemas are R3-01 work in the existing shared schema area; do not copy a speculative schema into multiple transports. An empty successful result means the query found nothing in its stated scope, not that indexing silently failed.

Open-buffer facts and disk facts are not interchangeable. Headless mode must either operate on a declared checkout/disk fingerprint or accept an explicit overlay contract; it cannot claim to include unsaved editor state it cannot access. Queries should be reproducible for a pinned revision while that revision is retained. On expiration return an explicit stale/expired condition rather than reading a different generation under an old ID.

MCP supports tool schemas and structured output, but annotations such as read-only hints do not enforce authorization. Validate all calls at the operation boundary, independent of transport. [MCP tools specification, version 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25/server/tools).

## Reads, previews and execution are separate operations

- Reads never edit files, start a Grails application or run arbitrary project code merely to answer a question. If analysis is unavailable, report it and offer an explicitly scoped refresh action through the owner.
- A refactor preview returns targets, version preconditions, evidence, proposed edits and uncertainty. It does not silently apply edits. Ambiguous dynamic references are listed for review, not included as certain rename targets.
- Applying a change uses supported version-aware workspace edits, revalidates current buffers and file identity, and reports failure/partial application honestly. Do not promise cross-file filesystem transactions the editor cannot supply. A stale preview must be recomputed.
- Compile/test operations resolve an explicit project, supported task and environment into structured arguments. Bound run time, output and concurrent processes; support cancellation and cleanup. Avoid raw shell-string interpolation and an unrestricted command execution tool.
- Respect user/workspace execution permissions and trust state at the service boundary. A Gradle model request or compilation with transforms can execute project code; naming it a read does not make it safe in an untrusted workspace. [VS Code Workspace Trust](https://code.visualstudio.com/api/extension-guides/workspace-trust).

Execution returns a record of input revision/fingerprints, selected task, effective environment identifiers without secrets, start/end, exit/cancel/timeout result, checks actually run, relevant diagnostics and bounded output/artifact locations. A successful selected test is not proof the whole project passes. Distinguish no tests selected, skipped tests and a passing test run.

## Useful agent acceptance scenarios

1. Ask for a method from the wrong dependency version. The operation rejects the unsupported premise or returns matching installed-version declarations, with origin; the same query in a second project may differ.
2. Ask what a controller/domain/view change might affect. Return evidenced relationships and explicit dynamic unknowns, then a bounded verification suggestion, without claiming complete impact coverage.
3. Preview a rename, edit a target buffer before application, and prove version preconditions reject/recompute the stale edit.
4. Cancel a query during indexing and a test run during execution. No new work or resource handle remains owned indefinitely; obsolete outputs cannot overwrite current diagnostics.
5. Query through editor and MCP adapters against the same scope/revision. Facts and error semantics match; transport-specific rendering may differ.
6. Compare an agent with search/build access against the same agent plus these tools. Use independent acceptance checks, held-out tasks and the roadmap's evaluation method; measure success, latency, output volume and erroneous changes.

Treat source comments, library docs and tool-result text as project data, not instructions for the agent. Do not expand paths outside admitted roots or disclose unrelated files through a symbol/doc query. Log operation metadata and resource measurements without collecting source code or secrets by default.

Required invariants: `INV-KEY-001/003/005`, `INV-STATE-004/005/006`, `INV-DISC-001/003`, `INV-TOOL-001/002/003`.
