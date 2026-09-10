package kingsk.grails.lsp.services

import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.context.ProjectContextImpl
import kingsk.grails.lsp.core.compiler.GrailsCompiler
import kingsk.grails.lsp.model.config.GrailsLspConfig
import kingsk.grails.lsp.model.types.TextFile
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import spock.lang.Specification
import org.mockito.stubbing.Answer

import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when
import static org.mockito.Mockito.doAnswer
import static org.mockito.ArgumentMatchers.any
import static org.mockito.ArgumentMatchers.eq

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BooleanSupplier

class DocumentCompilationSpec extends Specification {
    GrailsService service = mock(GrailsService)
    FileContentTracker tracker = mock(FileContentTracker)
    WorkspaceManager workspace = mock(WorkspaceManager)
    ProjectContextImpl project = mock(ProjectContextImpl)
    GrailsCompiler compiler = mock(GrailsCompiler)
    GrailsLspConfig config = new GrailsLspConfig()
    GrailsTextDocumentService document
    List<TextFile> compiled = Collections.synchronizedList([])
    Closure onCompile = { TextFile file -> compiled.add(file) }
    String uri = new File('build/document-queue/Example.groovy').toURI().toString()
    TextFile tracked = TextFile.create(uri, 'class Example {}')

    def setup() {
        when(service.getFileTracker()).thenReturn(tracker)
        when(service.getWorkspaceManager()).thenReturn(workspace)
        when(service.getConfig()).thenReturn(config)
        when(service.getErrorService()).thenReturn(mock(ErrorService))
        when(service.getCancellationService()).thenReturn(mock(CancellationService))
        when(service.getDiagnostics()).thenReturn(mock(GrailsDiagnosticService))
        when(workspace.getProjectForUri(any(String))).thenReturn(project)
        when(project.getCompiler()).thenReturn(compiler)
        when(tracker.getTextFile(any(String))).thenReturn(tracked)
        doAnswer({ call -> onCompile(call.getArgument(0)); null } as Answer).when(project).compileAndVisitAST(any(TextFile))
        doAnswer({ call ->
            BooleanSupplier current = call.getArgument(1)
            if (current.asBoolean) onCompile(call.getArgument(0))
            null
        } as Answer).when(project).compileAndVisitAST(any(TextFile), any(BooleanSupplier))
        when(tracker.didOpenFile(any(DidOpenTextDocumentParams))).thenReturn(tracked)
        when(tracker.didOpenFile(any(DidOpenTextDocumentParams), eq(false))).thenReturn(tracked)
        when(tracker.didChangeFile(any(DidChangeTextDocumentParams))).thenReturn(tracked)
        when(tracker.didChangeFile(any(DidChangeTextDocumentParams), eq(false))).thenReturn(tracked)
        doAnswer({ call -> tracked.markClosed(); tracked } as Answer).when(tracker).didCloseFile(any(DidCloseTextDocumentParams))
        document = new GrailsTextDocumentService(service)
    }

    def cleanup() {
        document.shutdown()
    }

    def 'opening a document returns while compilation is still busy'() {
        given:
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        onCompile = { TextFile file ->
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }

        when:
        def notification = CompletableFuture.runAsync { document.didOpen(openParams()) }

        then:
        entered.await(3, TimeUnit.SECONDS)
        notification.get(300, TimeUnit.MILLISECONDS) == null

        cleanup:
        release.countDown()
        notification?.get(3, TimeUnit.SECONDS)
    }

    def 'rapid changes defer compilation and keep only the latest document revision'() {
        given:
        config.debounceDelayMs = 60_000

        when:
        (1..50).each { version ->
            tracked.text = "class Example { int value = ${version} }"
            tracked.version = version
            document.didChange(changeParams(version))
        }

        then:
        compiled.empty
        document.@pendingChanges.size() == 1
        document.compilationQueueStats.pendingCount == 1
        document.compilationQueueStats.executorQueueCount <= 1

        when:
        config.debounceDelayMs = 0
        document.didChange(changeParams(50))
        document.compilationFinished(uri).get(3, TimeUnit.SECONDS)

        then:
        compiled.size() == 1
        compiled.first().version == 50
        compiled.first().text.contains('value = 50')
        document.@pendingChanges.isEmpty()
    }

