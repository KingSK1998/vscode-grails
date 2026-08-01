# Skill: Architecture Review

Verify required KB updates for code changes.

If project standards are not already available in context, load the applicable standards defined in AGENTS.md §7.

## Process

1. Consult `docs/architecture/change-triggers.md` for the trigger matrix.
2. Cross-reference changed files against triggers.
3. Report missing KB updates.

## Report

```text
## Architecture Review Report

KB UPDATES: [file] [section] [CURRENT | NEEDS UPDATE: reason]

Verdict: PASS | INCOMPLETE [missing updates]
```
