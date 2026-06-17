package kingsk.grails.lsp.providers.workspace

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.services.WorkspaceManager
import kingsk.grails.lsp.utils.ast.ASTUtils
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.providers.document.BaseProvider
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

@CompileStatic
class GrailsWorkspaceSymbolProvider extends BaseProvider {

    GrailsWorkspaceSymbolProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        super(providerContext, workspaceManager)
    }

    CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> provideWorkspaceSymbols(WorkspaceSymbolParams params) {
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync({ ->
            try {
                List<WorkspaceSymbol> results = []
                String query = params.query.toLowerCase()

                workspaceManager.getAllContexts().each { projectCtx ->
                    projectCtx.withReadLock {
                        projectCtx.visitor.allClassNodes.each { uri, nodes ->
                            nodes.each { ClassNode classNode ->
                                if (classNode.name.toLowerCase().contains(query)) {
                                    WorkspaceSymbol symbol = new WorkspaceSymbol()
                                    symbol.name = classNode.name
                                    symbol.kind = SymbolKind.Class
                                    symbol.location = Either.forLeft(ASTUtils.astNodeToLocation(classNode, uri))
                                    results << symbol
                                }
                            }
                        }
                    }
                }

                return Either.forRight(results) as Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>
            } finally {
                recordHealth("workspaceSymbols", System.currentTimeMillis() - startTime, true)
            }
        } as Supplier<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>>)
    }
}
