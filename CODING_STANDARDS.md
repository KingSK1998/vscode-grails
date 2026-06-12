# CODING STANDARDS
> Version: 1.0.0 | Status: MANDATORY
> These rules apply to all AI agents and human contributors. ZERO EXCEPTIONS.

## Architecture

### Client
```
Command → UseCase → Service(s)
```
- Commands contain no logic. Delegate 100% to a UseCase.
- Services are registered in `ServiceContainer` only. Never instantiated manually.

### Server
- Providers are retrieved via `ProviderRegistry` only.

| Tier        | Can access                                            | Cannot                                          |
| ----------- | ----------------------------------------------------- | ----------------------------------------------- |
| T1 Provider | `compiler`, `visitor`, `fileTracker`, `GrailsService` | Cache mutable state locally                     |
| T2 Utility  | Input parameters only. Pure functions.                | Hold state. Call T1. Reference `GrailsService`. |
| T3 Module   | Constructor-injected dependencies                     | Depend on other T3. Access `ProviderRegistry`.  |

---

## Source of Truth
```
Compiler → AST → Project Metadata → Dependencies
```
Avoid hardcoded lists, regexes, and heuristics. Real project state wins.

---

## Code Quality

- **Name intent, not implementation.** If a name needs a comment, rename it first.
- **One responsibility per unit.** A method that needs a paragraph comment is two methods.
- **Static typing first.** Dynamic dispatch requires a written justification.
- **No dead code.** Delete it — version control remembers.
- **Avoid deep nesting.** Return early. Extract conditions into named predicates.
- **Comments explain why, not what.**
- **No magic values.** Constants over literals; enums over stringly-typed flags.

---

## Performance

- Incremental updates over full rescans.
- Shared state and caches belong in `GrailsService`.
- Every cache requires an invalidation strategy.
- Never duplicate compiler, AST, or index data.
- Release resources when no longer needed.

---

## Concurrency

- LSP handlers run concurrently.
- Shared mutable state lives only in `GrailsService`.
- Prefer immutable data. Return new collections over mutating in place.

---

## Error Handling

- Client: all errors go through `errorService.handleError()`.
- Server: never throw raw exceptions from LSP handlers. Return partial results or `ResponseError`.
- Swallowed errors must be logged at WARN minimum before suppression.

---

## Testing

- Reproduce bugs with a failing test before fixing.
- Test names state behavior, not implementation detail.
- Read existing tests before modifying a module.

---

## AI Workflow

Before changing code:
1. Read the implementation.
2. Read the tests.
3. For bugs — reproduce with a failing test first.
4. Build the affected component after changes.
5. Do not edit generated files or lock files.

### Investigating Test Failures

When investigating test failures, do not repeatedly run `gradlew test`. First gather evidence by running:

```powershell
.\gradlew test --info 2>&1 | Select-String -Pattern "PASS|FAIL|ERROR|BUILD|tests completed" | Select-Object -Last 30
```

Then run:

```powershell
.\gradlew test --stacktrace
```

Inspect relevant implementation files referenced by the failure (e.g. `grep -n "ClassGraph" server/src/main/groovy/kingsk/grails/lsp/services/DiscoveryService.groovy`).

Inspect XML test reports:

```powershell
Get-ChildItem -Path "server\build\test-results\test" -Filter "*.xml" | Get-Content | Select-String -Pattern "<failure" -Context 3,0
```

Before modifying code, identify the exact failing test, exception, assertion, and root cause. Do not propose speculative fixes.

Prefer targeted commands (`--tests`) over full test suite execution. Do not run `clean build` or `clean test` unless explicitly requested. Do not rerun the same Gradle command without code changes or new diagnostic information. After fixing an issue, validate with the affected test(s) first before running broader validation.

---

## Conflicts

Local `RULES.md` wins for component-local concerns.
This file wins for architecture and cross-cutting standards.