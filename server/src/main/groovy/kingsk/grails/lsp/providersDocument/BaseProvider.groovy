package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.core.compiler.GrailsCompiler
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.model.GrailsLspConfig
import kingsk.grails.lsp.model.GrailsProject
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.services.FileContentTracker
import kingsk.grails.lsp.services.GrailsDiagnosticService
import kingsk.grails.lsp.utils.GrailsASTHelper
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier

import java.util.concurrent.CompletableFuture

@CompileStatic
abstract class BaseProvider {
    private final GrailsService _service

    BaseProvider(GrailsService service) {
        this._service = service
    }

    protected GrailsASTVisitor getVisitor()             { _service.visitor }
    protected FileContentTracker getFileTracker()       { _service.fileTracker }
    protected GrailsLspConfig getConfig()               { _service.config }
    protected GrailsCompiler getCompiler()              { _service.compiler }
    protected GrailsService getService()                 { _service }
    protected GrailsProject getProject()                { _service.getProject() }
    protected kingsk.grails.lsp.services.ErrorService getErrorService()  { _service.errorService }
    protected GrailsDiagnosticService getDiagnostics()  { _service.diagnostics }

    /**
     * Common pattern: get AST node at position with error handling
     */
    protected ASTNode getNodeAtPosition(String uri, Position position) {
        if (!visitor || visitor.empty) return null
        visitor.getNodeAtPosition(TextFile.normalizePath(uri), position)
    }

    /**
     * Common pattern: get AST node at position with error handling
     */
    protected ASTNode getNodeAtPosition(TextDocumentIdentifier textDocument, Position position) {
        getNodeAtPosition(textDocument.uri, position)
    }

    /**
     * Common pattern: get AST node at line/column with error handling
     */
    protected ASTNode getNodeAtLineAndColumn(TextDocumentIdentifier textDocument, int line, int character) {
        if (!visitor || visitor.empty) return null
        visitor.getNodeAtLineAndColumn(TextFile.normalizePath(textDocument.uri), line, character)
    }

    /**
     * Common pattern: get definition node with error handling
     */
    protected ASTNode getDefinitionNode(ASTNode offsetNode, boolean includeDeclaration = false) {
        offsetNode ? GrailsASTHelper.getDefinition(offsetNode, includeDeclaration, visitor) : null
    }

    /**
     * Common pattern: create empty result for CompletableFuture
     */
    protected static <T> CompletableFuture<T> emptyResult(T emptyValue) {
        CompletableFuture.completedFuture(emptyValue)
    }

    /**
     * Common pattern: create null result for CompletableFuture
     */
    protected static <T> CompletableFuture<T> nullResult() {
        CompletableFuture.completedFuture(null)
    }
}