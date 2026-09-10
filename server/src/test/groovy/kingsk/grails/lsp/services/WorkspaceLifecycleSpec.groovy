package kingsk.grails.lsp.services

import kingsk.grails.lsp.GrailsLanguageServer
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.context.ProjectContextImpl
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.state.ProjectState
import kingsk.grails.lsp.model.types.TextFile
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BooleanSupplier

class WorkspaceLifecycleSpec extends Specification {

    def "initialize returns capabilities promptly without waiting for Gradle and supports zero roots"() {
        given:
        def server = new GrailsLanguageServer()
        def params = new InitializeParams()
        params.workspaceFolders = []
        params.capabilities = new ClientCapabilities()

        when: "Initializing with zero workspace roots"
        def future = server.initialize(params)
        def result = future.get(3, TimeUnit.SECONDS)

        then: "Capabilities are returned immediately"
        result != null
        result.capabilities != null
        result.capabilities.hoverProvider.left
        result.capabilities.definitionProvider.left

        cleanup:
        server.shutdown().get(3, TimeUnit.SECONDS)
    }

    def "initialized starts discovery for provided roots after initialize"() {
        given:
        def server = new GrailsLanguageServer()
        def root1 = new File("build/lifecycle_test/root1").canonicalFile
        def root2 = new File("build/lifecycle_test/root2").canonicalFile
        root1.mkdirs()
        root2.mkdirs()

        def params = new InitializeParams()
        params.workspaceFolders = [
            new WorkspaceFolder(root1.toURI().toString(), "root1"),
            new WorkspaceFolder(root2.toURI().toString(), "root2")
        ]
        params.capabilities = new ClientCapabilities()

        def startedDiscoveries = Collections.synchronizedList([])
        def mockGradle = Mock(GradleService)
        mockGradle.getGrailsProjectAsync(_ as String) >> { String dir ->
            startedDiscoveries.add(dir)
            return new CompletableFuture<GrailsProject>()
        }
        server.@grailsService.gradle = mockGradle

        when: "Initialize is called"
        server.initialize(params).get(3, TimeUnit.SECONDS)

        then: "No discovery has started before initialized notification"
        startedDiscoveries.isEmpty()

        when: "Initialized notification arrives"
        server.initialized(new InitializedParams())

        then: "Discovery starts for both roots"
        startedDiscoveries.size() == 2

        cleanup:
        server.shutdown().get(3, TimeUnit.SECONDS)
    }

    def "root removal and re-add during blocked discovery uses generations and discards late results"() {
        given:
        def service = new GrailsService()
        def workspaceManager = service.workspaceManager
        def root = new File("build/lifecycle_gen_test").canonicalFile
        root.mkdirs()
        def rootUri = root.toURI().toString()

        def blockGen1 = new CompletableFuture<GrailsProject>()
        def gen2Result = new CompletableFuture<GrailsProject>()

        def callCount = new AtomicInteger(0)
        def mockGradle = Mock(GradleService)
        mockGradle.getGrailsProjectAsync(_ as String) >> { String dir ->
            int count = callCount.incrementAndGet()
            if (count == 1) {
                return blockGen1
            } else {
                return gen2Result
            }
        }
        service.gradle = mockGradle

        when: "Discovery starts for generation 1"
        def fut1 = workspaceManager.startRootDiscovery(rootUri)

        then:
        workspaceManager.@rootGenerations.get(TextFile.normalizePath(rootUri)) == 1L

        when: "Root is removed while generation 1 is in-flight"
        workspaceManager.removeRoot(rootUri)

        then: "Generation is cleared and in-flight future cancelled"
        workspaceManager.@rootGenerations.get(TextFile.normalizePath(rootUri)) == null
        fut1.isCancelled()

        when: "Root is re-added, starting generation 2"
        workspaceManager.startRootDiscovery(rootUri)

        then:
        workspaceManager.@rootGenerations.get(TextFile.normalizePath(rootUri)) == 2L

        when: "Generation 1 late result attempts to complete"
        def proj1 = new GrailsProject(name: "OldGenProj", rootDirectory: root)
        blockGen1.complete(proj1)

        then: "Old generation result did not overwrite context"
        workspaceManager.getProjectForUri(rootUri)?.project?.name != "OldGenProj"

        when: "Generation 2 result completes"
        def proj2 = new GrailsProject(name: "Gen2Proj", rootDirectory: root)
        gen2Result.complete(proj2)

        then: "Generation 2 project is properly registered"
        workspaceManager.getProjectForUri(rootUri)?.project?.name == "Gen2Proj"

        cleanup:
        service.shutdown()
    }

