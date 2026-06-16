# Skill: Review

Review code against project standards. Find violations, safely auto-fix what is mechanical, validate builds, and report results.

## 1. Load Standards (MANDATORY)

Before reviewing anything:

- Read `CODING_STANDARDS.md`
- If client files changed, read `client/RULES.md`
- If server files changed, read `server/RULES.md`

These files are authoritative.

Do not review code until all applicable standards files have been read.

## 2. Read Changes

Get changed files:

```bash
git diff --name-only HEAD
git diff --name-only --cached
```

Read every changed file.

Read relevant tests.

## 3. Validate

Validate all changes against:

- `CODING_STANDARDS.md`
- `client/RULES.md`
- `server/RULES.md`

Focus on:

- Architecture
- SOLID
- DI
- Provider tiers
- State mutation
- Lifecycle requirements
- Error handling
- Testing requirements
- Style guide compliance

Always cite:

- File
- Line range
- Rule reference

## 4. Auto-Fix

Auto-fix only if:

- Mechanical
- Safe
- Behavior unchanged
- No architectural decision required

Examples:

- Imports
- Formatting
- Explicit typing
- Logging prefixes
- Groovy property syntax
- Elvis/safe-navigation conversions

Do NOT auto-fix:

- Architecture
- DI redesign
- Provider redesign
- State ownership
- Lifecycle changes

Report those instead.

## 5. Build Validation

Run when available:

```bash
npm run check-types
npm run lint
npm run build:server
cd server && ./gradlew test
```

Follow testing workflow from `CODING_STANDARDS.md`.

## 6. Anti-Hallucination Rules

Never claim:

- A file was modified unless modified.
- A fix occurred unless code changed.
- A test passed unless executed.
- A build passed unless executed.
- A file was reviewed unless read.

Use `NOT VERIFIED` when verification is not possible.

## 7. Report

```text
## Review Report

### AUTO-FIXED
- [file:line-range] [rule] Fix

### CRITICAL
- [file:line-range] [rule] Issue

### SUGGESTIONS
- [file:line-range] Improvement

### PASSED
- Correct items

Build:
Client: PASS | FAIL | NOT VERIFIED
Server: PASS | FAIL | NOT VERIFIED

Summary:
X fixed, Y critical, Z suggestions

Verdict:
PASS | PASS WITH FIXES | BLOCK
```
