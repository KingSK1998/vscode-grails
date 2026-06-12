# Skill: Review
> Agent-agnostic skill. Any AI agent (Claude, Gemini, Copilot, Codex) can run this.
> Target: Post-implementation validation, build checks, and UI pattern capture.

Validate what was built. Find issues. Capture UI patterns. Do NOT fix.

## Step 1 — Load References

Read: `AGENTS.md` §3-§5 (L48-L140), `CODING_STANDARDS.md`, `client/RULES.md` (if client), `server/RULES.md` (if server), `MEMORY.md` (if exists).

## Step 2 — Get Changed Files

Find changed files via VCS:
- In git: `git diff --name-only HEAD` and `git diff --name-only --cached`
- Read every changed file.

## Step 3 — Validate

### Plan Deviation
- Matches plan? Files created/skipped vs plan? Scope creep?

### Client Checks (see client/RULES.md §2-§8 L34-L211)
- Commands delegate to UseCase? (§7 L184-L193)
- Services in ServiceContainer only? (§2 L34-L70)
- Errors via ErrorService? (§6 L158-L178)
- Disposable implemented? (§8 L196-L211)
- No `any`, explicit return types? (§9 L214-L221)

### Server Checks (see server/RULES.md §3-§5 L84-L217)
- Correct tier? T1/T2/T3 decision checklist (§3 L88-L100)
- T1: extends BaseProvider, cancellation+health? (§4 L124-L178)
- `@CompileStatic` on all classes? (§1 L23)
- Provider in ProviderRegistry? (§15 L391-L406)
- No writes to visitor/compiler/fileTracker? (§2 L58-L69)
- Groovy property getters, `?.`, `?:`? (§5 L184-L217)

### Code Quality (CODING_STANDARDS.md)
- No hardcoded values, no >30-line methods, no dead code, no magic values

### Tests & Debt
- Tests for changed behavior? TODOs? Missing @CompileStatic? Missing Disposable?

## Step 4 — UI Pattern Capture (if UI changed)

If changed files include webviews, tree views, commands, or `resources/styles/`:

1. Extract: CSS tokens, message passing pattern, state management, CSP approach
2. Check against existing `DESIGN_SYSTEM.md` (if exists) for conflicts
3. If conflict: report in review, don't auto-resolve
4. Append new pattern to `DESIGN_SYSTEM.md` (create if absent):

```markdown
## [ComponentName]
_Type: webview | tree-view | command_
_Files: [paths]_
_Key rule: [constraint]_
```

## Step 5 — Build Validation

Run verification checks:
- Client: `npm run check-types` and `npm run lint`
- Server: `npm run build:server` and `cd server && ./gradlew test`

## Step 6 — Report

```
## Review: [feature]

### 🔴 CRITICAL
1. [File:Line] [Rule: server/RULES.md §N LXX] Found: X Expected: Y

### 🟡 WARNING
1. [File:Line] [Issue] Impact: [why]

### 🔵 SUGGESTION
1. [File:Line] [improvement]

### ✅ PASSED
- [things done right]

Summary: [N] critical, [N] warnings, [N] suggestions
Build: PASS | FAIL
UI Patterns: [N] captured to DESIGN_SYSTEM.md (or "none")
Verdict: PASS | PASS WITH WARNINGS | BLOCK
```

## Rules
- NEVER apply fixes. Report only.
- Cite rule violations with file + section + line range.
- If approved in MEMORY.md, note as approved debt.