    def "path routing correctly handles Windows paths, encoded URIs, nested roots and unmatched URIs"() {
        given:
        def service = new GrailsService()
        def workspaceManager = service.workspaceManager

        File mainRoot = new File("build/route_test/main_app").canonicalFile
        File pluginRoot = new File(mainRoot, "plugins/sub_plugin").canonicalFile
        File otherRoot = new File("build/route_test/main_app_other").canonicalFile
        File spaceDir = new File(mainRoot, "src/main/groovy/my folder").canonicalFile
        mainRoot.mkdirs()
        pluginRoot.mkdirs()
        otherRoot.mkdirs()
        spaceDir.mkdirs()

        workspaceManager.addProject(new GrailsProject(name: "MainApp", rootDirectory: mainRoot))
        workspaceManager.addProject(new GrailsProject(name: "SubPlugin", rootDirectory: pluginRoot))

        expect: "Nested plugin file routes to SubPlugin context (longest match)"
        String pluginFile = new File(pluginRoot, "grails-app/controllers/PluginController.groovy").toURI().toString()
        workspaceManager.getProjectForUri(pluginFile)?.project?.name == "SubPlugin"

        and: "Main app file routes to MainApp context"
        String appFile = new File(mainRoot, "grails-app/controllers/AppController.groovy").toURI().toString()
        workspaceManager.getProjectForUri(appFile)?.project?.name == "MainApp"

        and: "Windows filesystem backslash path routes correctly"
        String winPath = new File(mainRoot, "grails-app/services/AppService.groovy").canonicalPath
        workspaceManager.getProjectForUri(winPath)?.project?.name == "MainApp"

        and: "Percent-encoded URI with spaces routes correctly"
        String encodedUri = new File(spaceDir, "MyBean.groovy").toURI().toASCIIString()
        workspaceManager.getProjectForUri(encodedUri)?.project?.name == "MainApp"

        and: "Prefix collision path (main_app_other) does NOT match main_app"
        String collisionFile = new File(otherRoot, "src/Other.groovy").toURI().toString()
        workspaceManager.getProjectForUri(collisionFile) == null

        and: "Unmatched external URI returns null without falling back to default project"
        String externalUri = new File("build/route_test/unrelated/External.groovy").toURI().toString()
        workspaceManager.getProjectForUri(externalUri) == null

        cleanup:
        service.shutdown()
    }

    def "root removal drains document work before disposing its project context"() {
        given:
        def service = new GrailsService()
        def workspaceManager = service.workspaceManager
        File root = new File('build/lifecycle_cancel_test').canonicalFile
        root.mkdirs()
        String rootUri = root.toURI().toString()
        workspaceManager.addProject(new GrailsProject(name: 'CancelRoot', rootDirectory: root))
        def documentService = Mock(GrailsTextDocumentService)
        service.document = documentService

        when:
        workspaceManager.removeRoot(rootUri)

        then:
        1 * documentService.cancelProjectWork(TextFile.normalizePath(rootUri))
        workspaceManager.getProjectForUri(rootUri) == null

        cleanup:
        service.shutdown()
    }

    def "root removal is prompt and prevents blocked document work from publishing"() {
        given:
        def service = new GrailsService()
        def workspaceManager = service.workspaceManager
        File root = new File('build/lifecycle_blocked_removal').canonicalFile
        root.mkdirs()
        String normalizedRoot = TextFile.normalizePath(root.toURI().toString())
        String documentUri = new File(root, 'Blocked.groovy').toURI().toString()
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def disposed = new CountDownLatch(1)
        def published = new AtomicBoolean(false)
        def context = new ProjectContextImpl(
            new GrailsProject(name: 'BlockedRoot', rootDirectory: root), service) {
            @Override
            void compileAndVisitAST(TextFile file, BooleanSupplier current) {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                if (current.asBoolean) published.set(true)
            }

            @Override
            void dispose() {
                try {
                    super.dispose()
                } finally {
                    disposed.countDown()
                }
            }
        }
        workspaceManager.@contexts.put(normalizedRoot, context)
        service.document.didOpen(new DidOpenTextDocumentParams(
            new TextDocumentItem(documentUri, 'groovy', 1, 'class Blocked {}')))
        assert entered.await(3, TimeUnit.SECONDS)

        when:
        CompletableFuture<Void> removal = CompletableFuture.runAsync { workspaceManager.removeRoot(root.toURI().toString()) }

        then: 'the workspace notification path does not wait for the compiler lock'
        removal.get(300, TimeUnit.MILLISECONDS) == null
        context.state == ProjectState.DISPOSING
        workspaceManager.getProjectForUri(documentUri) == null

        when:
        release.countDown()

        then: 'drain completes before resource release and obsolete work cannot publish'
        disposed.await(3, TimeUnit.SECONDS)
        !published.get()

        cleanup:
        release.countDown()
        service.shutdown()
    }

