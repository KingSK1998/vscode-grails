package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.context.CompilationContext
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.core.compiler.GrailsCompiler
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
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier

import java.util.concurrent.CompletableFuture

@CompileStatic
abstract class BaseProvider {
    private final ProviderContext _providerContext
    private final CompilationContext _compilationContext
    private final GrailsProjectGetter _projectGetter
    private final GrailsService _service

    BaseProvider(ProviderContext providerContext, CompilationContext compilationContext, GrailsProjectGetter projectGetter, GrailsService service) {
        this._providerContext = providerContext
        this._compilationContext = compilationContext
        this._projectGetter = projectGetter
        this._service = service
    }

    BaseProvider(GrailsService service) {
        this(service as ProviderContext, service as CompilationContext, { service.project }, service)
    }

    protected GrailsASTVisitor getVisitor()             { _compilationContext.visitor }
    protected FileContentTracker getFileTracker()       { _compilationContext.fileTracker }
    protected GrailsLspConfig getConfig()               { _providerContext.config }
    protected GrailsCompiler getCompiler()              { _compilationContext.compiler }
    protected ErrorService getErrorService()            { _providerContext.errorService }
    protected GrailsDiagnosticService getDiagnostics()  { _service.diagnostics }
    protected CancellationService getCancellationService() { _providerContext.cancellationService }
    protected ProviderHealthService getHealthService() { _providerContext.healthService }
    protected GrailsProject getProject()                { _projectGetter.getProject() }
    protected GrailsService getService()               { _service }

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

    protected <T> T withReadLock(groovy.lang.Closure<T> closure) {
        _service.withReadLock(closure)
    }

    protected ASTNode getNodeAtPosition(String uri, Position position) {
        withReadLock {
            if (!visitor || visitor.empty) return null
            visitor.getNodeAtPosition(TextFile.normalizePath(uri), position)
        }
    }

    protected ASTNode getNodeAtPosition(TextDocumentIdentifier textDocument, Position position) {
        getNodeAtPosition(textDocument.uri, position)
    }

    protected ASTNode getNodeAtLineAndColumn(TextDocumentIdentifier textDocument, int line, int character) {
        withReadLock {
            if (!visitor || visitor.empty) return null
            visitor.getNodeAtLineAndColumn(TextFile.normalizePath(textDocument.uri), line, character)
        }
    }

    protected ASTNode getDefinitionNode(ASTNode offsetNode, boolean includeDeclaration = false) {
        withReadLock {
            offsetNode ? GrailsASTHelper.getDefinition(offsetNode, includeDeclaration, visitor) : null
        }
    }

    protected static <T> CompletableFuture<T> emptyResult(T emptyValue) {
        CompletableFuture.completedFuture(emptyValue)
    }

    protected static <T> CompletableFuture<T> nullResult() {
        CompletableFuture.completedFuture(null)
    }

    interface GrailsProjectGetter {
        GrailsProject getProject()
    }
}