# AGENTS.md — Single Source of Truth
> ALL AI agents (Claude, Gemini, Copilot, Codex) read this file FIRST.
> Agent-specific wrappers: `CLAUDE.md`, `GEMINI.md` — thin pointers only.
> Version: 2.0 | Last updated: 2026-06-12

---

## §1 PROJECT IDENTITY (L8-L21)

**vscode-gng-support** — VS Code extension for Grails 7+ / Groovy 4.
Client-server architecture over LSP (stdio JSON-RPC).

| Component | Language | Entry Point | Rules |
|-----------|----------|-------------|-------|
| Client | TypeScript | `client/src/extension.ts` | `client/RULES.md` |
| Server | Groovy 4.0.23 | `server/src/.../GrailsLanguageServer.groovy` | `server/RULES.md` |
| Shared | TS/Groovy | `shared/schemas/`, `shared/configs/` | — |
| Resources | TextMate/CSS | `resources/syntaxes/`, `resources/styles/` | — |

---

## §2 FOLDER STRUCTURE (L22-L47)

```
ROOT/
├── client/src/                  # TS extension (see client/RULES.md §20 L325-L355)
│   ├── core/container/          # ServiceContainer, activation
│   ├── services/                # Domain services (one concern each)
│   ├── features/                # dashboard, dependency-graph, gorm-sql-preview
│   ├── ui/commands/             # Thin command delegates → UseCases
│   └── extension.ts             # Activation entry
├── server/src/main/groovy/kingsk/grails/lsp/   # (see server/RULES.md §14 L358-L387)
│   ├── GrailsService.groovy     # Composition root
│   ├── providers/document/      # T1 document providers
│   ├── providers/workspace/     # T1 workspace providers
│   ├── utils/                   # T2 static utilities (10 subfolders)
│   └── model/                   # Data models (4 subfolders)
├── shared/                      # Schemas, configs, templates
├── resources/                   # Syntaxes, snippets, icons, styles
├── docs/                        # Architecture, guides, skills
│   ├── architecture.md          # Overview (106 lines — read §57-63 for provider tiers)
│   └── skills/                  # Agent-agnostic skill files
└── graphify-out/                # Knowledge graph (read GRAPH_REPORT.md before grep)
```

---

## §3 ARCHITECTURE QUICK-REF (L48-L80)

### Client Pattern
```
Command → UseCase → Service(s) → VS Code API / LSP
```
- Commands: ZERO logic, delegate to UseCase
- Services: ONE concern, registered in `ServiceContainer` only
- All errors → `ErrorService.handleError()`
- All services implement `Disposable`
- Full rules: `client/RULES.md` §1-§6 (L1-L140)

### Server Pattern
```
LSP request → GrailsTextDocumentService → Provider (T1) → reads GrailsService state
```
- `GrailsService` = composition root. Shared mutable state is INTENTIONAL.
- Providers NEVER write to `visitor`, `compiler`, `fileTracker`
- `@CompileStatic` on ALL classes
- Full rules: `server/RULES.md` §0-§4 (L1-L120)

### Provider Tiers (server)

| Tier | Pattern | Inject | Example |
|------|---------|--------|---------|
| T1 | extends `BaseProvider` | `GrailsService` | Completion, Hover, Diagnostics |
| T2 | static utility | nothing | `GrailsUtils`, `CompletionUtil` |
| T3 | plug-n-play | only what's needed | `GrailsYamlIntelligenceProvider(config)` |

T1 providers MUST: `createCancellationToken(uri)` at entry, `checkCancellation(token)` at yield points, `recordHealth(latencyMs, success)` on exit. See `server/RULES.md` §15-§16 (L391-L433).

---

## §4 BUILD COMMANDS (L81-L103)

| Task | Command | When |
|------|---------|------|
| Client compile | `npm run compile` | After TS changes |
| Client types | `npm run check-types` | Verify types |
| Client lint | `npm run lint` | Before commit |
| Client bundle | `npm run bundle` | Production |
| Server build | `npm run build:server` | After Groovy changes |
| Server test | `cd server && ./gradlew test` | After server changes |
| Server copy | `npm run copy-server` | Update bundled JAR |
| Full build | `npm run build` | Both components |
| Watch mode | `npm run watch` | Dev loop |

### Validation Sequence
- **Client**: `npm run compile` → `npm run check-types` → `npm run lint`
- **Server**: `npm run build:server` → `cd server && ./gradlew test`

### Prerequisites
Node.js 20+ · JDK 17+ · Gradle 7+

---

## §5 MANDATORY RULES (L104-L137)

### Status Tracking — Enforced by Pre-commit Guard
After EVERY task touching client or server code:
1. Update `client/STATUS.md` or `server/STATUS.md`
2. Feature row: ⬜ → 🟡 → ✅ (or 🔴 if broken)
3. Add new features as rows; regressions to Known Issues
4. Update `Last updated` date

**Guard**: `scripts/pre-commit-guard.js` blocks commits if code changed but STATUS.md not staged.
Install: `node scripts/install-hooks.js`. Skip: `git commit --no-verify`.
Stale STATUS.md = stale agent context = worse AI output.

