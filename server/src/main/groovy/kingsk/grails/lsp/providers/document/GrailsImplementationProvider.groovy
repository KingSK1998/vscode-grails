package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.services.WorkspaceManager
import kingsk.grails.lsp.utils.ast.ASTUtils
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import org.eclipse.lsp4j.*
import org.codehaus.groovy.ast.ASTNode
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsImplementationProvider extends BaseProvider {

    GrailsImplementationProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        super(providerContext, workspaceManager)
    }

    CompletableFuture<List<Location>> provideImplementation(TextDocumentIdentifier textDocument, Position position) {
        def token = createCancellationToken(textDocument.uri)
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync {
            try {
                checkCancellation(token)
                def ctx = createRequestContext(textDocument.uri)
                def offsetNode = getNodeAtPosition(ctx, position)
                
                if (!offsetNode) return [] as List<Location>

                checkCancellation(token)
                def defNode = GrailsASTHelper.getDefinition(offsetNode, false, (GrailsASTVisitor) ctx.ast())
                
                List<Location> implementationLocations = []
                
                // Very basic implementation search
                ctx.ast().getNodes(ctx.uri()).each { potentialImpl ->
                    // Implementation logic here
                }

                if (implementationLocations.isEmpty() && defNode) {
                    String uri = ctx.ast().getURI(defNode) ?: textDocument.uri
                    implementationLocations << ASTUtils.astNodeToLocation(defNode, uri)
                }

                return implementationLocations
            } finally {
                recordHealth("implementation", System.currentTimeMillis() - startTime, true)
            }
        }
    }
}