    def 'an active revision does not retain the mutable editor buffer'() {
        given:
        config.debounceDelayMs = 0
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        onCompile = { TextFile file ->
            compiled.add(file)
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }

        when:
        document.didChange(changeParams(1))
        assert entered.await(3, TimeUnit.SECONDS)
        tracked.text = 'class Mutated {}'
        release.countDown()
        document.compilationFinished(uri).get(3, TimeUnit.SECONDS)

        then:
        compiled.size() == 1
        !compiled.first().is(tracked)
        compiled.first().text == 'class Example {}'

        cleanup:
        release.countDown()
    }

    def 'closing a document removes its pending compilation'() {
        given:
        config.debounceDelayMs = 60_000
        document.didChange(changeParams(1))

        when:
        document.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)))
        document.compilationFinished(uri).get(3, TimeUnit.SECONDS)

        then:
        compiled.empty
        document.@pendingChanges.isEmpty()
    }

    def 'shutdown cancels delayed work and rejects subsequent compilation'() {
        given:
        config.debounceDelayMs = 60_000
        document.didChange(changeParams(1))
        def completion = document.compilationFinished(uri)

        when:
        document.shutdown()
        when(tracker.getTextFile(any(String))).thenReturn(tracked)
        document.replayTrackedDocuments([uri])
        document.didChange(changeParams(2))

        then:
        completion.get(3, TimeUnit.SECONDS) == null
        compiled.empty
        document.@pendingChanges.isEmpty()
        document.@compilationExecutor.getQueue().isEmpty()
    }

    def 'changes arriving during a compile wait for the worker and compile the newest revision next'() {
        given:
        config.debounceDelayMs = 0
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        onCompile = { TextFile file ->
            compiled.add(file)
            if (file.version == 0) {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        document.didOpen(openParams())
        assert entered.await(3, TimeUnit.SECONDS)

        when:
        (1..50).each { version ->
            tracked.version = version
            tracked.text = "class Example { int value = ${version} }"
            document.didChange(changeParams(version))
        }

        then:
        compiled*.version == [0]
        compiled.first().text == 'class Example {}'
        document.compilationQueueStats.pendingCount == 1
        document.compilationQueueStats.executorQueueCount <= 1

        when:
        release.countDown()
        document.compilationFinished(uri).get(3, TimeUnit.SECONDS)

        then:
        compiled*.version == [0, 50]

        cleanup:
        release.countDown()
    }

    def 'closing a document supersedes compilation already in progress'() {
        given:
        config.debounceDelayMs = 0
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def closeApplied = new AtomicBoolean(false)
        doAnswer({ call ->
            BooleanSupplier current = call.getArgument(1)
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            if (current.asBoolean) onCompile(call.getArgument(0))
            null
        } as Answer).when(project).compileAndVisitAST(any(TextFile), any(BooleanSupplier))
        doAnswer({ call -> closeApplied.set(true); null } as Answer).when(project).closeDocument(any(String))
        document.didOpen(openParams())
        assert entered.await(3, TimeUnit.SECONDS)

        when:
        document.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)))
        release.countDown()
        document.compilationFinished(uri).get(3, TimeUnit.SECONDS)

        then:
        compiled.isEmpty()
        closeApplied.get()
        document.@pendingChanges.isEmpty()

        cleanup:
        release.countDown()
    }

    def 'an empty opened document is admitted to compilation'() {
        given:
        tracked.text = ''

        when:
        document.didOpen(openParams())
        document.compilationFinished(uri).get(3, TimeUnit.SECONDS)

        then:
        compiled.size() == 1
        compiled.first().text == ''
    }

    def 'project registration replays only the latest tracked revision'() {
        given:
        def missingContextObserved = new CountDownLatch(1)
        def registered = new AtomicBoolean(false)
        when(workspace.getProjectForUri(any(String))).thenAnswer({ call ->
            if (!registered.get()) {
                missingContextObserved.countDown()
                return null
            }
            return project
        } as Answer)
        when(tracker.getTextFile(any(String))).thenReturn(tracked)

        when:
        document.didOpen(openParams())
        assert missingContextObserved.await(3, TimeUnit.SECONDS)
        tracked.version = 2
        tracked.text = 'class Example { int latest = 2 }'
        document.didChange(changeParams(2))
        document.compilationFinished(uri).get(3, TimeUnit.SECONDS)
        registered.set(true)
        document.replayTrackedDocuments([uri, uri])
        document.compilationFinished(uri).get(3, TimeUnit.SECONDS)

        then:
        compiled.size() == 1
        compiled.first().version == 2
        compiled.first().text == 'class Example { int latest = 2 }'
        document.@pendingChanges.isEmpty()
    }

    def 'a blocked compiler keeps storm admission bounded and recovers roots fairly'() {
        given:
        config.debounceDelayMs = 0
        File rootA = new File('build/document-queue/root-a').canonicalFile
        File rootB = new File('build/document-queue/root-b').canonicalFile
        Map<String, TextFile> openFiles = installTrackedOpenAnswer()
        when(workspace.getRegisteredRootUri(any(String))).thenAnswer({ call ->
            String path = TextFile.normalizePath(call.getArgument(0))
            return WorkspaceManager.isSameOrChildPath(path, rootB.canonicalPath)
                ? rootB.canonicalPath
                : rootA.canonicalPath
        } as Answer)
        when(tracker.getOpenFiles()).thenAnswer({ openFiles.values() } as Answer)

        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        String blockerUri = new File(rootA, 'Blocker.groovy').toURI().toString()
        onCompile = { TextFile file ->
            compiled.add(file)
            if (file.uri == TextFile.normalizePath(blockerUri)) {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        document.didOpen(openParams(blockerUri, 0, 'class Blocker {}'))
        assert entered.await(3, TimeUnit.SECONDS)

        when:
        int stormSize = GrailsTextDocumentService.MAX_PENDING_PER_ROOT * 2
        String lastRootAUri = null
        String firstOverflowRootAUri = null
        (1..stormSize).each { index ->
            String stormUri = new File(rootA, "Storm${index}.groovy").toURI().toString()
            document.didOpen(openParams(stormUri, 1, "class Storm${index} {}"))
            if (index == GrailsTextDocumentService.MAX_PENDING_PER_ROOT + 1) firstOverflowRootAUri = stormUri
            lastRootAUri = stormUri
        }
        String lastRootBUri = null
        String firstOverflowRootBUri = null
        (1..stormSize).each { index ->
            String stormUri = new File(rootB, "Storm${index}.groovy").toURI().toString()
            document.didOpen(openParams(stormUri, 1, "class Storm${index} {}"))
            if (index == GrailsTextDocumentService.MAX_PENDING_PER_ROOT + 1) firstOverflowRootBUri = stormUri
            lastRootBUri = stormUri
        }
        CompletableFuture<Void> overloadRecovery = document.compilationFinished(lastRootBUri)
        Map<String, Object> blockedStats = document.compilationQueueStats

        then:
        blockedStats.pendingCount <= GrailsTextDocumentService.MAX_PENDING_DOCUMENTS
        blockedStats.pendingBytes <= GrailsTextDocumentService.MAX_PENDING_BYTES
        blockedStats.executorQueueCount <= 1
        blockedStats.recoveryRequired
        blockedStats.overflowDeferrals > 0

        when:
        release.countDown()
        overloadRecovery.get(15, TimeUnit.SECONDS)

        then: 'both roots receive fair recovery turns and reach their latest inputs'
        compiled.size() >= 2
        compiled[1].uri.startsWith(rootB.canonicalPath)
        int firstOverflowBIndex = compiled*.uri.indexOf(TextFile.normalizePath(firstOverflowRootBUri))
        int lastRootAIndex = compiled*.uri.indexOf(TextFile.normalizePath(lastRootAUri))
        firstOverflowBIndex > 0
        lastRootAIndex > 0
        firstOverflowBIndex < lastRootAIndex
        compiled.any { it.uri == TextFile.normalizePath(lastRootBUri) && it.text == "class Storm${stormSize} {}" }

        cleanup:
        release.countDown()
    }

    def 'an overflowed superseding revision keeps its completion barrier until recovery'() {
        given:
        config.debounceDelayMs = 0
        File root = new File('build/document-queue/superseded-overflow').canonicalFile
        Map<String, TextFile> openFiles = installTrackedOpenAnswer()
        when(workspace.getRegisteredRootUri(any(String))).thenReturn(root.canonicalPath)
        when(tracker.getOpenFiles()).thenAnswer({ openFiles.values() } as Answer)
        String activeUri = new File(root, 'Active.groovy').toURI().toString()
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def newestEntered = new CountDownLatch(1)
        def releaseNewest = new CountDownLatch(1)
        onCompile = { TextFile file ->
            compiled.add(file)
            if (file.uri == TextFile.normalizePath(activeUri) && file.version == 1) {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            } else if (file.uri == TextFile.normalizePath(activeUri) && file.version == 2) {
                newestEntered.countDown()
                releaseNewest.await(5, TimeUnit.SECONDS)
            }
        }
        document.didOpen(openParams(activeUri, 1, 'class Active { int oldValue }'))
        assert entered.await(3, TimeUnit.SECONDS)
        (1..GrailsTextDocumentService.MAX_PENDING_PER_ROOT).each { index ->
            String queuedUri = new File(root, "Queued${index}.groovy").toURI().toString()
            document.didOpen(openParams(queuedUri, 1, "class Queued${index} {}"))
        }

        when:
        document.didOpen(openParams(activeUri, 2, 'class Active { int newestValue }'))
        CompletableFuture<Void> newestCompletion = document.compilationFinished(activeUri)
        release.countDown()
        assert newestEntered.await(5, TimeUnit.SECONDS)

        then: 'the obsolete active future cannot satisfy the newer revision barrier'
        !newestCompletion.isDone()

        when:
        releaseNewest.countDown()
        newestCompletion.get(10, TimeUnit.SECONDS)

        then:
        compiled.any { it.uri == TextFile.normalizePath(activeUri) && it.version == 2 && it.text.contains('newestValue') }

        cleanup:
        release.countDown()
        releaseNewest.countDown()
    }

    def 'ticket byte admission remains bounded independently of item count'() {
        given:
        config.debounceDelayMs = 0
        File root = new File('build/document-queue/byte-bound').canonicalFile
        Map<String, TextFile> openFiles = installTrackedOpenAnswer()
        when(workspace.getRegisteredRootUri(any(String))).thenReturn(root.canonicalPath)
        when(tracker.getOpenFiles()).thenAnswer({ openFiles.values() } as Answer)
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        String blockerUri = new File(root, 'Blocker.groovy').toURI().toString()
        onCompile = { TextFile file ->
            if (file.uri == TextFile.normalizePath(blockerUri)) {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        document.didOpen(openParams(blockerUri, 1, 'class Blocker {}'))
        assert entered.await(3, TimeUnit.SECONDS)
        when(workspace.getRegisteredRootUri(any(String))).thenAnswer({ call ->
            new File(TextFile.normalizePath(call.getArgument(0))).parentFile.canonicalPath
        } as Answer)

        when:
        (1..GrailsTextDocumentService.MAX_PENDING_DOCUMENTS).each { index ->
            String candidate = new File(root, "root-${index % 8}/Example${index}.groovy").toURI().toString()
            document.didOpen(openParams(candidate, 1, "class Example${index} {}"))
        }
        Map<String, Object> stats = document.compilationQueueStats

        then:
        stats.pendingBytes <= GrailsTextDocumentService.MAX_PENDING_BYTES
        stats.pendingCount < GrailsTextDocumentService.MAX_PENDING_DOCUMENTS
        stats.overflowDeferrals > 0

        cleanup:
        release.countDown()
    }

    def 'a close storm beyond the per-root limit cleans every document'() {
        given:
        config.debounceDelayMs = 0
        File rootA = new File('build/document-queue/close-blocker').canonicalFile
        File rootB = new File('build/document-queue/close-storm').canonicalFile
        Map<String, TextFile> openFiles = installTrackedOpenAnswer()
        when(workspace.getRegisteredRootUri(any(String))).thenAnswer({ call ->
            String path = TextFile.normalizePath(call.getArgument(0))
            WorkspaceManager.isSameOrChildPath(path, rootA.canonicalPath) ? rootA.canonicalPath : rootB.canonicalPath
        } as Answer)
        when(tracker.getOpenFiles()).thenAnswer({ openFiles.values() } as Answer)
        Set<String> closed = java.util.concurrent.ConcurrentHashMap.newKeySet()
        doAnswer({ call -> closed.add(TextFile.normalizePath(call.getArgument(0))); null } as Answer)
            .when(project).closeDocument(any(String))

        int closeCount = GrailsTextDocumentService.MAX_PENDING_PER_ROOT + 8
        List<String> closeUris = (1..closeCount).collect { index ->
            String closeUri = new File(rootB, "Close${index}.groovy").toURI().toString()
            document.didOpen(openParams(closeUri, 1, "class Close${index} {}"))
            document.compilationFinished(closeUri).get(3, TimeUnit.SECONDS)
            closeUri
        }
        compiled.clear()
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        String blockerUri = new File(rootA, 'Blocker.groovy').toURI().toString()
        onCompile = { TextFile file ->
            if (file.uri == TextFile.normalizePath(blockerUri)) {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        document.didOpen(openParams(blockerUri, 1, 'class Blocker {}'))
        assert entered.await(3, TimeUnit.SECONDS)

        when:
        closeUris.each { closeUri ->
            document.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(closeUri)))
        }
        CompletableFuture<Void> cleanupBarrier = document.compilationFinished(closeUris.last())
        release.countDown()
        cleanupBarrier.get(10, TimeUnit.SECONDS)

        then:
        closed == closeUris.collect { TextFile.normalizePath(it) } as Set
        document.compilationQueueStats.pendingCount == 0

        cleanup:
        release.countDown()
    }

    def 'oversized automatic compilation is deferred without retaining its source text'() {
        given:
        config.debounceDelayMs = 0
        String largeText = 'x' * (GrailsTextDocumentService.MAX_AUTOMATIC_DOCUMENT_BYTES + 1)
        tracked.text = largeText
        when(tracker.getTextFile(any(String))).thenReturn(tracked)

        when:
        document.didOpen(openParams(uri, 1, largeText))
        document.compilationFinished(uri).get(3, TimeUnit.SECONDS)
        Map<String, Object> largeStats = document.compilationQueueStats

        then:
        compiled.isEmpty()
        largeStats.pendingBytes == 0L
        largeStats.activeBytes == 0L
        largeStats.oversizedDeferrals == 1L

        when: 'the next smaller revision becomes eligible normally'
        tracked.text = 'class Example { int recovered = 1 }'
        tracked.version = 2
        document.didChange(changeParams(2))
        document.compilationFinished(uri).get(3, TimeUnit.SECONDS)

        then:
        compiled.size() == 1
        compiled.first().version == 2
        compiled.first().text == tracked.text
    }

    def 'cancelling a root drains its delayed work without affecting another root'() {
        given:
        config.debounceDelayMs = 0
        File rootA = new File('build/document-queue/cancel-a').canonicalFile
        File rootB = new File('build/document-queue/cancel-b').canonicalFile
        Map<String, TextFile> openFiles = installTrackedOpenAnswer()
        when(workspace.getRegisteredRootUri(any(String))).thenAnswer({ call ->
            String path = TextFile.normalizePath(call.getArgument(0))
            return WorkspaceManager.isSameOrChildPath(path, rootA.canonicalPath)
                ? rootA.canonicalPath
                : rootB.canonicalPath
        } as Answer)
        when(tracker.getOpenFiles()).thenAnswer({ openFiles.values() } as Answer)
        String uriA = new File(rootA, 'A.groovy').toURI().toString()
        String uriB = new File(rootB, 'B.groovy').toURI().toString()
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        doAnswer({ call ->
            TextFile file = call.getArgument(0)
            BooleanSupplier current = call.getArgument(1)
            if (file.uri == TextFile.normalizePath(uriA)) {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
            if (current.asBoolean) compiled.add(file)
            null
        } as Answer).when(project).compileAndVisitAST(any(TextFile), any(BooleanSupplier))
        document.didOpen(openParams(uriA, 1, 'class A {}'))
        assert entered.await(3, TimeUnit.SECONDS)
        document.didOpen(openParams(uriB, 1, 'class B {}'))
        CompletableFuture<Void> rootACompletion = document.compilationFinished(uriA)
        Map<String, Object> beforeCancel = document.compilationQueueStats

        assert !WorkspaceManager.isSameOrChildPath(TextFile.normalizePath(uriB), rootA.canonicalPath)
        assert beforeCancel.activeCount == 1
        assert beforeCancel.pendingCount == 1

        when:
        document.cancelProjectWork(rootA.toURI().toString())
        Map<String, Object> stats = document.compilationQueueStats
        release.countDown()
        document.compilationFinished(uriB).get(3, TimeUnit.SECONDS)

        then:
        rootACompletion.get(3, TimeUnit.SECONDS) == null
        stats.pendingCount == 1
        !stats.pendingByRoot.containsKey(rootA.canonicalPath)
        stats.pendingByRoot[rootB.canonicalPath] == 1
        compiled*.uri == [TextFile.normalizePath(uriB)]

        cleanup:
        release.countDown()
    }

    private Map<String, TextFile> installTrackedOpenAnswer() {
        Map<String, TextFile> openFiles = new java.util.concurrent.ConcurrentHashMap<>()
        doAnswer({ call ->
            DidOpenTextDocumentParams params = call.getArgument(0)
            TextFile file = TextFile.create(params.textDocument.uri, params.textDocument.text)
            file.version = params.textDocument.version
            file.markOpened()
            openFiles.put(file.uri, file)
            return file
        } as Answer).when(tracker).didOpenFile(any(DidOpenTextDocumentParams), eq(false))
        when(tracker.getTextFile(any(String))).thenAnswer({ call ->
            openFiles.get(TextFile.normalizePath(call.getArgument(0)))
        } as Answer)
        doAnswer({ call ->
            DidCloseTextDocumentParams params = call.getArgument(0)
            TextFile file = openFiles.remove(TextFile.normalizePath(params.textDocument.uri))
            file?.markClosed()
            return file
        } as Answer).when(tracker).didCloseFile(any(DidCloseTextDocumentParams))
        return openFiles
    }

    private DidOpenTextDocumentParams openParams() {
        new DidOpenTextDocumentParams(new TextDocumentItem(uri, 'groovy', 0, tracked.text))
    }

    private static DidOpenTextDocumentParams openParams(String targetUri, int version, String text) {
        new DidOpenTextDocumentParams(new TextDocumentItem(targetUri, 'groovy', version, text))
    }

    private DidChangeTextDocumentParams changeParams(int version) {
        new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri, version),
            [new TextDocumentContentChangeEvent(tracked.text)])
    }
}
