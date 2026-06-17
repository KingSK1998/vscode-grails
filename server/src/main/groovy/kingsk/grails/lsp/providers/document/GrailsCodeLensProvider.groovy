package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.services.WorkspaceManager
import kingsk.grails.lsp.utils.ast.ASTUtils
import kingsk.grails.lsp.model.enums.CodeLensMode
import kingsk.grails.lsp.utils.grails.GrailsArtefactUtils
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.codehaus.groovy.ast.ClassNode

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

@CompileStatic
class GrailsCodeLensProvider extends BaseProvider {

    GrailsCodeLensProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        super(providerContext, workspaceManager)
    }

    CompletableFuture<List<? extends CodeLens>> provideCodeLenses(CodeLensParams params) {
        def token = createCancellationToken(params.textDocument.uri)
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync({ ->
            try {
                checkCancellation(token)
                def ctx = createRequestContext(params.textDocument.uri)
                String uri = params.textDocument.uri
                
                List<CodeLens> codeLenses = []
                def classNodes = ctx.ast().getNodes(uri)
                if (!classNodes) return codeLenses as List<? extends CodeLens>

                CodeLensMode mode = getConfig().codeLensMode

                classNodes.each { node ->
                    if (node instanceof ClassNode) {
                        ClassNode classNode = node as ClassNode
                        def artifactType = GrailsArtefactUtils.getGrailsArtifactType(classNode, uri)
                        
                        addBasicCodeLenses(codeLenses, classNode, artifactType, uri, mode)
                        
                        if (mode == CodeLensMode.ADVANCED || mode == CodeLensMode.FULL) {
                            addAdvancedCodeLenses(codeLenses, classNode, artifactType, uri)
                        }
                        
                        if (mode == CodeLensMode.FULL) {
                            addFullCodeLenses(codeLenses, classNode, uri)
                        }
                    }
                }

                return codeLenses as List<? extends CodeLens>
            } finally {
                recordHealth("codeLens", System.currentTimeMillis() - startTime, true)
            }
        } as Supplier<List<? extends CodeLens>>)
    }

    private void addBasicCodeLenses(List<CodeLens> lenses, ClassNode classNode, Object artifactType, String uri, CodeLensMode mode) {
    }

    private void addAdvancedCodeLenses(List<CodeLens> lenses, ClassNode classNode, Object artifactType, String uri) {
    }

    private void addFullCodeLenses(List<CodeLens> lenses, ClassNode classNode, String uri) {
    }
}
