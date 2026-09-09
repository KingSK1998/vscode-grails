# Daily IDE and embedded-language acceptance contract

**Status:** target behavior for R4/R5; provider presence alone does not satisfy it. **Updated:** 2026-09-08.

Deliver complete workflows over the shared project model. Grails 7+ / Groovy 4 is the first compatibility family; exact tested combinations belong in the release matrix. Native VS Code navigation, trees, tasks, testing, diagnostics and accessibility should remain familiar even when relationship views offer a richer presentation.

## One symbol, consistent features

Completion, hover, signature help, definition and references must agree about the same receiver/type/symbol in a given project and analysis revision. Identity includes scope and signature where needed; a simple method name or FQN alone is insufficient for overloads and same-FQN dependencies. Snapshot-local IDs are not durable rename handles.

Cover locals, shadowing, closures/delegates, generics, overloads, traits, extension methods, inferred types, inherited members, Groovy property access and unsaved edits. Return partial/unresolved results when dynamic binding cannot be established. Diagnostic freshness must match the [state contract](../state-and-lifecycle-specification.md); a newer buffer must not receive unqualified old error locations.

## Grails relationships

Start with existing domain/service/controller/TagLib/view/config providers. Add compact, provenance-bearing relationships only where a task requires them: route to action, injection target, domain association, view/template usage, tag call to TagLib, and configuration usage. Derive rules from applicable capabilities and project declarations. Runtime construction of a route or bean name may remain unknown.

Each edge records source/target identity, kind, originating source/artifact/rule, project/dependency revision and resolution kind. Deletion or dependency changes remove affected edges. Do not create another graph with copies of compiler AST nodes; graph views, impact analysis and tools query the same relationship facts. Large graph results need scope filters, caps and pagination, not a full-workspace layout on activation.

## Embedded-language contract for GSP

Model language regions with source mappings between the original document and generated/virtual documents. HTML, CSS, JavaScript and Groovy providers must route by the position's region. Use VS Code language services or request forwarding where supported; do not implement a second HTML/CSS/JS parser without evidence that existing services fail the requirement. [VS Code embedded languages guidance](https://code.visualstudio.com/api/language-extensions/embedded-languages).

The mapper owns source/virtual URIs, original document version and mapping generation. Test UTF-16 positions and negotiated encoding, CRLF/LF, astral characters, interpolation, GSP comments/directives/tags, attribute expressions and incomplete syntax. Regions may be nested; malformed input must recover enough to keep editing responsive. Masking text can preserve offsets, but generated helper code requires an explicit mapping.

Map completion replacements **and additional edits**, diagnostics, definitions, references, hover ranges and formatting edits back to the original document. Reject edits that touch unmappable/generated regions or overlap incompatibly. Preserve unrelated text. Invalidate virtual documents on edit, close, root removal and language/config changes. Do not send virtual paths to unrelated projects or retain every historical virtual document.

## Configuration and nested sources

- YAML/JSON/properties intelligence uses dependency-provided schema/config metadata and project profiles. Distinguish an unknown key from a proven-invalid key when metadata is incomplete; custom plugin settings must remain possible.
- Support nesting, arrays, quoted/dotted keys, profile/environment overrides, placeholders and i18n references. Defaults and docs retain metadata origin/version. Do not expose secret values in hover, views or agent logs.
- Source exploration follows Gradle roots/source sets and Grails artifacts rather than a fixed directory-depth assumption. Preserve project identity for nested modules, test/generated sources, assets and resources. Generated/read-only locations are visibly distinct.
- Tree nodes need stable scoped identity, lazy children, cancellation/disposal and empty/loading/error states. Large workspaces must not recursively materialize all files at activation.

## Run, debug, test and review

Use the selected project, wrapper, environment/profile and explicit configuration. Reuse existing VS Code task/test/debug integrations where appropriate. Verify process termination/cancellation, restart, missing Java, invalid wrapper and port conflicts through actionable status/output. Report the actual selected tests and exit status; locating a test method is not proof a runner executed it.

Refactor previews and check execution follow the [agent/change contract](agent-tools.md) for both humans and tools. Diagnostics, source changes, evidence and unchecked assumptions belong in the review, with links to originals and test output. A dependency graph or SQL preview must disclose whether it represents static approximation or runtime observation.

## UX quality gates

For each view/command, test keyboard-only use, focus preservation, screen-reader labels, high contrast, light/dark themes and zoom. Use native VS Code controls where sufficient. Webviews need restrictive CSP, validated message schemas, escaped untrusted content, bounded data and lifecycle cleanup. Animation/layout work must respect the performance budget and reduced motion. A futuristic appearance must not depend on continuous animation or fabricated metrics.

Acceptance includes first-use discovery, loading, cancellation, no-project, unsupported capability, stale data and recoverable error states. Remote extension hosts need an explicit support label and path/process ownership tests before claiming remote support; browser-only VS Code support is outside the JVM-first 1.0 promise.

Required invariants: `INV-OWN-003/004/005`, `INV-ID-003/005`, `INV-STATE-004/005`, `INV-DISC-001/003`, `INV-PERF-001/002`, `INV-TOOL-002`, `INV-EDIT-001`.
