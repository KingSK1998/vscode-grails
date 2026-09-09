# Installed-library discovery and compatibility contract

**Status:** implementation requirements for R2; current compliance is incomplete. **Updated:** 2026-09-08.

The IDE must describe the libraries resolved for the selected project and source set. It must not maintain a patched Grails distribution, silently upgrade project dependencies, or assume a method exists because it appeared in a different version. Rebuilding analysis metadata after a dependency change is distinct from updating extension code.

## Classify hardcoding before replacing it

| Data | Appropriate source | Permitted fixed knowledge |
|---|---|---|
| Groovy lexical keywords, operators, GSP delimiters | Language grammar and supported parser version | Grammar tables and TextMate patterns; test syntax versions |
| JVM/Groovy method/type/annotation declarations | Project compiler model and resolved classpath bytecode/source | None as a competing API inventory |
| Grails/GORM/plugin method signatures | Actual declarations, traits, extension-module descriptors and capability rules | Small adapter rules with artifact evidence and fixtures |
| Dynamic finder forms and injected conventions | Adapter expands a proven framework rule against the current domain model | Rule grammar, never a pre-generated list of all invented methods |
| Config keys, types, defaults and docs | Resolved dependency metadata and project config | Documented fallback only for a named capability, visibly incomplete |
| Snippets and artifact templates | Curated authoring templates | Allowed; do not describe a snippet as proof an API exists |
| Protocol names, error codes, setting IDs | LSP/custom schema and extension manifest | Allowed constants with one authoritative definition |

Do not replace a hardcoded array with an equally hardcoded JSON catalog and call it dynamic discovery. Do not delete language grammar keywords in an attempt to extract them from Grails JARs. Keep syntax coloring usable before discovery completes.

