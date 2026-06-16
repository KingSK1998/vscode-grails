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

## SOLID — Mapped to This Codebase

> SOLID is not abstract theory here. Every principle maps to a concrete pattern agents break.
> When you see a violation, name the principle — it makes the fix obvious.

### S — Single Responsibility

**One unit, one reason to change.**

| Context | Rule | Common violation |
|---------|------|-----------------|
| Client service | Each service owns ONE concern (see `client/RULES.md §4`) | Adding config reading to `GradleService` |
| Client command | Commands only bind a command ID to a UseCase — zero logic | Putting `if (!workspaceFolders)` in a command handler |
| Server T1 provider | Provider only reads/transforms AST for one LSP feature | Adding file tracking side-effects inside a hover provider |
| Server T2 utility | Static methods, pure input→output, zero state | Adding a cache field to a `*Utils` class |

### O — Open/Closed

**Extend to add behaviour. Never modify existing core classes to add a feature.**

```ts
// Client ✅ — add a new service, not modify ServiceContainer internals
export class NewFeatureService implements Disposable { ... }
// Wire it in ServiceContainer — that's the one allowed modification point

// Server ✅ — add a new provider extending BaseProvider
class GrailsNewFeatureProvider extends BaseProvider { ... }
// Register in ProviderRegistry — that's the one allowed modification point
```

```ts
// ❌ — modifying GrailsService write path to add a feature
GrailsService.compileAndVisitAST() {
    // ... adding new logic here ...   ← wrong, creates regression risk
}
```

### L — Liskov Substitution

**Implement the full contract. Never stub or skip interface methods.**

```ts
// Client ✅ — all Disposable implementors actually dispose their resources
dispose(): void {
    if (this._disposed) { return; }
    this._disposed = true;
    this._subscription?.dispose();
}

// ❌ — stub implementation that leaks
dispose(): void { this._disposed = true; }  // subscriptions never released
```

```groovy
// Server ✅ — all long-running handlers implement the full token + health contract
// ❌ — provider that extends BaseProvider but skips createCancellationToken
//       It compiles, it runs, but cancellation is broken for that feature
```

### I — Interface Segregation

**Inject only what you actually use. Never pass the whole composition root.**

```ts
// Client ✅ — inject the two services you need
constructor(
    private readonly gradleService: GradleService,
    private readonly errorService: ErrorService
) {}

// ❌ — inject ServiceContainer (hides real dependencies)
constructor(private readonly container: ServiceContainer) {}
//          ↑ what does this service actually use? Nobody knows.
```

```groovy
// Server ✅ — ProviderContext / CompilationContext / ProjectContext (interface segregation)
GrailsHoverProvider(ProviderContext pc, CompilationContext cc, ProjectContext prj)

// ❌ — passing full GrailsService when provider only needs visitor + config
GrailsHoverProvider(GrailsService service)   // old pattern — ISP violation
```

### D — Dependency Inversion

**Depend on abstractions. Inject from outside. No `new` inside a service.**

```ts
// Client ✅ — constructor injection, tested via mock
constructor(private readonly gradleService: GradleService) {}

// ❌ — constructing dependency internally (untestable, unconfigurable)
class RunGrailsAppUseCase {
    private gradle = new GradleService();  // ← hard-coded, breaks DI
}
```

```groovy
// Server ✅ — BaseProvider receives contexts via constructor injection
//             (GrailsService implements all three — ProviderRegistry wires it)

// ❌ — provider reaching into a global or calling GrailsService.getInstance()
class GrailsHoverProvider {
    def visitor = GrailsService.INSTANCE.visitor  // ← global coupling
}
```

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
- **Comments explain why, not what.** Add/update proper comments to the modified code.
- **No magic values.** Constants over literals; enums over stringly-typed flags.
- **Use Groovy coding style not Java.** Use Groovy idioms, dynamic features appropriately, and standard Groovy libraries over Java equivalents where applicable.

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

