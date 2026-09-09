# Task record template

Copy this structure into `docs/execution/records/<task-id>.md`. The queue stores status, dependencies, owner and the record path; this record stores evidence. Do not create another task-status table.

## Task and session

- Task ID, session date, agent/coordinator and scope of this slice:
- Branch/commit and relevant pre-existing dirty files (do not imply the commit describes uncommitted changes):
- Acceptance card/specs read:
- Prior record/remaining work inspected:

## Evidence brief before implementation

- Current behavior: source paths and methods, with test or runtime evidence.
- Failure/missing behavior and smallest reproducer:
- Relevant invariant IDs and affected owner/reader/cache boundaries:
- Unknowns and how this task will resolve them:

## Implementation decision

- Intended observable result; non-goals:
- Files/owners to change:
- Input revision, candidate state, commit/publication and cancellation behavior:
- Cache/resource impact, limits and invalidation (or not applicable):
- Alternatives considered and evidence for the smallest sufficient change:
- Contract conflict/ADR update, if any:

## Acceptance evidence

For each numbered case in the card, record test name or manual procedure, command, result, and durable evidence. Include negative cases. `Not run` and `failed` are valid observations; they are not completion.

| Case | Test/procedure and command | Result | Evidence/environment |
|---|---|---|---|
| `<task-id>/1` | Fill before acceptance | Not run | Report or concise relevant output |

Record exit code, timestamp, OS/JDK/Node/Gradle versions relevant to reproduction, fixture version/hash, and dirty-tree scope. A transient `build/` report can be referenced, but preserve the result summary here because it will be overwritten. Do not commit secrets or entire machine logs.

## Review and final checks

- Code review verdict and material findings:
- Invariant mapping and architecture/KB review:
- Required component/build/package checks:
- Changed STATUS/KB files:
- Unresolved failures and disposition:

## Resume

- Last working/verified behavior:
- Unverified changes:
- Next exact action/command and expected observation:
- Blocker and condition for resumption, if any:
- Running processes/resources owned by this task, if any:

Only after all acceptance and applicable final checks pass, add a standalone `Acceptance: PASS` line. The selector checks this marker for `done` tasks; the Reviewer must verify the evidence behind it.
