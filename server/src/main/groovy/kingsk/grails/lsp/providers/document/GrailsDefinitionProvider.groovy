package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.utils.ast.ASTUtils
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
        def offsetNode = getNodeAtPosition(textDocument, position)
        if (!offsetNode) {
            log.debug("[DEFINITION] No offset node found")
            return emptyResult(Either.forLeft([]))
        }

        def definitionNode = getDefinitionNode(offsetNode, false)
        if (!definitionNode || definitionNode.lineNumber == -1 || definitionNode.columnNumber == -1) {
            log.debug("[DEFINITION] No valid definition node found")
            return emptyResult(Either.forLeft([]))
        }

        String definitionURI = visitor.getURI(definitionNode) ?: TextFile.normalizePath(textDocument.uri)
        def location = ASTUtils.astNodeToLocation(definitionNode, definitionURI)

        if (!location) {
            log.debug("[DEFINITION] Could not create location")
            return emptyResult(Either.forLeft([]))
        }

        CompletableFuture.completedFuture(Either.forLeft([location]))
    }
}
