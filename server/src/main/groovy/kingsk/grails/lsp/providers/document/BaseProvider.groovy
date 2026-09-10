package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.services.WorkspaceManager
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.model.config.GrailsLspConfig
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.services.CancellationService
import kingsk.grails.lsp.services.ErrorService
import kingsk.grails.lsp.services.FileContentTracker
import kingsk.grails.lsp.services.GrailsDiagnosticService
import kingsk.grails.lsp.services.ProviderHealthService
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import kingsk.grails.lsp.core.compiler.GrailsCompiler
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import kingsk.grails.lsp.model.state.VersionedSnapshot
import kingsk.grails.lsp.context.ProjectContextImpl
import kingsk.grails.lsp.context.CompilationContext
import kingsk.grails.lsp.context.DetachedASTAccessor
import kingsk.grails.lsp.context.RequestLease

import java.util.concurrent.CompletableFuture

@CompileStatic
abstract class BaseProvider {
    protected final ProviderContext providerContext
    protected final WorkspaceManager workspaceManager

    BaseProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        this.providerContext = providerContext
        this.workspaceManager = workspaceManager
    }

    protected GrailsLspConfig getConfig()               { providerContext.config }
    protected ErrorService getErrorService()            { providerContext.errorService }
    protected GrailsDiagnosticService getDiagnostics()  { providerContext.diagnostics }
    protected CancellationService getCancellationService() { providerContext.cancellationService }
    protected ProviderHealthService getHealthService() { providerContext.healthService }

    protected RequestContext createRequestContext(String uri) {
        ProjectContextImpl projectCtx = workspaceManager.getProjectForUri(uri)
        if (!projectCtx) {
            // Fallback empty snapshot
            return new RequestContext(
                uri,
                new VersionedSnapshot(0, new kingsk.grails.lsp.index.ProjectIndex("empty").snapshot, null, DetachedASTAccessor.INSTANCE, [:], 0),
                providerContext,
                null,
                null,
                null,
                null,
                null
            )
        }
        RequestLease lease = projectCtx.acquireLease()
        return new RequestContext(
            uri,
            lease.snapshot,
            providerContext,
            (CompilationContext) projectCtx,
            lease,
            projectCtx.methodScopeCache,
            projectCtx.groovydocCache,
            projectCtx.classLoaderUnsafeOrNull
        )
    }

    protected CancellationService.CancellationToken createCancellationToken(String uri) {
        cancellationService.createToken(uri)
    }

    protected void checkCancellation(CancellationService.CancellationToken token) {
        if (token) token.checkCancellation()
    }

    protected void recordHealth(String providerName, long durationMs, boolean success = true) {
        healthService.recordRequest(providerName, durationMs, success)
    }

    protected String getProviderName() {
        this.class.simpleName
    }

    protected FileContentTracker getFileTracker() {
        providerContext.fileTracker
    }

    protected ASTNode getNodeAtPosition(RequestContext ctx, Position position) {
        ctx.ast()?.getNodeAtPosition(TextFile.normalizePath(ctx.uri()), position)
    }

    protected ASTNode getNodeAtLineAndColumn(RequestContext ctx, int line, int character) {
        ctx.ast()?.getNodeAtLineAndColumn(TextFile.normalizePath(ctx.uri()), line, character)
    }

    protected ASTNode getDefinitionNode(ASTNode offsetNode, RequestContext ctx, boolean includeDeclaration = false) {
        offsetNode && ctx.ast() ? GrailsASTHelper.getDefinition(offsetNode, includeDeclaration, (GrailsASTVisitor) ctx.ast()) : null
    }

    protected static <T> CompletableFuture<T> emptyResult(T emptyValue) {
        CompletableFuture.completedFuture(emptyValue)
    }

    protected static <T> CompletableFuture<T> nullResult() {
        CompletableFuture.completedFuture(null)
    }
}
