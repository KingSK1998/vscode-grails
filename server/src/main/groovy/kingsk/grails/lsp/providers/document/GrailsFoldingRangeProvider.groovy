package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.services.WorkspaceManager
import org.eclipse.lsp4j.*
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.stmt.Statement

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

@CompileStatic
class GrailsFoldingRangeProvider extends BaseProvider {

    GrailsFoldingRangeProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        super(providerContext, workspaceManager)
    }

    CompletableFuture<List<FoldingRange>> provideFoldingRanges(FoldingRangeRequestParams params) {
        def token = createCancellationToken(params.textDocument.uri)
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync({ ->
            RequestContext ctx = null
            try {
                checkCancellation(token)
                ctx = createRequestContext(params.textDocument.uri)
                String uri = params.textDocument.uri
                
                List<FoldingRange> ranges = []
                def nodes = ctx.ast()?.getNodes(uri)
                if (!nodes) return ranges

                nodes.each { node ->
                    if (node instanceof ClassNode) {
                        ClassNode clazz = node as ClassNode
                        addFoldingRange(ranges, clazz.lineNumber, clazz.lastLineNumber)
                        
                        clazz.methods.each { method ->
                            addFoldingRange(ranges, method.lineNumber, method.lastLineNumber)
                        }
                    }
                }

                return ranges
            } finally {
                ctx?.close()
                recordHealth("foldingRanges", System.currentTimeMillis() - startTime, true)
            }
        } as Supplier<List<FoldingRange>>)
    }

    private void addFoldingRange(List<FoldingRange> ranges, int start, int end) {
        if (start > 0 && end > start) {
            ranges << new FoldingRange(start - 1, end - 1)
        }
    }
}
