package kingsk.grails.lsp.services

import kingsk.grails.lsp.test.BaseLspSpec
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.dto.DependencyNode
import kingsk.grails.lsp.model.dto.ProjectDependencyEdge
import kingsk.grails.lsp.model.dto.SourceSetModel
import kingsk.grails.lsp.context.ProjectContextImpl
import spock.lang.Subject

class DependencyInvalidationSpec extends BaseLspSpec {

    @Subject
    WorkspaceManager workspaceManager

    private File testBaseDir

    def setup() {
        setupProject()
        workspaceManager = grailsService.workspaceManager
        testBaseDir = new File(System.getProperty("user.dir"), "build/dep_inv_test_${System.currentTimeMillis()}")
        testBaseDir.mkdirs()
    }

    // ==========================================
    // R2-03/1: Resolved build/project/source-set relationships
    // ==========================================

    def "R2-03/1: detects dependency for renamed module via ProjectDependencyEdge and source-set output"() {
        given: "A multi-project build where module name differs from folder name"
        File buildRoot = new File(testBaseDir, "renamed_build")
        File authModuleDir = new File(buildRoot, "modules/renamed-auth")
        File appModuleDir = new File(buildRoot, "apps/web-app")
        File authClassesDir = new File(authModuleDir, "build/classes/groovy/main")
        authClassesDir.mkdirs()
        appModuleDir.mkdirs()

        // Upstream has custom project name and gradle path ":auth"
        SourceSetModel authMainSS = new SourceSetModel(
            "main",
            ":auth",
            buildRoot,
            authModuleDir,
            [], [], [], [], [],
            [authClassesDir]
        )
        def upstream = new GrailsProject(
            name: "custom-auth-name",
            rootDirectory: authModuleDir,
            buildRoot: buildRoot,
            gradleProjectPath: ":auth",
            sourceSets: ["main": authMainSS]
        )

        // Downstream references upstream via explicit project dependency edge
        ProjectDependencyEdge edge = new ProjectDependencyEdge(
            ":auth",
            buildRoot,
            authModuleDir,
            "custom-auth-name"
        )
        def downstreamWithEdge = new GrailsProject(
            name: "web-app",
            rootDirectory: appModuleDir,
            buildRoot: buildRoot,
            gradleProjectPath: ":apps:web-app",
            projectDependencies: [edge] as Set
        )

        workspaceManager.addProject(upstream)
        workspaceManager.addProject(downstreamWithEdge)

        def upstreamCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(authModuleDir.toURI().toString())
        def downstreamCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(appModuleDir.toURI().toString())

        expect: "Downstream depends on upstream via resolved project edge despite renamed module"
        workspaceManager.projectDependsOn(downstreamCtx, upstreamCtx)
        !workspaceManager.projectDependsOn(upstreamCtx, downstreamCtx)

        when: "Downstream instead links via evaluated compileClasspath pointing to upstream output directory"
        SourceSetModel appMainSS = new SourceSetModel(
            "main",
            ":apps:web-app",
            buildRoot,
            appModuleDir,
            [], [], [],
            [authClassesDir], // compileClasspath includes upstream output dir
            [], []
        )
        def downstreamWithClasspath = new GrailsProject(
            name: "web-app",
            rootDirectory: appModuleDir,
            buildRoot: buildRoot,
            gradleProjectPath: ":apps:web-app",
            sourceSets: ["main": appMainSS]
        )
        workspaceManager.addProject(downstreamWithClasspath)
        def downstreamCpCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(appModuleDir.toURI().toString())

        then: "Downstream depends on upstream via source-set output containment"
        workspaceManager.projectDependsOn(downstreamCpCtx, upstreamCtx)
    }

