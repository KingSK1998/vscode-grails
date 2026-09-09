# SERVER ARCHITECTURE RULES
> Groovy 4 · LSP4J · GrailsService composition root  
> Any AI agent working on server code MUST read this first.

---

## 0. GOLDEN RULE

> **LSP server = single process, single instance, tight pipeline.**
>
> `GrailsService` is the composition root. Per-project `ProjectContextImpl` owns
> compiler, visitor, index/publication and lifecycle state; other service state
> has one designated owner in `docs/invariants.md`. Providers never become writers.
> See ADR-009 for the correction to older single-field-owner diagrams.
>
> Project contexts, VersionedSnapshot and scheduling code exist, but full isolation,
> coherent publication and classloader reclamation are not certified. Follow
> `docs/agent-execution.md`, the task queue and `docs/state-and-lifecycle-specification.md`.
> Phase completion labels are historical, not passing acceptance evidence.

---

## 0.1 PHASE 2 AST BOUNDARY RULE (PROJECTINDEX)

> **No ASTNode reference of any kind may exist inside ProjectIndex, SymbolInfo, or MethodScopeCache after construction. All data must be extracted and serialized to primitive/LSP types at index-build time.**

Providers query `ProjectIndex` and `MethodScopeCache` and receive `SymbolInfo` or `ReferenceInfo`. They must NEVER receive an `ASTNode`.
This boundary prevents index values from retaining compiler nodes. It does not by itself prove concurrent snapshot safety or responsive readers; R1-04 verifies those properties separately.

---

## 1. STACK

| Layer | Technology |
|---|---|
| Language | Groovy 4.0.23 |
| Static compilation | `@CompileStatic` on ALL classes — **no exceptions**. If `@CompileStatic` causes a type error, fix the types. Never remove the annotation. |
| LSP framework | LSP4J 0.23.1 |
| Build tooling | Gradle 8.x, Tooling API 8.12 |
| Testing | Spock Framework |
| JDK | 17+ |

---

## 2. GRAILSSERVICE — COMPOSITION ROOT

`GrailsService` is the **single wiring point**. It is NOT a service locator passed around carelessly.

```
GrailsService  ← composition root
  implements ProviderContext
  ├── workspaceManager      WorkspaceManager (owns registered project contexts)
  │    └── ProjectContextImpl implements ProjectContext, CompilationContext
  │         ├── compiler, visitor, projectIndex, indexManager
  │         └── lifecycle, locks, snapshotManager, scoped caches
  ├── errorService          ErrorService
  ├── discoveryService      DiscoveryService
  ├── gradle                GradleService
  ├── fileTracker           FileContentTracker
  ├── progressService       ProgressService
  ├── diagnostics           GrailsDiagnosticService
  ├── document              GrailsTextDocumentService    ← LSP entry point
  ├── workspace             GrailsWorkspaceService       ← LSP entry point
  ├── dependencyProvider    GrailsDependencyProvider
  ├── gormSqlProvider       GrailsGormSqlProvider
  ├── testDiscoveryProvider GrailsTestDiscoveryProvider
  ├── cancellationService   CancellationService          ← added SERVER-017
  ├── healthService         ProviderHealthService        ← added SERVER-016
  ├── providerRegistry      ProviderRegistry             ← added SERVER-014
  └── config                GrailsLspConfig
```

### WRITE PATH — sacred, untouchable by providers

Only designated owners may mutate their state. Current project write entry points include:

```groovy
ProjectContextImpl.compileAndVisitAST(TextFile)
ProjectContextImpl.visitAST(TextFile)
ProjectContextImpl.commitSnapshot()
ProjectContextImpl.closeDocument(uri)
// Activation, Gradle refresh, hibernate and dispose are also owned lifecycle writes.
// Document callbacks delegate buffer writes to FileContentTracker.
```

**Providers NEVER write to `visitor`, `compiler`, or `fileTracker`. EVER.**

### READ PATH — captured, coherent and responsive

Providers use the context captured for a request. Do not retain mutable references across requests or mix a captured view with newer live getters. The following accesses require coherent revision handling; they are not automatically safe simply because they are getters:

