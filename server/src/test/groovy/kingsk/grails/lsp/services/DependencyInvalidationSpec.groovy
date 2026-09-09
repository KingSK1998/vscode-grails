package kingsk.grails.lsp.services

import kingsk.grails.lsp.test.BaseLspSpec
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.dto.DependencyNode
import kingsk.grails.lsp.context.ProjectContextImpl
import spock.lang.Subject

class DependencyInvalidationSpec extends BaseLspSpec {

    @Subject
    WorkspaceManager workspaceManager

    def setup() {
        setupProject()
        workspaceManager = grailsService.workspaceManager
    }

    def "should detect dependency by matching dependency name"() {
        given: "A downstream project referencing upstream by name"
        File upstreamRoot = new File(System.getProperty("user.dir"), "build/dep_inv_upstream")
        File downstreamRoot = new File(System.getProperty("user.dir"), "build/dep_inv_downstream")
        upstreamRoot.mkdirs()
        downstreamRoot.mkdirs()

        def upstream = new GrailsProject(name: "UpstreamLib", rootDirectory: upstreamRoot)
        def depNode = new DependencyNode("UpstreamLib", "com.test", "1.0", "compile", null, null, null)
        def downstream = new GrailsProject(name: "DownstreamApp", rootDirectory: downstreamRoot, dependencies: [depNode])

        workspaceManager.addProject(upstream)
        workspaceManager.addProject(downstream)

        def upstreamCtx = workspaceManager.getProjectForUri(upstreamRoot.toURI().toString())
        def downstreamCtx = workspaceManager.getProjectForUri(downstreamRoot.toURI().toString())

        expect:
        workspaceManager.projectDependsOn((ProjectContextImpl) downstreamCtx, (ProjectContextImpl) upstreamCtx)
        !workspaceManager.projectDependsOn((ProjectContextImpl) upstreamCtx, (ProjectContextImpl) downstreamCtx)
    }

    def "should detect dependency by jar classpath inside dependency root"() {
        given: "A jar file inside the dependency project directory"
        File upstreamRoot = new File(System.getProperty("user.dir"), "build/dep_jar_upstream")
        File libDir = new File(upstreamRoot, "build/libs")
        libDir.mkdirs()
        File jarFile = new File(libDir, "upstream.jar")
        jarFile.createNewFile()

        File downstreamRoot = new File(System.getProperty("user.dir"), "build/dep_jar_downstream")
        downstreamRoot.mkdirs()

        def depNode = new DependencyNode("some-dep", "com.test", "1.0", "compile", jarFile, null, null)
        def downstream = new GrailsProject(name: "DownstreamApp", rootDirectory: downstreamRoot, dependencies: [depNode])
        def upstream = new GrailsProject(name: "UpstreamLib", rootDirectory: upstreamRoot)

        workspaceManager.addProject(upstream)
        workspaceManager.addProject(downstream)

        def upstreamCtx = workspaceManager.getProjectForUri(upstreamRoot.toURI().toString())
        def downstreamCtx = workspaceManager.getProjectForUri(downstreamRoot.toURI().toString())

        expect:
        workspaceManager.projectDependsOn((ProjectContextImpl) downstreamCtx, (ProjectContextImpl) upstreamCtx)
    }

    def "should not detect dependency when no link exists"() {
        given: "Two unrelated projects"
        File root1 = new File(System.getProperty("user.dir"), "build/dep_unrelated_a")
        File root2 = new File(System.getProperty("user.dir"), "build/dep_unrelated_b")
        root1.mkdirs()
        root2.mkdirs()

        def projA = new GrailsProject(name: "AppA", rootDirectory: root1)
        def projB = new GrailsProject(name: "AppB", rootDirectory: root2)

        workspaceManager.addProject(projA)
        workspaceManager.addProject(projB)

        def ctxA = workspaceManager.getProjectForUri(root1.toURI().toString())
        def ctxB = workspaceManager.getProjectForUri(root2.toURI().toString())

        expect:
        !workspaceManager.projectDependsOn((ProjectContextImpl) ctxA, (ProjectContextImpl) ctxB)
        !workspaceManager.projectDependsOn((ProjectContextImpl) ctxB, (ProjectContextImpl) ctxA)
    }

