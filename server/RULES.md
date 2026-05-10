# SERVER ARCHITECTURE RULES
> Groovy 4 · LSP4J · GrailsService composition root  
> Any AI agent working on server code MUST read this first.

---

## 0. GOLDEN RULE

> **LSP server = single process, single instance, tight pipeline.**  
> Shared mutable state through `GrailsService` is INTENTIONAL — not a smell.  
> Do NOT refactor it away. Understand WHY before touching anything.

---

## 1. STACK

| Layer | Technology |
|---|---|
| Language | Groovy 4.0.23 |
| Static compilation | `@CompileStatic` on ALL classes — exceptions allowed if absolutely necessary |
| LSP framework | LSP4J 0.23.1 |
| Build tooling | Gradle 8.x, Tooling API 8.12 |
| Testing | Spock Framework |
| JDK | 17+ |

---

## 2. GRAILSSERVICE — COMPOSITION ROOT

`GrailsService` is the **single wiring point**. It is NOT a service locator passed around carelessly.

```
GrailsService  ← connect client
  ├── errorService        ErrorService
  ├── discoveryService    DiscoveryService
  ├── gradle              GradleService
  ├── fileTracker         FileContentTracker
  ├── astService          ASTService
  ├── compiler            GrailsCompiler
  ├── progressService     ProgressService
  ├── diagnostics         GrailsDiagnosticService
  ├── document            GrailsTextDocumentService  ← LSP entry point
  ├── workspace           GrailsWorkspaceService     ← LSP entry point
  ├── visitor             GrailsASTVisitor
  ├── dependencyProvider  GrailsDependencyProvider
  ├── gormSqlProvider     GrailsGormSqlProvider
  ├── testDiscoveryProvider GrailsTestDiscoveryProvider
  └── config              GrailsLspConfig
```

### WRITE PATH — sacred, untouchable by providers

Only these methods may mutate shared state:

```groovy
GrailsService.setupWorkspace()
GrailsService.refreshAndReindexWorkspace()
GrailsService.compileAndVisitAST(TextFile)
GrailsService.visitAST(TextFile)
```

**Providers NEVER write to `visitor`, `compiler`, or `fileTracker`. EVER.**

### READ PATH — open, always fresh

Providers read freely via `BaseProvider` getters. Never cache — always read live:

```groovy
visitor.getClassNodes(uri)        // always fresh AST
compiler.getSourceUnit(file)      // always current compile
fileTracker.getContent(uri)       // always latest content
config.codeLensMode               // live config
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

### TIER 1 — Extends BaseProvider, takes GrailsService

Examples: `GrailsCompletionProvider`, `GrailsHoverProvider`, `GrailsDiagnosticService`, `GrailsGormSqlProvider`, `GrailsTestDiscoveryProvider`

### TIER 2 — Static utility, no dependencies

Examples: `GrailsUtils`, `DocumentationHelper`, `CompletionUtil`, `GrailsASTHelper`, `GrailsArtefactUtils`

ALL methods must be `static`. No constructor. No instance state.

### TIER 3 — Plug-n-play module, minimal own deps

Examples: `GrailsYamlIntelligenceProvider(GrailsLspConfig)`, `GrailsLspConfig`

Inject ONLY what is actually used — never full `GrailsService`.

### Special: Value/Context Objects

`CompletionRequest` and similar context bags are NOT providers. They may hold `GrailsService` directly with their own `getVisitor()` shortcut. This is correct and intentional. Do not change this pattern.

---

## 4. BASEPROVIDER — CANONICAL FORM

Every TIER 1 class MUST extend `BaseProvider` exactly like this:

```groovy
@CompileStatic
abstract class BaseProvider {
    private final GrailsService _service  // underscore = backing field, hands off

    BaseProvider(GrailsService service) {
        this._service = service
    }

    // Groovy property magic: getX() → accessible as .x in subclasses
    protected GrailsASTVisitor getVisitor()             { _service.visitor }
    protected FileContentTracker getFileTracker()       { _service.fileTracker }
    protected GrailsLspConfig getConfig()               { _service.config }
    protected GrailsCompiler getCompiler()              { _service.compiler }
    protected GrailsDiagnosticService getDiagnostics()  { _service.diagnostics }
    protected GrailsProject getProject()                { _service.project }
}
```

Rules:
- `_service` is `private final` with underscore prefix — backing field, not exposed
- `@CompileStatic` enforces `private` — subclasses cannot access `_service` directly
- To add access to a new `GrailsService` field — add a `protected` getter to `BaseProvider` first
- NEVER expose `_service` itself as a public or protected getter

---

## 5. PROVIDER ACCESS RULES

### Use Groovy property getters — NEVER `service.xxx` directly

```groovy
// WRONG ❌
service.visitor.getClassNodes(uri)
service.fileTracker.getContent(uri)
service.config.codeLensMode
getVisitor().getClassNodes(uri)       // verbose Java style

// CORRECT ✅
visitor.getClassNodes(uri)            // Groovy resolves getVisitor() → .visitor
fileTracker.getContent(uri)
config.codeLensMode
```

### No duplicate field declarations

```groovy
// WRONG ❌ — copy-pasting this in every provider
class GrailsCodeLensProvider {
    private final GrailsService service   // don't do this
}