```groovy
snapshot.ast.getClassNodes(uri)    // captured generation; actual accessor API must be verified
fileTracker.getContent(uri)        // current overlay may be NEWER than that generation
config.codeLensMode               // capture consistent settings for this request as needed
```

---

## 3. THREE-TIER CLASSIFICATION

**Classify EVERY class before writing code.**

### Decision checklist

Ask for each class:

- Q1: Does it use `service.visitor` at runtime?
- Q2: Does it use `service.compiler` at runtime?
- Q3: Does it use `service.fileTracker` at runtime?

```
Any YES  →  TIER 1  (extends BaseProvider)
All NO, pure function  →  TIER 2  (static utility)
All NO, own lifecycle  →  TIER 3  (plug-n-play module)
```

### TIER 1 — Extends BaseProvider, takes context interfaces

Examples: `GrailsCompletionProvider`, `GrailsHoverProvider`, `GrailsDiagnosticService`, `GrailsGormSqlProvider`, `GrailsTestDiscoveryProvider`

### TIER 2 — Static utility, no dependencies

Examples: `GrailsUtils`, `DocumentationHelper`, `CompletionUtil`, `GrailsASTHelper`, `GrailsArtefactUtils`

ALL methods must be `static`. No constructor. No instance state.

### TIER 3 — Plug-n-play module, minimal own deps

Examples: `GrailsYamlIntelligenceProvider(GrailsLspConfig)`, `GrailsLspConfig`

Inject ONLY what is actually used — never full `GrailsService`.

### Special: Value/Context Objects

`CompletionRequest` is a request value record with coordinates/AST inputs and ProviderContext. It is not a provider or a license to retain live GrailsService/compiler access. Any AST inputs stay within a proven request generation/lease; do not store the record across requests.

---

## 4. BASEPROVIDER — CANONICAL FORM

Every TIER 1 class MUST extend BaseProvider. Current constructors receive
`(ProviderContext, WorkspaceManager)` from ProviderRegistry. `createRequestContext(uri)`
routes the project and captures its snapshot. ProjectContext/CompilationContext describe
project access behind that boundary; they are not the current provider constructor arguments.
Do not restore the obsolete `(service, service, service)` pattern.

```groovy
@Slf4j
@CompileStatic
class GrailsHoverProvider extends BaseProvider {

    // ✅ Context-aware constructor — standard for all providers
    GrailsHoverProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        super(providerContext, workspaceManager)
    }

    // Public LSP handler — async + cancellation + health
    CompletableFuture<Hover> hover(HoverParams params) {
        def token   = createCancellationToken(params.textDocument.uri)
        long startTime = System.nanoTime()
        def ctx = createRequestContext(params.textDocument.uri)
        return CompletableFuture.supplyAsync {
            boolean success = false
            try {
                checkCancellation(token)
                // ... bounded read using ctx.snapshot()/ctx.ast(); compute result ...
                checkCancellation(token)
                success = true
                return result
            } finally {
                recordHealth("HoverProvider", (System.nanoTime() - startTime).intdiv(1_000_000), success)
            }
        }
    }
}
```

**BaseProvider exposes configuration/health/cancellation/file tracking and request capture.** This skeleton illustrates those APIs, not a drop-in complete handler or proof that current request routing is nonblocking. R1-04 repairs/audits that boundary. Do not mix live project getters with a captured view:

```groovy
ctx.ast()        // captured ASTAccessor, when available
ctx.snapshot()   // captured VersionedSnapshot
fileTracker      // FileContentTracker
config           // GrailsLspConfig
diagnostics      // GrailsDiagnosticService
```

**Rules:**
- Context-aware constructor is the ONLY constructor pattern. `ProviderRegistry` wires it.
- `createCancellationToken(uri)` at entry of every long-running handler.
- `checkCancellation(token)` at every yield point inside loops.
- `recordHealth(name, latencyMs, success)` in `finally` of every public handler.
- **Never** add a `private final GrailsService service` field to a provider — that's the old pattern, it's gone.

---

## 5. PROVIDER ACCESS RULES

### Use Groovy property getters — NEVER `service.xxx` directly

