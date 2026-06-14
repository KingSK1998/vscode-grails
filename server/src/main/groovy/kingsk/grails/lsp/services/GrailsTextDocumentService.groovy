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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Supplier

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

    private final ScheduledExecutorService debounceExecutor = Executors.newScheduledThreadPool(1)
    private final Map<String, ScheduledFuture<?>> compileTasks = new ConcurrentHashMap<>()
    private final Set<String> pendingChanges = ConcurrentHashMap.newKeySet()

    GrailsTextDocumentService(GrailsService service) {
        this.service = service
log.debug("[DOCUMENT] GrailsTextDocumentService initialized with lazy provider registry")
    }

    private GrailsCompletionProvider getCompletionProvider() {
        service.providerRegistry.getProvider(GrailsCompletionProvider)
    }

    private GrailsHoverProvider getHoverProvider() {
        service.providerRegistry.getProvider(GrailsHoverProvider)
    }

    private GrailsDefinitionProvider getDefinitionProvider() {
        service.providerRegistry.getProvider(GrailsDefinitionProvider)
    }

    private GrailsTypeDefinitionProvider getTypeDefinitionProvider() {
        service.providerRegistry.getProvider(GrailsTypeDefinitionProvider)
    }

    private GrailsImplementationProvider getImplementationProvider() {
        service.providerRegistry.getProvider(GrailsImplementationProvider)
    }

    private GrailsFormattingProvider getFormattingProvider() {
        service.providerRegistry.getProvider(GrailsFormattingProvider)
    }

    private GrailsFoldingRangeProvider getFoldingRangeProvider() {
        service.providerRegistry.getProvider(GrailsFoldingRangeProvider)
    }

    private GrailsCodeActionProvider getCodeActionProvider() {
        service.providerRegistry.getProvider(GrailsCodeActionProvider)
    }

    private GrailsReferenceProvider getReferenceProvider() {
        service.providerRegistry.getProvider(GrailsReferenceProvider)
    }

    private GrailsSignatureHelpProvider getSignatureHelpProvider() {
        service.providerRegistry.getProvider(GrailsSignatureHelpProvider)
    }

    private GrailsDocumentSymbolProvider getDocumentSymbolProvider() {
        service.providerRegistry.getProvider(GrailsDocumentSymbolProvider)
    }

    private GrailsCodeLensProvider getCodeLensProvider() {
        service.providerRegistry.getProvider(GrailsCodeLensProvider)
    }

    private GrailsInlayHintProvider getInlayHintProvider() {
        service.providerRegistry.getProvider(GrailsInlayHintProvider)
    }

    private GrailsRenameProvider getRenameProvider() {
        service.providerRegistry.getProvider(GrailsRenameProvider)
    }

    private GrailsSemanticTokensProvider getSemanticTokensProvider() {
        service.providerRegistry.getProvider(GrailsSemanticTokensProvider)
    }

    GrailsYamlIntelligenceProvider getYamlProvider() {
        service.providerRegistry.getProvider(GrailsYamlIntelligenceProvider)
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
                service.compiler.markDirty(textFile.uri)
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

            String uriString = textFile.uri

            if (pendingChanges.contains(uriString)) {
                compileTasks.remove(uriString)?.cancel(false)
                log.debug("[DOCUMENT] Coalescing change for: ${uriString}")
            }

            pendingChanges.add(uriString)

            long delay = service.config.debounceDelayMs
            compileTasks[uriString] = debounceExecutor.schedule({
                try {
                    pendingChanges.remove(uriString)
                    def latestTextFile = service.fileTracker.getTextFile(uriString)
                    if (latestTextFile) {
                        service.compiler.markDirty(latestTextFile.uri)
                        service.compileAndVisitAST(latestTextFile)
                    }
                } catch (Exception e) {
                    pendingChanges.remove(uriString)
                    service.errorService.handleError("Error in debounced compile", e)
                }
            } as Runnable, delay, TimeUnit.MILLISECONDS)
        } catch (Exception e) {
            service.errorService.handleError("Failed to handle didChange", e)
        }
    }

    @Override
    void didClose(DidCloseTextDocumentParams params) {
        try {
            log.info("[DOCUMENT] - Closed: ${params.textDocument.uri}")
            service.cancellationService.cancelForUri(params.textDocument.uri)
            def textFile = service.fileTracker.didCloseFile(params)
            if (textFile) {
                service.visitor.removeFileWithDependencies(textFile.uri)
                service.indexManager.evictFile(textFile.uri)
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

    private <T> CompletableFuture<T> safeProviderCall(String featureName, T defaultValue, Supplier<CompletableFuture<T>> supplier) {
        long t0 = System.currentTimeMillis()
        try {
            CompletableFuture<T> future = null
            service.withReadLock {
                future = supplier.get()
            }
            if (future == null) {
                return CompletableFuture.completedFuture(defaultValue)
            }
            return future.handle({ T result, Throwable throwable ->
                long duration = System.currentTimeMillis() - t0
                if (throwable != null) {
                    log.error("[DOCUMENT] Feature '$featureName' failed asynchronously: ${throwable.message}", throwable)
                    service.healthService.recordRequest(featureName, duration, false)
                    service.errorService.handleError("LSP feature '$featureName' failed", throwable)
                    return defaultValue
                }
                service.healthService.recordRequest(featureName, duration, true)
                return result
            } as BiFunction<T, Throwable, T>)
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - t0
            log.error("[DOCUMENT] Feature '$featureName' failed to initiate: ${e.message}", e)
            service.healthService.recordRequest(featureName, duration, false)
            service.errorService.handleError("LSP feature '$featureName' failed", e)
            return CompletableFuture.completedFuture(defaultValue)
        }
    }

    //-------------------- HOVER ------------------//

    @Override
    CompletableFuture<Hover> hover(HoverParams params) {
        return safeProviderCall("hover", (Hover) null) {
            TextFile textFile = service.fileTracker.getTextFile(params.textDocument.uri)
            if (textFile?.uri?.endsWith(".yml") || textFile?.uri?.endsWith(".yaml")) {
                return yamlProvider.provideHover(textFile, params.position)
            }
            return hoverProvider.provideHover(params.textDocument, params.position)
        }
    }

    //------------------- DOCUMENT SYMBOL -----------//

    @Override
    CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        return safeProviderCall("documentSymbol", [] as List<Either<SymbolInformation, DocumentSymbol>>) {
            return documentSymbolProvider.provideDocumentSymbols(params.textDocument)
        }
    }

    //------------------- DEFINITION -----------------//

    @Override
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        return safeProviderCall("definition", (Either<List<? extends Location>, List<? extends LocationLink>>) null) {
            TextFile textFile = service.fileTracker.getTextFile(params.textDocument.uri)
            if (textFile?.uri?.endsWith(".yml") || textFile?.uri?.endsWith(".yaml")) {
                return yamlProvider.provideDefinition(textFile, params.position).thenApply({ list -> Either.forLeft(list) } as Function)
            }
            return definitionProvider.provideDefinition(params.textDocument, params.position)
        }
    }

    //------------------- TYPE DEFINITION ------------//

    @Override
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(TypeDefinitionParams params) {
        return safeProviderCall("typeDefinition", (Either<List<? extends Location>, List<? extends LocationLink>>) null) {
            return typeDefinitionProvider.provideTypeDefinition(params.textDocument, params.position)
        }
    }

    //------------------- IMPLEMENTATION ------------//

    @Override
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(ImplementationParams params) {
        return safeProviderCall("implementation", (Either<List<? extends Location>, List<? extends LocationLink>>) null) {
            return implementationProvider.provideImplementation(params.textDocument, params.position)
        }
    }

    //------------------- REFERENCES -----------------//

    @Override
    CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return safeProviderCall("references", [] as List<Location>) {
            return referenceProvider.provideReferences(params.textDocument, params.position, params.context)
        }
    }

    @Override
    CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        return safeProviderCall("formatting", [] as List<TextEdit>) {
            return formattingProvider.provideFormatting(params.textDocument, params.options)
        }
    }

    @Override
    CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        return safeProviderCall("foldingRange", [] as List<FoldingRange>) {
            return foldingRangeProvider.provideFoldingRanges(params.textDocument)
        }
    }

    //------------------- RENAME ---------------------//

    @Override
    CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return safeProviderCall("rename", (WorkspaceEdit) null) {
            return renameProvider.provideRename(params)
        }
    }

    //------------------- SIGNATURE HELP -------------//

    @Override
    CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
        return safeProviderCall("signatureHelp", (SignatureHelp) null) {
            return signatureHelpProvider.provideSignatureHelp(params.textDocument, params.position, params.context)
        }
    }

    //------------------ COMPLETION ------------------//

    @Override
    CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        return safeProviderCall("completion", (Either<List<CompletionItem>, CompletionList>) Either.forLeft([] as List<CompletionItem>)) {
            TextFile textFile = service.fileTracker.getTextFile(params.textDocument.uri)
            if (textFile?.uri?.endsWith(".yml") || textFile?.uri?.endsWith(".yaml")) {
                return (CompletableFuture<Either<List<CompletionItem>, CompletionList>>) (Object) yamlProvider.provideCompletions(textFile, params.position)
            }
            return completionProvider.provideCompletions(params.textDocument, params.position, params.context)
        }
    }

    @Override
    CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        return safeProviderCall("resolveCompletionItem", unresolved) {
            return completionProvider.resolveCompletionItem(unresolved)
        }
    }

    //------------------ CODE LENS ------------------//

    @Override
    CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        return safeProviderCall("codeLens", [] as List<CodeLens>) {
            return codeLensProvider.provideCodeLens(params.textDocument)
        }
    }

    @Override
    CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
        return safeProviderCall("resolveCodeLens", unresolved) {
            return codeLensProvider.resolveCodeLens(unresolved)
        }
    }

    //------------------ INLAY HINT ------------------//

    @Override
    CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
        return safeProviderCall("inlayHint", [] as List<InlayHint>) {
            return inlayHintProvider.provideInlayHints(params.textDocument, params.range)
        }
    }

    @Override
    CompletableFuture<InlayHint> resolveInlayHint(InlayHint unresolved) {
        return safeProviderCall("resolveInlayHint", unresolved) {
            return inlayHintProvider.resolveInlayHint(unresolved)
        }
    }

    //------------------- COMMANDS ------------------//
    @Override
    CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return safeProviderCall("codeAction", [] as List<Either<Command, CodeAction>>) {
            return codeActionProvider.provideCodeActions(params)
        }
    }

    @Override
    CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        return safeProviderCall("semanticTokensFull", (SemanticTokens) null) {
            return semanticTokensProvider.provideSemanticTokens(params)
        }
    }

    void shutdown() {
        log.info("[DOCUMENT] Shutting down debounce executor...")
        debounceExecutor.shutdown()
        try {
            if (!debounceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                debounceExecutor.shutdownNow()
                log.warn("[DOCUMENT] Debounce executor did not terminate gracefully")
            }
            for (ScheduledFuture<?> task : compileTasks.values()) {
                task.cancel(false)
            }
            compileTasks.clear()
            pendingChanges.clear()
        } catch (InterruptedException e) {
            debounceExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
