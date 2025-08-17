package kingsk.grails.lsp.services

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.providersDocument.*
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService

import java.util.concurrent.CompletableFuture

/**
 * Clean LSP TextDocumentService implementation
 *
 * Responsibilities:
 * - Handle LSP protocol events (didOpen, didChange, didClose, didSave)
 * - Delegate LSP feature requests to appropriate providers
 * - Manage provider lifecycle
 *
 * Does NOT handle:
 * - AST context creation (providers handle this)
 * - Caching strategies (providers handle this)
 * - Complex business logic (providers handle this)
 */
@Slf4j
@CompileStatic
class GrailsTextDocumentService implements TextDocumentService {
	
	private final GrailsService service
	
	// Provider instances - initialized once for performance
	private final GrailsCompletionProvider completionProvider
	private final GrailsHoverProvider hoverProvider
	private final GrailsDefinitionProvider definitionProvider
	private final GrailsTypeDefinitionProvider typeDefinitionProvider
	private final GrailsReferenceProvider referenceProvider
	private final GrailsSignatureHelpProvider signatureHelpProvider
	private final GrailsDocumentSymbolProvider documentSymbolProvider
	private final GrailsCodeLensProvider codeLensProvider
	private final GrailsInlayHintProvider inlayHintProvider
	
	GrailsTextDocumentService(GrailsService service) {
		this.service = service
		
		// Initialize all providers
		this.completionProvider = new GrailsCompletionProvider(service)
		this.hoverProvider = new GrailsHoverProvider(service)
		this.definitionProvider = new GrailsDefinitionProvider(service)
		this.typeDefinitionProvider = new GrailsTypeDefinitionProvider(service)
		this.referenceProvider = new GrailsReferenceProvider(service)
		this.signatureHelpProvider = new GrailsSignatureHelpProvider(service)
		this.documentSymbolProvider = new GrailsDocumentSymbolProvider(service)
		this.codeLensProvider = new GrailsCodeLensProvider(service)
		this.inlayHintProvider = new GrailsInlayHintProvider(service)
		
		log.debug("[DOCUMENT] GrailsTextDocumentService service initialized with all providers")
	}
	
	
	//==========================================================//
	//                     File Events                          //
	//==========================================================//
	
	@Override
	void didOpen(DidOpenTextDocumentParams params) {
		log.info("[DOCUMENT] - Opened: ${params.textDocument.uri}")
		TextFile textFile = service.fileTracker.didOpenFile(params)
		if (!textFile) return
		service.onDocumentOpened(textFile)
	}
	
	@Override
	void didChange(DidChangeTextDocumentParams params) {
		log.info("[DOCUMENT] - Changed: ${params.textDocument.uri}")
		TextFile textFile = service.fileTracker.didChangeFile(params)
		if (!textFile) return
		
		// Clear cache for this file before reprocessing
		completionProvider.clearCaches(textFile.uri)
		
		service.onDocumentChanged(textFile)
	}
	
	@Override
	void didClose(DidCloseTextDocumentParams params) {
		log.info("[DOCUMENT] - Closed: ${params.textDocument.uri}")
		TextFile textFile = service.fileTracker.didCloseFile(params)
		if (!textFile) return
		service.onDocumentClosed(textFile)
		completionProvider.clearCaches(textFile.uri)
	}
	
	@Override
	void didSave(DidSaveTextDocumentParams params) {
		log.info("[DOCUMENT] - Saved: ${params.textDocument.uri}")
		//		fileTracker.didSaveFile(params)
	}
	
	//==========================================================//
	//                    LSP Feature Handlers                  //
	//==========================================================//
	
	//-------------------- HOVER ------------------//
	
	@Override
	CompletableFuture<Hover> hover(HoverParams params) {
		return hoverProvider.provideHover(params.textDocument, params.position)
	}
	
	//------------------- DOCUMENT SYMBOL -----------//
	
	@Override
	CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
		return documentSymbolProvider.provideDocumentSymbols(params.textDocument)
	}
	
	//------------------- DEFINITION -----------------//
	
	@Override
	CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
		return definitionProvider.provideDefinition(params.textDocument, params.position)
	}
	
	//------------------- TYPE DEFINITION ------------//
	
	@Override
	CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(TypeDefinitionParams params) {
		return typeDefinitionProvider.provideTypeDefinition(params.textDocument, params.position)
	}
	
	//------------------- REFERENCES -----------------//
	
	@Override
	CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
		return referenceProvider.provideReferences(params.textDocument, params.position, params.context)
	}
	
	//------------------- SIGNATURE HELP -------------//
	
	@Override
	CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
		return signatureHelpProvider.provideSignatureHelp(params.textDocument, params.position, params.context)
	}
	
	//------------------ COMPLETION ------------------//
	
	@Override
	CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
		return completionProvider.provideCompletions(params.textDocument, params.position, params.context)
	}
	
	@Override
	CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
		return completionProvider.resolveCompletionItem(unresolved)
	}
	
	//------------------ CODE LENS ------------------//
	
	@Override
	CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
		return codeLensProvider.provideCodeLens(params.textDocument)
	}
	
	@Override
	CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
		return codeLensProvider.resolveCodeLens(unresolved)
	}
	
	//------------------ INLAY HINT ------------------//
	
	@Override
	CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
		return inlayHintProvider.provideInlayHints(params.textDocument, params.range)
	}
	
	@Override
	CompletableFuture<InlayHint> resolveInlayHint(InlayHint unresolved) {
		return inlayHintProvider.resolveInlayHint(unresolved)
	}
	
	//------------------ DIAGNOSTICS ------------------//
	
	@Override
	CompletableFuture<DocumentDiagnosticReport> diagnostic(DocumentDiagnosticParams params) {
		return service.diagnostics.provideDocumentDiagnostics(params.textDocument, params.identifier, params.previousResultId)
	}
	
	//------------------- COMMANDS ------------------//
	@Override
	CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
		return CompletableFuture.completedFuture([])
	}
}