```groovy
// WRONG ❌
service.visitor.getClassNodes(uri)   // old global access pattern
service.fileTracker.getContent(uri)
service.config.codeLensMode

// CORRECT ✅
ctx.ast()?.getClassNodes(uri)          // request-scoped accessor, not a live visitor
fileTracker.getContent(uri)           // current overlay; validate its revision before combining
config.codeLensMode                   // capture relevant settings consistently
```

### No duplicate field declarations

```groovy
// WRONG ❌ — copy-pasting a service field in every provider
class GrailsCodeLensProvider {
    private final GrailsService service   // don't do this — BaseProvider already manages this
}

// CORRECT ✅ — extend BaseProvider, use context-aware constructor
@Slf4j
@CompileStatic
class GrailsCodeLensProvider extends BaseProvider {
    GrailsCodeLensProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        super(providerContext, workspaceManager)
    }
}
```

### Constructor tells the truth

If constructor takes `GrailsService` but body only uses `service.config` → WRONG. Demote to TIER 3 and inject only `GrailsLspConfig`.

---

## 6. PROVIDER INITIALIZATION — VIA PROVIDERREGISTRY ONLY

All TIER 1 providers are created by `ProviderRegistry`. Never construct them manually outside it.

```groovy
// CORRECT ✅ — ProviderRegistry wires contexts automatically
// Inside ProviderRegistry.createProvider():
if (type == GrailsHoverProvider)
    return new GrailsHoverProvider(providerContext, workspaceManager)

// CORRECT ✅ — retrieving a provider (lazy, cached)
def provider = providerRegistry.getProvider(GrailsHoverProvider)

// WRONG ❌ — manual construction outside ProviderRegistry
new GrailsHoverProvider(service)                              // old single-arg pattern
new GrailsHoverProvider(service, service, service)            // bypass registry
new GrailsRenameProvider(service.visitor, service.fileTracker) // arbitrary slicing
```

Note: GrailsService supplies ProviderContext and WorkspaceManager handles routing for
request capture. ProjectContext/CompilationContext remain project boundaries; do not
pass the service as all three contexts based on historical examples.

---

## 7. FIELD NAMING CONVENTIONS

```groovy
// Private fields — underscore prefix signals "internal, hands off"
private ClassNode _currentClass        // private mutable lazy cache
private boolean _currentClassComputed  // lazy flag

// Protected getters — no underscore, Groovy property style
protected GrailsLspConfig getConfig() { providerContext.config }
```

Rule: underscore = "internal, hands off". `@CompileStatic` + `private` enforces it.
**Note**: Providers no longer have a `_service` field — context fields live in `BaseProvider`.
Do NOT add `private final GrailsService _service` to any provider.

---

## 8. LAZY FIELD PATTERN

For expensive AST lookups — compute once, cache with flag:

```groovy
private ClassNode _currentClass
private boolean _currentClassComputed = false

ClassNode getCurrentClass() {
    if (!_currentClassComputed) {
        _currentClass = GrailsASTHelper.getEnclosingClassNode(offsetNode, visitor)
        _currentClassComputed = true
    }
    return _currentClass   // explicit return improves readability
}
```

---

## 9. VISITOR INTERNALS

GrailsASTVisitor is mutable compiler output. Its mutators may be called only by the owning project pipeline; public visibility does not grant provider write permission.

Only public read getters are exposed:

```groovy
visitor.getClassNodes(uri)     ✅
visitor.allClassNodes          ✅
visitor.visitSourceUnit(...)   ❌  // owning project pipeline only
visitor.invalidateVisitor()    ❌  // owning project pipeline only
```

No provider may call any method that mutates visitor state.

---

## 10. GROOVY STYLE GUIDE — STRICT ENFORCEMENT

> **Every line of Groovy you write is checked against this section.**
> Violations = blocking review comment. No exceptions.

---

### 10.1 IMPORTS

#### Rule: Explicit only. One class per line. No wildcards. No inline FQCNs.

```groovy
// ✅ CORRECT — one explicit import per class
import kingsk.grails.lsp.context.CompilationContext
import kingsk.grails.lsp.context.ProjectContext
import kingsk.grails.lsp.context.ProviderContext
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.TextDocumentIdentifier

// ❌ WRONG — wildcard, always
import org.eclipse.lsp4j.*

// ❌ WRONG — inline FQCN in parameter or field position (most common AI error)
GrailsHoverProvider(kingsk.grails.lsp.context.ProviderContext ctx, ...)
private kingsk.grails.lsp.context.CompilationContext compilationContext
```

