package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.utils.ast.ASTUtils
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ReferenceParams
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.services.WorkspaceManager
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor

import java.util.concurrent.CompletableFuture

/**
 * Modern Reference Provider using tiered lookup.
 */
@Slf4j
@CompileStatic
class GrailsReferenceProvider extends BaseProvider {

    GrailsReferenceProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        super(providerContext, workspaceManager)
    }

    CompletableFuture<List<? extends Location>> provideReferences(ReferenceParams params) {
        def textDocument = params.textDocument
        def position = params.position
        
        def token = createCancellationToken(textDocument.uri)
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync {
            try {
                checkCancellation(token)
                def ctx = createRequestContext(textDocument.uri)
                
                if (getConfig().referenceUsesIndex) {
                    def offsetNode = getNodeAtPosition(ctx, position)
                    if (offsetNode) {
                        String targetName = offsetNode.text
                        checkCancellation(token)
                        def refs = ctx.compilationContext().projectIndex.snapshot.getReferencesFor(targetName)
                        if (refs) {
                            log.info("[REFERENCES] path=index tier=1 count=${refs.size()}")
                            return refs.collect { new Location(it.fileUri, it.range) }
                        }
                    }
                }

                checkCancellation(token)
                log.info("[REFERENCES] path=ast tier=3 kind=fallback")
                def offsetNode = getNodeAtPosition(ctx, position)
                if (!offsetNode) {
                    log.debug("[REFERENCES] No offset node found")
                    return [] as List<Location>
                }

                def references = GrailsASTHelper.getReferences(offsetNode, (GrailsASTVisitor) ctx.ast(), position) ?: []

                def results = references.collect { refNode ->
                    String fileUri = ctx.ast().getURI(refNode) ?: TextFile.normalizePath(textDocument.uri)
                    ASTUtils.astNodeToLocation(refNode, fileUri)
                }.findAll { it != null }

                log.debug("[REFERENCES] found ${results.size()} references for ${offsetNode.text}")
                return results as List<Location>
            } finally {
                recordHealth("references", System.currentTimeMillis() - startTime, true)
            }
        }
    }
}
