package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.CompilationContext
import kingsk.grails.lsp.context.ProjectContext
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
    protected final ProviderContext providerContext
    protected final CompilationContext compilationContext
    protected final ProjectContext projectContext

    BaseProvider(ProviderContext providerContext, CompilationContext compilationContext, ProjectContext projectContext) {
        this.providerContext = providerContext
        this.compilationContext = compilationContext
        this.projectContext = projectContext
    }

    protected GrailsASTVisitor getVisitor()             { compilationContext.visitor }
    protected FileContentTracker getFileTracker()       { compilationContext.fileTracker }
    protected GrailsLspConfig getConfig()               { providerContext.config }
    protected GrailsCompiler getCompiler()              { compilationContext.compiler }
    protected ErrorService getErrorService()            { providerContext.errorService }
    protected GrailsDiagnosticService getDiagnostics()  { providerContext.diagnostics }
    protected CancellationService getCancellationService() { providerContext.cancellationService }
    protected ProviderHealthService getHealthService() { providerContext.healthService }
    protected GrailsProject getProject()                { projectContext.project }
    

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
        compilationContext.withReadLock(closure)
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

    
}