#### Import group order (blank line between each group)

```groovy
// 1. groovy.*
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

// 2. kingsk.* — project-local classes
import kingsk.grails.lsp.context.CompilationContext
import kingsk.grails.lsp.model.enums.DocumentationType

// 3. org.codehaus.groovy.* — AST types
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.Expression

// 4. org.eclipse.lsp4j.* — LSP protocol types
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.SignatureHelp

// 5. java.* — JDK (last)
import java.util.concurrent.CompletableFuture
```

#### Import rules — by package type

| Package type | Rule | Why |
|-------------|------|-----|
| `kingsk.grails.lsp.*` — **project-internal** | **Always explicit, one per line** | Agents must see exactly which context interfaces a class depends on. Wildcards hide this and cause FQCN/constructor bugs. |
| `org.eclipse.lsp4j.*` — LSP types | Explicit when <5 classes. **Wildcard OK at 5+** | Dense protocol clusters hit 10-20 types easily. IntelliJ optimizer collapses these automatically. |
| `org.codehaus.groovy.ast.*` — AST types | Explicit when <5 classes. **Wildcard OK at 5+** | AST-heavy providers easily use 10+ node types. |
| `groovy.*` — Groovy runtime | Always explicit — rarely more than 2-3 | `groovy.transform.CompileStatic`, `groovy.util.logging.Slf4j` |
| `java.*` — JDK | Explicit when <5 classes. Wildcard OK at 5+ | Rare to hit threshold in LSP code. |

```groovy
// ✅ — project-internal: ALWAYS explicit regardless of count
import kingsk.grails.lsp.context.CompilationContext
import kingsk.grails.lsp.context.ProjectContext
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.model.enums.DocumentationType
import kingsk.grails.lsp.utils.ast.ASTUtils

// ✅ — external library with 5+ classes: wildcard is fine
import org.eclipse.lsp4j.*         // when using SignatureHelp, Position, Range, Hover, etc.
import org.codehaus.groovy.ast.*   // when working with many AST node types

// ✅ — external library with <5 classes: explicit
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.SignatureHelp

// ❌ — NEVER wildcard on project-internal packages
import kingsk.grails.lsp.context.*      // hides which contexts are actually used
import kingsk.grails.lsp.utils.ast.*    // hides which utilities are actually used
```

#### Inline FQCNs — NEVER regardless of package

```groovy
// ❌ ALWAYS wrong — inline FQCN in parameter or field position
GrailsHoverProvider(kingsk.grails.lsp.context.ProviderContext ctx, ...)
private kingsk.grails.lsp.context.CompilationContext ctx

// ✅ Import it, use the short name
import kingsk.grails.lsp.context.ProviderContext
GrailsHoverProvider(ProviderContext ctx, ...)
```

#### Zero-use imports — delete immediately

If a class is no longer referenced after a refactor, delete its import. Do not leave
unused imports "for future use". IntelliJ / IDE import optimizers enforce this on save.

---

### 10.2 COLLECTIONS — Groovy Literals, Never Java Constructors

```groovy
// ✅
def items = []
def map   = [:]
def pair  = [name: "Grails", version: 7]
def set   = [] as Set

// ❌ — Java constructors have zero place in Groovy code
def items = new ArrayList<>()
def map   = new HashMap<String, Object>()
def set   = new HashSet<>()
```

---

### 10.3 LOCAL VARIABLE TYPING

```groovy
// ✅ def — when type is obvious from RHS
def uri     = textDocument.uri
def methods = GrailsASTHelper.getMethodOverloadsFromCallExpression(methodCall, visitor)

// ✅ Explicit type — when @CompileStatic needs narrowing or type matters for clarity
ASTNode      current          = offset
Expression   args             = methodCall.arguments
int          activeParamIndex = -1
List<String> names            = []

// ❌ Exhaustive Java-style typing on every local
ArrayList<MethodNode> methods = new ArrayList<>(GrailsASTHelper.getMethodOverloads(...))
String uri = (String) textDocument.getUri()
```

