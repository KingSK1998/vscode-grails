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

    GrailsHoverProvider(GrailsService service) {
        super(service)
    }

    CompletableFuture<Hover> provideHover(TextDocumentIdentifier textDocument, Position position) {
        def offsetNode = getNodeAtPosition(textDocument, position)
        if (!offsetNode) {
            log.debug("[HOVER] No ASTNode found at the specified position.")
            return nullResult()
        }

        def definitionNode = getDefinitionNode(offsetNode, false) ?: offsetNode

        // Use DocumentationHelper for consistent documentation generation
        def documentation = DocumentationHelper.getDocumentation(
            definitionNode,
            project?.isGrailsProject ?: false,
            visitor,
            DocumentationType.HOVER
        )

        if (!documentation?.value) {
            log.debug("[HOVER] No hover content found for node type: ${definitionNode.class.simpleName}")
            return nullResult()
        }

        CompletableFuture.completedFuture(new Hover(documentation))
    }
}
