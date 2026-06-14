# TASK 2a.2: IndexBuilder & SymbolInfo Update
**EXECUTION MODE:** PATCH MODE

## Objective
Extend `SymbolInfo` with `fieldType` and implement `IndexBuilder` (TIER 2 utility) to extract AST nodes into `SymbolInfo` and `LocalSymbolInfo` records without ASTNode leakage.

## Files to modify
- `server/src/main/groovy/kingsk/grails/lsp/index/SymbolInfo.groovy`
- `server/src/main/groovy/kingsk/grails/lsp/index/IndexBuilder.groovy` (NEW)
- `server/src/test/groovy/kingsk/grails/lsp/index/IndexBuilderSpec.groovy` (NEW)

## Methods to add/modify
**1. `SymbolInfo.groovy`:**
- Add `String fieldType` to the record components (required for Phase 5 semantic detection).

**2. `IndexBuilder.groovy` (Static TIER 2 Utility):**
- `static List<SymbolInfo> buildSymbols(String uri, List<ClassNode> classNodes)`
- `static String buildDescriptor(ClassNode clazz, MethodNode method)` (append `#N` for dynamic overloads with same param count to prevent collisions)
- `static String buildDescriptor(ClassNode clazz, FieldNode field)`
- `static String buildDescriptor(ClassNode clazz, PropertyNode property)`
- `static List<LocalSymbolInfo> buildLocals(String uri, List<ClassNode> classNodes)` (walk method bodies, extract parameters and VariableExpressions).

## Execution Commands
**Build command:** `npm run build:server`
**Test command:** `cd server && ./gradlew test --tests *IndexBuilderSpec*`

## Pass Criteria
- BUILD SUCCESSFUL, 0 failures.
- `IndexBuilder` contains absolutely ZERO `ASTNode` references in its output.

## Status Update
- Update `server/STATUS.md`: Add `Phase 2a.2: IndexBuilder` -> ✅.

**STOP HERE. DO NOT CONTINUE TO THE NEXT STEP IN THE SAME SESSION.**