---

### 10.4 PROPERTY ACCESS — Prefer Groovy Properties for Equivalent Value Access

Property syntax is preferred when equivalent. Lifecycle direct-field access and explicit
methods are required when property dispatch changes meaning, for example `state` versus
`getState()` or `Map.isEmpty()` versus the `empty` key. Keep static typing and regression
tests for these cases. Do not mechanically convert these accesses during review.

```groovy
// ✅
config.codeLensMode
method.parameters
node.name
textDocument.uri
classNode.methods       // returns List<MethodNode> directly

// Ordinary equivalent value getters prefer property syntax:
config.getCodeLensMode()
method.getParameters()
node.getName()
textDocument.getUri()
```

---

### 10.5 NULL SAFETY — Safe Navigation and Elvis

```groovy
// ✅
def label = node?.name ?: "unknown"
nodes?.each   { process(it) }
def found = nodes?.find { it.name == target }

// ✅ — nested safe nav
def docs = method?.groovydoc?.content ?: ""

// ❌ — verbose null guards
if (node != null) { label = node.getName() }
if (nodes != null) { for (ASTNode n : nodes) { process(n) } }
```

---

### 10.6 CLOSURES AND COLLECTION PIPELINES — Never Java Streams

```groovy
// ✅
def info = methods.collect { method ->
    new SignatureInformation(method.name, docs, buildParams(method))
}
def match    = nodes.find    { it.name == target }
def filtered = nodes.findAll { it.isPublic() }
def idx      = nodes.findIndexOf { it.name == target }
nodes.each   { log.debug("[X] ${it.name}") }
def names    = nodes*.name                    // spread operator

// ❌ — Java streams never belong here
methods.stream()
       .map(m -> new SignatureInformation(m.getName(), docs, params))
       .collect(Collectors.toList())
Optional<MethodNode> opt = methods.stream().filter(...).findFirst()
```

---

### 10.7 STRING INTERPOLATION — GStrings Always

```groovy
// ✅
log.debug("[SIGNATURE] No node at ${uri}:${position.line}")
log.info("[HOVER] ${classNode.name} — ${methods.size()} overloads")
throw new IllegalStateException("Provider not registered: ${type.simpleName}")

// ❌
log.debug("[SIGNATURE] No node at " + uri + ":" + position.line)
throw new IllegalStateException("Provider not registered: " + type.getSimpleName())
```

---

### 10.8 ELVIS AND TERNARY — Use the Right One

```groovy
// ✅ Elvis — when any falsy value should fall back to default
def label = method.name ?: "<unknown>"
return result ?: []
def items = cache.get(uri) ?: []

// ✅ Ternary — when -1, 0, false carry distinct semantic meaning
int idx = rawIdx >= 0 ? rawIdx : expressions.size()
boolean ok = count > 0 ? count < limit : false

// ❌ — ternary for simple null/empty defaults
def label = method.name != null ? method.name : "<unknown>"
return result != null ? result : new ArrayList<>()
```

---

### 10.9 RETURN STATEMENTS

```groovy
// ✅ Explicit return in multi-statement / async methods — aids scanability
CompletableFuture<SignatureHelp> provideSignatureHelp(...) {
    def token = createCancellationToken(textDocument.uri)
    long t0   = System.currentTimeMillis()
    return CompletableFuture.supplyAsync {
        checkCancellation(token)
        ...
        return new SignatureHelp(information, bestIdx, activeParamIndex)
    }
}

// ✅ Implicit return fine for single-expression getters
protected GrailsLspConfig getConfig() { providerContext.config }

// ❌ Missing outer return — supplyAsync result dropped silently
CompletableFuture<SignatureHelp> provideSignatureHelp(...) {
    def token = createCancellationToken(textDocument.uri)
    CompletableFuture.supplyAsync {                    // ← NO return — method returns null
        new SignatureHelp(information, bestIdx, activeParamIndex)
    }
}
```

---

## 11. CONCURRENCY RULES

