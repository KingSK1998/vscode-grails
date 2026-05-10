package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.utils.ASTUtils
import kingsk.grails.lsp.utils.GrailsASTHelper
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.TextDocumentIdentifier

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsReferenceProvider extends BaseProvider {
	
	GrailsReferenceProvider(GrailsService service) {
		super(service)
	}
	
    CompletableFuture<List<? extends Location>> provideReferences(TextDocumentIdentifier textDocument, Position position, ReferenceContext context) {
        def offsetNode = getNodeAtPosition(textDocument, position)
        if (!offsetNode) {
            log.debug("[REFERENCES] No offset node found")
            return emptyResult([] as List<Location>)
        }
        log.debug("[REFERENCES] offsetNode: $offsetNode")

        def references = GrailsASTHelper.getReferences(offsetNode, visitor, position)
        log.debug("[REFERENCES] found ${references.size()} references")

        def locations = references.findResults { node ->
            def uri = visitor.getURI(node)
            uri ? ASTUtils.astNodeToLocation(node, uri) : null
        }

        log.debug("[REFERENCES] converted to ${locations.size()} locations")
        CompletableFuture.completedFuture(locations as List<Location>)
    }
}