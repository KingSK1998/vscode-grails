package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.utils.ASTUtils
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsDefinitionProvider extends BaseProvider {
	
	GrailsDefinitionProvider(GrailsService service) {
		super(service)
	}
	
	CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> provideDefinition(TextDocumentIdentifier textDocument, Position position) {
		ASTNode offsetNode = getNodeAtPosition(textDocument, position)
		if (!offsetNode) {
			log.debug("[DEFINITION] No offset node found")
			return emptyResult(Either.forLeft([]))
		}
		log.debug("[DEFINITION] offsetNode: ${offsetNode.class.simpleName}")
		
		ASTNode definitionNode = getDefinitionNode(offsetNode, false)
		if (!definitionNode || definitionNode.lineNumber == -1 || definitionNode.columnNumber == -1) {
			log.debug("[DEFINITION] No valid definition node found")
			return emptyResult(Either.forLeft([]))
		}
		log.debug("[DEFINITION] definitionNode: ${definitionNode.class.simpleName}")
		
		def definitionURI = visitor.getURI(definitionNode) ?: TextFile.normalizePath(textDocument.uri)
		Location location = ASTUtils.astNodeToLocation(definitionNode, definitionURI)
		
		if (!location) {
			log.debug("[DEFINITION] Could not create location")
			return emptyResult(Either.forLeft([]))
		}
		
		log.debug("[DEFINITION] location: $location")
		return CompletableFuture.completedFuture(Either.forLeft(Collections.singletonList(location)))
	}
}