- All LSP handler methods (`didChange`, `completion`, `hover`, etc.) are called on the LSP I/O thread — **never block it**
- Off-load expensive work through an owned bounded scheduler/executor. Existing lightweight provider futures may use the common pool; that alone supplies neither admission limits nor isolation.
- Compilation/document scheduling and Gradle use explicit owned background lifecycles with cancellation/disposal. Do not allocate an executor per provider/request or block readers behind those workers. See `docs/specs/performance.md`.
- `progressService` for user-facing progress — always begin / update / end trio
- CancellationToken MUST be checked inside the `supplyAsync` block, not outside it

---

## 12. HOW TO FIX EXISTING CODE — STEP BY STEP

1. Classify every provider/service into TIER 1, 2, or 3
2. TIER 1 → ensure extends `BaseProvider` with context-aware constructor; remove any duplicate `private final GrailsService service` field
3. TIER 2 → make all methods `static`, remove constructor and any `GrailsService` field entirely
4. TIER 3 → replace `GrailsService` constructor arg with only what is actually used
5. Inside all TIER 1 classes → use `visitor`, `config`, `project` etc. from BaseProvider getters — never `service.xxx`
6. Add missing getters to `BaseProvider` if a commonly-accessed field has no getter yet
7. Return statements: **explicit** `return` in multi-statement / async methods; implicit return OK only for single-expression methods (see §10.9)
8. Verify NO provider writes to `service.visitor`, `service.compiler`, or `service.fileTracker`

---

## 13. WHAT NOT TO CHANGE

- `GrailsService` constructor wiring — leave as is
- `GrailsService` pipeline write methods (`setupWorkspace`, `refreshAndReindexWorkspace`, `compileAndVisitAST`, `visitAST`) — leave as is
- `CompletionRequest` and similar value/context objects — they are NOT providers; they may hold `GrailsService` with their own `getVisitor()` shortcut — this is correct and intentional
- `@CompileStatic` on any class — **never** remove it; fix the types instead
- Lazy field pattern (`_xxx` + `_xxxComputed` flag) where it exists — intentional performance optimization; do not replace with live reads

---

## 14. PACKAGE STRUCTURE

```
kingsk.grails.lsp/
  GrailsService.groovy              ← composition root (implements ProviderContext; project contexts own compilation state)
  GrailsLanguageServer.groovy       ← LSP entry point
  context/                          ← ProjectContext, ProviderContext, CompilationContext interfaces
  core/
    compiler/                       ← GrailsCompiler, CompilerOptions
    gradle/                         ← GrailsProjectBuilder, ProjectCache
    visitor/                        ← GrailsASTVisitor
  services/                         ← GrailsTextDocumentService, GrailsWorkspaceService,
                                       CancellationService, ProviderHealthService, ProviderRegistry,
                                       FileContentTracker, and all domain services
  providers/
    document/                       ← TIER 1 document providers (completion, hover, diagnostics, etc.)
    workspace/                      ← TIER 1 workspace providers (references, symbols, etc.)
    completions/
      strategies/
        context/                    ← context-aware completion strategies
        snippet/                    ← snippet strategies
        type/                       ← type-resolution strategies
        special/                    ← Grails-specific strategies
  model/                            ← GrailsProject, TextFile, GrailsLspConfig, etc. (4 type subfolders)
  utils/                            ← TIER 2 static utilities (10 domain subfolders):
    ast/        cache/    completion/   diagnostics/  docs/
    grails/     gsp/      position/     project/      resolution/
  protocol/                         ← LSP protocol extension types
  dummy/                            ← test dummy implementations (test scope only)
```

---

## 15. PROVIDERREGISTRY — LAZY PROVIDER ACCESS

`ProviderRegistry` manages lazy initialization of TIER 1 providers. It lives in `GrailsService` as `providerRegistry`.

All TIER 1 providers created here — never anywhere else.

```groovy
// Retrieve a provider (created on first access, reused thereafter)
def provider = providerRegistry.getProvider(GrailsCompletionProvider)
```

### Rules
- Providers retrieved via `ProviderRegistry` MUST extend `BaseProvider`.
- ProviderRegistry passes `(providerContext, workspaceManager)`; project/snapshot selection happens through request capture.
- NEVER instantiate `ProviderRegistry` outside `GrailsService`.
- Providers that are never requested are never created — intentional.

---