### Session Memory
At session end, update or create `MEMORY.md` (project root):
- What was done, decisions made, patterns established
- Open items, blockers, known fragile areas
- Next session priorities
- Merge with existing content — never overwrite prior decisions

### Repetition → Automate
If you are repeating a task, failing repeatedly, or going back-and-forth on same work:
1. **3+ manual repetitions** → create a script in `scripts/` to do it in one step
2. **Cross-session pattern** → create a skill in `docs/skills/` so any agent can reuse
3. **Complex multi-step workflow** → create a subagent in `.claude/agents/`

Priority: script (fastest) → skill (reusable) → subagent (reasoning-heavy).
Never repeat manually what a script can guarantee.

### Cleanup
Prefix temporary files with `tmp_rovodev_` — delete on task completion.

---

## §6 TOOL SELECTION (L138-L159)

> Auto-select tools. Don't ask permission for obvious operations.

| Intent | Do | Don't |
|--------|----|-------|
| "find files" | `Glob` immediately | "Would you like me to search?" |
| "search for X" | `Grep` for code, web search for docs | "I can look that up" |
| "where is X defined?" | `Grep` → `Read` | Guess from file names |
| "check/run/build" | Execute build command | "Shall I run that?" |
| "analyze codebase" | Read `graphify-out/GRAPH_REPORT.md` first | Grep blindly |

### Priority Order
1. Skills/plugins explicitly named → invoke immediately
2. `graphify-out/GRAPH_REPORT.md` → read before architecture questions
3. Built-in search (Grep/Glob) → for code patterns
4. File operations (Read/Edit) → for file tasks
5. Execution (Bash/terminal) → for commands
6. Ask user → only when truly ambiguous

---

## §7 SKILLS INDEX (L160-L181)

### In-repo Skills (docs/skills/)

| Skill | File | When |
|-------|------|------|
| Architecture & Performance | `docs/skills/architecture-perf.md` | Designing/reviewing perf-sensitive code |
| Architect (Planning) | `docs/skills/architect.md` | Pre-feature planning, gap analysis, generating implementation plans |
| Review (Validation) | `docs/skills/review.md` | Post-implementation validation, quality checks, UI pattern capture |
| Recover (Diagnostics) | `docs/skills/recover.md` | Diagnosing build/test failures, loops, or environment issues |

### Session Flow
```
1. Start  → read MEMORY.md + STATUS.md files
2. Plan   → Use `docs/skills/architect.md` (before complex changes)
3. Build  → Normal development loop
4. Check  → Use `docs/skills/review.md` (verify rules, builds, capture UI patterns)
5. End    → Update MEMORY.md + STATUS.md (§5 rules)
```

---

## §8 KEY REFERENCES (L182-L207)

| What | Where | Key Lines |
|------|-------|-----------|
| Client architecture rules | `client/RULES.md` | §0 Golden Rule L8-L13, §2 ServiceContainer L34-L70, §3 UseCases L73-L105 |
| Client folder structure | `client/RULES.md` | §20 L325-L355 |
| Client services list | `client/RULES.md` | §4 L107-L126 |
| Client anti-patterns | `client/RULES.md` | §21 L363-L370 |
| Server architecture rules | `server/RULES.md` | §0 Golden Rule L7-L14, §2 GrailsService L31-L81, §3 Tiers L84-L120 |
| Server package structure | `server/RULES.md` | §14 L358-L387 |
| Server BaseProvider | `server/RULES.md` | §4 L124-L178 |
| Server concurrency | `server/RULES.md` | §11 L325-L333 |
| Provider tiers quick-ref | This file | §3 L48-L79 |
| Build commands | This file | §4 L81-L101 |
| Architecture overview | `docs/architecture.md` | Full file (106 lines) |
| Runtime flow | `docs/architecture.md` | L5-L18 |
| Provider tiers detail | `docs/architecture.md` | L57-L63 |
| Feature ownership | `docs/architecture.md` | L67-L82 |
| Caching strategy | `docs/architecture.md` | L83-L91 |
| Coding standards | `CODING_STANDARDS.md` | Full file (119 lines) |
| Client status | `client/STATUS.md` | Check before modifying client |
| Server status | `server/STATUS.md` | Check before modifying server |
| Knowledge graph | `graphify-out/GRAPH_REPORT.md` | Read before architecture questions |

---

## §9 CROSS-CUTTING STANDARDS (L208-L221)

From `CODING_STANDARDS.md` (read full file for details):

- **Source of truth**: Compiler → AST → Project Metadata → Dependencies. No hardcoded lists.
- **Naming**: intent, not implementation. If name needs comment, rename.
- **One responsibility** per unit. Method needing paragraph comment = two methods.
- **Static typing first**. Dynamic dispatch needs written justification.
- **No dead code**. Delete it — VCS remembers.
- **No magic values**. Constants over literals; enums over stringly-typed flags.
- **Error handling**: Client → `ErrorService`. Server → never throw raw exceptions from handlers.
- **Test first**: Reproduce bugs with failing test before fixing.
- **Conflicts**: Local `RULES.md` wins for component concerns. `CODING_STANDARDS.md` wins for cross-cutting.
