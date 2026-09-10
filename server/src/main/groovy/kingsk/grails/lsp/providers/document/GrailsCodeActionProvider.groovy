package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.services.WorkspaceManager
import kingsk.grails.lsp.utils.ast.ASTUtils
import org.eclipse.lsp4j.*
import org.codehaus.groovy.ast.ClassNode

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

@CompileStatic
class GrailsCodeActionProvider extends BaseProvider {

    GrailsCodeActionProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        super(providerContext, workspaceManager)
    }

    CompletableFuture<List<CodeAction>> provideCodeActions(CodeActionParams params) {
        def token = createCancellationToken(params.textDocument.uri)
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync({ ->
            RequestContext ctx = null
            try {
                checkCancellation(token)
                ctx = createRequestContext(params.textDocument.uri)
                String uri = params.textDocument.uri
                
                List<CodeAction> actions = []
                def classNodes = ctx.ast()?.getNodes(uri)
                if (!classNodes) return actions

                classNodes.each { node ->
                    if (node instanceof ClassNode) {
                        // Code action logic here
                    }
                }

                return actions
            } finally {
                ctx?.close()
                recordHealth("codeAction", System.currentTimeMillis() - startTime, true)
            }
        } as Supplier<List<CodeAction>>)
    }
}
