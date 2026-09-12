package kingsk.grails.lsp.services

import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.context.ProjectContextImpl
import kingsk.grails.lsp.fixtures.IsolationTestFixtures
import kingsk.grails.lsp.model.dto.DependencyNode
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.dto.SourceSetModel
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.test.MockLanguageClient
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class ClasspathAndSourceSetSpec extends Specification {

    @TempDir
    Path tempDir

    GrailsService grailsService
    MockLanguageClient mockClient

    def setup() {
        mockClient = new MockLanguageClient()
        grailsService = new GrailsService()
        grailsService.connect(mockClient)
    }

    def cleanup() {
        grailsService?.shutdown()
    }

    def "R2-01/1: multi-root classpath isolation with identical FQNs and sibling prefix names"() {
        given: "Two sibling project root directories with prefix collision risk"
        File workDir = tempDir.toFile()
        File projectDirA = new File(workDir, "my-project")
        File projectDirB = new File(workDir, "my-project-api")
        projectDirA.mkdirs()
        projectDirB.mkdirs()

        File jarsDir = new File(workDir, "jars")
        jarsDir.mkdirs()
        File v1Jar = IsolationTestFixtures.createSharedApiV1Jar(jarsDir)
        File v2Jar = IsolationTestFixtures.createSharedApiV2Jar(jarsDir)

        def depV1 = new DependencyNode("shared-api", "kingsk.isolate.fixture", "1.0.0", "COMPILE", v1Jar, null, null)
        def depV2 = new DependencyNode("shared-api", "kingsk.isolate.fixture", "2.0.0", "COMPILE", v2Jar, null, null)

        GrailsProject projectA = new GrailsProject(
            name: "my-project",
            rootDirectory: projectDirA,
            sourceDirectories: [new File(projectDirA, "src/main/groovy")] as Set,
            dependencies: [depV1] as Set
        )

        GrailsProject projectB = new GrailsProject(
            name: "my-project-api",
            rootDirectory: projectDirB,
            sourceDirectories: [new File(projectDirB, "src/main/groovy")] as Set,
            dependencies: [depV2] as Set
        )

        when: "Both projects are registered in workspace manager"
        grailsService.workspaceManager.addProject(projectA)
        grailsService.workspaceManager.addProject(projectB)

        ProjectContextImpl ctxA = grailsService.workspaceManager.getProjectForUri(projectDirA.toURI().toString())
        ProjectContextImpl ctxB = grailsService.workspaceManager.getProjectForUri(projectDirB.toURI().toString())

        then: "Both project contexts are created"
        ctxA != null
        ctxB != null

        when: "Querying routing for a file in project-api"
        String fileInB = new File(projectDirB, "src/main/groovy/ApiTest.groovy").toURI().toString()
        ProjectContextImpl routedForB = grailsService.workspaceManager.getProjectForUri(fileInB)

        then: "Sibling prefix path does not falsely route to project A"
        routedForB == ctxB
        routedForB != ctxA

        when: "Checking classloaders and loaded SharedApi class for each project"
        ClassLoader clA = ctxA.getClassLoaderUnsafeOrNull()
        ClassLoader clB = ctxB.getClassLoaderUnsafeOrNull()

        Class<?> clazzA = clA?.loadClass("kingsk.isolate.fixture.shared.SharedApi")
        Class<?> clazzB = clB?.loadClass("kingsk.isolate.fixture.shared.SharedApi")

        then: "Each project loads its own version of SharedApi without cross-root leakage"
        clazzA != null
        clazzB != null
        // Under isolated classpaths, clazzA and clazzB MUST be different Class objects loaded from different classloaders
        clazzA != clazzB
        // Classes from JARs are loaded by the URLClassLoader parent of the GroovyClassLoader;
        // verify the class is loaded within the correct classloader chain
        isInClassLoaderChain(clazzA.classLoader, clA)
        isInClassLoaderChain(clazzB.classLoader, clB)

        // Project A must have v1OnlyMethod, and must NOT have v2OnlyMethod
        clazzA.getMethod("v1OnlyMethod") != null
        try {
            clazzA.getMethod("v2OnlyMethod")
            assert false, "Project A should not have v2OnlyMethod"
        } catch (NoSuchMethodException ignored) {}

        // Project B must have v2OnlyMethod, and must NOT have v1OnlyMethod
        clazzB.getMethod("v2OnlyMethod") != null
        try {
            clazzB.getMethod("v1OnlyMethod")
            assert false, "Project B should not have v1OnlyMethod"
        } catch (NoSuchMethodException ignored) {}
    }

    def "R2-01/2: production sources do not leak test-only APIs"() {
        given: "A project with main source and test source directories"
        File workDir = tempDir.toFile()
        File projectDir = new File(workDir, "proj-sourceset")
        File mainSrc = new File(projectDir, "src/main/groovy")
        File testSrc = new File(projectDir, "src/test/groovy")
        mainSrc.mkdirs()
        testSrc.mkdirs()

        File jarsDir = new File(workDir, "jars-sourceset")
        jarsDir.mkdirs()
        File v1Jar = IsolationTestFixtures.createSharedApiV1Jar(jarsDir)
        File testOnlyJar = IsolationTestFixtures.createTestOnlyJar(jarsDir)

        def mainDep = new DependencyNode("shared-api", "kingsk.isolate.fixture", "1.0.0", "COMPILE", v1Jar, null, null)
        def testDep = new DependencyNode("test-only-api", "kingsk.isolate.fixture", "1.0.0", "TEST", testOnlyJar, null, null)

        GrailsProject project = new GrailsProject(
            name: "proj-sourceset",
            rootDirectory: projectDir,
            sourceDirectories: [mainSrc] as Set,
            testDirectories: [testSrc] as Set,
            dependencies: [mainDep, testDep] as Set
        )

        when: "Project is registered"
        grailsService.workspaceManager.addProject(project)
        ProjectContextImpl ctx = grailsService.workspaceManager.getProjectForUri(projectDir.toURI().toString())

        File mainFile = new File(mainSrc, "MainService.groovy")
        File testFile = new File(testSrc, "MainServiceSpec.groovy")

        then: "Main source file cannot resolve or load test-only API"
        // Under current code, this will FAIL because all dependencies are on the single classloader
        ClassLoader mainCl = ctx.getClassLoaderForUri(mainFile.toURI().toString())
        try {
            mainCl.loadClass("kingsk.isolate.fixture.testonly.TestOnlyApi")
            assert false, "Test-only API leaked to main source classloader!"
        } catch (ClassNotFoundException e) {
            // Expected: test-only API is absent from production classpath
            assert true
        }

        and: "Test source file CAN resolve both main and test APIs"
        ClassLoader testCl = ctx.getClassLoaderForUri(testFile.toURI().toString())
        testCl.loadClass("kingsk.isolate.fixture.shared.SharedApi") != null
        testCl.loadClass("kingsk.isolate.fixture.testonly.TestOnlyApi") != null
    }

    def "R2-01/1: routing selects most specific nested root and rejects unrelated or unknown files"() {
        given: "A parent project and a nested child project"
        File workDir = tempDir.toFile()
        File parentDir = new File(workDir, "parent")
        File childDir = new File(parentDir, "child")
        parentDir.mkdirs()
        childDir.mkdirs()

        File parentSrc = new File(parentDir, "src/main/groovy")
        File childSrc = new File(childDir, "src/main/groovy")
        parentSrc.mkdirs()
        childSrc.mkdirs()

        GrailsProject parentProject = new GrailsProject(
            name: "parent",
            rootDirectory: parentDir,
            sourceDirectories: [parentSrc] as Set
        )
        GrailsProject childProject = new GrailsProject(
            name: "child",
            rootDirectory: childDir,
            sourceDirectories: [childSrc] as Set
        )

        grailsService.workspaceManager.addProject(parentProject)
        grailsService.workspaceManager.addProject(childProject)

        ProjectContextImpl parentCtx = grailsService.workspaceManager.getProjectForUri(parentDir.toURI().toString())
        ProjectContextImpl childCtx = grailsService.workspaceManager.getProjectForUri(childDir.toURI().toString())

        when: "Querying file inside child module"
        String childFile = new File(childSrc, "ChildService.groovy").toURI().toString()
        ProjectContextImpl routedChild = grailsService.workspaceManager.getProjectForUri(childFile)

        then: "Most specific (child) context wins over parent"
        routedChild == childCtx
        routedChild != parentCtx

        when: "Querying file in parent module"
        String parentFile = new File(parentSrc, "ParentService.groovy").toURI().toString()
        ProjectContextImpl routedParent = grailsService.workspaceManager.getProjectForUri(parentFile)

        then: "Parent file routes to parent context"
        routedParent == parentCtx

        when: "Querying an unrelated file outside the workspace"
        File outsideDir = new File(workDir, "outside")
        outsideDir.mkdirs()
        String outsideFile = new File(outsideDir, "Unrelated.groovy").toURI().toString()
        ProjectContextImpl routedOutside = grailsService.workspaceManager.getProjectForUri(outsideFile)

        then: "Unrelated file returns null, never falling back to default project"
        routedOutside == null
        DiscoveryService.getClassGraphScanResult(outsideFile) == null
    }

    def "R2-01/1: compiler enforces multi-root classpath isolation with identical FQNs under static compilation"() {
        given: "Two sibling projects with different versions of SharedApi"
        File workDir = tempDir.toFile()
        File projectDirA = new File(workDir, "proj-a")
        File projectDirB = new File(workDir, "proj-b")
        File srcDirA = new File(projectDirA, "src/main/groovy")
        File srcDirB = new File(projectDirB, "src/main/groovy")
        srcDirA.mkdirs()
        srcDirB.mkdirs()

        File jarsDir = new File(workDir, "jars-multi-compile")
        jarsDir.mkdirs()
        File v1Jar = IsolationTestFixtures.createSharedApiV1Jar(jarsDir)
        File v2Jar = IsolationTestFixtures.createSharedApiV2Jar(jarsDir)

        def depV1 = new DependencyNode("shared-api", "kingsk.isolate.fixture", "1.0.0", "COMPILE", v1Jar, null, null)
        def depV2 = new DependencyNode("shared-api", "kingsk.isolate.fixture", "2.0.0", "COMPILE", v2Jar, null, null)

        GrailsProject projectA = new GrailsProject(
            name: "proj-a",
            rootDirectory: projectDirA,
            sourceDirectories: [srcDirA] as Set,
            dependencies: [depV1] as Set
        )
        GrailsProject projectB = new GrailsProject(
            name: "proj-b",
            rootDirectory: projectDirB,
            sourceDirectories: [srcDirB] as Set,
            dependencies: [depV2] as Set
        )

        grailsService.workspaceManager.addProject(projectA)
        grailsService.workspaceManager.addProject(projectB)

        ProjectContextImpl ctxA = grailsService.workspaceManager.getProjectForUri(projectDirA.toURI().toString())
        ProjectContextImpl ctxB = grailsService.workspaceManager.getProjectForUri(projectDirB.toURI().toString())

        when: "Compiling source using v1 API in Project A"
        File fileA = new File(srcDirA, "TestA.groovy")
        String codeA = '''package test
import groovy.transform.CompileStatic
import kingsk.isolate.fixture.shared.SharedApi

@CompileStatic
class TestA {
    String run() {
        new SharedApi().v1OnlyMethod()
    }
}
'''
        fileA.text = codeA
        TextFile tfA = new TextFile(fileA.toURI().toString(), codeA)
        ctxA.compiler.compileSourceFile(tfA)
        def errorsA = ctxA.compiler.errorCollectorOrNull?.errors

        then: "Project A compiles v1 API successfully without errors"
        errorsA == null || errorsA.isEmpty()

        when: "Compiling source using v2 API in Project B"
        File fileB = new File(srcDirB, "TestB.groovy")
        String codeB = '''package test
import groovy.transform.CompileStatic
import kingsk.isolate.fixture.shared.SharedApi

@CompileStatic
class TestB {
    String run() {
        new SharedApi().v2OnlyMethod()
    }
}
'''
        fileB.text = codeB
        TextFile tfB = new TextFile(fileB.toURI().toString(), codeB)
        ctxB.compiler.compileSourceFile(tfB)
        def errorsB = ctxB.compiler.errorCollectorOrNull?.errors

        then: "Project B compiles v2 API successfully without errors"
        errorsB == null || errorsB.isEmpty()

        when: "Compiling source attempting to use v2 API in Project A"
        File fileAInvalid = new File(srcDirA, "TestAInvalid.groovy")
        String codeAInvalid = '''package test
import groovy.transform.CompileStatic
import kingsk.isolate.fixture.shared.SharedApi

@CompileStatic
class TestAInvalid {
    String run() {
        new SharedApi().v2OnlyMethod()
    }
}
'''
        fileAInvalid.text = codeAInvalid
        TextFile tfAInvalid = new TextFile(fileAInvalid.toURI().toString(), codeAInvalid)
        ctxA.compiler.compileSourceFile(tfAInvalid)
        def errorsAInvalid = ctxA.compiler.errorCollectorOrNull?.errors

        then: "Project A fails compilation because v2OnlyMethod does not exist on v1 classpath"
        errorsAInvalid != null && !errorsAInvalid.isEmpty()
    }

    def "R2-01/2: ordered classpath precedence is preserved for duplicate FQNs"() {
        given: "Two JARs providing the same class with different return values"
        File workDir = tempDir.toFile()
        File jarsDir = new File(workDir, "precedence-jars")
        jarsDir.mkdirs()
        File jarA = IsolationTestFixtures.createPrecedenceJarA(jarsDir)
        File jarB = IsolationTestFixtures.createPrecedenceJarB(jarsDir)

        and: "Project AB has classpath [jarA, jarB]"
        File projDirAB = new File(workDir, "proj-ab")
        projDirAB.mkdirs()
        GrailsProject projAB = new GrailsProject(
            name: "proj-ab",
            rootDirectory: projDirAB,
            sourceDirectories: [new File(projDirAB, "src")] as Set,
            sourceSets: [
                main: new SourceSetModel("main", ":", projDirAB, projDirAB, [new File(projDirAB, "src")], [], [], [jarA, jarB])
            ]
        )

        and: "Project BA has classpath [jarB, jarA]"
        File projDirBA = new File(workDir, "proj-ba")
        projDirBA.mkdirs()
        GrailsProject projBA = new GrailsProject(
            name: "proj-ba",
            rootDirectory: projDirBA,
            sourceDirectories: [new File(projDirBA, "src")] as Set,
            sourceSets: [
                main: new SourceSetModel("main", ":", projDirBA, projDirBA, [new File(projDirBA, "src")], [], [], [jarB, jarA])
            ]
        )

        grailsService.workspaceManager.addProject(projAB)
        grailsService.workspaceManager.addProject(projBA)

        ProjectContextImpl ctxAB = grailsService.workspaceManager.getProjectForUri(projDirAB.toURI().toString())
        ProjectContextImpl ctxBA = grailsService.workspaceManager.getProjectForUri(projDirBA.toURI().toString())

        when: "Loading PrecedenceApi from Project AB"
        ClassLoader clAB = ctxAB.getClassLoaderForUri(new File(projDirAB, "src/Main.groovy").toURI().toString())
        Class<?> clazzAB = clAB.loadClass("kingsk.isolate.fixture.precedence.PrecedenceApi")
        Object instanceAB = clazzAB.getDeclaredConstructor().newInstance()
        String originAB = clazzAB.getMethod("getOrigin").invoke(instanceAB) as String

        and: "Loading PrecedenceApi from Project BA"
        ClassLoader clBA = ctxBA.getClassLoaderForUri(new File(projDirBA, "src/Main.groovy").toURI().toString())
        Class<?> clazzBA = clBA.loadClass("kingsk.isolate.fixture.precedence.PrecedenceApi")
        Object instanceBA = clazzBA.getDeclaredConstructor().newInstance()
        String originBA = clazzBA.getMethod("getOrigin").invoke(instanceBA) as String

        then: "Project AB resolves A, Project BA resolves B, proving order is preserved"
        originAB == "A"
        originBA == "B"
    }

    def "R2-01/2: host test classpath does not leak into project classloader"() {
        given: "A project with empty dependencies"
        File workDir = tempDir.toFile()
        File projectDir = new File(workDir, "isolated-host")
        File srcDir = new File(projectDir, "src/main/groovy")
        srcDir.mkdirs()

        GrailsProject project = new GrailsProject(
            name: "isolated-host",
            rootDirectory: projectDir,
            sourceDirectories: [srcDir] as Set,
            sourceSets: [
                main: new SourceSetModel("main", ":", projectDir, projectDir, [srcDir], [], [], [])
            ]
        )

        grailsService.workspaceManager.addProject(project)
        ProjectContextImpl ctx = grailsService.workspaceManager.getProjectForUri(projectDir.toURI().toString())

        when: "Attempting to load host test libraries (JUnit, Spock) from the project classloader"
        ClassLoader cl = ctx.getClassLoaderForUri(new File(srcDir, "App.groovy").toURI().toString())

        then: "Host test framework classes are strictly rejected"
        try {
            cl.loadClass("org.junit.Test")
            assert false, "Host JUnit class leaked into project classloader"
        } catch (ClassNotFoundException e) {
            assert true
        }

        try {
            cl.loadClass("spock.lang.Specification")
            assert false, "Host Spock class leaked into project classloader"
        } catch (ClassNotFoundException e) {
            assert true
        }
    }

    def "R2-01/2: generated main source files cannot access test-only APIs while generated test sources can"() {
        given: "A project with generated main and generated test source directories"
        File workDir = tempDir.toFile()
        File projectDir = new File(workDir, "proj-gen-sources")
        File mainSrc = new File(projectDir, "src/main/groovy")
        File genMainSrc = new File(projectDir, "build/generated/sources/main")
        File testSrc = new File(projectDir, "src/test/groovy")
        File genTestSrc = new File(projectDir, "build/generated/sources/test")
        mainSrc.mkdirs()
        genMainSrc.mkdirs()
        testSrc.mkdirs()
        genTestSrc.mkdirs()

        File jarsDir = new File(workDir, "gen-jars")
        jarsDir.mkdirs()
        File sharedJar = IsolationTestFixtures.createSharedApiV1Jar(jarsDir)
        File testJar = IsolationTestFixtures.createTestOnlyJar(jarsDir)

        SourceSetModel mainModel = new SourceSetModel(
            "main", ":", projectDir, projectDir,
            [mainSrc, genMainSrc], [], [genMainSrc], [sharedJar]
        )
        SourceSetModel testModel = new SourceSetModel(
            "test", ":", projectDir, projectDir,
            [testSrc, genTestSrc], [], [genTestSrc], [sharedJar, testJar]
        )

        GrailsProject project = new GrailsProject(
            name: "proj-gen-sources",
            rootDirectory: projectDir,
            sourceDirectories: [mainSrc, genMainSrc] as Set,
            testDirectories: [testSrc, genTestSrc] as Set,
            sourceSets: [main: mainModel, test: testModel]
        )

        grailsService.workspaceManager.addProject(project)
        ProjectContextImpl ctx = grailsService.workspaceManager.getProjectForUri(projectDir.toURI().toString())

        when: "Resolving classloader for generated main source"
        File genMainFile = new File(genMainSrc, "GenService.groovy")
        ClassLoader genMainCl = ctx.getClassLoaderForUri(genMainFile.toURI().toString())

        then: "Generated main source cannot load test-only API"
        try {
            genMainCl.loadClass("kingsk.isolate.fixture.testonly.TestOnlyApi")
            assert false, "Test-only API leaked to generated main source!"
        } catch (ClassNotFoundException expected) {
            assert true
        }

        and: "Generated main source CAN load main API"
        genMainCl.loadClass("kingsk.isolate.fixture.shared.SharedApi") != null

        when: "Resolving classloader for generated test source"
        File genTestFile = new File(genTestSrc, "GenTestService.groovy")
        ClassLoader genTestCl = ctx.getClassLoaderForUri(genTestFile.toURI().toString())

        then: "Generated test source CAN load both main and test APIs"
        genTestCl.loadClass("kingsk.isolate.fixture.shared.SharedApi") != null
        genTestCl.loadClass("kingsk.isolate.fixture.testonly.TestOnlyApi") != null
    }

    def "R2-01/3: removing one project root does not invalidate or close another project sharing identical artifact JARs"() {
        given: "Two projects sharing the same dependency JAR file"
        File workDir = tempDir.toFile()
        File projectDirA = new File(workDir, "proj-share-a")
        File projectDirB = new File(workDir, "proj-share-b")
        projectDirA.mkdirs()
        projectDirB.mkdirs()

        File jarsDir = new File(workDir, "shared-jars")
        jarsDir.mkdirs()
        File sharedJar = IsolationTestFixtures.createSharedApiV1Jar(jarsDir)

        GrailsProject projectA = new GrailsProject(
            name: "proj-share-a",
            rootDirectory: projectDirA,
            sourceDirectories: [new File(projectDirA, "src")] as Set,
            sourceSets: [
                main: new SourceSetModel("main", ":", projectDirA, projectDirA, [new File(projectDirA, "src")], [], [], [sharedJar])
            ]
        )
        GrailsProject projectB = new GrailsProject(
            name: "proj-share-b",
            rootDirectory: projectDirB,
            sourceDirectories: [new File(projectDirB, "src")] as Set,
            sourceSets: [
                main: new SourceSetModel("main", ":", projectDirB, projectDirB, [new File(projectDirB, "src")], [], [], [sharedJar])
            ]
        )

        grailsService.workspaceManager.addProject(projectA)
        grailsService.workspaceManager.addProject(projectB)

        ProjectContextImpl ctxA = grailsService.workspaceManager.getProjectForUri(projectDirA.toURI().toString())
        ProjectContextImpl ctxB = grailsService.workspaceManager.getProjectForUri(projectDirB.toURI().toString())

        when: "Project A is removed from workspace"
        grailsService.workspaceManager.removeProject(projectDirA.toURI().toString())

        then: "Project A context is removed"
        grailsService.workspaceManager.getProjectForUri(projectDirA.toURI().toString()) == null

        and: "Project B remains unaffected and can still load SharedApi"
        ProjectContextImpl remainingB = grailsService.workspaceManager.getProjectForUri(projectDirB.toURI().toString())
        remainingB == ctxB
        ClassLoader clB = remainingB.getClassLoaderForUri(new File(projectDirB, "src/Main.groovy").toURI().toString())
        Class<?> clazzB = clB.loadClass("kingsk.isolate.fixture.shared.SharedApi")
        clazzB != null
        clazzB.getMethod("v1OnlyMethod") != null
    }

    def "R2-01/3: request lease held across project removal remains usable until released"() {
        given: "A project registered in the workspace"
        File workDir = tempDir.toFile()
        File projectDir = new File(workDir, "proj-lease")
        projectDir.mkdirs()

        GrailsProject project = new GrailsProject(
            name: "proj-lease",
            rootDirectory: projectDir,
            sourceDirectories: [new File(projectDir, "src")] as Set
        )
        grailsService.workspaceManager.addProject(project)
        ProjectContextImpl ctx = grailsService.workspaceManager.getProjectForUri(projectDir.toURI().toString())

        when: "A request lease is acquired before removal"
        def lease = ctx.acquireLease()

        and: "Project is removed from workspace"
        grailsService.workspaceManager.removeProject(projectDir.toURI().toString())

        then: "The held lease snapshot remains accessible without crashing"
        lease != null
        lease.snapshot != null
        lease.snapshot.version >= 0

        when: "Lease is released"
        lease.close()

        then: "No exception thrown upon lease release"
        noExceptionThrown()
    }

    def "R2-01/3: late scan publication is safely discarded and cannot resurrect removed project"() {
        given: "A project URI"
        String projectUri = tempDir.resolve("scan-proj").toUri().toString()

        when: "Starting ClassGraph scan"
        ClassLoader cl = new URLClassLoader([] as URL[], ClassLoader.getPlatformClassLoader())
        DiscoveryService.updateClassGraph(projectUri, cl, grailsService.errorService)

        and: "Project is immediately removed before scan publishes"
        DiscoveryService.removeProject(projectUri)

        // Wait briefly for background thread to execute
        Thread.sleep(500)

        then: "Scan result for removed project is null (discarded, not published)"
        DiscoveryService.getClassGraphScanResult(projectUri) == null
    }

    def "Regression: ProjectCache rejects v1 legacy cache and cache missing sourceSets"() {
        given: "A project directory with a legacy v1 cache file"
        File workDir = tempDir.toFile()
        File projectDir = new File(workDir, "proj-cache-legacy")
        projectDir.mkdirs()

        File cacheDir = new File(projectDir, ".grails-lsp")
        cacheDir.mkdirs()
        File cacheFile = new File(cacheDir, "projectInfo.cache")

        // Write a legacy version 1 cache
        cacheFile.withObjectOutputStream { oos ->
            oos.writeInt(1) // legacy version
            oos.writeObject(new GrailsProject(name: "legacy", rootDirectory: projectDir))
        }

        kingsk.grails.lsp.core.gradle.ProjectCache cache = new kingsk.grails.lsp.core.gradle.ProjectCache()

        when: "Loading cache with version mismatch"
        GrailsProject loaded = cache.load(projectDir)

        then: "Legacy cache is rejected and returns null"
        loaded == null

        when: "Writing current version (2) but with null sourceSets"
        cacheFile.withObjectOutputStream { oos ->
            oos.writeInt(2)
            oos.writeObject(new GrailsProject(name: "missing-sourcesets", rootDirectory: projectDir, sourceSets: null))
        }
        GrailsProject loadedNoSS = cache.load(projectDir)

        then: "Cache missing source-set membership is rejected"
        loadedNoSS == null
    }

    /**
     * Checks whether actualLoader is the same as or a parent in the chain of expectedRoot.
     * GroovyClassLoader delegates JAR class loading to its URLClassLoader parent,
     * so the loaded class's classLoader will be the parent, not the GroovyClassLoader itself.
     */
    private static boolean isInClassLoaderChain(ClassLoader actualLoader, ClassLoader expectedRoot) {
        if (actualLoader == null || expectedRoot == null) return false
        ClassLoader current = expectedRoot
        while (current != null) {
            if (current == actualLoader) return true
            current = current.parent
        }
        return false
    }
}
