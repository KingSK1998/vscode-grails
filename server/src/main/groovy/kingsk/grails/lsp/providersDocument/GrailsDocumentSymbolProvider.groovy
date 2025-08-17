package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.utils.ASTUtils
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
	
	GrailsDocumentSymbolProvider(GrailsService service) {
		super(service)
	}
	
	CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> provideDocumentSymbols(TextDocumentIdentifier textDocument) {
		if (!textDocument?.uri) {
			log.warn("[DOCUMENT_SYMBOLS] TextDocument or URI is null")
			return emptyResult([])
		}
		
		String uri = TextFile.normalizePath(textDocument.uri)
		log.info("[DOCUMENT_SYMBOLS] Providing document symbols for: ${uri}")
		
		if (!visitor || visitor.empty) {
			log.warn("[DOCUMENT_SYMBOLS] AST visitor unavailable for: $uri")
			return emptyResult([])
		}
		
		try {
			List<Either<SymbolInformation, DocumentSymbol>> symbols = []
			// Get only class nodes - much cleaner than 351+ random nodes
			visitor.getClassNodes(uri).each { classNode ->
				DocumentSymbol symbol = ASTUtils.astNodeToDocumentSymbol(classNode, uri)
				if (symbol) {
					symbols.add(Either.forRight(symbol))
				}
			}
			
			log.debug("[DOCUMENT_SYMBOLS] Found ${symbols.size()} symbols for: $uri")
			return CompletableFuture.completedFuture(symbols)
		} catch (Exception e) {
			log.error("[DOCUMENT_SYMBOLS] Error providing symbols for $uri: ${e.message}", e)
			return emptyResult([])
		}
	}
}
