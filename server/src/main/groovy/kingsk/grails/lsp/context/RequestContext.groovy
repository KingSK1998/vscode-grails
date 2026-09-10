package kingsk.grails.lsp.context

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.state.VersionedSnapshot
import kingsk.grails.lsp.model.dto.GradleModel
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.index.MethodScopeCache
import kingsk.grails.lsp.index.GroovydocCache

/**
 * Request-scoped context that binds an LSP operation to a specific VersionedSnapshot and lease.
 * ELIMINATES: State-drift during long-running provider executions.
 * Tracks reader lease to prevent memory reclamation during in-flight executions.
 */
@CompileStatic
class RequestContext implements AutoCloseable {
    private final String uri
    private final VersionedSnapshot snapshot
    private final kingsk.grails.lsp.context.ProviderContext providerContext
    private final CompilationContext compilationContext
    private final RequestLease lease
    private final MethodScopeCache methodScopeCache
    private final GroovydocCache groovydocCache
    private final ClassLoader classLoader

    RequestContext(
        String uri,
        VersionedSnapshot snapshot,
        kingsk.grails.lsp.context.ProviderContext providerContext,
        CompilationContext compilationContext,
        RequestLease lease = null,
        MethodScopeCache methodScopeCache = null,
        GroovydocCache groovydocCache = null,
        ClassLoader classLoader = null
    ) {
        this.uri = uri
        this.snapshot = snapshot
        this.providerContext = providerContext
        this.compilationContext = compilationContext
        this.lease = lease
        this.methodScopeCache = methodScopeCache
        this.groovydocCache = groovydocCache
        this.classLoader = classLoader
    }

    String uri() { uri }
    VersionedSnapshot snapshot() { snapshot }
    ASTAccessor ast() { snapshot?.ast() }
    CompilationContext compilationContext() { compilationContext }
    MethodScopeCache methodScopeCache() { methodScopeCache }
    GroovydocCache groovydocCache() { groovydocCache }
    ClassLoader classLoader() { classLoader }

    GrailsService grailsService() { providerContext instanceof kingsk.grails.lsp.GrailsService ? (GrailsService) providerContext : null }
    GradleModel grailsProject() { snapshot?.gradleModel() }
    kingsk.grails.lsp.context.ProviderContext providerContext() { providerContext }

    @Override
    void close() {
        if (lease != null) {
            lease.close()
        }
    }
}
