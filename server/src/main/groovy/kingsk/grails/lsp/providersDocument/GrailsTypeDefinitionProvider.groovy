package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.utils.ASTUtils
import kingsk.grails.lsp.utils.GrailsASTHelper
import kingsk.grails.lsp.utils.GrailsArtefactUtils
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsTypeDefinitionProvider extends BaseProvider {
	
	GrailsTypeDefinitionProvider(GrailsService service) {
		super(service)
	}
	
	CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> provideTypeDefinition(TextDocumentIdentifier textDocument, Position position) {
		def offsetNode = getNodeAtPosition(textDocument, position)
		if (!offsetNode) {
			log.debug("[TYPE DEFINITION] No offset node found")
			return nullResult()
		}
		log.debug("[TYPE DEFINITION] offsetNode: $offsetNode")
		
		// Try Grails-specific type resolution first
		def grailsDefinitionNode = GrailsArtefactUtils.tryToResolveGrailsTypeDefinition(offsetNode, visitor)
		
		// Fall back to standard Groovy type resolution if Grails-specific resolution fails
		ASTNode definitionNode = grailsDefinitionNode ?: GrailsASTHelper.getTypeDefinition(offsetNode, visitor)
		if (!definitionNode || definitionNode.lineNumber == -1 || definitionNode.columnNumber == -1) {
			log.debug("[TYPE DEFINITION] No valid definition node found")
			return emptyResult(Either.forLeft([]))
		}
		log.debug("[TYPE DEFINITION] definitionNode: $definitionNode")
		
		def definitionURI = visitor.getURI(definitionNode) ?: textDocument.uri
		
		Location location = ASTUtils.astNodeToLocation(definitionNode, definitionURI)
		if (!location) {
			log.debug("[TYPE DEFINITION] Could not create location")
			return emptyResult(Either.forLeft([]))
		}
		
		log.debug("[TYPE DEFINITION] location: $location")
		return CompletableFuture.completedFuture(Either.forLeft([location]))
	}
}