Groovy supports runtime and compile-time metaprogramming, so declaration scanning alone cannot prove every dynamic call. Treat unresolved behavior as incomplete analysis. [Groovy metaprogramming documentation](https://groovy-lang.org/metaprogramming.html).

## Inputs and authority

1. Route the URI to an explicitly registered project and source set. Canonical URI containment must respect path segments; similarly named sibling roots are distinct. A file outside every root does not inherit the first project's framework capabilities.
2. Resolve the project using its Gradle model/wrapper. Preserve build identity, project path, source set, selected dependency/variant and ordered compile classpath. Test dependencies must not silently enter production completion scope.
3. Use the compiler/AST for language binding in that scope. Consult resolved declaration metadata for external symbols and framework adapters for conventions absent from ordinary declarations. Build metadata determines visibility; no global source ranking can make an out-of-scope symbol valid.
4. Attach provenance: declaring symbol/signature, artifact coordinates when available, content fingerprint, source/bytecode location, source set, classpath revision and derivation kind. Source attachments enrich docs/navigation; absence of sources must not erase valid bytecode declarations.

The Tooling API obtains build models and supports asynchronous operations and cancellation. A model request can involve build work; it belongs in bounded background synchronization. [Gradle Tooling API](https://docs.gradle.org/current/userguide/tooling_api.html).

## Refresh is a transaction

```text
build input changes -> mark affected roots dirty -> coalesced model resolution
  -> candidate classpath membership + graph + capability facts
  -> compiler/discovery/index candidates using the same dependency revision
  -> validate root generation and input fingerprints
  -> commit one coherent view -> notify consumers
```

- Watch Groovy/Kotlin build and settings files, properties, catalogs and relevant model inputs actually used by the build. Imported scripts, included builds and custom catalogs need model-derived tracking or an explicit refresh fallback; the standard filename list is not a completeness claim.
- Union roots affected by different events during debounce. Cancel/ignore superseded work. Removal and shutdown invalidate candidates even if Gradle returns afterward.
- A failed/offline refresh retains the last committed dependency generation and reports it stale. Do not attach new metadata to an old compiler or clear usable answers first.
- A successful removal must remove old methods, origins, docs, config keys and capability-derived results. Test both addition and disappearance without restarting the server.
- Local/project output directories and changing/snapshot artifacts can change without new coordinates. Fingerprint content, not just `group:name:version`; filesystem size/mtime may accelerate detection but are not sufficient final identity.

## Capability adapters

Use a small, explicitly wired adapter boundary; extending `CompletionBuilder` with another hardcoded Grails-version method list is not the boundary. Each adapter declares:

| Required field | Meaning |
|---|---|
| Capability and supported evidence | Which artifact/type/annotation/descriptor enables the rule |
| Applicability | Project/source-set scope and any tested version/signature guard |
| Inputs | Declaration facts and current project entities used by the rule |
| Output | Typed symbols/relations with origin, derivation and limitations |
| Invalidation | Which source/config/dependency changes invalidate the output |
| Fallback | Behavior when evidence is absent, ambiguous or unsupported |
| Fixtures | Positive, missing-capability, changed-version and removal cases |

Prefer capability/signature detection to major-version branching. Where semantics differ despite the same surface signature, a narrow, documented version guard is acceptable. Unknown versions may reuse declaration discovery; adapter behavior remains experimental until validated. Do not claim unlimited forward compatibility.

Example acceptance fixture: a current domain declares `title: String`; a proven finder capability may derive the appropriate `findByTitle` shape. Renaming/removing that property removes the derived result. A type in an unrelated project must not receive it. The exact rule/signature must be checked against the fixture's actual GORM artifacts before implementation.

Runtime execution is not a prerequisite for ordinary reads. Do not initialize application classes or run a Grails application to enumerate completions. Bytecode/descriptor inspection is preferred for metadata. Compiler transforms and Gradle execution must respect the product's workspace trust boundary; even compilation can execute project-supplied code. Runtime observations, if admitted under R6-04, carry session/environment scope and never become universal facts.

## Cache and classloader contract

- Separate shared immutable artifact facts from **project membership**. Two projects may share the facts for identical bytes, never a mutable compilation unit or an unscoped list of visible symbols.
- Metadata cache key: artifact content fingerprint + extractor schema/version + relevant target/runtime interpretation. Membership key: canonical project identity + source set + dependency revision + ordered classpath. Adapter cache keys also include adapter version and all source/config revisions used.
- Close scan results and owned classloaders when their consumers release them. Root removal, dependency replacement, eviction and shutdown must release references. A static classloader map needs an explicit owner/removal path; weak/soft references are not a lifecycle policy.
- Scan only the relevant resolved inputs. Broad system/module or whole-workspace scans on each completion are forbidden. Read method docs lazily; retain compact declaration facts, not entire JAR copies or AST serializations.
- Persist only reproducible, schema-versioned data with atomic replacement, corruption recovery and the [performance spec's resource limits](performance.md). Corrupt cache means rebuild; it is never authoritative evidence.

## Required conformance cases

| Case | Expected result |
|---|---|
| Two projects select different versions of one library | Each sees its own signatures/origins; same FQN does not collapse scope |
| Library changes at the same coordinates | Content revision changes; old facts disappear after successful refresh |
| Dependency removed or trait/extension module absent | Removed APIs and dependent capability results disappear |
| Sources absent but bytecode available | Declarations still resolve; docs/source navigation show the limitation |
| Same-name modules, renamed paths, composite build | Visibility follows resolved build/project edges, not name/prefix guesses |
| Cyclic module graph | Traversal terminates; each node visited once per propagation |
| Offline/cancelled/broken Gradle model | Last good facts remain scoped and stale; no partial replacement |
| Arbitrary `metaClass` behavior | Unknown/heuristic unless evidenced; no guessed rename target |
| New library member in a real fixture | Appears without editing an extension API inventory |
| Same input after warm restart | Same facts/origins with measured reuse; no classloader accumulation |

Map implementations to `INV-OWN-004/005/007/008`, `INV-KEY-001/004`, `INV-STATE-003/004/005/009/010` and `INV-DISC-001/002/003`. See [validation](validation.md) for fixture provenance and matrix requirements.
