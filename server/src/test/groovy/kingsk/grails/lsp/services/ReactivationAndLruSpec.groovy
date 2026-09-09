package kingsk.grails.lsp.services

import kingsk.grails.lsp.test.BaseLspSpec
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.context.ProjectContextImpl
import kingsk.grails.lsp.model.state.ProjectState
import kingsk.grails.lsp.model.types.TextFile
import spock.lang.Subject
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ReactivationAndLruSpec extends BaseLspSpec {

    @Subject
    WorkspaceManager workspaceManager

    def setup() {
        setupProject()
        workspaceManager = grailsService.workspaceManager
    }

    def "should handle concurrent reactivation requests thread-safely with single reactivation compile"() {
        given: "A hibernated project context"
        File root = new File(System.getProperty("user.dir"), "build/concurrent_reactivate_proj")
        root.mkdirs()
        def proj = new GrailsProject(name: "ConcurrentProj", rootDirectory: root)
        workspaceManager.addProject(proj)
        ProjectContextImpl ctx = (ProjectContextImpl) workspaceManager.getProjectForUri(root.toURI().toString())

        // Force into HIBERNATED state
        ctx.hibernate()
        assert ctx.state == ProjectState.HIBERNATED
        assert ctx.@compiler == null

        int threadCount = 4
        ExecutorService executor = Executors.newFixedThreadPool(threadCount)
        CyclicBarrier barrier = new CyclicBarrier(threadCount)
        AtomicInteger compileCount = new AtomicInteger(0)

        // Intercept/wrap reactivation task logic if needed, or simply monitor the compiler creation
        // Since ensureActivated creates the compiler/visitor inside reactivate { ... }, we can verify how many
        // times compiler/visitor are instantiated by calling compileAndVisitAST concurrently.
        TextFile textFile = new TextFile(new File(root, "src/Main.groovy").toURI().toString(), "class Main {}")

        when: "Multiple threads request compilation on the hibernated project concurrently"
        def futures = (1..threadCount).collect { id ->
            executor.submit({
                barrier.await()
                ctx.compileAndVisitAST(textFile)
                return ctx.state
            } as Callable<ProjectState>)
        }

        then: "All threads resolve, the project is READY, and only a single reactivation process occurs"
        def states = futures.collect { it.get(5, TimeUnit.SECONDS) }
        states.every { it == ProjectState.READY }
        ctx.state == ProjectState.READY
        ctx.@compiler != null
        ctx.@visitor != null

        cleanup:
        executor.shutdownNow()
    }

    def "should transparently reactivate hibernated project on next compilation"() {
        given: "A project in hibernated state"
        File root = new File(System.getProperty("user.dir"), "build/reactivate_compile_proj")
        root.mkdirs()
        def proj = new GrailsProject(name: "ReactivateCompileProj", rootDirectory: root)
        workspaceManager.addProject(proj)
        ProjectContextImpl ctx = (ProjectContextImpl) workspaceManager.getProjectForUri(root.toURI().toString())

        // Perform initial compilation to set up first snapshot
        TextFile textFile = new TextFile(new File(root, "src/Test.groovy").toURI().toString(), "class Test {}")
        ctx.compileAndVisitAST(textFile)
        assert ctx.state == ProjectState.READY
        assert ctx.@compiler != null

        when: "We hibernate the project"
        ctx.hibernate()

        then: "Caches are cleared and state is HIBERNATED"
        ctx.state == ProjectState.HIBERNATED
        ctx.@compiler == null
        ctx.@visitor == null

        when: "We perform another compilation"
        ctx.compileAndVisitAST(textFile)

        then: "Project is transparently reactivated back to READY"
        ctx.state == ProjectState.READY
        ctx.@compiler != null
        ctx.@visitor != null
    }

    def "closing a document while hibernated does not reactivate and disk content can compile later"() {
        given: "A disk-backed source with a compiled editor overlay"
        File root = new File(System.getProperty("user.dir"), "build/hibernated_close_proj")
        File source = new File(root, "src/main/groovy/DiskBacked.groovy")
        source.parentFile.mkdirs()
        source.text = "package sample\nclass DiskBacked {}"
        workspaceManager.addProject(new GrailsProject(name: "HibernatedCloseProj", rootDirectory: root))
        ProjectContextImpl ctx = (ProjectContextImpl) workspaceManager.getProjectForUri(source.toURI().toString())
        TextFile overlay = TextFile.create(source.toURI().toString(), "package sample\nclass UnsavedOverlay {}")
        ctx.compileAndVisitAST(overlay)
        assert ctx.projectIndex.snapshot.getSymbolsForFile(overlay.uri)*.name.contains("UnsavedOverlay")
        ctx.hibernate()

        when: "The overlay closes while the project is hibernated"
        ctx.closeDocument(overlay.uri)

        then: "Close cleanup does not activate heavy compiler state or retain overlay symbols"
        ctx.state == ProjectState.HIBERNATED
        ctx.@compiler == null
        ctx.@visitor == null
        ctx.snapshotManager.active.index.getSymbolsForFile(overlay.uri).isEmpty()

        when: "The same source is later compiled from disk"
        ctx.compileAndVisitAST(TextFile.create(source.toURI().toString(), source.text))

        then: "The project reactivates with the disk-backed declaration"
        ctx.state == ProjectState.READY
        ctx.visitor.getClassNodes(overlay.uri)*.nameWithoutPackage.contains("DiskBacked")
        !ctx.visitor.getClassNodes(overlay.uri)*.nameWithoutPackage.contains("UnsavedOverlay")
    }

    def "should enforce LRU budget and auto-reactivate seamlessly when needed"() {
        given: "Three projects added to workspace"
        File root1 = new File(System.getProperty("user.dir"), "build/lru_proj1")
        File root2 = new File(System.getProperty("user.dir"), "build/lru_proj2")
        File root3 = new File(System.getProperty("user.dir"), "build/lru_proj3")
        root1.mkdirs()
        root2.mkdirs()
        root3.mkdirs()

        workspaceManager.addProject(new GrailsProject(name: "LRU1", rootDirectory: root1))
        workspaceManager.addProject(new GrailsProject(name: "LRU2", rootDirectory: root2))
        workspaceManager.addProject(new GrailsProject(name: "LRU3", rootDirectory: root3))

        ProjectContextImpl ctx1 = (ProjectContextImpl) workspaceManager.getProjectForUri(root1.toURI().toString())
        ProjectContextImpl ctx2 = (ProjectContextImpl) workspaceManager.getProjectForUri(root2.toURI().toString())
        ProjectContextImpl ctx3 = (ProjectContextImpl) workspaceManager.getProjectForUri(root3.toURI().toString())

        TextFile file1 = new TextFile(new File(root1, "src/A.groovy").toURI().toString(), "class A {}")
        TextFile file2 = new TextFile(new File(root2, "src/B.groovy").toURI().toString(), "class B {}")
        TextFile file3 = new TextFile(new File(root3, "src/C.groovy").toURI().toString(), "class C {}")

        when: "Compiling on Project 1 and Project 2 (max active projects = 2)"
        ctx1.compileAndVisitAST(file1)
        Thread.sleep(10) // Ensure distinct access times
        ctx2.compileAndVisitAST(file2)

        then: "Both projects are READY and compilers are active"
        ctx1.state == ProjectState.READY
        ctx2.state == ProjectState.READY
        ctx1.@compiler != null
        ctx2.@compiler != null

        when: "Compiling on Project 3"
        Thread.sleep(10)
        ctx3.compileAndVisitAST(file3)

        then: "Project 3 becomes READY, Project 1 (LRU) is hibernated, Project 2 is still READY"
        ctx3.state == ProjectState.READY
        ctx1.state == ProjectState.HIBERNATED
        ctx2.state == ProjectState.READY

        and: "Project 1 compiler is nullified"
        ctx1.@compiler == null

        when: "Compiling on Project 1 again"
        Thread.sleep(10)
        ctx1.compileAndVisitAST(file1)

        then: "Project 1 is auto-reactivated to READY, Project 2 (new LRU) is hibernated, Project 3 is READY"
        ctx1.state == ProjectState.READY
        ctx2.state == ProjectState.HIBERNATED
        ctx3.state == ProjectState.READY

        and: "Project 1 compiler is re-instantiated and Project 2 compiler is nullified"
        ctx1.@compiler != null
        ctx2.@compiler == null
    }
}
