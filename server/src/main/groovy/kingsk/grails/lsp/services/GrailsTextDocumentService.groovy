package kingsk.grails.lsp.services

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.providers.document.*
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
    private final GrailsImplementationProvider implementationProvider
    private final GrailsFormattingProvider formattingProvider
    private final GrailsFoldingRangeProvider foldingRangeProvider
    private final GrailsCodeActionProvider codeActionProvider
    private final GrailsReferenceProvider referenceProvider
    private final GrailsSignatureHelpProvider signatureHelpProvider
    private final GrailsDocumentSymbolProvider documentSymbolProvider
    private final GrailsCodeLensProvider codeLensProvider
    private final GrailsInlayHintProvider inlayHintProvider
    private final GrailsRenameProvider renameProvider
    private final GrailsSemanticTokensProvider semanticTokensProvider
    final GrailsYamlIntelligenceProvider yamlProvider

    private final java.util.concurrent.ScheduledExecutorService debounceExecutor = java.util.concurrent.Executors.newScheduledThreadPool(1)
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> compileTasks = new java.util.concurrent.ConcurrentHashMap<>()

    GrailsTextDocumentService(GrailsService service) {
        this.service = service

        // Initialize all providers
        this.completionProvider = new GrailsCompletionProvider(service)
        this.hoverProvider = new GrailsHoverProvider(service)
        this.definitionProvider = new GrailsDefinitionProvider(service)
        this.typeDefinitionProvider = new GrailsTypeDefinitionProvider(service)
        this.implementationProvider = new GrailsImplementationProvider(service)
        this.formattingProvider = new GrailsFormattingProvider(service)
        this.foldingRangeProvider = new GrailsFoldingRangeProvider(service)
        this.codeActionProvider = new GrailsCodeActionProvider(service)
        this.referenceProvider = new GrailsReferenceProvider(service)
        this.signatureHelpProvider = new GrailsSignatureHelpProvider(service)
        this.documentSymbolProvider = new GrailsDocumentSymbolProvider(service)
        this.codeLensProvider = new GrailsCodeLensProvider(service)
        this.inlayHintProvider = new GrailsInlayHintProvider(service)
        this.renameProvider = new GrailsRenameProvider(service)
        this.semanticTokensProvider = new GrailsSemanticTokensProvider(service)
        this.yamlProvider = new GrailsYamlIntelligenceProvider(service)

        log.debug("[DOCUMENT] GrailsTextDocumentService service initialized with all providers")
    }


    //==========================================================//
    //                     File Events                          //
    //==========================================================//

    @Override
    void didOpen(DidOpenTextDocumentParams params) {
        try {
            log.info("[DOCUMENT] - Opened: ${params.textDocument.uri}")
            service.activeProjectUri = service.projects.keySet().find { params.textDocument.uri.startsWith(it) } ?: service.activeProjectUri
            def textFile = service.fileTracker.didOpenFile(params)
            if (textFile) {
                service.compileAndVisitAST(textFile)
            }
        } catch (Exception e) {
            service.errorService.handleError("Failed to handle didOpen", e)
        }
    }

    @Override
    void didChange(DidChangeTextDocumentParams params) {
        try {
            log.info("[DOCUMENT] - Changed: ${params.textDocument.uri}")
            def textFile = service.fileTracker.didChangeFile(params)
            if (!textFile) return

            completionProvider.clearCaches(textFile.uri)

            compileTasks.remove(textFile.uri)?.cancel(false)
            def uriString = textFile.uri
            compileTasks[uriString] = debounceExecutor.schedule({ ->
                try {
                    def latestTextFile = service.fileTracker.getTextFile(uriString)
                    if (latestTextFile) {
                        service.compileAndVisitAST(latestTextFile)
                    }
                } catch (Exception e) {
                    service.errorService.handleError("Error in debounced compile", e)
                }
            } as Runnable, 500L, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (Exception e) {
            service.errorService.handleError("Failed to handle didChange", e)
        }
    }

    @Override
    void didClose(DidCloseTextDocumentParams params) {
        try {
            log.info("[DOCUMENT] - Closed: ${params.textDocument.uri}")
            def textFile = service.fileTracker.didCloseFile(params)
            if (textFile) {
                service.visitor.removeFileWithDependencies(textFile.uri)
                service.diagnostics.clearDiagnosticsForFile(textFile.uri)
                completionProvider.clearCaches(textFile.uri)
            }
        } catch (Exception e) {
            service.errorService.handleError("Failed to handle didClose", e)
        }
    }

    @Override
    void didSave(DidSaveTextDocumentParams params) {
        log.info("[DOCUMENT] - Saved: ${params.textDocument.uri}")
    }

    //==========================================================//
    //                    LSP Feature Handlers                  //
    //==========================================================//

    //-------------------- HOVER ------------------//

    @Override
    CompletableFuture<Hover> hover(HoverParams params) {
        TextFile textFile = service.fileTracker.getTextFile(params.textDocument.uri)
        if (textFile?.uri?.endsWith(".yml") || textFile?.uri?.endsWith(".yaml")) {
            return yamlProvider.provideHover(textFile, params.position)
        }
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
        TextFile textFile = service.fileTracker.getTextFile(params.textDocument.uri)
        if (textFile?.uri?.endsWith(".yml") || textFile?.uri?.endsWith(".yaml")) {
            return yamlProvider.provideDefinition(textFile, params.position).thenApply { list -> Either.forLeft(list) }
        }
        return definitionProvider.provideDefinition(params.textDocument, params.position)
    }

    //------------------- TYPE DEFINITION ------------//

    @Override
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(TypeDefinitionParams params) {
        return typeDefinitionProvider.provideTypeDefinition(params.textDocument, params.position)
    }

    //------------------- IMPLEMENTATION ------------//

    @Override
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(ImplementationParams params) {
        return implementationProvider.provideImplementation(params.textDocument, params.position)
    }

    //------------------- REFERENCES -----------------//

    @Override
    CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return referenceProvider.provideReferences(params.textDocument, params.position, params.context)
    }

    @Override
    CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        return formattingProvider.provideFormatting(params.textDocument, params.options)
    }

    @Override
    CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        return foldingRangeProvider.provideFoldingRanges(params.textDocument)
    }

    //------------------- RENAME ---------------------//

    @Override
    CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return renameProvider.provideRename(params)
    }

    //------------------- SIGNATURE HELP -------------//

    @Override
    CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
        return signatureHelpProvider.provideSignatureHelp(params.textDocument, params.position, params.context)
    }

    //------------------ COMPLETION ------------------//

    @Override
    CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        TextFile textFile = service.fileTracker.getTextFile(params.textDocument.uri)
        if (textFile?.uri?.endsWith(".yml") || textFile?.uri?.endsWith(".yaml")) {
            return yamlProvider.provideCompletions(textFile, params.position)
        }
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

//	@Override
//	CompletableFuture<DocumentDiagnosticReport> diagnostic(DocumentDiagnosticParams params) {
//		return service.diagnostics.provideDocumentDiagnostics(params.textDocument, params.identifier, params.previousResultId)
//	}

    //------------------- COMMANDS ------------------//
    @Override
    CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return codeActionProvider.provideCodeActions(params)
    }

    @Override
    CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        return semanticTokensProvider.provideSemanticTokens(params)
    }
}
