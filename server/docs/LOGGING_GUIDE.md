# Simple Logging Standardization

## ✅ **Simple Component-Based Logging**

### **Format: `[COMPONENT] message`**

#### **Examples:**

```
log.info("[COMPILER] Starting full project compilation for: MyProject")
log.info("[COMPILER] Added 15 source files to compilation unit")
log.info("[COMPILER] Incremental compilation: BookController.groovy")
log.warn("[COMPILER] Cannot compile null text file")
log.error("[COMPILER] Project compilation failed", exception)

log.info("[AST] Removing source unit: BookController.groovy")
log.info("[AST] Successfully removed source unit: BookController.groovy")

log.info("[COMPLETION] Resolving completions for: BookController.groovy")
log.info("[HOVER] Providing hover info for: BookService.groovy")
log.info("[DIAGNOSTICS] Running diagnostics on: 5 files")
```

### **Component Tags:**

- `[COMPILER]` - Compilation operations
- `[AST]` - AST manipulation
- `[COMPLETION]` - Code completion
- `[HOVER]` - Hover information
- `[DIAGNOSTICS]` - Error/warning diagnostics
- `[DEFINITION]` - Go to definition
- `[WORKSPACE]` - Workspace operations
- `[GRADLE]` - Gradle integration

### **Usage Guidelines:**

1. **Keep it simple** - Just add `[COMPONENT]` prefix to existing log messages
2. **Be descriptive** - Include relevant context (file names, counts, etc.)
3. **Use appropriate levels** - INFO for operations, WARN for issues, ERROR for failures
4. **Stay consistent** - Same component always uses same tag

### **Benefits:**

- **Easy to filter logs** by component
- **Clear identification** of which part of the system is logging
- **Simple to implement** - just prefix existing messages
- **No complex framework** - uses existing SLF4J logging