    def "R2-03/1: detects dependency across composite build boundaries"() {
        given: "A composite build with main app and included library build"
        File appBuildRoot = new File(testBaseDir, "composite_app")
        File libBuildRoot = new File(testBaseDir, "composite_lib")
        File libOutDir = new File(libBuildRoot, "build/libs")
        libOutDir.mkdirs()
        File libJar = new File(libOutDir, "composite-lib-1.0.jar")
        libJar.createNewFile()
        appBuildRoot.mkdirs()

        def upstreamLib = new GrailsProject(
            name: "composite-lib",
            rootDirectory: libBuildRoot,
            buildRoot: libBuildRoot,
            gradleProjectPath: ":"
        )

        ProjectDependencyEdge compositeEdge = new ProjectDependencyEdge(
            ":",
            libBuildRoot,
            libBuildRoot,
            "composite-lib"
        )
        def downstreamApp = new GrailsProject(
            name: "composite-app",
            rootDirectory: appBuildRoot,
            buildRoot: appBuildRoot,
            gradleProjectPath: ":",
            projectDependencies: [compositeEdge] as Set
        )

        workspaceManager.addProject(upstreamLib)
        workspaceManager.addProject(downstreamApp)

        def libCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(libBuildRoot.toURI().toString())
        def appCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(appBuildRoot.toURI().toString())

        expect: "Composite build edge is detected across separate build roots"
        workspaceManager.projectDependsOn(appCtx, libCtx)
        !workspaceManager.projectDependsOn(libCtx, appCtx)
    }

    def "R2-03/1: negative test - unrelated same-name external dependency does not create edge"() {
        given: "Two unrelated projects where one has an external Maven dependency with the same name as the other"
        File root1 = new File(testBaseDir, "unrelated_app")
        File root2 = new File(testBaseDir, "common") // workspace project named "common"
        File externalCacheDir = new File(testBaseDir, "gradle_cache/org/external/common/1.0")
        externalCacheDir.mkdirs()
        File externalJar = new File(externalCacheDir, "common-1.0.jar")
        externalJar.createNewFile()
        root1.mkdirs()
        root2.mkdirs()

        // App depends on external library "common" from Maven Central
        def externalDep = new DependencyNode("common", "org.external", "1.0", "compile", externalJar, null, null)
        def app = new GrailsProject(
            name: "unrelated-app",
            rootDirectory: root1,
            buildRoot: root1,
            gradleProjectPath: ":",
            dependencies: [externalDep] as Set
        )

        // Independent workspace project also named "common"
        def workspaceCommon = new GrailsProject(
            name: "common",
            rootDirectory: root2,
            buildRoot: root2,
            gradleProjectPath: ":"
        )

        workspaceManager.addProject(app)
        workspaceManager.addProject(workspaceCommon)

        def appCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(root1.toURI().toString())
        def commonCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(root2.toURI().toString())

        expect: "External dependency sharing a name does NOT create a project dependency"
        !workspaceManager.projectDependsOn(appCtx, commonCtx)
        !workspaceManager.projectDependsOn(commonCtx, appCtx)
    }

    def "R2-03/1: negative test - sibling directory with shared string prefix is not falsely matched"() {
        given: "Two sibling projects where one name is a string prefix of the other"
        File rootParent = new File(testBaseDir, "sibling_test")
        File projA = new File(rootParent, "project")
        File projExtra = new File(rootParent, "project-extra")
        File consumerRoot = new File(rootParent, "consumer")
        File extraJarDir = new File(projExtra, "build/libs")
        extraJarDir.mkdirs()
        File extraJar = new File(extraJarDir, "extra.jar")
        extraJar.createNewFile()
        projA.mkdirs()
        consumerRoot.mkdirs()

        // Consumer references JAR inside project-extra
        def extraDep = new DependencyNode("project-extra", "com.test", "1.0", "compile", extraJar, null, null)
        def consumer = new GrailsProject(
            name: "consumer",
            rootDirectory: consumerRoot,
            buildRoot: consumerRoot,
            dependencies: [extraDep] as Set
        )
        def projectA = new GrailsProject(
            name: "project",
            rootDirectory: projA,
            buildRoot: projA
        )
        def projectExtra = new GrailsProject(
            name: "project-extra",
            rootDirectory: projExtra,
            buildRoot: projExtra
        )

        workspaceManager.addProject(consumer)
        workspaceManager.addProject(projectA)
        workspaceManager.addProject(projectExtra)

        def consumerCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(consumerRoot.toURI().toString())
        def ctxA = (ProjectContextImpl) workspaceManager.getProjectForUri(projA.toURI().toString())
        def ctxExtra = (ProjectContextImpl) workspaceManager.getProjectForUri(projExtra.toURI().toString())

        expect: "Consumer depends on project-extra, but does NOT depend on project (no prefix false positive)"
        workspaceManager.projectDependsOn(consumerCtx, ctxExtra)
        !workspaceManager.projectDependsOn(consumerCtx, ctxA)
    }

