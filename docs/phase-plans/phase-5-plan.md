# Phase 5: Semantic Intelligence & Refactoring — Detailed Implementation Plan

> **Current delivery order (2026-09-08):** These are candidate designs for R2/R4/R5 in the [product roadmap](../product-roadmap.md), not completed capabilities. Validate their evidence and prerequisites before implementation. A first set of useful agent read tools is planned in R3, before the entire IDE/refactoring surface is complete.

**Execution Mode:** ARCHITECTURE DESIGN MODE
**Last updated:** 2026-06-14

**ACTIVE_CONSTRAINT_SET:**
- Scope: Universal + Target
- Version Binding: §3 ACTIVE
- All rules from Phase 2-3 carry forward
- New constraint: Semantic model must not become God Object

**Dependency:** Phase 3 complete (VersionedSnapshot active, multi-root working).

---

## Dependency Graph

```text
Phase 3 (✅ required)
  └─► Phase 5a: Semantic Model
        ├─► Phase 5a.1: GrailsEntity type system
        ├─► Phase 5a.2: SemanticModelBuilder (Index → Semantic)
        └─► Phase 5a.3: Semantic layer in VersionedSnapshot
              └─► Phase 5b: Stable Symbol Identities
                    └─► Phase 5b.1: SymbolId generation
                    └─► Phase 5b.2: Cross-feature identity verification
                          └─► Phase 5c: Refactoring Transactions
                                ├─► Phase 5c.1: RefactoringContext
                                ├─► Phase 5c.2: RenameTransaction
                                └─► Phase 5c.3: Semantic consistency tests
```

---

## Phase 5a: Semantic Model

### STEP 1 — GrailsEntity Type System

```
Layer: Server
Goal: Define semantic types for Grails domain concepts
READS: None (type definitions only)
WRITES: New model classes in kingsk.grails.lsp.model.semantic
Active rules: §2 (state model — derived state), §1 (server layer)
Failure mode: N/A (type definitions)
Validation: No runtime behavior. Immutable records.
Confidence: High
```

**Actions:**
1. Create `model/semantic/` package:
   ```groovy
   sealed interface GrailsEntity permits ControllerEntity, ServiceEntity, DomainEntity, TagLibEntity {}

   record ControllerEntity(
       String descriptor,           // from SymbolInfo
       String name,
       List<ActionInfo> actions,    // URL-mapped methods
       List<String> interceptors
   ) implements GrailsEntity {}

   record ServiceEntity(
       String descriptor,
       String name,
       boolean transactional,
       List<String> injectedInto    // descriptors of classes that inject this service
   ) implements GrailsEntity {}

   record DomainEntity(
       String descriptor,
       String name,
       List<PropertyInfo> persistentProperties,
       List<ConstraintInfo> constraints,
       List<RelationshipInfo> relationships  // hasMany, belongsTo, hasOne
   ) implements GrailsEntity {}
   ```
2. All records — immutable, no ASTNode references.
3. State classification: Derived state. Built from Index + Grails conventions.

**Invalidation ownership:**
- Owner: SemanticModelBuilder (new)
- Trigger: Index change (via VersionedSnapshot commit)
- Scope: Per-file (incremental) or Full
- Strategy: Lazy (built on first semantic query, cached in snapshot)

---

### STEP 2 — SemanticModelBuilder

```
Layer: Server
Goal: Transform ProjectIndex symbols into GrailsEntity instances using Grails conventions
READS: IndexSnapshot (SymbolInfo entries), GrailsArtifactType
WRITES: SemanticModel (Map<String, GrailsEntity>)
Active rules: §2, §6 (dependency graph), §7 (invalidation), §13 (no Grails logic in UI)
Failure mode: Convention detection fails → entity not created, feature degrades to Tier 1 (raw symbol info)
Validation: TIER 2 static utility. No state. Input = IndexSnapshot, output = SemanticModel.
Confidence: Medium (Grails convention detection complexity)
```

**Actions:**
1. Create `SemanticModelBuilder.groovy` — TIER 2 static utility.
2. Convention rules:
   - Class in `grails-app/controllers/` with artifact type CONTROLLER → `ControllerEntity`
   - Class in `grails-app/services/` → `ServiceEntity`
   - Class in `grails-app/domain/` → `DomainEntity`
   - Methods in controllers → `ActionInfo` (with URL mapping inference)
   - Fields named `hasMany`, `belongsTo`, `hasOne` with `fieldType` containing `Map` or `Class` → `RelationshipInfo`
3. Input: `IndexSnapshot`. Output: `Map<String, GrailsEntity>`.
4. **GAP-12 FIX:** Relationship detection requires `fieldType` and field name, NOT just `modifierFlags`. Current `SymbolInfo` does NOT include field type name. Required action before SemanticModelBuilder can work:
   - Extend `SymbolInfo` record to include `String fieldType` (the declared type of the field, e.g., `"Map"`, `"Class"`, `"String"`).
   - `IndexBuilder.buildSymbols()` must extract field type from `FieldNode.type.name` at build time (this is safe — type name is a String, not an ASTNode).
   - Only after this extension can SemanticModelBuilder detect GORM relationships from IndexSnapshot alone.

---

### STEP 3 — Semantic Layer in VersionedSnapshot

```
Layer: Server
Goal: Include SemanticModel in VersionedSnapshot for version-bound semantic queries
READS: IndexSnapshot
WRITES: Extended VersionedSnapshot
Active rules: §3 (version binding), §2 (derived state)
Failure mode: SemanticModel build fails → snapshot committed without semantic layer. Providers fall back to raw index.
Validation: SemanticModel is optional in snapshot. Null = degraded, not failed.
Confidence: High
```

