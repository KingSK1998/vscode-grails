package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.services.WorkspaceManager
import kingsk.grails.lsp.utils.ast.ASTUtils
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import kingsk.grails.lsp.utils.grails.GrailsArtefactUtils
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor

import java.util.concurrent.CompletableFuture

@CompileStatic
class GrailsTypeDefinitionProvider extends BaseProvider {

    GrailsTypeDefinitionProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        super(providerContext, workspaceManager)
    }

    CompletableFuture<List<? extends Location>> provideTypeDefinition(TextDocumentIdentifier textDocument, Position position) {
        def token = createCancellationToken(textDocument.uri)
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync {
            try {
                checkCancellation(token)
                def ctx = createRequestContext(textDocument.uri)
                def offsetNode = getNodeAtPosition(ctx, position)
                
                if (!offsetNode) {
                    return [] as List<Location>
                }

                checkCancellation(token)
                def grailsDefinitionNode = GrailsArtefactUtils.tryToResolveGrailsTypeDefinition(offsetNode, (GrailsASTVisitor) ctx.ast())
                
                def definitionNode = grailsDefinitionNode ?: GrailsASTHelper.getTypeDefinition(offsetNode, (GrailsASTVisitor) ctx.ast())
                if (!definitionNode || definitionNode.lineNumber == -1 || definitionNode.columnNumber == -1) {
                    return [] as List<Location>
                }

                checkCancellation(token)
                String definitionURI = ctx.ast().getURI(definitionNode) ?: textDocument.uri
                
                def location = ASTUtils.astNodeToLocation(definitionNode, definitionURI)
                return (location ? [location] : []) as List<Location>
            } finally {
                recordHealth("typeDefinition", System.currentTimeMillis() - startTime, true)
            }
        }
    }
}
