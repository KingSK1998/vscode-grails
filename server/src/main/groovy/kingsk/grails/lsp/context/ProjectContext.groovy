package kingsk.grails.lsp.context

import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.state.ProjectState
import kingsk.grails.lsp.model.state.SnapshotManager
import kingsk.grails.lsp.protocol.dto.ProjectDTO
import java.util.concurrent.CompletableFuture

interface ProjectContext {
    GrailsProject getProject()
    Map<String, GrailsProject> getProjects()
    String getActiveProjectUri()
    void setActiveProjectUri(String uri)
    ProjectDTO getProjectInfo(String projectDir)
    void notifyAllProjects()
    void addProject(GrailsProject project)
    void removeProject(String projectDir)
    void updateProject(GrailsProject project, String projectDir)
    GrailsProject getProjectForUri(String uri)

    // Lifecycle Management (Phase 3b)
    SnapshotManager getSnapshotManager()
    void markAccessed()
    long getLastAccessedTime()
    ProjectState getState()
    CompletableFuture<Void> getActivationFuture()
    void reactivate(Closure<Void> activationTask)
    void ready()
    void hibernate()
    void dispose()

    // Dependency & Sync Status (Phase 3 & R1-03)
    boolean isDependencyDirty()
    void setDependencyDirty(boolean dirty)
    void resetFailedState()
    void triggerGradleSync()
    void retryGradleSync()
    boolean isGradleSyncInProgress()
    boolean isGradleSyncStale()
    String getLastSyncError()
    int getSyncRetryCount()
    CompletableFuture<GrailsProject> getCurrentGradleSyncFuture()
    void recompileAsync()

    // Classpath Isolation (R2-01)
    ClassLoader getClassLoaderForUri(String uri)
}