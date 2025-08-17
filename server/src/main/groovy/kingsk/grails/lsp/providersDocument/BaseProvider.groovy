package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.utils.GrailsASTHelper
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier

import java.util.concurrent.CompletableFuture

/**
 * Base class for all LSP providers to eliminate redundant code.
 * Provides common functionality for AST node resolution and error handling.
 */
@Slf4j
@CompileStatic
abstract class BaseProvider {
	protected final GrailsService service
	
	BaseProvider(GrailsService grailsService) {
		this.service = grailsService
	}
	
	GrailsASTVisitor getVisitor() {
		return service.visitor
	}
	
	/**
	 * Common pattern: get AST node at position with error handling
	 */
	ASTNode getNodeAtPosition(String uri, Position position) {
		if (!visitor) {
			log.warn("${this.class.simpleName} - AST visitor is null")
			return null
		}
		if (visitor.empty) {
			log.warn("${this.class.simpleName} - AST visitor is empty")
			return null
		}
		
		return visitor.getNodeAtPosition(TextFile.normalizePath(uri), position)
	}
	
	/**
	 * Common pattern: get AST node at position with error handling
	 */
	protected ASTNode getNodeAtPosition(TextDocumentIdentifier textDocument, Position position) {
		return getNodeAtPosition(textDocument.uri, position)
	}
	
	/**
	 * Common pattern: get AST node at line/column with error handling
	 */
	protected ASTNode getNodeAtLineAndColumn(TextDocumentIdentifier textDocument, int line, int character) {
		if (!visitor) {
			log.warn("${this.class.simpleName} - AST visitor is null")
			return null
		}
		if (visitor.empty) {
			log.warn("${this.class.simpleName} - AST visitor is empty")
			return null
		}
		
		def uri = TextFile.normalizePath(textDocument.uri)
		return visitor.getNodeAtLineAndColumn(uri, line, character)
	}
	
	/**
	 * Common pattern: get definition node with error handling
	 */
	protected ASTNode getDefinitionNode(ASTNode offsetNode, boolean includeDeclaration = false) {
		if (!offsetNode) return null
		return GrailsASTHelper.getDefinition(offsetNode, includeDeclaration, visitor)
	}
	
	/**
	 * Common pattern: create empty result for CompletableFuture
	 */
	protected static <T> CompletableFuture<T> emptyResult(T emptyValue) {
		return CompletableFuture.completedFuture(emptyValue)
	}
	
	/**
	 * Common pattern: create null result for CompletableFuture
	 */
	protected static <T> CompletableFuture<T> nullResult() {
		return CompletableFuture.completedFuture(null)
	}
}