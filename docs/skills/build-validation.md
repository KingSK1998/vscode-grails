# Skill: Build Validation

Execute repository validation for changed components.

## Input

- Changed files (from Reviewer)
- Validation commands (from AGENTS.md §4)

## Process

Run validation commands for affected components. Report results.

## Report

```text
## Build Validation Report

| Component | Command | Result |
|-----------|---------|--------|

Verdict: PASS | FAIL [failing commands]
```

## Rules

- PASS requires execution. SKIPPED for unaffected components.
- Include error output for FAIL.
