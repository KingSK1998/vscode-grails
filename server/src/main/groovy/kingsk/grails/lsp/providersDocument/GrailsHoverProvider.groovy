package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.DocumentationType
import kingsk.grails.lsp.utils.DocumentationHelper
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsHoverProvider extends BaseProvider {
	
	GrailsHoverProvider(GrailsService grailsService) {
		super(grailsService)
	}
	
	CompletableFuture<Hover> provideHover(TextDocumentIdentifier textDocument, Position position) {
		ASTNode offsetNode = getNodeAtPosition(textDocument, position)
		if (!offsetNode) {
			log.debug("[HOVER] No ASTNode found at the specified position.")
			return nullResult()
		}
		
		ASTNode definitionNode = getDefinitionNode(offsetNode, false)
		if (!definitionNode) {
			// If we can't find a definition node, try to use the offset node directly
			definitionNode = offsetNode
			log.debug("[HOVER] Using offset node as definition node: ${definitionNode.class.simpleName}")
		}
		
		// Use DocumentationHelper for consistent documentation generation
		MarkupContent documentation = DocumentationHelper.getDocumentation(definitionNode, service, DocumentationType.HOVER)
		if (!documentation?.value) {
			log.debug("[HOVER] No hover content found for node type: ${definitionNode.class.simpleName}")
			return nullResult()
		}
		
		Hover hover = new Hover(documentation)
		return CompletableFuture.completedFuture(hover)
	}
}
