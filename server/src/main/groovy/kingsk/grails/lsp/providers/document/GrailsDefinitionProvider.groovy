package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
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

    GrailsDefinitionProvider(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }

    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> provideDefinition(TextDocumentIdentifier textDocument, Position position) {
        def token = createCancellationToken(textDocument.uri)
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync {
            try {
                checkCancellation(token)
                if (getConfig().definitionUsesIndex) {
                    def uri = textDocument.uri
                    
                    def local = compilationContext.methodScopeCache.getLocalAt(uri, position)
                    if (local) {
                        log.info("[DEFINITION] path=index tier=0 kind=local")
                        return Either.forLeft([new Location(local.fileUri, local.range)] as List<? extends Location>)
                    }
                    
                    checkCancellation(token)
                    def symbol = compilationContext.projectIndex.snapshot.getSymbolAt(uri, position)
                    if (symbol) {
                        log.info("[DEFINITION] path=index tier=1 kind=symbol")
                        return Either.forLeft([new Location(symbol.fileUri, symbol.selectionRange ?: symbol.range)] as List<? extends Location>)
                    }
                }

                checkCancellation(token)
                log.info("[DEFINITION] path=ast tier=3 kind=fallback")
                def offsetNode = getNodeAtPosition(textDocument, position)
                if (!offsetNode) {
                    log.debug("[DEFINITION] No offset node found")
                    return Either.forLeft([] as List<? extends Location>)
                }

                def definitionNode = getDefinitionNode(offsetNode, false)
                checkCancellation(token)

                if (!definitionNode || definitionNode.lineNumber == -1 || definitionNode.columnNumber == -1) {
                    log.debug("[DEFINITION] No valid definition node found")
                    return Either.forLeft([] as List<? extends Location>)
                }

                String definitionURI = visitor.getURI(definitionNode) ?: TextFile.normalizePath(textDocument.uri)
                def location = ASTUtils.astNodeToLocation(definitionNode, definitionURI)

                if (!location) {
                    log.debug("[DEFINITION] Could not create location")
                    return Either.forLeft([] as List<? extends Location>)
                }

                return Either.forLeft([location] as List<? extends Location>)
            } finally {
                recordHealth("definition", System.currentTimeMillis() - startTime, true)
            }
        }
    }
}