> Full server testing rules: `server/RULES.md §17`.
> This section is the cross-cutting baseline.

### The Pyramid — Prefer Fast Tests

```
         ┌─────────────────────────┐
         │   Integration Tests     │  ← BaseLspSpec (slow, expensive, ClassGraph scan)
         │  (full LSP lifecycle)   │     Use ONLY when you need GrailsService running
         ├─────────────────────────┤
         │      Unit Tests         │  ← Pure Spock Specification (fast, ms)
         │  (pure functions)       │     Default choice for all T2 utilities + algorithms
         └─────────────────────────┘
```

**Rule**: If you can test something without a running `GrailsService`, you MUST test it that way.

### Fix a Bug → Write a Failing Test First

This is mandatory, not optional:

1. Write the smallest test that **fails** and proves the bug
2. Confirm it fails — `gradlew test --tests "...SpecName"`
3. Fix the code
4. Confirm the test passes
5. Done

No test = fix is not complete.

### When to Add a Test

| Situation | Action |
|-----------|--------|
| Bug fixed in utility method | New `where:` row reproducing the broken case |
| Bug fixed in provider logic | New `when/then` or `where:` case in provider spec |
| New utility method added | New unit spec covering happy path + nulls + edge cases |
| New provider feature | New integration test in `BaseLspSpec` subclass |
| Refactor, no behaviour change | Run existing tests — no new tests unless coverage is missing |

### Test Naming

```groovy
// ✅ — observable behaviour
def "should return empty list when no overloads found"() { ... }
def "should use 0-based index for active parameter"() { ... }

// ❌ — code paths, not behaviours
def "test getActiveParameter"() { ... }
def "null check test"() { ... }
```

### Data-Driven Tests — `where:` Tables for Multiple Inputs

```groovy
// ✅ — one test, many cases
def "should compute offset correctly"() {
    expect:
    PositionHelper.getOffset(content, pos) == expected

    where:
    content         | pos              || expected
    "abc\ndef"      | new Position(0,0)|| 0
    "abc\ndef"      | new Position(1,0)|| 4
    null            | new Position(0,0)|| -1
}

// ❌ — one test per input (bloat)
def "returns 0 for line 0"() { ... }
def "returns 4 for line 1"() { ... }
```

### Integration Test — Use `ProjectType.DUMMY` for Single-File Tests

`ProjectType.GRAILS` / `ProjectType.GROOVY` trigger a full ClassGraph workspace scan (slow).
`ProjectType.DUMMY` creates a minimal temp workspace — use it for all single-file provider tests.

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

---

## Code Style — Quick Reference Enforcer

> Full rules live in component-level RULES.md files.
> This section is the **cross-cutting baseline** — both languages must satisfy it.
> When in doubt: read the full rules file for your component.
>
> - Server (Groovy): `server/RULES.md §10` — full Groovy style guide
> - Client (TypeScript): `client/RULES.md §25` — full TypeScript style guide

---

### Imports — Universal Rule

**Groovy** — threshold-based (matches IntelliJ optimizer defaults):

| Package | Rule |
|---------|------|
| `kingsk.grails.lsp.*` (project-internal) | **Always explicit** — wildcards hide dependency contracts |
| External libraries (`org.eclipse.lsp4j.*`, `org.codehaus.groovy.ast.*`) | Explicit when <5 classes from package. **Wildcard OK at 5+** |
| `groovy.*` runtime | Always explicit (rarely >3 imports) |
| Inline FQCNs | **Never** — regardless of package |

Full reference: `server/RULES.md §10.1`

```groovy
// Groovy ✅ — internal: always explicit
import kingsk.grails.lsp.context.CompilationContext
import kingsk.grails.lsp.context.ProviderContext

// Groovy ✅ — external with 5+ types: wildcard fine
import org.eclipse.lsp4j.*

// Groovy ❌ — NEVER wildcard on internal packages
import kingsk.grails.lsp.context.*

// Groovy ❌ — inline FQCN always wrong
GrailsHoverProvider(kingsk.grails.lsp.context.ProviderContext ctx, ...)
```

