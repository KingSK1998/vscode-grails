package kingsk.grails.lsp.context

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.state.VersionedSnapshot
import kingsk.grails.lsp.model.dto.GradleModel
import kingsk.grails.lsp.GrailsService

/**
 * Request-scoped context that binds an LSP operation to a specific VersionedSnapshot.
 * ELIMINATES: State-drift during long-running provider executions.
 */
@CompileStatic
class RequestContext {
    private final String uri
    private final VersionedSnapshot snapshot
    private final kingsk.grails.lsp.context.ProviderContext providerContext
    private final CompilationContext compilationContext

    RequestContext(String uri, VersionedSnapshot snapshot, kingsk.grails.lsp.context.ProviderContext providerContext, CompilationContext compilationContext) {
        this.uri = uri
        this.snapshot = snapshot
        this.providerContext = providerContext
        this.compilationContext = compilationContext
    }

    String uri() { uri }
    VersionedSnapshot snapshot() { snapshot }
    ASTAccessor ast() { snapshot.ast }
    CompilationContext compilationContext() { compilationContext }
    
    GrailsService grailsService() { providerContext instanceof kingsk.grails.lsp.GrailsService ? (GrailsService) providerContext : null }
    
    // Narrow access to metadata
    GradleModel grailsProject() { snapshot?.gradleModel }
    kingsk.grails.lsp.context.ProviderContext providerContext() { providerContext }
}
