# Skill: Recover
> Agent-agnostic skill. Any AI agent (Claude, Gemini, Copilot, Codex) can run this.
> Target: Diagnosing build failures, test regressions, LSP runtime issues, and agent spirals.

This skill diagnoses a failure and proposes a bounded repair. Its diagnostic stage does not edit code. Return the evidence to the Reviewer, who continues the already authorized repair and validation under [agent execution](../agent-execution.md); do not stop the overall task at a plan or infer a new permission gate.

## Step 1 — Stop

```
🛑 RECOVERY MODE ACTIVE — no changes until diagnosis complete.
```

## Step 2 — Assess

Check status and VCS state:
- `git status`
- `git log --oneline -10`
- `git diff --stat HEAD`
- Read: `MEMORY.md`, `client/STATUS.md`, `server/STATUS.md`, `AGENTS.md` §5 (L104-L137).

## Step 3 — Classify Failure

### A: Client Build Broken
- Run typechecks: `npm run check-types`
- Check ServiceContainer wiring (client/RULES.md §2 L34-L70)

### B: Server Build Broken
- Run server build: `npm run build:server`
- Check `@CompileStatic` violations, ProviderRegistry wiring (server/RULES.md §15 L391-L406)

### C: Test Regression
- Read the current XML/stack trace, then run the smallest affected test through `node scripts/gradle.js test --tests "<pattern>" --console=plain` from the root.
- Do not repeatedly run the full suite without a change or new diagnostic reason.

### D: Agent Spiral
- Look for circular file changes in the last 5-10 commits
- Identify the bad assumption that started the loop.

### E: LSP Runtime Issues
- Shadow JAR exists? Check `client/server/*.jar`
- GrailsService init sequence (server/RULES.md §2 L31-L81)
- OOM? Check `build.gradle` JVM args, ClassGraph scan

### F: Environment
- Node 20+, JDK 17+, Gradle 7+ present?
- `package.json` / `build.gradle` recent changes?

## Step 4 — Find Last Good State

- Inspect Git history: `git log --oneline -20`
- Check with the user or history for the last working commit.

## Step 5 — Recovery Plan

```
## Diagnosis
Mode: [A-F] [Name]
Root cause: [file, line, bad assumption]
Evidence: [git/file proof]
Last good: [SHA or "uncommitted"]

## Recovery Options

### Bounded repair
1. [specific step]
2. [validate: build command]
Risk: [what could fail] | When: [condition]

### Alternative, only if materially useful
[Evidence-based alternative and its tradeoff; preserve unrelated dirty work.]

### Validation After Recovery
Client: npm run compile → check-types → lint
Server: npm run build:server → cd server && ./gradlew test

### Context for Next Agent
[2-3 sentences to prevent repeat]
```

## Rules
- Diagnosis first; implementation continues through the Reviewer using validated evidence.
- Find BAD ASSUMPTION in spirals — treating symptoms won't stop the loop.
- Do not fabricate alternatives or reset/stash unrelated work. A prior passing commit is evidence to inspect, not authority to overwrite the working tree.
