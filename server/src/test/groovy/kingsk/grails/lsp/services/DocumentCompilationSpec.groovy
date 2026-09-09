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
        document.@compilationExecutor.queue.size() == 1

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

    def 'a queued revision does not retain the mutable editor buffer'() {
        given:
        config.debounceDelayMs = 50

        when:
        document.didChange(changeParams(1))
        tracked.text = 'class Mutated {}'
        document.compilationFinished(uri).get(3, TimeUnit.SECONDS)

        then:
        compiled.size() == 1
        !compiled.first().is(tracked)
        compiled.first().text == 'class Example {}'
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
        document.@compilationExecutor.queue.size() == 1

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

    private DidOpenTextDocumentParams openParams() {
        new DidOpenTextDocumentParams(new TextDocumentItem(uri, 'groovy', 0, tracked.text))
    }

    private DidChangeTextDocumentParams changeParams(int version) {
        new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri, version),
            [new TextDocumentContentChangeEvent(tracked.text)])
    }
}