## 16. CANCELLATIONSERVICE — REQUEST LIFECYCLE

`CancellationService` provides URI-scoped request cancellation. All long-running LSP handlers MUST participate.

### Usage pattern
```groovy
@Override
CompletableFuture<List<CompletionItem>> completion(CompletionParams params) {
    def token   = createCancellationToken(params.textDocument.uri)
    long startTime = System.nanoTime()
    return CompletableFuture.supplyAsync {
        boolean success = false
        try {
            checkCancellation(token)
            // ... work ...
            checkCancellation(token)  // at every major yield point
            success = true
            return result
        } finally {
            recordHealth("CompletionProvider", (System.nanoTime() - startTime).intdiv(1_000_000), success)
        }
    }
}
```

### Rules
- Close/shutdown must cancel owned pending work and invalidate active output; verify lifecycle tests before claiming this for a changed path.
- Do NOT create `CancellationToken` instances manually. Always use `createCancellationToken(uri)`.
- `checkCancellation()` throws `CancellationException` — let it propagate; the executor handles it.
- `CancellationToken` is created BEFORE `supplyAsync`, checked INSIDE the lambda.

---

## 17. TESTING — MANDATORY RULES

> **These rules are ENFORCED. A fix with no test is incomplete.**

---

### 17.1 The Test Pyramid — Prefer the Cheap Tier

This project has two test tiers. Prefer the cheaper one **always**.

| Tier | Base class | Speed | Cost | Use when |
|------|-----------|-------|------|----------|
| **Unit** | `Specification` | Fast (ms) | Free | Pure functions, T2 utilities, algorithms, data transforms, edge cases |
| **Integration** | `BaseLspSpec` | Slow (seconds, ClassGraph scan) | Expensive | Full provider round-trip through LSP lifecycle that cannot be tested any other way |

**Rule**: If you can test it without `BaseLspSpec`, you MUST. Integration tests are for things that truly require a running `GrailsService`.

---

### 17.2 When to Write a Unit Test (Pure `Specification`)

Write a pure unit test (`extends Specification`) for:

- Any T2 utility method (`static` methods in `utils/` — `PositionHelper`, `ASTUtils`, `GrailsASTHelper`, etc.)
- Any private/package-local algorithm (index calculation, range comparison, token parsing)
- Any method that takes plain inputs and returns a value — no `GrailsService`, no file I/O
- Any bug fix in a utility function — reproduce with a `where:` table row first

**Pattern** — look at `PositionHelperSpec` as the gold standard:
```groovy
class MyUtilSpec extends Specification {

    def "should compute X correctly"() {
        expect:
        MyUtil.compute(input) == expected

        where:
        input  || expected
        "a"    || 1
        ""     || 0
        null   || -1
    }

    def "should handle edge case Y"() {
        expect:
        MyUtil.process(null) == []
    }
}
```

---

### 17.3 When to Write an Integration Test (`BaseLspSpec`)

Write a `BaseLspSpec` integration test **only** when the behavior under test requires:

- A live `GrailsService` pipeline (`compileAndVisitAST`, `visitor`, `compiler`)
- Opening a real document via `openTextDocument()` / `didOpen`
- Verifying actual provider output (completions, hover, signature help, diagnostics)
- Cross-file resolution that needs the full AST index

Use `ProjectType.DUMMY` for single-file tests — it avoids ClassGraph workspace scan overhead.

```groovy
class GrailsMyProviderSpec extends BaseLspSpec {

    @Shared GrailsService service

    def setupSpec() {
        service = initializeProject(ProjectType.DUMMY)  // ← DUMMY for single-file tests
    }

    def "should return X for input Y"() {
        given:
        String uri = openTextDocument("MyClass.groovy", """
            class MyClass {
                def method(String name) { name }
            }
        """.stripIndent())

        when:
        def result = getXxx(uri, 2, 20)

        then:
        result != null
        result.something == expected

        where:
        expected << ["value1", "value2"]
    }
}
```

---

### 17.4 Fix a Bug → Write a Test First

**MANDATORY workflow:**

1. Identify the exact function or code path that is broken
2. Write the smallest test that **fails** to reproduce the bug
3. Run it — confirm it fails
4. Fix the code
5. Run again — confirm it passes
6. Never skip step 2-3 for "obvious" bugs — obvious bugs have non-obvious edge cases

