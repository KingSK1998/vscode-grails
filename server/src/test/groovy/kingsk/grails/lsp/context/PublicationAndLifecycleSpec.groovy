package kingsk.grails.lsp.context

import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.index.GroovydocCache
import kingsk.grails.lsp.index.IndexSnapshot
import kingsk.grails.lsp.index.LocalSymbolInfo
import kingsk.grails.lsp.index.MethodScopeCache
import kingsk.grails.lsp.index.ProjectIndex
import kingsk.grails.lsp.index.ReferenceInfo
import kingsk.grails.lsp.index.SymbolInfo
import kingsk.grails.lsp.model.dto.DependencyNode
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.dto.ProjectDependencyEdge
import kingsk.grails.lsp.model.state.ProjectState
import kingsk.grails.lsp.model.state.VersionedSnapshot
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.providers.document.BaseProvider
import kingsk.grails.lsp.test.MockLanguageClient
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.eclipse.lsp4j.Position
import spock.lang.Specification

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PublicationAndLifecycleSpec extends Specification {

    private GrailsService grailsService
    private File testDir
    private File projectRootA
    private File projectRootB
    private ProjectContextImpl contextA
    private ProjectContextImpl contextB
    private MockLanguageClient mockClient

    def setup() {
        testDir = new File(System.getProperty("user.dir"), "build/test-pub-lifecycle-${System.currentTimeMillis()}")
        testDir.mkdirs()

        projectRootA = new File(testDir, "projA")
        projectRootA.mkdirs()
        projectRootB = new File(testDir, "projB")
        projectRootB.mkdirs()

        grailsService = new GrailsService()
        mockClient = new MockLanguageClient()
        grailsService.connect(mockClient)

        GrailsProject projA = new GrailsProject(
            name: "projA",
            rootDirectory: projectRootA,
            buildRoot: testDir,
            gradleProjectPath: ":projA",
            sourceDirectories: [new File(projectRootA, "src/main/groovy")] as Set,
            dependencies: [new DependencyNode(name: "projB", version: "1.0", scope: "compile")] as Set,
            projectDependencies: [new ProjectDependencyEdge(":projB", testDir, projectRootB, "projB")] as Set
        )
        GrailsProject projB = new GrailsProject(
            name: "projB",
            rootDirectory: projectRootB,
            buildRoot: testDir,
            gradleProjectPath: ":projB",
            sourceDirectories: [new File(projectRootB, "src/main/groovy")] as Set,
            dependencies: [new DependencyNode(name: "projA", version: "1.0", scope: "compile")] as Set,
            projectDependencies: [new ProjectDependencyEdge(":projA", testDir, projectRootA, "projA")] as Set
        )

        grailsService.workspaceManager.addProject(projA)
        grailsService.workspaceManager.addProject(projB)

        contextA = (ProjectContextImpl) grailsService.workspaceManager.getProjectForUri(projectRootA.toURI().toString())
        contextB = (ProjectContextImpl) grailsService.workspaceManager.getProjectForUri(projectRootB.toURI().toString())
    }

    def cleanup() {
        grailsService?.shutdown()
        if (testDir.exists()) {
            testDir.deleteDir()
        }
    }

    // =========================================================================
    // R1-04/1: Audit reader/writer paths & deterministic concurrent-read test
    // =========================================================================

    def "R1-04/1: deterministic concurrent reads under continuous compile writer load remain responsive without locking behind writers"() {
        given: "A source file compiled into initial snapshot"
        File source = new File(projectRootA, "src/main/groovy/Worker.groovy")
        source.parentFile.mkdirs()
        source.text = "package sample\nclass Worker { int id = 0\n void work() {} }"
        String uri = source.toURI().toString()

        TextFile initialFile = new TextFile(uri, source.text)
        initialFile.version = 1
        initialFile.openGeneration = 1L
        contextA.compileAndVisitAST(initialFile)

        assert contextA.state == ProjectState.READY
        assert contextA.snapshotManager.active != null

        int readerCount = 6
        int readsPerThread = 25
        ExecutorService readerPool = Executors.newFixedThreadPool(readerCount)
        ExecutorService writerPool = Executors.newSingleThreadExecutor()

        CyclicBarrier startBarrier = new CyclicBarrier(readerCount + 1)
        AtomicBoolean writerRunning = new AtomicBoolean(true)
        AtomicInteger totalReads = new AtomicInteger(0)
        AtomicInteger slowReads = new AtomicInteger(0)
        List<Throwable> readerErrors = Collections.synchronizedList(new ArrayList<Throwable>())

        when: "Continuous background compilation commits new versions while readers repeatedly acquire RequestContext"
        Future<Integer> writerFuture = writerPool.submit({ ->
            startBarrier.await()
            int writeVersion = 2
            while (writerRunning.get() && writeVersion < 40) {
                TextFile nextRev = new TextFile(uri, "package sample\nclass Worker { int id = ${writeVersion}\n void work() {} }")
                nextRev.version = writeVersion
                nextRev.openGeneration = 1L
                contextA.compileAndVisitAST(nextRev)
                writeVersion++
                Thread.sleep(5)
            }
            return writeVersion
        } as Callable<Integer>)

        List<Future<Void>> readerFutures = (1..readerCount).collect { threadId ->
            readerPool.submit({ ->
                startBarrier.await()
                for (int i = 0; i < readsPerThread; i++) {
                    long t0 = System.nanoTime()
                    RequestLease lease = contextA.acquireLease()
                    try {
                        VersionedSnapshot snap = lease.snapshot
                        assert snap != null
                        assert snap.ast != null
                        assert snap.index != null

                        // Query index symbols
                        def symbols = snap.index.getSymbolsForFile(uri)
                        assert symbols != null

                        // Query ASTAccessor
                        def node = snap.ast.getNodeAtPosition(uri, new Position(1, 8))
                        // Read must complete safely
                    } catch (Throwable t) {
                        readerErrors.add(t)
                    } finally {
                        lease.close()
                    }
                    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)
                    if (elapsedMs > 100) {
                        slowReads.incrementAndGet()
                    }
                    totalReads.incrementAndGet()
                    Thread.sleep(2)
                }
                return null
            } as Callable<Void>)
        }

        // Wait for readers to finish
        readerFutures.each { it.get(10, TimeUnit.SECONDS) }
        writerRunning.set(false)
        int committedWrites = writerFuture.get(5, TimeUnit.SECONDS)

        then: "All reads execute without errors, zero tearing, responsive (< 100ms), and writes committed concurrently"
        readerErrors.isEmpty()
        totalReads.get() == readerCount * readsPerThread
        slowReads.get() == 0
        committedWrites >= 2

        cleanup:
        readerPool.shutdownNow()
        writerPool.shutdownNow()
    }

    // =========================================================================
    // R1-04/2: Readers retained during compile/remove observe stable generation facts;
    //          AST boundary check (INV-OWN-005)
    // =========================================================================

    def "R1-04/2: retained reader observes stable generation facts across subsequent compilation and file deletion"() {
        given: "A source file compiled at generation V1"
        File source = new File(projectRootA, "src/main/groovy/Alpha.groovy")
        source.parentFile.mkdirs()
        source.text = "package sample\nclass Alpha { String name = 'alpha'\n void execute() {} }"
        String uri = source.toURI().toString()

        TextFile fileV1 = new TextFile(uri, source.text)
        fileV1.version = 1
        fileV1.openGeneration = 1L
        contextA.compileAndVisitAST(fileV1)

        VersionedSnapshot snapV1 = contextA.snapshotManager.active
        assert snapV1 != null
        assert snapV1.version >= 1L
        assert snapV1.index.getSymbolsForFile(uri).size() > 0

        and: "A reader acquires a RequestLease on generation V1"
        RequestLease retainedLease = contextA.acquireLease()
        VersionedSnapshot readerSnap = retainedLease.snapshot
        assert readerSnap.version == snapV1.version

        when: "A new compilation cycle modifies the class and commits V2"
        TextFile fileV2 = new TextFile(uri, "package sample\nclass Beta { String name = 'beta' }")
        fileV2.version = 2
        fileV2.openGeneration = 1L
        contextA.compileAndVisitAST(fileV2)

        and: "A document deletion occurs, committing another generation"
        contextA.deleteDocument(uri)

        then: "The active snapshot reflects the latest changes and deletion"
        VersionedSnapshot latestSnap = contextA.snapshotManager.active
        latestSnap.version > readerSnap.version
        latestSnap.index.getSymbolsForFile(uri).isEmpty()

        and: "The retained reader holding V1 lease continues to observe stable V1 facts"
        readerSnap.index.getSymbolsForFile(uri).size() > 0
        readerSnap.ast.getClassNodes(uri).find { it.name == "sample.Alpha" } != null

        cleanup:
        retainedLease?.close()
    }

    def "R1-04/2: INV-OWN-005 AST boundary verification - no mutable ASTNode or ClassNode references in index or cache structures"() {
        expect: "ProjectIndex, SymbolInfo, LocalSymbolInfo, ReferenceInfo, MethodScopeCache, and GroovydocCache contain no ASTNode fields"
        List<Class<?>> classesToCheck = [
            SymbolInfo,
            LocalSymbolInfo,
            ReferenceInfo,
            ProjectIndex,
            IndexSnapshot,
            MethodScopeCache,
            GroovydocCache
        ]

        classesToCheck.each { Class<?> clazz ->
            List<Field> fields = getAllFields(clazz)
            fields.each { Field field ->
                Class<?> fieldType = field.type
                assert !ASTNode.isAssignableFrom(fieldType) : "Class ${clazz.name} field ${field.name} holds ASTNode type ${fieldType.name}"
                assert !ClassNode.isAssignableFrom(fieldType) : "Class ${clazz.name} field ${field.name} holds ClassNode type ${fieldType.name}"

                // Also check generic types if field is a Collection or Map
                String typeSignature = field.genericType.typeName
                assert !typeSignature.contains("org.codehaus.groovy.ast.ASTNode") : "Class ${clazz.name} field ${field.name} generic type references ASTNode: ${typeSignature}"
                assert !typeSignature.contains("org.codehaus.groovy.ast.ClassNode") : "Class ${clazz.name} field ${field.name} generic type references ClassNode: ${typeSignature}"
            }
        }
    }

    private static List<Field> getAllFields(Class<?> type) {
        List<Field> result = []
        Class<?> current = type
        while (current != null && current != Object) {
            for (Field f : current.declaredFields) {
                if (!Modifier.isStatic(f.modifiers)) {
                    result.add(f)
                }
            }
            current = current.superclass
        }
        return result
    }

    // =========================================================================
    // R1-04/3: Request lease and retention strategy for hibernation & disposal
    //          (INV-STATE-010, INV-STATE-011)
    // =========================================================================

    def "R1-04/3: hibernation preserves active reader lease and detaches AST only after lease closes"() {
        given: "A compiled project with an active reader lease"
        File source = new File(projectRootA, "src/main/groovy/Sample.groovy")
        source.parentFile.mkdirs()
        source.text = "package sample\nclass Sample { int count = 1 }"
        String uri = source.toURI().toString()

        TextFile file = new TextFile(uri, source.text)
        file.version = 1
        file.openGeneration = 1L
        contextA.compileAndVisitAST(file)

        RequestLease lease = contextA.acquireLease()
        assert contextA.activeLeaseCount == 1
        assert lease.snapshot.ast != null
        assert !(lease.snapshot.ast instanceof DetachedASTAccessor)

        when: "Project is hibernated while lease is still open"
        contextA.hibernate()

        then: "State is HIBERNATED and live compiler/visitor references are nulled"
        contextA.state == ProjectState.HIBERNATED
        contextA.@compiler == null
        contextA.@visitor == null

        and: "Because lease is active, the snapshot AST has not yet been detached"
        !(contextA.snapshotManager.active.ast instanceof DetachedASTAccessor)
        lease.snapshot.ast.getClassNodes(uri).find { it.name == "sample.Sample" } != null

        when: "Reader finishes and closes the lease"
        lease.close()

        then: "Active leases reaches 0 and AST is detached to DetachedASTAccessor.INSTANCE"
        contextA.activeLeaseCount == 0
        contextA.snapshotManager.active.ast instanceof DetachedASTAccessor
        contextA.snapshotManager.active.ast.getClassNodes(uri).isEmpty()

        and: "Index facts and gradle model facts remain fully usable (INV-STATE-010)"
        contextA.snapshotManager.active.index.getSymbolsForFile(uri).size() > 0
        contextA.snapshotManager.active.gradleModel != null
        contextA.snapshotManager.active.gradleModel.name == "projA"
    }

    def "R1-04/3: disposal purges snapshots only after active reader lease closes"() {
        given: "A compiled project with an active reader lease"
        File source = new File(projectRootA, "src/main/groovy/Disposable.groovy")
        source.parentFile.mkdirs()
        source.text = "package sample\nclass Disposable {}"
        String uri = source.toURI().toString()

        TextFile file = new TextFile(uri, source.text)
        file.version = 1
        file.openGeneration = 1L
        contextA.compileAndVisitAST(file)

        RequestLease lease = contextA.acquireLease()
        assert contextA.activeLeaseCount == 1

        when: "Project is disposed while lease is active"
        contextA.dispose()

        then: "State is DISPOSING, compiler and visitor nulled"
        contextA.state == ProjectState.DISPOSING
        contextA.@compiler == null
        contextA.@visitor == null

        and: "Reader snapshot remains intact until lease close"
        contextA.snapshotManager.active != null

        when: "Reader closes the lease"
        lease.close()

        then: "Leases drained, snapshots cleared"
        contextA.activeLeaseCount == 0
        contextA.snapshotManager.active == null
    }

    // =========================================================================
    // R1-04/4: Non-blocking routing, getters, and cross-project lock ordering
    // =========================================================================

    def "R1-04/4: routing and read getters do not synchronously activate or wait behind compilation"() {
        given: "A hibernated project context"
        contextA.hibernate()
        assert contextA.state == ProjectState.HIBERNATED

        when: "WorkspaceManager routes URI to the project"
        ProjectContextImpl routed = grailsService.workspaceManager.getProjectForUri(projectRootA.toURI().toString())

        then: "Project is returned without changing state to REACTIVATING or READY"
        routed == contextA
        routed.state == ProjectState.HIBERNATED

        when: "Compiler and visitor getters are queried"
        def compiler = contextA.getCompiler()
        def visitor = contextA.getVisitor()
        def classLoader = contextA.getClassLoaderUnsafeOrNull()

        then: "Getters return null non-blockingly without triggering activation"
        compiler == null
        visitor == null
        classLoader == null
        contextA.state == ProjectState.HIBERNATED
    }

    def "R1-04/4: cross-project invalidation and concurrent compilation avoid deadlocks with deterministic lock ordering"() {
        given: "Two mutually dependent projects with compiled source"
        File srcA = new File(projectRootA, "src/main/groovy/ClassA.groovy")
        srcA.parentFile.mkdirs()
        srcA.text = "package sample\nclass ClassA {}"
        contextA.compileAndVisitAST(new TextFile(srcA.toURI().toString(), srcA.text))

        File srcB = new File(projectRootB, "src/main/groovy/ClassB.groovy")
        srcB.parentFile.mkdirs()
        srcB.text = "package sample\nclass ClassB {}"
        contextB.compileAndVisitAST(new TextFile(srcB.toURI().toString(), srcB.text))

        ExecutorService executor = Executors.newFixedThreadPool(4)
        CountDownLatch latch = new CountDownLatch(4)
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>())

        when: "Concurrently propagating invalidation in opposite directions while compilation runs"
        executor.submit({ ->
            try {
                for (int i = 0; i < 15; i++) {
                    grailsService.workspaceManager.propagateInvalidation(contextA)
                    Thread.sleep(2)
                }
            } catch (Throwable t) { errors.add(t) }
            finally { latch.countDown() }
        })

        executor.submit({ ->
            try {
                for (int i = 0; i < 15; i++) {
                    grailsService.workspaceManager.propagateInvalidation(contextB)
                    Thread.sleep(2)
                }
            } catch (Throwable t) { errors.add(t) }
            finally { latch.countDown() }
        })

        executor.submit({ ->
            try {
                for (int i = 0; i < 15; i++) {
                    TextFile fA = new TextFile(srcA.toURI().toString(), "package sample\nclass ClassA { int v = ${i} }")
                    fA.version = i + 2
                    contextA.compileAndVisitAST(fA)
                    Thread.sleep(2)
                }
            } catch (Throwable t) { errors.add(t) }
            finally { latch.countDown() }
        })

        executor.submit({ ->
            try {
                for (int i = 0; i < 15; i++) {
                    TextFile fB = new TextFile(srcB.toURI().toString(), "package sample\nclass ClassB { int v = ${i} }")
                    fB.version = i + 2
                    contextB.compileAndVisitAST(fB)
                    Thread.sleep(2)
                }
            } catch (Throwable t) { errors.add(t) }
            finally { latch.countDown() }
        })

        boolean finishedInTime = latch.await(10, TimeUnit.SECONDS)

        then: "All operations complete cleanly within timeout with zero deadlocks or exceptions"
        finishedInTime
        errors.isEmpty()
        contextA.state == ProjectState.READY
        contextB.state == ProjectState.READY

        cleanup:
        executor.shutdownNow()
    }
}
