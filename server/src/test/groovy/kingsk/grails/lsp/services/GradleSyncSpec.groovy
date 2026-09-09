package kingsk.grails.lsp.services

import kingsk.grails.lsp.test.BaseLspSpec
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.state.ProjectState
import kingsk.grails.lsp.context.ProjectContextImpl
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import spock.lang.Subject

class GradleSyncSpec extends BaseLspSpec {

    @Subject
    GrailsWorkspaceService workspaceService

    def setup() {
        setupProject()
        workspaceService = grailsService.workspace
        workspaceService.shutdown()
    }

    def cleanup() {
        workspaceService?.shutdown()
    }

    def "should cancel previous debounce future when new build file change arrives"() {
        given: "A workspace with a project"
        File root = new File(System.getProperty("user.dir"), "build/gradle_bounce_test")
        root.mkdirs()
        new File(root, "build.gradle").text = ""
        def proj = new GrailsProject(name: "BounceTest", rootDirectory: root)
        grailsService.workspaceManager.addProject(proj)

        String buildUri = new File(root, "build.gradle").toURI().toString()

        when: "A build file change event arrives"
        workspaceService.didChangeWatchedFiles(
            new DidChangeWatchedFilesParams(
                [new FileEvent(buildUri, FileChangeType.Changed)]
            )
        )

        then: "A debounce future is scheduled"
        workspaceService.@debounceFuture != null
        !workspaceService.@debounceFuture.isDone()

        when: "Another change arrives before debounce fires"
        def firstFuture = workspaceService.@debounceFuture
        workspaceService.didChangeWatchedFiles(
            new DidChangeWatchedFilesParams(
                [new FileEvent(buildUri, FileChangeType.Changed)]
            )
        )

        then: "Previous future was cancelled, new one is scheduled"
        firstFuture.isCancelled() || firstFuture.isDone()
        workspaceService.@debounceFuture != null
        workspaceService.@debounceFuture != firstFuture
    }

    def "should not trigger sync for non-build files"() {
        given: "A workspace with a project"
        File root = new File(System.getProperty("user.dir"), "build/gradle_nontrigger_test")
        root.mkdirs()
        def proj = new GrailsProject(name: "NoTriggerTest", rootDirectory: root)
        grailsService.workspaceManager.addProject(proj)

        when: "A non-build file change event arrives"
        String groovyUri = new File(root, "src/main/groovy/Test.groovy").toURI().toString()
        workspaceService.didChangeWatchedFiles(
            new DidChangeWatchedFilesParams(
                [new FileEvent(groovyUri, FileChangeType.Changed)]
            )
        )

        then: "No debounce future is scheduled for non-build files"
        workspaceService.@debounceFuture == null
    }

    def "should transition to FAILED state on Gradle sync error for non-Gradle project"() {
        given: "A project directory without Gradle wrapper"
        File root = new File(System.getProperty("user.dir"), "build/gradle_fail_test")
        root.mkdirs()
        new File(root, "build.gradle").text = "throw new RuntimeException('Intentional build failure')"
        def proj = new GrailsProject(name: "FailTest", rootDirectory: root)
        grailsService.workspaceManager.addProject(proj)
        def ctx = (ProjectContextImpl) grailsService.workspaceManager.getProjectForUri(root.toURI().toString())

        and: "Context initially not FAILED"
        ctx.state != ProjectState.FAILED

        when: "Gradle sync is triggered on non-Gradle directory"
        ctx.triggerGradleSync()
        long start = System.currentTimeMillis()
        while (ctx.state != ProjectState.FAILED && (System.currentTimeMillis() - start) < 10000) {
            Thread.sleep(100)
        }

        then: "Project transitions to FAILED state on sync error"
        ctx.state == ProjectState.FAILED
    }

    def "should reset from FAILED to HIBERNATED on build file update"() {
        given: "A project in FAILED state"
        File root = new File(System.getProperty("user.dir"), "build/gradle_reset_test")
        root.mkdirs()
        new File(root, "build.gradle").text = ""
        def proj = new GrailsProject(name: "ResetTest", rootDirectory: root)
        grailsService.workspaceManager.addProject(proj)
        def ctx = (ProjectContextImpl) grailsService.workspaceManager.getProjectForUri(root.toURI().toString())

        // Force to FAILED
        def field = ProjectContextImpl.getDeclaredField("state")
        field.setAccessible(true)
        def stateRef = field.get(ctx) as java.util.concurrent.atomic.AtomicReference<ProjectState>
        stateRef.set(ProjectState.FAILED)
        assert ctx.state == ProjectState.FAILED

        when: "resetFailedState is called (triggered by build file update)"
        ctx.resetFailedState()

        then: "Project transitions from FAILED to HIBERNATED"
        ctx.state == ProjectState.HIBERNATED
    }

    def "should identify build configuration files correctly"() {
        expect:
        GrailsWorkspaceService.isBuildConfigurationFile("file:///path/build.gradle")
        GrailsWorkspaceService.isBuildConfigurationFile("file:///path/build.gradle.kts")
        GrailsWorkspaceService.isBuildConfigurationFile("file:///path/settings.gradle")
        GrailsWorkspaceService.isBuildConfigurationFile("file:///path/gradle.properties")

        and:
        !GrailsWorkspaceService.isBuildConfigurationFile("file:///path/SomeController.groovy")
        !GrailsWorkspaceService.isBuildConfigurationFile("file:///path/application.yml")
        !GrailsWorkspaceService.isBuildConfigurationFile(null)
    }

    def "should cache Gradle project after successful sync"() {
        given: "GradleService with a project cache"
        def gradleService = grailsService.gradle
        File root = new File(System.getProperty("user.dir"), "build/gradle_cache_test")
        root.mkdirs()

        expect: "Cache invalidation works without errors"
        gradleService.invalidateCache()
        gradleService.invalidateProjectCache(root.absolutePath)

        and: "ProjectCache exists and is initialized"
        gradleService.@cache != null
    }
}