    def "should mark downstream project dirty on upstream snapshot commit"() {
        given: "An upstream library and downstream app"
        File upstreamRoot = new File(System.getProperty("user.dir"), "build/dep_commit_upstream")
        File downstreamRoot = new File(System.getProperty("user.dir"), "build/dep_commit_downstream")
        upstreamRoot.mkdirs()
        downstreamRoot.mkdirs()

        def upstream = new GrailsProject(name: "UpstreamLib", rootDirectory: upstreamRoot)
        def depNode = new DependencyNode("UpstreamLib", "com.test", "1.0", "compile", null, null, null)
        def downstream = new GrailsProject(name: "DownstreamApp", rootDirectory: downstreamRoot, dependencies: [depNode])

        workspaceManager.addProject(upstream)
        workspaceManager.addProject(downstream)

        def upstreamCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(upstreamRoot.toURI().toString())
        def downstreamCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(downstreamRoot.toURI().toString())

        and: "Downstream initially clean"
        !downstreamCtx.isDependencyDirty()

        when: "Upstream commits a new snapshot"
        upstreamCtx.commitSnapshot()

        then: "Downstream is marked dirty"
        downstreamCtx.isDependencyDirty()
    }

    def "should not mark independent project dirty when upstream changes"() {
        given: "An upstream, a dependent downstream, and an independent project"
        File upstreamRoot = new File(System.getProperty("user.dir"), "build/dep_ind_upstream")
        File downstreamRoot = new File(System.getProperty("user.dir"), "build/dep_ind_downstream")
        File standaloneRoot = new File(System.getProperty("user.dir"), "build/dep_ind_standalone")
        upstreamRoot.mkdirs()
        downstreamRoot.mkdirs()
        standaloneRoot.mkdirs()

        def upstream = new GrailsProject(name: "UpstreamLib", rootDirectory: upstreamRoot)
        def depNode = new DependencyNode("UpstreamLib", "com.test", "1.0", "compile", null, null, null)
        def downstream = new GrailsProject(name: "DownstreamApp", rootDirectory: downstreamRoot, dependencies: [depNode])
        def standalone = new GrailsProject(name: "StandaloneApp", rootDirectory: standaloneRoot)

        workspaceManager.addProject(upstream)
        workspaceManager.addProject(downstream)
        workspaceManager.addProject(standalone)

        def upstreamCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(upstreamRoot.toURI().toString())
        def downstreamCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(downstreamRoot.toURI().toString())
        def standaloneCtx = (ProjectContextImpl) workspaceManager.getProjectForUri(standaloneRoot.toURI().toString())

        and: "All initially clean"
        !downstreamCtx.isDependencyDirty()
        !standaloneCtx.isDependencyDirty()

        when: "Upstream commits a new snapshot"
        upstreamCtx.commitSnapshot()

        then: "Only downstream is marked dirty; standalone remains clean"
        downstreamCtx.isDependencyDirty()
        !standaloneCtx.isDependencyDirty()
    }

    def "should handle circular dependency graph without infinite loop"() {
        given: "Three projects with a circular dependency: A -> B -> C -> A"
        File rootA = new File(System.getProperty("user.dir"), "build/dep_cyc_a")
        File rootB = new File(System.getProperty("user.dir"), "build/dep_cyc_b")
        File rootC = new File(System.getProperty("user.dir"), "build/dep_cyc_c")
        rootA.mkdirs()
        rootB.mkdirs()
        rootC.mkdirs()

        // A depends on B, B depends on C, C depends on A
        def depA = new DependencyNode("B", "com.test", "1.0", "compile", null, null, null)
        def depB = new DependencyNode("C", "com.test", "1.0", "compile", null, null, null)
        def depC = new DependencyNode("A", "com.test", "1.0", "compile", null, null, null)

        def projA = new GrailsProject(name: "A", rootDirectory: rootA, dependencies: [depA])
        def projB = new GrailsProject(name: "B", rootDirectory: rootB, dependencies: [depB])
        def projC = new GrailsProject(name: "C", rootDirectory: rootC, dependencies: [depC])

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

        when: "Project A commits a new snapshot (triggers propagation)"
        ctxA.commitSnapshot()

        then: "Project C is marked dirty (depends on A); Project B is marked dirty (depends on C); cycle protection prevents infinite loop"
        ctxC.isDependencyDirty()
        ctxB.isDependencyDirty()
    }
}
