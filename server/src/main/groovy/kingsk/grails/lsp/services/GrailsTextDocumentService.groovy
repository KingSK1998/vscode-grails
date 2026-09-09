package kingsk.grails.lsp.services

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.state.ProjectState
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.providers.document.*
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService

import java.util.List
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction
import java.util.function.BooleanSupplier
import java.util.function.Function
import java.util.function.Supplier

@Slf4j
@CompileStatic
class GrailsTextDocumentService implements TextDocumentService {
    private final GrailsService service
    // One pending revision per URI; the single worker also serializes close cleanup.
    // Superseded tasks are removed from the executor queue immediately.
    private final Map<String, PendingChange> pendingChanges = [:]
    private final Object admissionLock = new Object()
    private final ScheduledThreadPoolExecutor compilationExecutor
    private volatile boolean stopped = false

    GrailsTextDocumentService(GrailsService service) {
        this.service = service
        compilationExecutor = new ScheduledThreadPoolExecutor(1, { Runnable work ->
            def thread = new Thread(work, 'grails-document-compiler')
            thread.daemon = true
            return thread
        } as ThreadFactory)
        compilationExecutor.removeOnCancelPolicy = true
        compilationExecutor.executeExistingDelayedTasksAfterShutdownPolicy = false
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

    private GrailsYamlIntelligenceProvider getYamlProvider() {
        service.providerRegistry.getProvider(GrailsYamlIntelligenceProvider)
    }

    @Override
    void didOpen(DidOpenTextDocumentParams params) {
        try {
            log.info("[DOCUMENT] - Opened: ${params.textDocument.uri}")
            
            synchronized (admissionLock) {
                def textFile = service.fileTracker.didOpenFile(params, false)
                if (textFile) {
                    scheduleCompilation(textFile, 0L)
                }
            }
        } catch (Exception e) {
            service.errorService.handleError("Failed to handle didOpen", e)
        }
    }

    @Override
    void didChange(DidChangeTextDocumentParams params) {
        try {
            synchronized (admissionLock) {
                def textFile = service.fileTracker.didChangeFile(params, false)
                if (textFile) {
                    scheduleCompilation(textFile, Math.max(0L, service.config.debounceDelayMs))
                }
            }
        } catch (Exception e) {
            service.errorService.handleError("Failed to handle didChange", e)
        }
    }

    private void scheduleCompilation(TextFile tracked, long delayMs) {
        def revision = TextFile.create(tracked.uri, tracked.text)
        revision.version = tracked.version
        revision.fileState = tracked.fileState
        def pending = new PendingChange(revision)
        synchronized (pendingChanges) {
            if (stopped) return
            def previous = pendingChanges.put(revision.uri, pending)
            if (previous != null) {
                previous.task?.cancel(false)
                previous.finished.complete(null)
            }
            pending.task = compilationExecutor.schedule({ -> runPendingChange(pending) } as Runnable,
                delayMs, TimeUnit.MILLISECONDS)
        }
    }

    private boolean isCurrent(PendingChange pending) {
        synchronized (pendingChanges) {
            PendingChange current = pendingChanges.get(pending.revision.uri)
            return !stopped && current != null && current.is(pending)
        }
    }

    private void runPendingChange(PendingChange pending) {
        try {
            if (!isCurrent(pending)) return
            def revision = pending.revision
            def context = service.workspaceManager.getProjectForUri(revision.uri)
            if (context == null || context.state == ProjectState.DISPOSING) return
            if (revision.closed) {
                if (!isCurrent(pending)) return
                context.closeDocument(revision.uri)
                service.fileTracker.clearClosedFileDependencies(revision.uri)
                service.diagnostics.clearDiagnosticsForFile(revision.uri)
                return
            }
            service.fileTracker.updateFileDependenciesForSourceFile(revision)
            if (!isCurrent(pending)) return
            context.compileAndVisitAST(revision, { -> isCurrent(pending) } as BooleanSupplier)
        } catch (Exception e) {
            pending.finished.completeExceptionally(e)
            service.errorService.handleError('Error in background document compilation', e)
        } finally {
            synchronized (pendingChanges) {
                PendingChange current = pendingChanges.get(pending.revision.uri)
                if (current != null && current.is(pending)) {
                    pendingChanges.remove(pending.revision.uri)
                }
                pending.finished.complete(null)
            }
        }
    }

    /** Completion of work already scheduled for a URI; callers never block the LSP thread. */
    CompletableFuture<Void> compilationFinished(String uri) {
        synchronized (pendingChanges) {
            return pendingChanges.get(TextFile.normalizePath(uri))?.finished ?: CompletableFuture.completedFuture(null)
        }
    }

    /**
     * Re-admits the latest tracked buffers after their project context is registered.
     * The caller owns path-safe root matching and discovery-generation validation.
     * No replay copy is retained here; each URI is re-read under the notification
     * admission lock and enters the normal latest-revision queue.
     */
    void replayTrackedDocuments(Collection<String> uris) {
        if (uris == null || uris.isEmpty() || stopped) return
        synchronized (admissionLock) {
            if (stopped) return
            Set<String> uniqueUris = new LinkedHashSet<>(uris)
            for (String uri : uniqueUris) {
                if (!uri) continue
                TextFile tracked = service.fileTracker.getTextFile(uri)
                if (tracked == null || tracked.closed) continue
                scheduleCompilation(tracked, 0L)
            }
        }
    }

    @Override
    void didClose(DidCloseTextDocumentParams params) {
        try {
            log.info("[DOCUMENT] - Closed: ${params.textDocument.uri}")
            synchronized (admissionLock) {
                def textFile = service.fileTracker.didCloseFile(params)
                service.cancellationService.cancelForUri(params.textDocument.uri)
                service.cancellationService.cancelForUri(TextFile.normalizePath(params.textDocument.uri))
                if (textFile) {
                    scheduleCompilation(textFile, 0L)
                }
            }
        } catch (Exception e) {
            service.errorService.handleError("Failed to handle didClose", e)
        }
    }

    @Override
    void didSave(DidSaveTextDocumentParams params) {
    }

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

    @Override
    CompletableFuture<Hover> hover(HoverParams params) {
        return safeProviderCall("hover", (Hover) null) {
            TextFile textFile = service.fileTracker.getTextFile(params.textDocument.uri)
            if (textFile?.uri?.endsWith(".yml") || textFile?.uri?.endsWith(".yaml")) {
                return getYamlProvider().provideHover(textFile, params.position)
            }
            return getHoverProvider().provideHover(params.textDocument, params.position)
        }
    }

    @Override
    CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        return safeProviderCall("documentSymbol", [] as List<Either<SymbolInformation, DocumentSymbol>>) {
            return getDocumentSymbolProvider().provideDocumentSymbols(params)
        }
    }

