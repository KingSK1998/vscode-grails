package kingsk.grails.lsp.services

import kingsk.grails.lsp.test.BaseLspSpec
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.context.ProjectContextImpl
import kingsk.grails.lsp.model.state.ProjectState
import spock.lang.Subject

class WorkspaceIsolationSpec extends BaseLspSpec {

    @Subject
    WorkspaceManager workspaceManager

    def setup() {
        setupProject()
        workspaceManager = grailsService.workspaceManager
    }

    def "should maintain isolated contexts for different workspace roots"() {
        given: "Two separate project roots"
        File root1 = new File(System.getProperty("user.dir"), "build/test_proj1")
        File root2 = new File(System.getProperty("user.dir"), "build/test_proj2")
        root1.mkdirs()
        root2.mkdirs()

        String uri1 = root1.toURI().toString()
        String uri2 = root2.toURI().toString()

        def proj1 = new GrailsProject(name: "Proj1", rootDirectory: root1)
        def proj2 = new GrailsProject(name: "Proj2", rootDirectory: root2)

        when: "Adding both projects to workspace manager"
        workspaceManager.addProject(proj1)
        workspaceManager.addProject(proj2)

        then: "Both projects have independent contexts"
        def ctx1 = workspaceManager.getProjectForUri(uri1)
        def ctx2 = workspaceManager.getProjectForUri(uri2)

        ctx1 != null
        ctx2 != null
        ctx1 != ctx2
        ctx1.project.name == "Proj1"
        ctx2.project.name == "Proj2"

        and: "Snapshot versions are tracked independently"
        ctx1.commitSnapshot()
        ctx1.snapshotManager.active.version == 1
        ctx2.snapshotManager.active.version == 0
    }

    def "should route file events to correct project context using Longest Root Match"() {
        given: "A workspace with a project and a nested plugin"
        File root1 = new File(System.getProperty("user.dir"), "build/main_project")
        File root2 = new File(root1, "plugins/my_plugin")
        root1.mkdirs()
        root2.mkdirs()

        workspaceManager.addProject(new GrailsProject(name: "MainApp", rootDirectory: root1))
        workspaceManager.addProject(new GrailsProject(name: "Plugin", rootDirectory: root2))

        when: "Requesting context for a file inside the plugin"
        String fileInPlugin = new File(root2, "src/A.groovy").toURI().toString()
        String fileInApp = new File(root1, "src/B.groovy").toURI().toString()

        then: "Plugin file routes to Plugin context"
        workspaceManager.getProjectForUri(fileInPlugin).project.name == "Plugin"

        and: "App file routes to MainApp context"
        workspaceManager.getProjectForUri(fileInApp).project.name == "MainApp"
    }

    def "should hibernate and drop caches when project is removed"() {
        given: "A project in the workspace"
        File root1 = new File(System.getProperty("user.dir"), "build/test_proj1")
        root1.mkdirs()
        String uri = root1.toURI().toString()

        workspaceManager.addProject(new GrailsProject(name: "P1", rootDirectory: root1))
        def ctx = workspaceManager.getProjectForUri(uri)

        when: "Removing the project"
        workspaceManager.removeProject(uri)

        then: "Context is removed"
        workspaceManager.getProjectForUri(uri) == null
        !workspaceManager.getAllProjects().any { it.name == "P1" }
    }

    def "should hibernate LRU project context when active projects exceed limit"() {
        given: "Three projects added to workspace"
        File root1 = new File(System.getProperty("user.dir"), "build/test_proj1")
        File root2 = new File(System.getProperty("user.dir"), "build/test_proj2")
        File root3 = new File(System.getProperty("user.dir"), "build/test_proj3")
        root1.mkdirs()
        root2.mkdirs()
        root3.mkdirs()

        def proj1 = new GrailsProject(name: "Proj1", rootDirectory: root1)
        def proj2 = new GrailsProject(name: "Proj2", rootDirectory: root2)
        def proj3 = new GrailsProject(name: "Proj3", rootDirectory: root3)

        workspaceManager.addProject(proj1)
        workspaceManager.addProject(proj2)
        workspaceManager.addProject(proj3)

        def ctx1 = workspaceManager.getProjectForUri(root1.toURI().toString())
        def ctx2 = workspaceManager.getProjectForUri(root2.toURI().toString())

        // Force them to be READY to trigger LRU eviction budget checking
        ctx1.ready()
        Thread.sleep(10)
        ctx2.ready()

        when: "Accessing third project causing it to become READY"
        // Simulate a delay so timestamps are distinct
        Thread.sleep(10)
        def ctx3 = workspaceManager.getProjectForUri(root3.toURI().toString())
        ctx3.ready()

        then: "Oldest project (Proj1) is hibernated, Proj2 and Proj3 remain READY"
        ctx1.state == ProjectState.HIBERNATED
        ctx2.state == ProjectState.READY
        ctx3.state == ProjectState.READY
    }
}
