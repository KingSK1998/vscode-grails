# Skill: Architect
> Agent-agnostic skill. Any AI agent (Claude, Gemini, Copilot, Codex) can run this.
> Target: Pre-feature planning to identify gaps and generate an implementation plan.

## Step 1 — Load Context

Read in order (skip missing):
- `AGENTS.md` §1-§5 (L8-L140) — project, structure, architecture, builds, rules
- `CODING_STANDARDS.md` (119 lines)
- `client/RULES.md` §0-§3 (L1-L105) if client work
- `server/RULES.md` §0-§4 (L1-L178) if server work
- `client/STATUS.md` — current client state
- `server/STATUS.md` — current server state
- `MEMORY.md` — prior decisions (if exists)
- `graphify-out/GRAPH_REPORT.md` — dependency structure

## Step 2 — Extract

- What is being built/changed?
- Components: Client (TS) / Server (Groovy) / Shared / Resources
- Server: which provider tier? (see `AGENTS.md` §3 L48-L79)
- Client: which layer? Command → UseCase → Service (see `client/RULES.md` §3 L73-L105)
- Existing code paths affected

## Step 3 — Gap Analysis

Classify gaps: **BLOCKING** (can't proceed) / **IMPORTANT** (has default) / **COSMETIC** (safe default).
Surface only BLOCKING + IMPORTANT.

## Step 4 — Ask (max 5 questions)

```
Before generating plan, I need [N] things:

1. [BLOCKING] <question>
   → Options: A) ... B) ...

2. [IMPORTANT] <question>
   → Default: X
```

Wait for answers.

## Step 5 — Generate Plan

```
## Implementation Plan: [Feature]

### Component: Client / Server / Both
### Files: CREATE [path+purpose] | MODIFY [path+changes] | NOT TOUCH [path+reason]

### Approach
[2-3 sentences. Reference UseCase pattern, provider tier, ServiceContainer as applicable]

### Steps
1. [File + action]
2. ...

### Validation
- Client: npm run compile → check-types → lint
- Server: npm run build:server → cd server && ./gradlew test

### Rules Applied
- [Cite: server/RULES.md §N LXX or CODING_STANDARDS.md §N]

### Out of Scope
### Risks (check STATUS.md for 🔴/🟡 items)
```

## Rules

- NEVER write code. Plan only.
- Cite rules with file + section + line range.
- If STATUS.md shows 🔴/🟡, flag as risk.
- Plan must be executable by another agent without questions.