    @Override
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        return safeProviderCall("definition", (Either<List<? extends Location>, List<? extends LocationLink>>) null) {
            TextFile textFile = service.fileTracker.getTextFile(params.textDocument.uri)
            if (textFile?.uri?.endsWith(".yml") || textFile?.uri?.endsWith(".yaml")) {
                return getYamlProvider().provideDefinition(textFile, params.position).thenApply({ list -> Either.forLeft(list) } as Function)
            }
            return getDefinitionProvider().provideDefinition(params.textDocument, params.position) as CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
        }
    }

    @Override
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(TypeDefinitionParams params) {
        return safeProviderCall("typeDefinition", (Either<List<? extends Location>, List<? extends LocationLink>>) null) {
            return getTypeDefinitionProvider().provideTypeDefinition(params.textDocument, params.position).thenApply({ list ->
                Either.forLeft(list)
            } as Function) as CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
        }
    }

    @Override
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(ImplementationParams params) {
        return safeProviderCall("implementation", (Either<List<? extends Location>, List<? extends LocationLink>>) null) {
            return getImplementationProvider().provideImplementation(params.textDocument, params.position).thenApply({ list ->
                Either.forLeft(list)
            } as Function) as CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
        }
    }

    @Override
    CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return safeProviderCall("references", [] as List<Location>) {
            return getReferenceProvider().provideReferences(params)
        }
    }

    @Override
    CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        return safeProviderCall("formatting", [] as List<TextEdit>) {
            return getFormattingProvider().provideFormatting(params.textDocument, params.options)
        }
    }

    @Override
    CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        return safeProviderCall("foldingRange", [] as List<FoldingRange>) {
            return getFoldingRangeProvider().provideFoldingRanges(params)
        }
    }

    @Override
    CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return safeProviderCall("rename", (WorkspaceEdit) null) {
            return getRenameProvider().provideRename(params)
        }
    }

    @Override
    CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
        return safeProviderCall("signatureHelp", (SignatureHelp) null) {
            return getSignatureHelpProvider().provideSignatureHelp(params.textDocument, params.position)
        }
    }

    @Override
    CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        return safeProviderCall("completion", (Either<List<CompletionItem>, CompletionList>) Either.forLeft([] as List<CompletionItem>)) {
            TextFile textFile = service.fileTracker.getTextFile(params.textDocument.uri)
            if (textFile?.uri?.endsWith(".yml") || textFile?.uri?.endsWith(".yaml")) {
                return (CompletableFuture<Either<List<CompletionItem>, CompletionList>>) (Object) getYamlProvider().provideCompletions(textFile, params.position)
            }
            return getCompletionProvider().provideCompletion(params.textDocument, params.position, params.context)
        }
    }

    @Override
    CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        return CompletableFuture.completedFuture(unresolved)
    }

    @Override
    CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        return safeProviderCall("codeLens", [] as List<CodeLens>) {
            return getCodeLensProvider().provideCodeLenses(params)
        }
    }

    @Override
    CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
        return CompletableFuture.completedFuture(unresolved)
    }

    @Override
    CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
        return safeProviderCall("inlayHint", [] as List<InlayHint>) {
            return getInlayHintProvider().provideInlayHints(params)
        }
    }

    @Override
    CompletableFuture<InlayHint> resolveInlayHint(InlayHint unresolved) {
        return CompletableFuture.completedFuture(unresolved)
    }

    @Override
    CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return safeProviderCall("codeAction", [] as List<Either<Command, CodeAction>>) {
            return getCodeActionProvider().provideCodeActions(params).thenApply({ list ->
                list.collect { action -> Either.forRight(action) }
            } as Function) as CompletableFuture<List<Either<Command, CodeAction>>>
        }
    }

    @Override
    CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        return safeProviderCall("semanticTokensFull", (SemanticTokens) null) {
            return getSemanticTokensProvider().provideSemanticTokens(params)
        }
    }

    void shutdown() {
        synchronized (pendingChanges) {
            stopped = true
            pendingChanges.values().each { pending ->
                pending.task?.cancel(false)
                pending.finished.complete(null)
            }
            pendingChanges.clear()
            compilationExecutor.shutdownNow()
        }
    }

    @CompileStatic
    private static class PendingChange {
        final TextFile revision
        final CompletableFuture<Void> finished = new CompletableFuture<>()
        ScheduledFuture<?> task

        PendingChange(TextFile revision) {
            this.revision = revision
        }
    }
}
