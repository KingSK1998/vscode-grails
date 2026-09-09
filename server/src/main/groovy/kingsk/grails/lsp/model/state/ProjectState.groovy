package kingsk.grails.lsp.model.state

import groovy.transform.CompileStatic

@CompileStatic
enum ProjectState {
    INITIALIZING,  // Loading initial Gradle model & dependencies
    READY,         // Fully loaded, accepting LSP requests
    HIBERNATED,    // Memory released, keeping only metadata & LKG
    REACTIVATING,  // Waking up from hibernation, restoring compiler
    FAILED,        // Fatal initialization or Gradle error
    DISPOSING      // Terminal state. Workspace folder removed. No transition out.
}
