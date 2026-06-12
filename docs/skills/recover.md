# Skill: Recover
> Agent-agnostic skill. Any AI agent (Claude, Gemini, Copilot, Codex) can run this.
> Target: Diagnosing build failures, test regressions, LSP runtime issues, and agent spirals.

Diagnose. Recovery plan only. NO changes.

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
- Run tests: `cd server && ./gradlew test`
- Read test result XMLs to inspect failures.

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

### Option 1: Hard Reset
git checkout <SHA> -- <files>
Risk: [what's lost] | When: [condition]

### Option 2: Surgical Fix
1. [specific step]
2. [validate: build command]
Risk: [what could fail] | When: [condition]

### Option 3: Stash + Restart
git stash
Then: [what to do differently]

### Recommended: Option [N] because [reason]

### Validation After Recovery
Client: npm run compile → check-types → lint
Server: npm run build:server → cd server && ./gradlew test

### Context for Next Agent
[2-3 sentences to prevent repeat]
```

## Rules
- NEVER make changes. Plan only.
- Find BAD ASSUMPTION in spirals — treating symptoms won't stop the loop.
- Always offer at least 2 recovery options.