```groovy
// 1. Reproduce first — this MUST fail before the fix
def "should return 0 index when cursor is at first argument"() {
    expect:
    GrailsSignatureHelpProvider.getActiveParameter(pos(2, 10), [argExpr]) == 0
    // ← write this BEFORE fixing the off-by-one
}
```

---

### 17.5 Add a Test Whenever You Fix Code

| Code change | Required test |
|-------------|---------------|
| Fix a bug in a utility method | New row in `where:` table covering the failing case |
| Fix a bug in a provider algorithm | New `when/then` block or `where:` case in the provider spec |
| Add a new utility method | New `Specification` test class or method covering happy path + edge cases |
| Add a new provider feature | New `BaseLspSpec` test covering the new behavior |
| Refactor with no behaviour change | Verify existing tests still pass — no new tests required unless coverage was missing |

---

### 17.6 Test Naming — Behaviour, Not Implementation

```groovy
// ✅ — describes observable behaviour
def "should return empty list when no overloads found"() { ... }
def "should use 0-based index for active parameter"() { ... }
def "should climb AST to find enclosing MethodCall"() { ... }

// ❌ — describes code path, not behaviour
def "test getActiveParameter"() { ... }
def "provideSignatureHelp null check"() { ... }
def "test fix for issue 42"() { ... }
```

---

### 17.7 Data-Driven Tests — Use `where:` Tables

When testing pure functions with multiple inputs, always use `where:` — never duplicate `when/then` blocks.

```groovy
// ✅
def "should compute active parameter index"() {
    expect:
    getActiveParameter(pos(line, col), exprs) == expected

    where:
    line | col | exprs                || expected
    2    | 10  | [expr("a")]          || 0        // cursor at first arg
    2    | 25  | [expr("a"), expr("b")] || 1      // cursor at second arg
    2    | 50  | [expr("a")]          || 1        // cursor past all args → size()
    2    | 0   | []                   || 0        // no args → 0
}

// ❌ — duplicated blocks for same function
def "should return 0 for first arg"() { expect: getActiveParameter(...) == 0 }
def "should return 1 for second arg"() { expect: getActiveParameter(...) == 1 }
```

---

### 17.8 Test Isolation Rules

- Unit tests (`Specification`): **no `@Shared` state** — every test gets fresh inputs
- Integration tests (`BaseLspSpec`): `@Shared GrailsService` is OK — it is expensive to recreate; use `openTextDocument` / `closeTextDocument` to manage document state per test
- **NEVER** share mutable AST nodes or `ASTNode` references across tests — the AST is recompiled
- `mockClient.clear()` in `cleanup:` if checking diagnostics — previous test diagnostics bleed through

---

### 17.9 Do NOT Over-Test Integration

**Anti-patterns to avoid:**

```groovy
// ❌ — testing pure string logic through the full LSP stack
def "should format label as 'method(type): returnType'"() {
    // This does not need BaseLspSpec — test the formatting method directly
    given: initializeProject(ProjectType.DUMMY)   // 3-second ClassGraph scan
    when:  def result = getHover(uri, 5, 10)
    then:  result.contents.value.contains("String foo(int x): void")
}

// ✅ — test the formatter directly (fast, reliable, readable)
def "should format method label correctly"() {
    expect:
    ASTUtils.astNodeToName(methodNode) == "String foo(int x): void"
}
```

---

### 17.10 Test File Location

| What you are testing | Where the test goes |
|----------------------|---------------------|
| T2 utility in `utils/position/` | `test/groovy/.../utils/PositionHelperSpec.groovy` |
| T2 utility in `utils/ast/` | `test/groovy/.../utils/ASTUtilSpec.groovy` |
| T1 provider in `providers/document/` | `test/groovy/.../providersDocument/GrailsXxxProviderSpec.groovy` |
| Core compiler / visitor | `test/groovy/.../core/compiler/` |
| Index / cache logic | `test/groovy/.../index/` |
| Model classes | `test/groovy/.../model/` |

Mirror the source tree exactly. One spec file per source class (or per logical group for small utilities).

---