    def "replays only latest still-open buffers belonging to the newly registered root"() {
        given:
        def service = new GrailsService()
        def workspaceManager = service.workspaceManager
        def tracker = service.fileTracker

        File root = new File("build/replay_test/app").canonicalFile
        root.mkdirs()
        String rootUri = root.toURI().toString()

        File openFileInRoot = new File(root, "grails-app/controllers/OpenController.groovy").canonicalFile
        File closedFileInRoot = new File(root, "grails-app/services/ClosedService.groovy").canonicalFile
        File externalOpenFile = new File("build/replay_test/other/External.groovy").canonicalFile
        openFileInRoot.parentFile.mkdirs()
        closedFileInRoot.parentFile.mkdirs()
        externalOpenFile.parentFile.mkdirs()

        // Track files
        def tf1 = tracker.didOpenFile(new DidOpenTextDocumentParams(new TextDocumentItem(openFileInRoot.toURI().toString(), "groovy", 1, "class OpenController {}")))
        def tf2 = tracker.didOpenFile(new DidOpenTextDocumentParams(new TextDocumentItem(closedFileInRoot.toURI().toString(), "groovy", 1, "class ClosedService {}")))
        def tf3 = tracker.didOpenFile(new DidOpenTextDocumentParams(new TextDocumentItem(externalOpenFile.toURI().toString(), "groovy", 1, "class External {}")))

        // Close second file before discovery
        tracker.didCloseFile(new DidCloseTextDocumentParams(new TextDocumentIdentifier(closedFileInRoot.toURI().toString())))

        List<String> replayed = []
        def mockDocService = Mock(GrailsTextDocumentService)
        mockDocService.replayTrackedDocuments(_ as Collection<String>) >> { List args ->
            replayed.addAll((Collection<String>) args[0])
            null
        }
        service.document = mockDocService

        def projFuture = new CompletableFuture<GrailsProject>()
        def mockGradle = Mock(GradleService)
        mockGradle.getGrailsProjectAsync(_ as String) >> projFuture
        service.gradle = mockGradle

        when: "Discovery runs and finishes for root"
        workspaceManager.startRootDiscovery(rootUri)
        projFuture.complete(new GrailsProject(name: "ReplayApp", rootDirectory: root))

        then: "Only the open file in the matching root was replayed"
        replayed.size() == 1
        TextFile.normalizePath(replayed.first()) == TextFile.normalizePath(openFileInRoot.canonicalPath)

        cleanup:
        service.shutdown()
    }

    def "build-watch debounce unions affected roots across configuration files including Kotlin and version catalogs"() {
        given:
        def service = new GrailsService()
        def workspaceManager = service.workspaceManager
        def workspaceService = service.workspace

        File root1 = new File("build/watch_test/proj1").canonicalFile
        File root2 = new File("build/watch_test/proj2").canonicalFile
        root1.mkdirs()
        root2.mkdirs()

        def proj1 = new GrailsProject(name: "WatchProj1", rootDirectory: root1)
        def proj2 = new GrailsProject(name: "WatchProj2", rootDirectory: root2)
        workspaceManager.addProject(proj1)
        workspaceManager.addProject(proj2)

        def ctx1 = (ProjectContextImpl) workspaceManager.getProjectForUri(root1.toURI().toString())
        def ctx2 = (ProjectContextImpl) workspaceManager.getProjectForUri(root2.toURI().toString())

        def syncCounts = new java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
        syncCounts.put("WatchProj1", new AtomicInteger(0))
        syncCounts.put("WatchProj2", new AtomicInteger(0))

        def latch = new CountDownLatch(2)
        def mockGradle = Mock(GradleService)
        mockGradle.getGrailsProjectAsync(_ as String) >> { String dir ->
            if (dir.contains("proj1")) syncCounts.get("WatchProj1").incrementAndGet()
            if (dir.contains("proj2")) syncCounts.get("WatchProj2").incrementAndGet()
            latch.countDown()
            return CompletableFuture.completedFuture(new GrailsProject(name: "Synced", rootDirectory: new File(dir)))
        }
        service.gradle = mockGradle

        expect: "Build configuration file patterns match correctly"
        GrailsWorkspaceService.isBuildConfigurationFile("file:///p/build.gradle")
        GrailsWorkspaceService.isBuildConfigurationFile("file:///p/build.gradle.kts")
        GrailsWorkspaceService.isBuildConfigurationFile("file:///p/settings.gradle")
        GrailsWorkspaceService.isBuildConfigurationFile("file:///p/settings.gradle.kts")
        GrailsWorkspaceService.isBuildConfigurationFile("file:///p/gradle.properties")
        GrailsWorkspaceService.isBuildConfigurationFile("file:///p/gradle/libs.versions.toml")
        GrailsWorkspaceService.isBuildConfigurationFile("file:///p/my.versions.toml")
        !GrailsWorkspaceService.isBuildConfigurationFile("file:///p/App.groovy")

        when: "Multiple events for different projects arrive within debounce window"
        workspaceService.didChangeWatchedFiles(new DidChangeWatchedFilesParams([
            new FileEvent(new File(root1, "build.gradle.kts").toURI().toString(), FileChangeType.Changed),
            new FileEvent(new File(root1, "gradle.properties").toURI().toString(), FileChangeType.Changed),
            new FileEvent(new File(root2, "settings.gradle.kts").toURI().toString(), FileChangeType.Changed),
            new FileEvent(new File(root2, "gradle/libs.versions.toml").toURI().toString(), FileChangeType.Changed)
        ]))

        // Trigger debounced work immediately for testing
        workspaceService.processBuildConfigurationChanges()

        then: "Both projects were synced once"
        latch.await(3, TimeUnit.SECONDS)
        syncCounts.get("WatchProj1").get() == 1
        syncCounts.get("WatchProj2").get() == 1

        cleanup:
        service.shutdown()
    }
}
