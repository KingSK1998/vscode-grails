# CLAUDE.md — Claude Code Agent Config
> Thin wrapper. All project context lives in `AGENTS.md`.

Read `AGENTS.md` first — it is the single source of truth for all agents.

## Claude-Specific

### Agent Skills
Located in `docs/skills/`:
- `architect.md` — pre-feature planning
- `review.md` — post-implementation validation + UI pattern capture
- `recover.md` — diagnose broken state

See `AGENTS.md` §7 (L160-L181) for when to use each.

### Graphify Integration
Knowledge graph at `graphify-out/`. Before architecture/codebase questions:
1. Read `graphify-out/GRAPH_REPORT.md` for god nodes and community structure
2. If `graphify-out/wiki/index.md` exists, navigate it instead of raw files
3. After modifying code, run `graphify update .` to keep graph current

### Hooks (.claude/settings.json)
Pre-tool hook on Glob/Grep: checks if graphify graph exists, suggests reading GRAPH_REPORT.md first.

### Key Line References
- Build commands: `AGENTS.md` §4 (L81-L103)
- Mandatory rules: `AGENTS.md` §5 (L104-L137)
- Tool selection: `AGENTS.md` §6 (L138-L159)
- Client rules: `client/RULES.md` (400 lines, §0-§24)
- Server rules: `server/RULES.md` (433 lines, §0-§16)
- Coding standards: `CODING_STANDARDS.md` (119 lines)
- Full reference index: `AGENTS.md` §8 (L182-L207)
