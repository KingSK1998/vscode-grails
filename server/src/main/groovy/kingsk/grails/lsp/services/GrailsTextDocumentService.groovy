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
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
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
    // Provisional safety limits. R1-05 owns workload measurement and tuning.
    static final int MAX_PENDING_DOCUMENTS = 256
    static final int MAX_PENDING_PER_ROOT = 32
    static final long MAX_PENDING_BYTES = 256L * 1024L
    static final long MAX_AUTOMATIC_DOCUMENT_BYTES = 4L * 1024L * 1024L
    // Conservative allowance for the ticket, future and map/lane bookkeeping.
    private static final long TICKET_BASE_BYTES = 1024L
    private static final String UNASSIGNED_ROOT = '<unassigned>'

    private final GrailsService service
    // Pending entries are compact URI/version tickets. Only the active job owns a text copy.
    private final Map<String, PendingChange> pendingChanges = new LinkedHashMap<>()
    private final Map<String, LinkedHashMap<String, PendingChange>> pendingByRoot = new LinkedHashMap<>()
    private final Object admissionLock = new Object()
    private final ScheduledThreadPoolExecutor compilationExecutor
    private PendingChange activeChange
    private ScheduledFuture<?> dispatchTask
    private long dispatchDueNanos = Long.MAX_VALUE
    private long pendingBytes = 0L
    private long activeBytes = 0L
    private long coalescedChanges = 0L
    private long overflowDeferrals = 0L
    private long oversizedDeferrals = 0L
    private boolean recoveryRequired = false
    private boolean closeRecoveryRequired = false
    private long recoveryGeneration = 0L
    private long closeRecoveryGeneration = 0L
    private final Map<String, Integer> handledVersions = new ConcurrentHashMap<>()
    private String lastServedRoot
    private CompletableFuture<Void> idleFuture = CompletableFuture.completedFuture(null)
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
            log.debug("[DOCUMENT] - Opened: ${params.textDocument.uri}")
            
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
        if (tracked == null || !tracked.uri) return
        String uri = TextFile.normalizePath(tracked.uri)
        String rootKey = service.workspaceManager.getRegisteredRootUri(uri) ?: UNASSIGNED_ROOT
        long dueNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, delayMs))
        synchronized (pendingChanges) {
            if (stopped) return
            ensureBusyFutureLocked()

            if (activeChange != null && activeChange.uri == uri) {
                activeChange.superseded = true
            }

            PendingChange previous = removePendingLocked(uri)
            if (previous != null) {
                coalescedChanges++
            }

            PendingChange pending = previous ?: new PendingChange(uri, rootKey)
            pending.rootKey = rootKey
            pending.version = tracked.version
            pending.closed = tracked.closed
            pending.dueNanos = dueNanos
            pending.superseded = false

            if (!admitPendingLocked(pending)) {
                pending.finished.complete(null)
                markRecoveryRequiredLocked()
                if (pending.closed) {
                    closeRecoveryRequired = true
                    closeRecoveryGeneration++
                }
            }
            requestDispatchLocked()
        }
    }

    private boolean isCurrent(PendingChange pending) {
        synchronized (pendingChanges) {
            return !stopped && activeChange != null && activeChange.is(pending) && !pending.superseded
        }
    }

    private void runPendingChange(PendingChange pending) {
        try {
            if (!isCurrent(pending)) return
            TextFile revision
            if (pending.closed) {
                revision = TextFile.create(pending.uri, null)
                revision.version = pending.version
                revision.markClosed()
            } else {
                TextFile tracked = service.fileTracker.getTextFile(pending.uri)
                if (tracked == null || tracked.closed) return
                long textBytes = estimateTextBytes(tracked.text)
                if (textBytes > MAX_AUTOMATIC_DOCUMENT_BYTES) {
                    synchronized (pendingChanges) {
                        oversizedDeferrals++
                        handledVersions.put(pending.uri, pending.version)
                    }
                    log.warn('[DOCUMENT] Automatic compilation deferred for oversized document {} ({} bytes, limit {})',
                        pending.uri, textBytes, MAX_AUTOMATIC_DOCUMENT_BYTES)
                    return
                }
                revision = TextFile.create(tracked.uri, tracked.text)
                revision.version = tracked.version
                revision.fileState = tracked.fileState
                synchronized (pendingChanges) {
                    if (!isCurrentLocked(pending)) return
                    activeBytes = textBytes
                }
            }
            def context = service.workspaceManager.getProjectForUri(revision.uri)
            if (context == null || context.state == ProjectState.DISPOSING) return
            if (revision.closed) {
                if (!isCurrent(pending)) return
                context.closeDocument(revision.uri)
                service.fileTracker.clearClosedFileDependencies(revision.uri)
                service.diagnostics.clearDiagnosticsForFile(revision.uri)
                synchronized (pendingChanges) {
                    handledVersions.remove(revision.uri)
                }
                return
            }
            service.fileTracker.updateFileDependenciesForSourceFile(revision)
            if (!isCurrent(pending)) return
            context.compileAndVisitAST(revision, { -> isCurrent(pending) } as BooleanSupplier)
            synchronized (pendingChanges) {
                if (isCurrentLocked(pending)) handledVersions.put(pending.uri, revision.version)
            }
        } catch (CancellationException e) {
            pending.finished.completeExceptionally(e)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            pending.finished.completeExceptionally(e)
        } catch (Exception e) {
            pending.finished.completeExceptionally(e)
            service.errorService.handleError('Error in background document compilation', e)
        } finally {
            synchronized (pendingChanges) {
                if (activeChange != null && activeChange.is(pending)) {
                    activeChange = null
                }
                activeBytes = 0L
                pending.finished.complete(null)
            }
            recoverDeferredWork()
            synchronized (pendingChanges) {
                requestDispatchLocked()
                completeIdleIfDrainedLocked()
            }
        }
    }

    /** Completion of work already scheduled for a URI; callers never block the LSP thread. */
    CompletableFuture<Void> compilationFinished(String uri) {
        synchronized (pendingChanges) {
            String normalizedUri = TextFile.normalizePath(uri)
            PendingChange pending = pendingChanges.get(normalizedUri)
            if (pending != null) return pending.finished
            if (recoveryRequired) return idleFuture
            if (activeChange != null && activeChange.uri == normalizedUri) return activeChange.finished
            return CompletableFuture.completedFuture(null)
        }
    }

    /** Bounded scheduler observations for health reporting and deterministic tests. */
    Map<String, Object> getCompilationQueueStats() {
        synchronized (pendingChanges) {
            Map<String, Integer> roots = new LinkedHashMap<>()
            for (Map.Entry<String, LinkedHashMap<String, PendingChange>> entry : pendingByRoot.entrySet()) {
                roots.put(entry.key, entry.value.size())
            }
            return Collections.unmodifiableMap([
                activeCount: activeChange == null ? 0 : 1,
                pendingCount: pendingChanges.size(),
                pendingBytes: pendingBytes,
                activeBytes: activeBytes,
                executorQueueCount: compilationExecutor.queue.size(),
                recoveryRequired: recoveryRequired,
                recoveryMarkerCount: recoveryRequired ? 1 : 0,
                closeRecoveryRequired: closeRecoveryRequired,
                coalescedChanges: coalescedChanges,
                overflowDeferrals: overflowDeferrals,
                oversizedDeferrals: oversizedDeferrals,
                pendingLimit: MAX_PENDING_DOCUMENTS,
                perRootLimit: MAX_PENDING_PER_ROOT,
                pendingByteLimit: MAX_PENDING_BYTES,
                automaticDocumentByteLimit: MAX_AUTOMATIC_DOCUMENT_BYTES,
                pendingByRoot: Collections.unmodifiableMap(roots),
                stopped: stopped
            ] as Map<String, Object>)
        }
    }

    /** Invalidates all queued and active work beneath a removed workspace root. */
    CompletableFuture<Void> cancelProjectWork(String rootUri) {
        String normalizedRoot = TextFile.normalizePath(rootUri)
        if (!normalizedRoot) return CompletableFuture.completedFuture(null)
        synchronized (pendingChanges) {
            CompletableFuture<Void> drained = CompletableFuture.completedFuture(null)
            Set<String> toRemove = pendingChanges.keySet().findAll { String uri ->
                WorkspaceManager.isSameOrChildPath(uri, normalizedRoot)
            }
            for (String uri : toRemove) {
                PendingChange removed = removePendingLocked(uri)
                removed?.finished?.complete(null)
            }
            if (activeChange != null && WorkspaceManager.isSameOrChildPath(activeChange.uri, normalizedRoot)) {
                activeChange.superseded = true
                drained = activeChange.finished
            }
            handledVersions.keySet().removeIf { String uri -> WorkspaceManager.isSameOrChildPath(uri, normalizedRoot) }
            requestDispatchLocked()
            completeIdleIfDrainedLocked()
            return drained
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

    private void ensureBusyFutureLocked() {
        if (idleFuture.isDone()) {
            idleFuture = new CompletableFuture<Void>()
        }
    }

    private boolean admitPendingLocked(PendingChange pending) {
        long ticketBytes = estimateTicketBytes(pending.uri)
        LinkedHashMap<String, PendingChange> lane = pendingByRoot.get(pending.rootKey)
        int rootCount = lane == null ? 0 : lane.size()
        if (!hasAdmissionCapacityLocked(rootCount, ticketBytes, pending.closed)) {
            makeRoomForPriorityLocked(pending, rootCount, ticketBytes)
            lane = pendingByRoot.get(pending.rootKey)
            rootCount = lane == null ? 0 : lane.size()
        }
        if (!hasAdmissionCapacityLocked(rootCount, ticketBytes, pending.closed)) {
            return false
        }
        if (lane == null) {
            lane = new LinkedHashMap<String, PendingChange>()
            pendingByRoot.put(pending.rootKey, lane)
        }
        pending.ticketBytes = ticketBytes
        pendingChanges.put(pending.uri, pending)
        lane.put(pending.uri, pending)
        pendingBytes += ticketBytes
        return true
    }

    private boolean hasAdmissionCapacityLocked(int rootCount, long ticketBytes, boolean priorityClose) {
        return pendingChanges.size() < MAX_PENDING_DOCUMENTS &&
            (priorityClose || rootCount < MAX_PENDING_PER_ROOT) &&
            pendingBytes + ticketBytes <= MAX_PENDING_BYTES
    }

    private void makeRoomForPriorityLocked(PendingChange incoming, int incomingRootCount, long ticketBytes) {
        if (ticketBytes > MAX_PENDING_BYTES) return
        boolean newRootNeedsFairTurn = incomingRootCount == 0 && !pendingByRoot.isEmpty()
        if (!incoming.closed && !newRootNeedsFairTurn) return

        while (!hasAdmissionCapacityLocked(incomingRootCount, ticketBytes, incoming.closed)) {
            Collection<PendingChange> candidates = pendingChanges.values().findAll { PendingChange candidate ->
                !candidate.closed && (incoming.closed || candidate.rootKey != incoming.rootKey)
            }
            if (candidates.isEmpty()) return
            PendingChange victim = candidates.max { PendingChange candidate ->
                int laneSize = pendingByRoot.get(candidate.rootKey)?.size() ?: 0
                return laneSize + (incoming.closed && candidate.rootKey == incoming.rootKey ? MAX_PENDING_DOCUMENTS : 0)
            }
            PendingChange removed = removePendingLocked(victim.uri)
            removed?.finished?.complete(null)
            markRecoveryRequiredLocked()
            LinkedHashMap<String, PendingChange> incomingLane = pendingByRoot.get(incoming.rootKey)
            incomingRootCount = incomingLane == null ? 0 : incomingLane.size()
        }
    }

    private PendingChange removePendingLocked(String uri) {
        PendingChange removed = pendingChanges.remove(uri)
        if (removed == null) return null
        LinkedHashMap<String, PendingChange> lane = pendingByRoot.get(removed.rootKey)
        lane?.remove(uri)
        if (lane != null && lane.isEmpty()) pendingByRoot.remove(removed.rootKey)
        pendingBytes = Math.max(0L, pendingBytes - removed.ticketBytes)
        return removed
    }

    private void markRecoveryRequiredLocked() {
        recoveryRequired = true
        recoveryGeneration++
        overflowDeferrals++
    }

    private void requestDispatchLocked() {
        if (stopped || activeChange != null) return
        if (pendingChanges.isEmpty()) {
            if (recoveryRequired) {
                if (dispatchTask == null || dispatchTask.isDone()) {
                    dispatchDueNanos = System.nanoTime()
                    dispatchTask = compilationExecutor.schedule({ -> dispatchOne() } as Runnable, 0L, TimeUnit.NANOSECONDS)
                }
                return
            }
            completeIdleIfDrainedLocked()
            return
        }

        long now = System.nanoTime()
        long earliest = pendingChanges.values().collect { PendingChange p -> p.dueNanos }.min() as long
        long target = Math.max(now, earliest)
        if (dispatchTask != null && !dispatchTask.isDone() && dispatchDueNanos <= target) return
        dispatchTask?.cancel(false)
        dispatchDueNanos = target
        long delay = Math.max(0L, target - now)
        dispatchTask = compilationExecutor.schedule({ -> dispatchOne() } as Runnable, delay, TimeUnit.NANOSECONDS)
    }

    private void dispatchOne() {
        PendingChange selected = null
        boolean recover = false
        synchronized (pendingChanges) {
            dispatchTask = null
            dispatchDueNanos = Long.MAX_VALUE
            if (stopped || activeChange != null) return
            if (pendingChanges.isEmpty() && recoveryRequired) {
                recover = true
            } else {
                selected = selectReadyLocked(System.nanoTime())
            }
            if (selected == null) {
                if (!recover) {
                    requestDispatchLocked()
                    return
                }
            } else {
                removePendingLocked(selected.uri)
                activeChange = selected
            }
        }
        if (recover) {
            recoverDeferredWork()
            synchronized (pendingChanges) {
                requestDispatchLocked()
                completeIdleIfDrainedLocked()
            }
            return
        }
        runPendingChange(selected)
    }

    private PendingChange selectReadyLocked(long now) {
        List<String> eligibleRoots = []
        for (Map.Entry<String, LinkedHashMap<String, PendingChange>> entry : pendingByRoot.entrySet()) {
            if (entry.value.values().any { PendingChange pending -> pending.dueNanos <= now }) {
                eligibleRoots.add(entry.key)
            }
        }
        if (eligibleRoots.isEmpty()) return null
        Collections.sort(eligibleRoots)
        int start = 0
        if (lastServedRoot != null) {
            int prior = eligibleRoots.indexOf(lastServedRoot)
            start = prior >= 0 ? (prior + 1) % eligibleRoots.size() : 0
        }
        String selectedRoot = eligibleRoots.get(start)
        PendingChange selected = pendingByRoot.get(selectedRoot).values().find { PendingChange pending ->
            pending.dueNanos <= now
        }
        lastServedRoot = selectedRoot
        return selected
    }

    /** Performs overload scanning on the compiler worker, never on an LSP notification monitor. */
    private void recoverDeferredWork() {
        long observedGeneration
        long observedCloseGeneration
        boolean reconcileCloses
        synchronized (pendingChanges) {
            if (!recoveryRequired || stopped) return
            observedGeneration = recoveryGeneration
            observedCloseGeneration = closeRecoveryGeneration
            reconcileCloses = closeRecoveryRequired
        }

        try {
            Set<String> alreadyQueued
            String activeUri
            synchronized (pendingChanges) {
                alreadyQueued = new HashSet<String>(pendingChanges.keySet())
                activeUri = activeChange?.uri
            }

            Map<String, List<RecoveryCandidate>> filesByRoot = new TreeMap<>()
            Map<String, Set<String>> openUrisByRoot = new HashMap<>()
            Set<String> openUris = new HashSet<>()
            for (TextFile file : service.fileTracker.openFiles) {
                if (file == null || file.closed) continue
                String rootKey = service.workspaceManager.getRegisteredRootUri(file.uri)
                if (rootKey == null) continue
                openUris.add(file.uri)
                openUrisByRoot.computeIfAbsent(rootKey, { String ignored -> new HashSet<String>() }).add(file.uri)
                Integer handledVersion = handledVersions.get(file.uri)
                if ((handledVersion != null && handledVersion == file.version) ||
                    alreadyQueued.contains(file.uri) || file.uri == activeUri) continue
                filesByRoot.computeIfAbsent(rootKey, { String ignored -> new ArrayList<RecoveryCandidate>() })
                    .add(new RecoveryCandidate(file, rootKey))
            }
            for (List<RecoveryCandidate> files : filesByRoot.values()) {
                files.sort { RecoveryCandidate left, RecoveryCandidate right -> left.file.uri <=> right.file.uri }
            }
            if (reconcileCloses) service.workspaceManager.reconcileClosedDocuments(openUrisByRoot)
            List<RecoveryCandidate> fairOrder = interleaveRoots(filesByRoot)

            synchronized (pendingChanges) {
                if (stopped) return
                if (reconcileCloses && observedCloseGeneration == closeRecoveryGeneration) {
                    closeRecoveryRequired = false
                    handledVersions.keySet().retainAll(openUris)
                }
                boolean allRecovered = fairOrder.size() <= MAX_PENDING_DOCUMENTS
                long now = System.nanoTime()
                int attempts = Math.min(fairOrder.size(), MAX_PENDING_DOCUMENTS)
                for (int index = 0; index < attempts; index++) {
                    RecoveryCandidate candidate = fairOrder.get(index)
                    TextFile file = candidate.file
                    Integer handledVersion = handledVersions.get(file.uri)
                    if ((handledVersion != null && handledVersion == file.version) ||
                        pendingChanges.containsKey(file.uri) ||
                        (activeChange != null && activeChange.uri == file.uri)) continue
                    PendingChange ticket = new PendingChange(file.uri, candidate.rootKey)
                    ticket.version = file.version
                    ticket.dueNanos = now
                    if (!admitPendingLocked(ticket)) allRecovered = false
                }
                if (observedGeneration == recoveryGeneration && allRecovered && !closeRecoveryRequired) {
                    recoveryRequired = false
                }
            }
        } catch (Exception e) {
            service.errorService.handleError('Failed to recover deferred document work', e)
        }
    }

    private static List<RecoveryCandidate> interleaveRoots(Map<String, List<RecoveryCandidate>> filesByRoot) {
        List<RecoveryCandidate> result = []
        int depth = 0
        boolean added = true
        while (added) {
            added = false
            for (List<RecoveryCandidate> files : filesByRoot.values()) {
                if (depth < files.size()) {
                    result.add(files.get(depth))
                    added = true
                }
            }
            depth++
        }
        return result
    }

    private void completeIdleIfDrainedLocked() {
        if (!stopped && (activeChange != null || !pendingChanges.isEmpty() || recoveryRequired || closeRecoveryRequired)) return
        idleFuture.complete(null)
    }

    private boolean isCurrentLocked(PendingChange pending) {
        return !stopped && activeChange != null && activeChange.is(pending) && !pending.superseded
    }

    private static long estimateTicketBytes(String uri) {
        return TICKET_BASE_BYTES + (uri == null ? 0L : uri.length() * 2L)
    }

    private static long estimateTextBytes(String text) {
        return text == null ? 0L : text.length() * 2L
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
            dispatchTask?.cancel(false)
            dispatchTask = null
            dispatchDueNanos = Long.MAX_VALUE
            pendingChanges.values().each { pending ->
                pending.finished.complete(null)
            }
            pendingChanges.clear()
            pendingByRoot.clear()
            pendingBytes = 0L
            recoveryRequired = false
            closeRecoveryRequired = false
            handledVersions.clear()
            if (activeChange != null) {
                activeChange.superseded = true
                activeChange.finished.complete(null)
            }
            activeChange = null
            activeBytes = 0L
            idleFuture.complete(null)
        }
        compilationExecutor.shutdownNow()
        try {
            if (!compilationExecutor.awaitTermination(1L, TimeUnit.SECONDS)) {
                log.warn('[DOCUMENT] Compilation worker did not terminate within the shutdown bound')
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt()
        }
    }

    @CompileStatic
    private static class PendingChange {
        final String uri
        final CompletableFuture<Void> finished = new CompletableFuture<>()
        String rootKey
        int version
        boolean closed
        boolean superseded
        long dueNanos
        long ticketBytes

        PendingChange(String uri, String rootKey) {
            this.uri = uri
            this.rootKey = rootKey
        }
    }

    @CompileStatic
    private static class RecoveryCandidate {
        final TextFile file
        final String rootKey

        RecoveryCandidate(TextFile file, String rootKey) {
            this.file = file
            this.rootKey = rootKey
        }
    }
}
