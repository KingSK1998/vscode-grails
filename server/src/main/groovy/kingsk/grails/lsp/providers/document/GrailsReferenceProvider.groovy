package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.utils.ast.ASTUtils
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.TextDocumentIdentifier

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsReferenceProvider extends BaseProvider {
	
	GrailsReferenceProvider(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }
	
    CompletableFuture<List<? extends Location>> provideReferences(TextDocumentIdentifier textDocument, Position position, ReferenceContext context) {
        def offsetNode = getNodeAtPosition(textDocument, position)
        if (!offsetNode) {
            log.debug("[REFERENCES] No offset node found")
            return emptyResult([] as List<Location>)
        }
        log.debug("[REFERENCES] offsetNode: $offsetNode")

        def references = GrailsASTHelper.getReferences(offsetNode, visitor, position) ?: []
        log.debug("[REFERENCES] found ${references.size()} references")

        def locations = references.findResults { node ->
            def uri = visitor.getURI(node)
            uri ? ASTUtils.astNodeToLocation(node, uri) : null
        }

        log.debug("[REFERENCES] converted to ${locations.size()} locations")
        CompletableFuture.completedFuture(locations as List<Location>)
    }
}