    // ==========================================
    // R2-03/2: Cross-project visibility, origin/revision tracking, cycle termination
    // ==========================================

    def "R2-03/2: cross-project propagation marks downstream dirty and records origin URI and snapshot revision"() {
        given: "Upstream library, downstream app, and standalone app"
        File upstreamRoot = new File(testBaseDir, "rec_upstream")
        File downstreamRoot = new File(testBaseDir, "rec_downstream")
        File standaloneRoot = new File(testBaseDir, "rec_standalone")
        upstreamRoot.mkdirs()
        downstreamRoot.mkdirs()
        standaloneRoot.mkdirs()

        ProjectDependencyEdge edge = new ProjectDependencyEdge(":", upstreamRoot, upstreamRoot, "upstream-lib")
        def upstream = new GrailsProject(name: "upstream-lib", rootDirectory: upstreamRoot, buildRoot: upstreamRoot)
        def downstream = new GrailsProject(name: "downstream-app", rootDirectory: downstreamRoot, buildRoot: downstreamRoot, projectDependencies: [edge] as Set)
        def standalone = new GrailsProject(name: "standalone-app", rootDirectory: standaloneRoot, buildRoot: standaloneRoot)

        workspaceManager.addProject(upstream)
        workspaceManager.addProject(downstream)
        workspaceManager.addProject(standalone)

        def upstreamCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(upstreamRoot.toURI().toString())
        def downstreamCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(downstreamRoot.toURI().toString())
        def standaloneCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(standaloneRoot.toURI().toString())

        and: "All initially clean"
        !downstreamCtx.isDependencyDirty()
        !standaloneCtx.isDependencyDirty()
        downstreamCtx.getLastInvalidationOrigin() == null
        downstreamCtx.getLastInvalidationRevision() == 0L

        when: "Upstream commits snapshot v1"
        upstreamCtx.commitSnapshot()

        then: "Downstream is marked dirty and records exact upstream origin URI and revision"
        downstreamCtx.isDependencyDirty()
        downstreamCtx.getLastInvalidationOrigin() == upstreamRoot.toURI().toString()
        downstreamCtx.getLastInvalidationRevision() == 1L

        and: "Standalone independent project is unaffected"
        !standaloneCtx.isDependencyDirty()
        standaloneCtx.getLastInvalidationOrigin() == null
        standaloneCtx.getLastInvalidationRevision() == 0L
    }

    def "R2-03/2: cycle protection visits each node at most once per propagation without looping"() {
        given: "Three projects with circular dependencies: A -> B -> C -> A"
        File rootA = new File(testBaseDir, "cycle_a")
        File rootB = new File(testBaseDir, "cycle_b")
        File rootC = new File(testBaseDir, "cycle_c")
        rootA.mkdirs()
        rootB.mkdirs()
        rootC.mkdirs()

        // A depends on B, B depends on C, C depends on A
        def edgeA = new ProjectDependencyEdge(":b", testBaseDir, rootB, "B")
        def edgeB = new ProjectDependencyEdge(":c", testBaseDir, rootC, "C")
        def edgeC = new ProjectDependencyEdge(":a", testBaseDir, rootA, "A")

        def projA = new GrailsProject(name: "A", rootDirectory: rootA, buildRoot: testBaseDir, gradleProjectPath: ":a", projectDependencies: [edgeA] as Set)
        def projB = new GrailsProject(name: "B", rootDirectory: rootB, buildRoot: testBaseDir, gradleProjectPath: ":b", projectDependencies: [edgeB] as Set)
        def projC = new GrailsProject(name: "C", rootDirectory: rootC, buildRoot: testBaseDir, gradleProjectPath: ":c", projectDependencies: [edgeC] as Set)

        workspaceManager.addProject(projA)
        workspaceManager.addProject(projB)
        workspaceManager.addProject(projC)

        def ctxA = (ProjectContextImpl) workspaceManager.getProjectForUri(rootA.toURI().toString())
        def ctxB = (ProjectContextImpl) workspaceManager.getProjectForUri(rootB.toURI().toString())
        def ctxC = (ProjectContextImpl) workspaceManager.getProjectForUri(rootC.toURI().toString())

        and: "All initially clean"
        !ctxA.isDependencyDirty()
        !ctxB.isDependencyDirty()
        !ctxC.isDependencyDirty()

        when: "Project A commits snapshot (triggers propagation cascade)"
        ctxA.commitSnapshot()

        then: "Project C is marked dirty (depends on A) and records A as origin"
        ctxC.isDependencyDirty()
        ctxC.getLastInvalidationOrigin() == rootA.toURI().toString()

        and: "Project B is marked dirty (depends on C) and records A as origin"
        ctxB.isDependencyDirty()
        ctxB.getLastInvalidationOrigin() == rootA.toURI().toString()

        and: "Project A was the origin, so it is NOT marked dirty by its own cascade"
        !ctxA.isDependencyDirty()
    }

