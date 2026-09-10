package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.services.WorkspaceManager
import kingsk.grails.lsp.utils.ast.ASTUtils
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.codehaus.groovy.ast.ClassNode

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

@CompileStatic
class GrailsDocumentSymbolProvider extends BaseProvider {

    GrailsDocumentSymbolProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        super(providerContext, workspaceManager)
    }

    CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> provideDocumentSymbols(DocumentSymbolParams params) {
        def token = createCancellationToken(params.textDocument.uri)
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync({ ->
            RequestContext ctx = null
            try {
                checkCancellation(token)
                ctx = createRequestContext(params.textDocument.uri)
                String uri = params.textDocument.uri
                
                List<Either<SymbolInformation, DocumentSymbol>> symbols = []
                def nodes = ctx.ast()?.getNodes(uri)
                if (!nodes) return symbols

                nodes.each { node ->
                    if (node instanceof ClassNode) {
                        def symbol = ASTUtils.astNodeToDocumentSymbol(node as ClassNode, uri)
                        if (symbol) {
                            symbols << Either.forRight(symbol)
                        }
                    }
                }

                return symbols
            } finally {
                ctx?.close()
                recordHealth("documentSymbols", System.currentTimeMillis() - startTime, true)
            }
        } as Supplier<List<Either<SymbolInformation, DocumentSymbol>>>)
    }
}
