package kingsk.grails.lsp.providers.document

import kingsk.grails.lsp.context.CompilationContext
import kingsk.grails.lsp.context.ProjectContext
import kingsk.grails.lsp.context.ProviderContext

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.utils.ast.ASTUtils
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either

import java.util.concurrent.CompletableFuture

/**
 * Simple and efficient DocumentSymbolProvider.
 * Just gets top-level class/module nodes and converts them to DocumentSymbols.
 */
@Slf4j
@CompileStatic
class GrailsDocumentSymbolProvider extends BaseProvider {
	
	GrailsDocumentSymbolProvider(ProviderContext providerContext, CompilationContext compilationContext, ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }
	
    CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> provideDocumentSymbols(TextDocumentIdentifier textDocument) {
        if (!textDocument?.uri) {
            log.warn("[DOCUMENT_SYMBOLS] TextDocument or URI is null")
            return emptyResult([] as List<Either<SymbolInformation, DocumentSymbol>>)
        }

        String uri = TextFile.normalizePath(textDocument.uri)
        log.info("[DOCUMENT_SYMBOLS] Providing document symbols for: ${uri}")

        if (!visitor || visitor.empty) {
            log.warn("[DOCUMENT_SYMBOLS] AST visitor unavailable for: $uri")
            return emptyResult([] as List<Either<SymbolInformation, DocumentSymbol>>)
        }

        try {
            List<Either<SymbolInformation, DocumentSymbol>> symbols = []
            visitor.getClassNodes(uri).each { classNode ->
                def symbol = ASTUtils.astNodeToDocumentSymbol(classNode, uri)
                if (symbol) {
                    symbols << Either.forRight(symbol)
                }
            }

            log.debug("[DOCUMENT_SYMBOLS] Found ${symbols.size()} symbols for: $uri")
            CompletableFuture.completedFuture(symbols)
        } catch (Exception e) {
            log.error("[DOCUMENT_SYMBOLS] Error providing symbols for $uri: ${e.message}", e)
            emptyResult([] as List<Either<SymbolInformation, DocumentSymbol>>)
        }
    }
}
