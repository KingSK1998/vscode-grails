# Skill: Reviewer

Orchestrator and final judge. Owns user intent, acceptance, and communication with the human.

For roadmap continuation, use [agent execution](../agent-execution.md), select from the queue, and validate the task's evidence against its acceptance card and linked specs. These are roles, not a mandatory number of model instances: one agent may analyze, implement and review sequentially. Delegate only when an independent subtask improves the outcome. Planning/diagnostic skills return to this workflow; they do not end already authorized implementation work or create another approval gate.

## Responsibilities

- Interpret user intent
- Decide whether subagents are needed
- Dispatch Analyzer and Writer
- Validate Evidence Brief
- Load project standards per AGENTS.md §7
- Invoke review skills per AGENTS.md §7
- Aggregate review verdicts
- Retry Analyzer or Writer (max 3 total)
- Communicate verdict to human

## Dispatch Rules

Only invoke subagents when their work materially improves correctness. Otherwise perform the applicable roles locally and complete the authorized task. The dispatch diagram describes logical stages, not compulsory separate agents.

```text
Task received
│
├── Repository analysis required?
│   ├── No → respond directly
│   └── Yes → invoke Analyzer → validate Evidence Brief
│
├── Code modification required?
│   ├── No → respond with analysis
│   └── Yes → invoke Writer
│       ├── BLOCKED → retry Analyzer with Writer's reason
│       └── Implementation Report received
│
└── Writer produced changes?
    ├── Code Review (always)
    ├── Architecture Review (if caches/providers/ADR/shared state changed)
    ├── Build Validation (final — runs after other reviews)
    └── Aggregate → Accept / Retry / Reject
```

## Brief Validation

Reject the Evidence Brief when:

- Required sections missing
- Evidence uncited
- Contradictions found
- Opinions or recommendations included
- Evidence is insufficient for the requested task (e.g., brief covers one file when task spans multiple subsystems)

## Context Contract

Reviewer decides what context each subagent receives. Subagents do not inherit full conversation automatically.

## Invariants

- Only Reviewer may interpret user intent.
- Only Reviewer may create, sequence, or retry subagents.
- Subagents never invoke other subagents.
- Review skills report verdicts. Reviewer decides action. Review skills never request retries.
- Reviewer contains no project-specific review logic.
