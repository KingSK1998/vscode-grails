package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.services.WorkspaceManager
import org.eclipse.lsp4j.*
import org.codehaus.groovy.ast.ClassNode

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

@CompileStatic
class GrailsInlayHintProvider extends BaseProvider {

    GrailsInlayHintProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        super(providerContext, workspaceManager)
    }

    CompletableFuture<List<InlayHint>> provideInlayHints(InlayHintParams params) {
        def token = createCancellationToken(params.textDocument.uri)
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync({ ->
            RequestContext ctx = null
            try {
                checkCancellation(token)
                ctx = createRequestContext(params.textDocument.uri)
                String uri = params.textDocument.uri
                
                List<InlayHint> hints = []
                def nodes = ctx.ast()?.getNodes(uri)
                if (!nodes) return hints

                nodes.each { node ->
                    if (node instanceof ClassNode) {
                        // Implementation of hint collection
                    }
                }

                return hints
            } finally {
                ctx?.close()
                recordHealth("inlayHints", System.currentTimeMillis() - startTime, true)
            }
        } as Supplier<List<InlayHint>>)
    }
}