// CORRECT ✅ — extend BaseProvider
class GrailsCodeLensProvider extends BaseProvider {
    GrailsCodeLensProvider(GrailsService service) { super(service) }
}
```

### Constructor tells the truth

If constructor takes `GrailsService` but body only uses `service.config` → WRONG. Demote to TIER 3 and inject only `GrailsLspConfig`.

---

## 6. PROVIDER INITIALIZATION — UNIFORM PATTERN

All providers initialized the same way in `GrailsTextDocumentService`:

```groovy
// CORRECT ✅ — uniform
new GrailsCompletionProvider(service)
new GrailsHoverProvider(service)
new GrailsGormSqlProvider(service)

// WRONG ❌ — breaks pattern, no explanation
new GrailsRenameProvider(service.visitor, service.fileTracker)
```

If a provider needs less than `GrailsService` → demote it to TIER 3. Do not break the pattern.

---

## 7. FIELD NAMING CONVENTIONS

```groovy
private final GrailsService _service   // private backing field — underscore prefix
private ClassNode _currentClass        // private mutable lazy cache — underscore prefix
private boolean _currentClassComputed  // lazy flag — underscore prefix

protected GrailsASTVisitor getVisitor() { _service.visitor }  // exposed getter — no underscore
```

Rule: underscore = "internal, hands off". `@CompileStatic` + `private` enforces it.

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

`GrailsASTVisitor` internal index/mutate methods = `private`.

Only public read getters are exposed:

```groovy
visitor.getClassNodes(uri)     ✅
visitor.allClassNodes          ✅
visitor.visitSourceUnit(...)   ❌  // called only by GrailsService pipeline
visitor.invalidateVisitor()    ❌  // called only by GrailsService pipeline
```

No provider may call any method that mutates visitor state.

---

## 10. GROOVY 4 STYLE — ENFORCE EVERYWHERE

```groovy
// USE explicit return for readability
ClassNode getCurrentClass() {
    return _currentClass   // ✅
}

// NO verbose Java collections
def items = []     // ✅  not new ArrayList()
def map = [:]      // ✅  not new HashMap()

// NO verbose type for locals where def works
def uri = TextFile.normalizePath(doc.uri)   // ✅
String uri = TextFile.normalizePath(...)    // ❌ (unless type matters for clarity)

// Groovy property access — no getX()/setX() calls
config.codeLensMode         // ✅
config.getCodeLensMode()    // ❌

// Safe navigation over null checks
service?.visitor?.getClassNodes(uri)   // ✅

// Elvis for defaults
def result = value ?: defaultValue    // ✅

// Groovy collections
classNodes?.each { ... }              // ✅
classNodes?.findAll { ... }           // ✅
classNodes?.collect { ... }           // ✅
classNodes?.find { ... }              // ✅

// Single-expression methods — no braces bloat
protected GrailsASTVisitor getVisitor() { _service.visitor }   // ✅
```

---

## 11. CONCURRENCY RULES

- All LSP handler methods (`didChange`, `completion`, `hover` etc.) are called on LSP threads
- Background work goes through `backgroundExecutor` in `GrailsService`
- Use `CompletableFuture` for async LSP responses
- Never block LSP thread with long Gradle or compile operations
- `progressService` for user-facing progress — always begin/update/end trio

---

## 12. HOW TO FIX EXISTING CODE — STEP BY STEP

1. Classify every provider/service into TIER 1, 2, or 3
2. TIER 1 → ensure extends `BaseProvider`, remove any duplicate `private final GrailsService service` field
3. TIER 2 → make all methods `static`, remove constructor and `GrailsService` field entirely
4. TIER 3 → replace `GrailsService` constructor arg with only what is actually used
5. Inside all TIER 1 classes → replace `service.visitor` with `visitor`, `service.config` with `config`, etc.
6. Add missing getters to `BaseProvider` if a commonly accessed field has no getter yet
7. Ensure all methods use implicit return, Groovy collections, no verbose Java style
8. Verify NO provider writes to `service.visitor`, `service.compiler`, or `service.fileTracker`

---

## 13. WHAT NOT TO CHANGE

- `GrailsService` constructor wiring — leave as is
- `GrailsService` pipeline write methods — leave as is
- `CompletionRequest` and similar value/context objects — they are NOT providers, they may hold `GrailsService` with their own `getVisitor()` — this is correct
- `@CompileStatic` on any class — never remove it
- Lazy field pattern where it exists — it is intentional performance optimization

---

## 14. PACKAGE STRUCTURE

```
kingsk.grails.lsp/
  GrailsService.groovy              ← composition root
  GrailsLanguageServer.groovy       ← LSP entry point
  core/
    compiler/                       ← GrailsCompiler, CompilerOptions
    gradle/                         ← GrailsProjectBuilder, ProjectCache
    visitor/                        ← GrailsASTVisitor
  services/                         ← GrailsTextDocumentService, GrailsWorkspaceService + all services
  providersDocument/                ← TIER 1 document providers
  providersWorkspace/               ← TIER 1 workspace providers
  model/                            ← GrailsProject, TextFile, GrailsLspConfig, etc.
  utils/                            ← TIER 2 static utilities
```