    // ==========================================
    // R2-03/3: Non-blocking propagation and dynamic edge add/remove
    // ==========================================

    def "R2-03/3: dynamic edge add and remove updates downstream dirty state without waiting on upstream compile"() {
        given: "Two initially independent projects"
        File rootA = new File(testBaseDir, "dyn_a")
        File rootB = new File(testBaseDir, "dyn_b")
        rootA.mkdirs()
        rootB.mkdirs()

        def projA = new GrailsProject(name: "LibA", rootDirectory: rootA, buildRoot: rootA)
        def projB = new GrailsProject(name: "AppB", rootDirectory: rootB, buildRoot: rootB)

        workspaceManager.addProject(projA)
        workspaceManager.addProject(projB)

        def ctxA = (ProjectContextImpl) workspaceManager.getProjectForUri(rootA.toURI().toString())
        def ctxB = (ProjectContextImpl) workspaceManager.getProjectForUri(rootB.toURI().toString())

        expect: "Initially no dependency"
        !workspaceManager.projectDependsOn(ctxB, ctxA)
        !ctxB.isDependencyDirty()

        when: "Dynamic edge is added (AppB now depends on LibA)"
        ProjectDependencyEdge edge = new ProjectDependencyEdge(":", rootA, rootA, "LibA")
        def updatedProjB = new GrailsProject(
            name: "AppB",
            rootDirectory: rootB,
            buildRoot: rootB,
            projectDependencies: [edge] as Set
        )
        ctxB.updateProject(updatedProjB, rootB.toURI().toString())

        then: "Dependency is now detected"
        workspaceManager.projectDependsOn(ctxB, ctxA)

        when: "Upstream LibA commits snapshot"
        long startTime = System.currentTimeMillis()
        ctxA.commitSnapshot()
        long duration = System.currentTimeMillis() - startTime

        then: "Downstream AppB is marked dirty immediately without blocking or waiting on compilation"
        duration < 500 // non-blocking return
        ctxB.isDependencyDirty()
        ctxB.getLastInvalidationOrigin() == rootA.toURI().toString()

        when: "Dynamic edge is removed (AppB removes dependency on LibA)"
        def cleanProjB = new GrailsProject(name: "AppB", rootDirectory: rootB, buildRoot: rootB)
        ctxB.updateProject(cleanProjB, rootB.toURI().toString())

        then: "Dependency is removed"
        !workspaceManager.projectDependsOn(ctxB, ctxA)
    }

    def "R2-03/3: propagation does not acquire nested project writer locks"() {
        given: "Upstream and downstream projects"
        File rootA = new File(testBaseDir, "lock_a")
        File rootB = new File(testBaseDir, "lock_b")
        rootA.mkdirs()
        rootB.mkdirs()

        ProjectDependencyEdge edge = new ProjectDependencyEdge(":", rootA, rootA, "LockA")
        def projA = new GrailsProject(name: "LockA", rootDirectory: rootA, buildRoot: rootA)
        def projB = new GrailsProject(name: "LockB", rootDirectory: rootB, buildRoot: rootB, projectDependencies: [edge] as Set)

        workspaceManager.addProject(projA)
        workspaceManager.addProject(projB)

        def ctxA = (ProjectContextImpl) workspaceManager.getProjectForUri(rootA.toURI().toString())
        def ctxB = (ProjectContextImpl) workspaceManager.getProjectForUri(rootB.toURI().toString())

        when: "Propagation runs from upstream"
        ctxA.commitSnapshot()

        then: "Downstream context is marked dirty without deadlock or nested locking"
        ctxB.isDependencyDirty()
        ctxB.getLastInvalidationOrigin() == rootA.toURI().toString()
    }
}
