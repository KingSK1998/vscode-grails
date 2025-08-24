# Grails Language Server - Client Reference

## Feature Responsibility Overview

This document explains how different language features in the Grails Language Server are handled — whether by Grails
artefact handlers, utility classes, or the client (VS Code). It also defines **execution scope** for caching or
batching: node-level, file-level, or workspace-level.

### 🧩 Feature Ownership Matrix

| Feature                    | Handler ✅ | Utility ✅ | Client ✅ | Notes                             |
|----------------------------|-----------|-----------|----------|-----------------------------------|
| Inlay Hints                | ✅         |           |          | Type hints for local variables    |
| Completion                 | ✅         |           |          | Context-sensitive autocomplete    |
| Hover                      | ✅         |           |          | Grails/Groovy doc                 |
| CodeLens (test run)        | ✅         |           |          | Unit test or method marker        |
| Document Symbols           | ✅         |           |          | AST-based structure               |
| Go to Definition           | ✅         |           |          | Type and usage tracing            |
| Find References            | ✅         |           |          | Backward analysis                 |
| Diagnostics (custom rules) | ✅         |           |          | Missing Grails annotations etc    |
| Artefact type by filename  |           | ✅         |          | Simple file pattern match         |
| Class/package detection    |           | ✅         |          | Extract from top-level class node |
| Test name matching         |           | ✅         |          | Regex or annotation parser        |
| Folding ranges             |           | ✅         | ✅        | AST or brace matcher              |
| Comments                   |           | ✅         | ✅        | Non-AST parsing                   |
| Formatting (basic)         |           | ✅         | ✅        | Prefer client formatting tools    |
| Syntax highlighting        |           |           | ✅        | Provided by grammar               |
| Auto-closing brackets      |           |           | ✅        | Editor handles it                 |
| Spellcheck, whitespace     |           |           | ✅        | Client extensions                 |

### 🔁 Feature Execution Scope

| Feature                    | AST Node Level | File Level   | Workspace Level | Notes                                |
|----------------------------|----------------|--------------|-----------------|--------------------------------------|
| Inlay Hints                | ✅              |              |                 | `def var = ...`                      |
| Completion                 | ✅              | ✅ (fallback) |                 | Depends on cursor location           |
| Hover                      | ✅              |              |                 | Doc, annotations, type               |
| CodeLens                   | ✅              |              |                 | e.g. method-level test runners       |
| Document Symbols           |                | ✅            |                 | Single-file symbol tree              |
| Go to Definition           | ✅              | ✅            | ✅ (index)       | Prefer cache across files            |
| References                 |                | ✅            | ✅               | Full index recommended               |
| Diagnostics (Grails rules) | ✅              | ✅            | ✅               | Validate imports, usage              |
| Artefact type detection    |                | ✅            |                 | Based on filename or content         |
| Class/package detection    | ✅              | ✅            |                 | Helps with symbol/diagnostic context |
| Test name matching         | ✅              | ✅            |                 | Used in CodeLens                     |
| Folding                    |                | ✅            |                 | Use brace scanner                    |
| Comments                   |                | ✅            |                 | Basic token scan                     |
| Formatting                 |                | ✅            |                 | Usually handled on file save         |
| Dependency resolution      |                | ✅            | ✅               | Track file import links              |
| Type index building        |                |              | ✅               | For full-project cross-ref           |
| Plugin and config sync     |                |              | ✅               | `application.yml`, `plugins.groovy`  |

### 🗃️ Caching Strategy Recommendations

- **Node-level:** Evaluate on-the-fly or lazily on demand (e.g. hover, inlay hints).
- **File-level:** Re-evaluate on file save or change (e.g. folding, symbols).
- **Workspace-level:** Compute once during load or rebuild (e.g. references, cross-file types, plugin definitions).

## Server Entry Point

### GrailsLanguageServer

```text
GrailsLanguageServer.initialize(params) {
    1. params.workspaceFolders is required else throws ResponseErrorException
    2. sendProgressBegin()
    3. setupWorkspace(params.workspaceFolders[0].uri, true)
    4. clientCapabilities.complete(params.getCapabilities()) (optional)
    5. configureServerCapabilities()
    6. sendProgressEnd()
    6. return InitializeResult(serverCapabilities)
}
```

## Progress Reporting

```groovy
// Progress Bar
client.notifyProgress({
	progressParam {
		token: "GLS-SERVER-SETUP"
		value: begin || report || end
	}
})

begin {
	title: "Indexing"
	message: "Started indexing..."
	percentage: 0
	cancellable: false
}

report {
	message: someMessage
	percentage: somePercentage
}

end {
	message: "Indexing completed"
	percentage: 100
}

// Notification
client.showMessage {
	type: MessageType.Error || MessageType.Warning || MessageType.Info
	message: "errorMessage"
}
```

## Configuration Events

### Workspace Configuration Changes

```json5
// Client sends configuration updates
{
  // DidChangeConfigurationParams from Eclipse LSP4J
  "params": {
    // Eclipse LSP4J default JSON object
    "settings": {
      // LSP configuration starts from here, with default values
      "grailsLsp": {
        // Indicates which CodeLens mode to use, Default is "ADVANCED"
        "codeLensMode": [
          "OFF",
          "BASIC",
          "ADVANCED",
          "FULL"
        ],
        // Indicates which compilation phase to use, default auto-detection based on project size
        "compilerPhase": 0,
        // Force recompilation on config change
        "shouldRecompileOnChange": false,
        // Indicates which completion detail level to use, Default is "ADVANCED"
        "completionDetail": [
          "BASIC",
          "STANDARD",
          "ADVANCED"
        ],
        // Indicates the maximum number of completion items to return
        "maxCompletionItems": 1000,
        "includeSnippets": true,
        // Enable Grails magic features
        "enableGrailsMagic": true
      }
    }
  }
}
```

### File Watching

Server requests client to watch:

- `**/*.groovy` - Source file changes, test file changes
- `**/build.gradle` - Build configuration changes
- `**/settings.gradle` - Build configuration changes
- `**/application.yml` - Grails configuration changes
- `**/src/**/*.groovy` - Source file changes (Groovy + Grails)
- `**/grails-app/**/*.groovy` - Grails artifacts

## Error Handling

### Server Errors

* Document level diagnostics are sent as `textDocument/publishDiagnostics` notifications
* Workspace level diagnostics are sent as `workspace/diagnostic` notifications
* Diagnostics notifications are sent from the server to the client to signal results of validation runs as
  `client/publishDiagnostics`