**TypeScript** — named only, grouped, no namespace imports:

Full reference: `client/RULES.md §25.1`

```ts
// TypeScript ✅
import { Position, Range, Location } from 'vscode-languageclient/node';
import type { GrailsLspConfig } from '../shared/protocol/types';

// TypeScript ❌
import * as lsp from 'vscode-languageclient/node';    // namespace import
import { Position } from '...';
import { Range }    from '...';                         // same module — collapse to one statement
```

---

### Collections

```groovy
// Groovy ✅ — literals
def items = []
def map = [:]
def set = [] as Set

// Groovy ❌ — Java constructors
def items = new ArrayList<>()
def map = new HashMap<>()
```

```ts
// TypeScript ✅ — spread for immutable ops
const updated = [...existing, newItem];
const merged  = { ...defaults, ...overrides };

// TypeScript ❌ — mutation in-place when a new collection is intended
existing.push(newItem);   // if existing is shared state
```

---

### Null Safety

```groovy
// Groovy ✅
def label = node?.name ?: "unknown"
nodes?.each { process(it) }

// Groovy ❌
if (node != null) { label = node.getName() }
```

```ts
// TypeScript ✅
const timeout = config.timeout ?? 5000;    // nullish coalescing, not ||
const name = document?.fileName ?? 'unknown';

// TypeScript ❌
const timeout = config.timeout || 5000;    // falsy-based, breaks for 0
this._client!.stop();                       // non-null assertion hides bugs
```

---

### String Formatting

```groovy
// Groovy ✅
log.debug("[HOVER] Node at ${uri}:${position.line}")

// Groovy ❌
log.debug("[HOVER] Node at " + uri + ":" + position.line)
```

```ts
// TypeScript ✅
const msg = `[HOVER] Node at ${uri}:${position.line}`;

// TypeScript ❌
const msg = '[HOVER] Node at ' + uri + ':' + position.line;
```

---

### Async / Concurrency

```groovy
// Groovy — every long-running LSP handler MUST use:
// 1. createCancellationToken(uri) at entry
// 2. CompletableFuture.supplyAsync { ... } with explicit return
// 3. checkCancellation(token) at yield points
// 4. recordHealth(...) in finally
// See: server/RULES.md §16
```

```ts
// TypeScript — always async/await, errors always via ErrorService
// Never: .then()/.catch() chains, console.error, implicit fire-and-forget
// See: client/RULES.md §25.4, §25.11
```

---

### Error Handling

| Language | Rule |
|----------|------|
| Groovy | Never throw raw exceptions from LSP handlers. Return partial results or log + recover. |
| TypeScript | All errors through `errorService.handleError()`. No `console.error`, no swallowing. |

---

### Static Typing

| Language | Rule |
|----------|------|
| Groovy | `@CompileStatic` on **every** class. Never remove to silence type errors — fix the types. |
| TypeScript | `strict: true`. No `any` — use `unknown` and narrow. Explicit return types on all public methods. |

---

### Property / Member Access

```groovy
// Groovy ✅ — Groovy property syntax
config.codeLensMode    node.name    method.parameters

// Groovy ❌ — explicit getter calls
config.getCodeLensMode()    node.getName()    method.getParameters()
```

```ts
// TypeScript ✅ — optional chaining
config?.codeLensMode

// TypeScript ❌ — non-null assertion
config!.codeLensMode
```

---

### Logging — Structured Prefixes (Both Languages)

Every log line carries a bracketed subsystem prefix for grep-ability:

```groovy
log.debug("[HOVER] No node at ${uri}")          // Groovy
log.info("[SIGNATURE] ${information.size()} overloads found")
```

```ts
this.logger.debug(`[HOVER] No node at ${uri}`);   // TypeScript
this.logger.info(`[SIGNATURE] ${items.length} overloads found`);
```

Never: bare `log.debug("done")` or `console.log(...)`.

---