**Actions:**
1. Extend VersionedSnapshot:
   ```groovy
   record VersionedSnapshot(
       long version,
       IndexSnapshot index,
       SemanticModel semanticModel,  // nullable — degraded if absent
       Map<String, String> fileHashes,
       long timestamp
   ) {}
   ```
2. Commit protocol: build semantic model after index, include in snapshot.
3. If semantic build fails → commit snapshot with `semanticModel = null`.

---

## Phase 5b: Stable Symbol Identities

### STEP 4 — SymbolId Generation

```
Layer: Server
Goal: Generate durable symbol IDs independent of AST node instances
READS: SymbolInfo.descriptor (already exists)
WRITES: Verification that descriptor is stable across recompilations
Active rules: §2, §3
Failure mode: Descriptor changes across recompilation → bug in IndexBuilder.buildDescriptor()
Validation: Test: compile same file twice → same descriptors produced.
Confidence: High (descriptors already designed for this)
```

**Actions:**
1. Verify `IndexBuilder.buildDescriptor()` produces identical output for identical source.
2. **GAP-14 FIX — Descriptor uniqueness for dynamic Groovy overloads:**
   - Typed overloads: `pkg.ClassName#methodName(ParamType1,ParamType2)` — unique by parameter types
   - Dynamic overloads (`def method(args)`): all parameters become `Object`. Two `def` methods with different param counts must be disambiguated.
   - Rule: if descriptor collision detected during `buildSymbols()`, append `#N` suffix (e.g., `pkg.ClassName#process(Object)#1`, `pkg.ClassName#process(Object)#2`)
   - Log: `[INDEX] Descriptor collision detected for {descriptor}, using suffix {N}`
3. Test: `StableSymbolIdSpec` — compile, index, change whitespace, recompile, re-index, verify descriptors match.
4. Test: `DescriptorUniquenessSpec` — class with dynamic overloads produces unique descriptors with `#N` suffixes.

---

### STEP 5 — Cross-Feature Identity Verification

```
Layer: Server
Goal: Ensure Definition, References, Hover, Rename resolve same symbol for same position
READS: All providers querying same VersionedSnapshot
WRITES: Test assertions only
Active rules: §0.1, §3
Failure mode: N/A (test only)
Validation: All features return consistent descriptor for given position.
Confidence: High
```

**Actions:**
1. `SemanticConsistencySpec`:
   - For a given position, query HoverProvider, DefinitionProvider, ReferencesProvider.
   - Verify all resolve to same `SymbolInfo.descriptor`.
   - Verify Rename targets same descriptor.

---

## Phase 5c: Refactoring Transactions

### STEP 6 — RefactoringContext

```
Layer: Server
Goal: Create transactional context for multi-file refactoring operations
READS: VersionedSnapshot (captures snapshot at transaction start)
WRITES: Produces WorkspaceEdit (LSP type) — does NOT mutate server state directly
Active rules: §0.1 (partial failure), §3 (version binding), §8 (cancelable)
Failure mode: Validation fails → transaction aborted, no WorkspaceEdit applied. User notified.
Validation: Transaction is snapshot-bound. If snapshot version changes during transaction → abort (stale).
Confidence: Medium
```

**Actions:**
1. Create `RefactoringContext.groovy`:
   ```groovy
   @CompileStatic
   class RefactoringContext {
       final VersionedSnapshot snapshot   // captured at transaction start from ProjectContext
       final String targetDescriptor
       final CancellationService.CancellationToken token
       private final Map<String, List<TextEdit>> editsByUri = [:]

       // GAP-13 FIX: compare against VersionedSnapshot.version, not IndexSnapshot.version
       boolean isStale(ProjectContext projectContext) {
           projectContext.activeSnapshot.get().version != snapshot.version
       }

       void addEdit(String uri, TextEdit edit) {
           editsByUri.computeIfAbsent(uri, { [] }).add(edit)
       }

       WorkspaceEdit buildWorkspaceEdit() {
           new WorkspaceEdit(editsByUri.collectEntries { uri, edits ->
               [(uri): edits]
           })
       }
   }
   ```
2. Refactoring flow: capture `VersionedSnapshot` from `ProjectContext.activeSnapshot.get()` → find all references → validate → build edits → check `isStale(projectContext)` → apply or abort.

---

### STEP 7 — RenameTransaction

```
Layer: Server
Goal: Implement transactional Rename with validation and rollback
READS: VersionedSnapshot, ProjectIndex (references by descriptor)
WRITES: Produces WorkspaceEdit for client to apply
Active rules: §0.1, §3, §8, §9, §10
Failure mode: Stale snapshot → abort. Partial reference resolution → abort with warning. Cancellation → abort.
Validation: Never applies partial rename. All-or-nothing.
Confidence: Medium
```

**Actions:**
1. RenameProvider.prepareRename → verify symbol exists, return current name.
2. RenameProvider.rename → create RefactoringContext → find all references → validate all references resolvable → build WorkspaceEdit → check staleness → return.
3. If any reference is UNRESOLVED → warn user, require confirmation.

**Degradation tiers (Rename):**
- Tier 0: Full transactional rename across all files
- Tier 1: Single-file rename only (cross-file refs UNRESOLVED)
- Tier 2: None
- Tier 3: None

---

## Final Validation (Phase 5)

- [x] Semantic model is derived state, version-bound in snapshot.
- [x] No God Object: SemanticModelBuilder is TIER 2 static, entities are records.
- [x] Symbol IDs are stable across recompilations.
- [x] Cross-feature consistency verified by tests.
- [x] Refactoring is transactional: all-or-nothing with staleness check.
- [x] All failure modes defined with explicit degradation tiers.
- [x] No ASTNode references in semantic layer.
- [x] Cancelable at every step.
