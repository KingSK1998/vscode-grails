# Grails Language Server – Feature Responsibility Overview

This document explains how different language features in the Grails Language Server are handled — whether by Grails
artefact handlers, utility classes, or the client (VS Code). It also defines **execution scope** for caching or
batching: node-level, file-level, or workspace-level.

## 🧩 Feature Ownership Matrix

| Feature                    | Handler ✅ | Utility ✅ | Client ✅ | Notes                             |
| -------------------------- | ---------- | ---------- | --------- | --------------------------------- |
| Inlay Hints                | ✅         |            |           | Type hints for local variables    |
| Completion                 | ✅         |            |           | Context-sensitive autocomplete    |
| Hover                      | ✅         |            |           | Grails/Groovy doc                 |
| CodeLens (test run)        | ✅         |            |           | Unit test or method marker        |
| Document Symbols           | ✅         |            |           | AST-based structure               |
| Go to Definition           | ✅         |            |           | Type and usage tracing            |
| Find References            | ✅         |            |           | Backward analysis                 |
| Diagnostics (custom rules) | ✅         |            |           | Missing Grails annotations etc    |
| Artefact type by filename  |            | ✅         |           | Simple file pattern match         |
| Class/package detection    |            | ✅         |           | Extract from top-level class node |
| Test name matching         |            | ✅         |           | Regex or annotation parser        |
| Folding ranges             |            | ✅         | ✅        | AST or brace matcher              |
| Comments                   |            | ✅         | ✅        | Non-AST parsing                   |
| Formatting (basic)         |            | ✅         | ✅        | Prefer client formatting tools    |
| Syntax highlighting        |            |            | ✅        | Provided by grammar               |
| Auto-closing brackets      |            |            | ✅        | Editor handles it                 |
| Spellcheck, whitespace     |            |            | ✅        | Client extensions                 |

## 🔁 Feature Execution Scope

| Feature                    | AST Node Level | File Level    | Workspace Level | Notes                                |
| -------------------------- | -------------- | ------------- | --------------- | ------------------------------------ |
| Inlay Hints                | ✅             |               |                 | `def var = ...`                      |
| Completion                 | ✅             | ✅ (fallback) |                 | Depends on cursor location           |
| Hover                      | ✅             |               |                 | Doc, annotations, type               |
| CodeLens                   | ✅             |               |                 | e.g. method-level test runners       |
| Document Symbols           |                | ✅            |                 | Single-file symbol tree              |
| Go to Definition           | ✅             | ✅            | ✅ (index)      | Prefer cache across files            |
| References                 |                | ✅            | ✅              | Full index recommended               |
| Diagnostics (Grails rules) | ✅             | ✅            | ✅              | Validate imports, usage              |
| Artefact type detection    |                | ✅            |                 | Based on filename or content         |
| Class/package detection    | ✅             | ✅            |                 | Helps with symbol/diagnostic context |
| Test name matching         | ✅             | ✅            |                 | Used in CodeLens                     |
| Folding                    |                | ✅            |                 | Use brace scanner                    |
| Comments                   |                | ✅            |                 | Basic token scan                     |
| Formatting                 |                | ✅            |                 | Usually handled on file save         |
| Dependency resolution      |                | ✅            | ✅              | Track file import links              |
| Type index building        |                |               | ✅              | For full-project cross-ref           |
| Plugin and config sync     |                |               | ✅              | `application.yml`, `plugins.groovy`  |

## 🗃️ Caching Strategy Recommendations

- **Node-level:** Evaluate on-the-fly or lazily on demand (e.g. hover, inlay hints).
- **File-level:** Re-evaluate on file save or change (e.g. folding, symbols).
- **Workspace-level:** Compute once during load or rebuild (e.g. references, cross-file types, plugin definitions).
