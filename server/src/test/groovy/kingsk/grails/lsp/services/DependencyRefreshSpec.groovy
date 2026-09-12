package kingsk.grails.lsp.services

import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.context.ProjectContextImpl
import kingsk.grails.lsp.core.gradle.ArtifactFactCache
import kingsk.grails.lsp.core.gradle.ArtifactFacts
import kingsk.grails.lsp.core.gradle.ProjectCache
import kingsk.grails.lsp.fixtures.IsolationTestFixtures
import kingsk.grails.lsp.model.dto.DependencyNode
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.dto.SourceSetModel
import kingsk.grails.lsp.model.state.ProjectState
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.test.MockLanguageClient
import org.gradle.tooling.CancellationTokenSource
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DependencyRefreshSpec extends Specification {

    @TempDir
    Path tempDir

    GrailsService grailsService
    MockLanguageClient mockClient

    def setup() {
        mockClient = new MockLanguageClient()
        grailsService = new GrailsService()
        grailsService.connect(mockClient)
        ArtifactFactCache.instance.clear()
    }

    def cleanup() {
        grailsService?.shutdown()
        ArtifactFactCache.instance.clear()
        grailsService?.gradle?.setBuildFunction(null)
    }

    def "R2-02/1: add, remove, and change a dependency with coherent convergence without restart"() {
        given: "A project directory with test and main source files"
        File workDir = tempDir.toFile()
        File projectDir = new File(workDir, "refresh-app")
        File srcDir = new File(projectDir, "src/main/groovy/test")
        srcDir.mkdirs()

        File jarsDir = new File(workDir, "jars")
        jarsDir.mkdirs()
        File v1Jar = IsolationTestFixtures.createSharedApiV1Jar(jarsDir)
        File v2Jar = IsolationTestFixtures.createSharedApiV2Jar(jarsDir)

        def depV1 = new DependencyNode("shared-api", "kingsk.isolate.fixture", "1.0.0", "COMPILE", v1Jar, null, null)
        def depV2 = new DependencyNode("shared-api", "kingsk.isolate.fixture", "2.0.0", "COMPILE", v2Jar, null, null)

        String projectUri = projectDir.toURI().toString()
        File myServiceFile = new File(srcDir, "MyService.groovy")
        myServiceFile.text = '''package test
import kingsk.isolate.fixture.shared.SharedApi
class MyService {
    String check() { new SharedApi().getApiVersion() }
}
'''
        String fileUri = myServiceFile.toURI().toString()

        SourceSetModel mainSS0 = new SourceSetModel("main", ":", projectDir, projectDir, [srcDir] as Set, [] as Set, [] as Set, [] as List)
        GrailsProject project0 = new GrailsProject(
            name: "refresh-app",
            rootDirectory: projectDir,
            sourceDirectories: [srcDir] as Set,
            sourceSets: ["main": mainSS0],
            dependencies: [] as Set
        )

        when: "Initial project has NO dependencies"
        grailsService.workspaceManager.addProject(project0)
        ProjectContextImpl ctx = (ProjectContextImpl) grailsService.workspaceManager.getProjectForUri(projectUri)
        ctx.ready()

        then: "Project is ready and classloader cannot resolve SharedApi"
        ctx.state == ProjectState.READY
        ctx.snapshotManager.active != null
        ctx.snapshotManager.active.gradleModel.dependencies.isEmpty()
        try {
            ctx.getClassLoaderForUri(fileUri).loadClass("kingsk.isolate.fixture.shared.SharedApi")
            assert false, "Should have thrown ClassNotFoundException"
        } catch (ClassNotFoundException ignored) {
            assert true
        }

        when: "Step 1: ADD dependency (depV1)"
        SourceSetModel mainSS1 = new SourceSetModel("main", ":", projectDir, projectDir, [srcDir] as Set, [] as Set, [] as Set, [v1Jar] as List)
        GrailsProject project1 = new GrailsProject(
            name: "refresh-app",
            rootDirectory: projectDir,
            sourceDirectories: [srcDir] as Set,
            sourceSets: ["main": mainSS1],
            dependencies: [depV1] as Set
        )
        ctx.updateProject(project1, projectUri)
        // Await discovery scan convergence
        ctx.getCurrentDiscoveryFuture()?.get(10, TimeUnit.SECONDS)

        then: "Compiler, classloader, and snapshot converge on v1.0.0 without restart"
        ctx.snapshotManager.active.gradleModel.dependencies.size() == 1
        ctx.snapshotManager.active.gradleModel.dependencies[0].version == "1.0.0"

        ClassLoader clV1 = ctx.getClassLoaderForUri(fileUri)
        Class<?> clazzV1 = clV1.loadClass("kingsk.isolate.fixture.shared.SharedApi")
        clazzV1 != null
        clazzV1.getMethod("v1OnlyMethod") != null
        try {
            clazzV1.getMethod("v2OnlyMethod")
            assert false, "v1 should not have v2OnlyMethod"
        } catch (NoSuchMethodException ignored) {
            assert true
        }

        when: "Step 2: CHANGE dependency (upgrade to depV2)"
        SourceSetModel mainSS2 = new SourceSetModel("main", ":", projectDir, projectDir, [srcDir] as Set, [] as Set, [] as Set, [v2Jar] as List)
        GrailsProject project2 = new GrailsProject(
            name: "refresh-app",
            rootDirectory: projectDir,
            sourceDirectories: [srcDir] as Set,
            sourceSets: ["main": mainSS2],
            dependencies: [depV2] as Set
        )
        ctx.updateProject(project2, projectUri)
        ctx.getCurrentDiscoveryFuture()?.get(10, TimeUnit.SECONDS)

        then: "Compiler, classloader, and snapshot converge on v2.0.0 without restart"
        ctx.snapshotManager.active.gradleModel.dependencies.size() == 1
        ctx.snapshotManager.active.gradleModel.dependencies[0].version == "2.0.0"

        ClassLoader clV2 = ctx.getClassLoaderForUri(fileUri)
        Class<?> clazzV2 = clV2.loadClass("kingsk.isolate.fixture.shared.SharedApi")
        clazzV2 != null
        clazzV2 != clazzV1
        clazzV2.getMethod("v2OnlyMethod") != null
        try {
            clazzV2.getMethod("v1OnlyMethod")
            assert false, "v2 should not have v1OnlyMethod"
        } catch (NoSuchMethodException ignored) {
            assert true
        }

        when: "Step 3: REMOVE dependency"
        SourceSetModel mainSS3 = new SourceSetModel("main", ":", projectDir, projectDir, [srcDir] as Set, [] as Set, [] as Set, [] as List)
        GrailsProject project3 = new GrailsProject(
            name: "refresh-app",
            rootDirectory: projectDir,
            sourceDirectories: [srcDir] as Set,
            sourceSets: ["main": mainSS3],
            dependencies: [] as Set
        )
        ctx.updateProject(project3, projectUri)
        ctx.getCurrentDiscoveryFuture()?.get(10, TimeUnit.SECONDS)

        then: "Dependency is completely removed and classes disappear without restart"
        ctx.snapshotManager.active.gradleModel.dependencies.isEmpty()
        ClassLoader clRemoved = ctx.getClassLoaderForUri(fileUri)
        try {
            clRemoved.loadClass("kingsk.isolate.fixture.shared.SharedApi")
            assert false, "SharedApi should not be resolvable after removal"
        } catch (ClassNotFoundException ignored) {
            assert true
        }
    }

    def "R2-02/2: a changed artifact at same coordinates invalidates by content, unchanged artifacts are reused"() {
        given: "A dynamic jar and an unchanged jar"
        File workDir = tempDir.toFile()
        File jarsDir = new File(workDir, "artifacts")
        jarsDir.mkdirs()

        File dynamicJar = new File(jarsDir, "dynamic-api-1.0.jar")
        IsolationTestFixtures.createJar(dynamicJar, [
            "kingsk/test/DynamicApi.java": '''package kingsk.test;
public class DynamicApi {
    public String getVersion() { return "v1"; }
}
'''
        ])

        File unchangedJar = new File(jarsDir, "unchanged-api-1.0.jar")
        IsolationTestFixtures.createJar(unchangedJar, [
            "kingsk/test/UnchangedApi.java": '''package kingsk.test;
public class UnchangedApi {
    public String status() { return "ok"; }
}
'''
        ])

        ArtifactFactCache cache = ArtifactFactCache.instance
        cache.clear()

        when: "Extracting facts initially"
        ArtifactFacts factsDynamic1 = cache.getOrExtract(dynamicJar)
        ArtifactFacts factsUnchanged1 = cache.getOrExtract(unchangedJar)

        then: "Initial extraction populates facts with cache misses"
        factsDynamic1 != null
        factsDynamic1.containsClass("kingsk.test.DynamicApi")
        factsUnchanged1 != null
        factsUnchanged1.containsClass("kingsk.test.UnchangedApi")
        cache.missCount == 2
        cache.hitCount == 0

        when: "Querying again without changes"
        ArtifactFacts factsDynamic1Reused = cache.getOrExtract(dynamicJar)
        ArtifactFacts factsUnchanged1Reused = cache.getOrExtract(unchangedJar)

        then: "Unchanged artifacts are reused from cache"
        factsDynamic1Reused.is(factsDynamic1)
        factsUnchanged1Reused.is(factsUnchanged1)
        cache.missCount == 2
        cache.hitCount == 2

        when: "Modifying dynamicJar content in place at the EXACT same coordinates"
        // Wait 100ms so file timestamp is distinguishable if filesystem granularity requires
        Thread.sleep(100)
        IsolationTestFixtures.createJar(dynamicJar, [
            "kingsk/test/DynamicApi.java": '''package kingsk.test;
public class DynamicApi {
    public String getVersion() { return "v2"; }
    public String v2NewMethod() { return "new"; }
}
'''
        ])

        ArtifactFacts factsDynamic2 = cache.getOrExtract(dynamicJar)
        ArtifactFacts factsUnchanged2 = cache.getOrExtract(unchangedJar)

        then: "Changed artifact invalidates by content fingerprint; unchanged artifact is reused"
        cache.missCount == 3 // 1 new extraction for changed content
        cache.hitCount == 3  // unchangedJar was a cache hit
        factsDynamic2 != null
        factsDynamic2.fingerprint != factsDynamic1.fingerprint
        factsUnchanged2.is(factsUnchanged1)

        when: "ProjectCache saves project and detects modified artifact at same coordinates"
        File projectDir = new File(workDir, "cache-test-proj")
        projectDir.mkdirs()
        def dep = new DependencyNode("dynamic-api", "kingsk.test", "1.0", "COMPILE", dynamicJar, null, null)
        GrailsProject proj = new GrailsProject(
            name: "cache-test-proj",
            rootDirectory: projectDir,
            sourceDirectories: [projectDir] as Set,
            sourceSets: ["main": new SourceSetModel("main", ":", projectDir, projectDir, [projectDir] as Set, [] as Set, [] as Set, [dynamicJar] as List)],
            dependencies: [dep] as Set
        )

        ProjectCache projectCache = new ProjectCache()
        projectCache.save(projectDir, proj)
        GrailsProject loaded1 = projectCache.load(projectDir)

        then: "Initial project load from binary cache succeeds"
        loaded1 != null
        loaded1.name == "cache-test-proj"

        when: "Modifying dynamicJar in place again"
        Thread.sleep(100)
        IsolationTestFixtures.createJar(dynamicJar, [
            "kingsk/test/DynamicApi.java": '''package kingsk.test;
public class DynamicApi {
    public String getVersion() { return "v3"; }
}
'''
        ])
        GrailsProject loadedAfterModify = projectCache.load(projectDir)

        then: "ProjectCache detects content fingerprint mismatch and invalidates by content"
        loadedAfterModify == null
    }

    def "R2-02/3: failed or cancelled refresh preserves stale last-good state; simultaneous edits/removal cannot publish mismatched candidate"() {
        given: "A project in READY state with a usable dependency"
        File workDir = tempDir.toFile()
        File projectDir = new File(workDir, "lkg-test-proj")
        File srcDir = new File(projectDir, "src/main/groovy/test")
        srcDir.mkdirs()

        File jarsDir = new File(workDir, "jars")
        jarsDir.mkdirs()
        File v1Jar = IsolationTestFixtures.createSharedApiV1Jar(jarsDir)
        def depV1 = new DependencyNode("shared-api", "kingsk.isolate.fixture", "1.0.0", "COMPILE", v1Jar, null, null)

        String projectUri = projectDir.toURI().toString()
        File serviceFile = new File(srcDir, "AppService.groovy")
        serviceFile.text = '''package test
import kingsk.isolate.fixture.shared.SharedApi
class AppService {
    String test() { new SharedApi().getApiVersion() }
}
'''
        String fileUri = serviceFile.toURI().toString()

        SourceSetModel mainSS = new SourceSetModel("main", ":", projectDir, projectDir, [srcDir] as Set, [] as Set, [] as Set, [v1Jar] as List)
        GrailsProject goodProject = new GrailsProject(
            name: "lkg-test-proj",
            rootDirectory: projectDir,
            sourceDirectories: [srcDir] as Set,
            sourceSets: ["main": mainSS],
            dependencies: [depV1] as Set
        )

        grailsService.workspaceManager.addProject(goodProject)
        ProjectContextImpl ctx = (ProjectContextImpl) grailsService.workspaceManager.getProjectForUri(projectUri)
        ctx.ready()
        long initialLkgVersion = ctx.snapshotManager.active.version()

        expect: "Initial project is READY and can resolve SharedApi"
        ctx.state == ProjectState.READY
        ctx.getClassLoaderForUri(fileUri).loadClass("kingsk.isolate.fixture.shared.SharedApi") != null
        !ctx.isGradleSyncStale()

        when: "A Gradle sync is triggered that fails with an exception"
        grailsService.gradle.setBuildFunction({ File dir, CancellationTokenSource cts ->
            throw new IllegalStateException("Simulated network/Gradle error")
        })
        ctx.triggerGradleSync()

        // Wait for sync attempt to complete
        long start = System.currentTimeMillis()
        while (ctx.isGradleSyncInProgress() && (System.currentTimeMillis() - start) < 5000) {
            Thread.sleep(50)
        }

        then: "Failed refresh preserves stale last-good state; project remains READY and usable"
        ctx.state == ProjectState.READY
        ctx.isGradleSyncStale()
        ctx.lastSyncError != null
        // Active snapshot is still the prior usable LKG version
        ctx.snapshotManager.active.version() == initialLkgVersion
        // Compiler can still resolve SharedApi from preserved LKG state
        ctx.getClassLoaderForUri(fileUri).loadClass("kingsk.isolate.fixture.shared.SharedApi") != null

        when: "Simulating root removal while a sync is in flight"
        CountDownLatch syncEntered = new CountDownLatch(1)
        CountDownLatch finishSync = new CountDownLatch(1)

        grailsService.gradle.setBuildFunction({ File dir, CancellationTokenSource cts ->
            syncEntered.countDown()
            finishSync.await(5, TimeUnit.SECONDS)
            return new GrailsProject(name: "MismatchedLateProject", rootDirectory: dir)
        })

        ctx.triggerGradleSync()
        assert syncEntered.await(5, TimeUnit.SECONDS)
        assert ctx.isGradleSyncInProgress()

        // Project root is removed (disposed) while sync is running
        ctx.dispose()

        then: "State is DISPOSING"
        ctx.state == ProjectState.DISPOSING

        when: "The late sync finishes"
        finishSync.countDown()
        Thread.sleep(200)

        then: "Mismatched late project is rejected and not published"
        ctx.state == ProjectState.DISPOSING
        ctx.project.name != "MismatchedLateProject"
    }
}
