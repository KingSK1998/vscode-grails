package kingsk.grails.lsp

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.core.compiler.GrailsCompiler
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.dto.DependencyNode
import kingsk.grails.lsp.model.config.GrailsLspConfig
import kingsk.grails.lsp.model.enums.ErrorSource
import kingsk.grails.lsp.model.enums.ErrorSeverity
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.protocol.GrailsLanguageClient
import kingsk.grails.lsp.protocol.dto.ProjectDTO
import kingsk.grails.lsp.protocol.mapper.ProjectMapper
import kingsk.grails.lsp.providers.ProviderRegistry
import kingsk.grails.lsp.providers.document.GrailsGormSqlProvider
import kingsk.grails.lsp.providers.workspace.GrailsDependencyProvider
import kingsk.grails.lsp.providers.workspace.GrailsTestDiscoveryProvider
import kingsk.grails.lsp.services.CancellationService
import kingsk.grails.lsp.services.DiscoveryService
import kingsk.grails.lsp.services.ErrorService
import kingsk.grails.lsp.services.FileContentTracker
import kingsk.grails.lsp.services.GradleService
import kingsk.grails.lsp.services.GrailsDiagnosticService
import kingsk.grails.lsp.services.GrailsTextDocumentService
import kingsk.grails.lsp.services.GrailsWorkspaceService
import kingsk.grails.lsp.services.ProgressService
import kingsk.grails.lsp.services.ProviderHealthService
import kingsk.grails.lsp.services.WorkspaceManager
import kingsk.grails.lsp.utils.cache.ThreadSafeLruCache
import kingsk.grails.lsp.utils.project.ProjectDiffUtil
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock

@Slf4j
@CompileStatic
class GrailsService implements LanguageClientAware, ProviderContext {

    GrailsLanguageClient client

    private final Map<String, ProjectDTO> lastSent = new ConcurrentHashMap<>()

    @Override
    void connect(LanguageClient client) {
        this.client = client as GrailsLanguageClient
    }

    final GrailsWorkspaceService workspace
    final GrailsTextDocumentService document

    final GradleService gradle
    final FileContentTracker fileTracker
    final GrailsDiagnosticService diagnostics
    final ProgressService progressService
    final ErrorService errorService
    final DiscoveryService discoveryService
    final GrailsDependencyProvider dependencyProvider
    final GrailsGormSqlProvider gormSqlProvider
    final GrailsTestDiscoveryProvider testDiscoveryProvider
    final CancellationService cancellationService
    final ProviderHealthService healthService
    final ProviderRegistry providerRegistry

    final GrailsLspConfig config

    final Executor backgroundExecutor = Executors.newFixedThreadPool(4)
    private final ReentrantReadWriteLock astLock = new ReentrantReadWriteLock()

    public <T> T withReadLock(groovy.lang.Closure<T> closure) {
        def lock = astLock.readLock()
        lock.lock()
        try {
            closure()
        } finally {
            lock.unlock()
        }
    }

    public <T> T withWriteLock(groovy.lang.Closure<T> closure) {
        def lock = astLock.writeLock()
        lock.lock()
        try {
            closure()
        } finally {
            lock.unlock()
        }
    }

    final WorkspaceManager workspaceManager

    GrailsService() {
        this.errorService = new ErrorService(this)
        this.cancellationService = new CancellationService()
        this.healthService = new ProviderHealthService()
        this.discoveryService = new DiscoveryService()
        this.workspaceManager = new WorkspaceManager(this)
        this.providerRegistry = new ProviderRegistry(this, workspaceManager)
        this.gradle = new GradleService(this)
        this.fileTracker = new FileContentTracker(this)
        this.progressService = new ProgressService(this)
        this.diagnostics = new GrailsDiagnosticService(this)
        this.document = new GrailsTextDocumentService(this)
        this.workspace = new GrailsWorkspaceService(this)
        this.dependencyProvider = new GrailsDependencyProvider(this, workspaceManager)
        this.gormSqlProvider = new GrailsGormSqlProvider(this, workspaceManager)
        this.testDiscoveryProvider = new GrailsTestDiscoveryProvider(this, workspaceManager)
        this.config = new GrailsLspConfig()
    }

    /**
     * Clears all globally tracked symbol caches.
     */
    void clearCrossFileCaches() {
        log.debug("[GrailsService] Evicting cross-file symbol caches")
        providerRegistry.getProvider(kingsk.grails.lsp.providers.document.GrailsCompletionProvider).clearCaches()
        discoveryService.clearSymbolCaches()
        kingsk.grails.lsp.utils.grails.GroovyRuntimeIntegration.clearCaches()
        kingsk.grails.lsp.utils.grails.GroovyHelperIntegration.clearCaches()
        kingsk.grails.lsp.utils.grails.GrailsHelperIntegration.clearCaches()
    }

    ProjectDTO getProjectInfo(String projectDir) {
        String uri = new File(projectDir).toURI().toString()
        GrailsProject project = workspaceManager.getProjectForUri(uri)?.project

        if (project == null) {
            project = gradle.getGrailsProject(projectDir)
            if (project == null) return null
            workspaceManager.addProject(project)
        }

        return ProjectMapper.toDTO(project)
    }

    void notifyAllProjects() {
        List<ProjectDTO> dtos = workspaceManager.allProjects
            .collect { ProjectMapper.toDTO(it) }

        client.notifyAllProjects(dtos)
    }

    void addProject(GrailsProject project) {
        if (project?.rootDirectory) {
            workspaceManager.addProject(project)
        }
    }

    void removeProject(String projectDir) {
        String uri = new File(projectDir).toURI().toString()
        workspaceManager.removeProject(uri)
    }

    void updateProject(GrailsProject project, String projectDir) {
        if (project && projectDir) {
            workspaceManager.addProject(project)
        }
    }

    GrailsProject getProject() {
        return workspaceManager.getDefaultProject()?.project
    }

    GrailsProject getProjectForUri(String uri) {
        return workspaceManager.getProjectForUri(uri)?.project
    }

    File getJavaDocJarFile(DependencyNode dependency) {
        GrailsProject currentProject = getProject()
        if (!dependency || !currentProject) return null

        DependencyNode dep = currentProject.dependencies.find { it == dependency }
        if (dep?.javadocFileClasspath) return dep.javadocFileClasspath

        File downloaded = gradle?.downloadJavaDocJarFile(currentProject.rootDirectory, dependency)
        if (downloaded) {
            dep?.javadocFileClasspath = downloaded
            return downloaded
        }

        return null
    }

    File getSourcesJarFile(DependencyNode dependency) {
        GrailsProject currentProject = getProject()
        if (!dependency || !currentProject) return null

        DependencyNode dep = currentProject.dependencies.find { it == dependency }
        if (dep?.sourceJarFileClasspath) return dep.sourceJarFileClasspath

        File download = gradle?.downloadSourcesJarFile(currentProject.rootDirectory, dependency)
        if (download) {
            dep?.sourceJarFileClasspath = download
            return download
        }

        return null
    }

    /**
     * Emergency Exit Protocol for OutOfMemoryError.
     */
    void handleOOM(OutOfMemoryError e) {
        log.error("[GrailsService] FATAL: OutOfMemoryError detected! Executing Emergency Exit Protocol.", e)
        
        // Fatal exit to trigger external process restart
        System.exit(1)
    }

    void shutdown() {
        log.info("[GrailsService] Shutting down executors...")

        if (backgroundExecutor instanceof java.util.concurrent.ExecutorService) {
            ((java.util.concurrent.ExecutorService) backgroundExecutor).shutdown()
            try {
                if (!((java.util.concurrent.ExecutorService) backgroundExecutor).awaitTermination(5, TimeUnit.SECONDS)) {
                    ((java.util.concurrent.ExecutorService) backgroundExecutor).shutdownNow()
                }
            } catch (InterruptedException e) {
                ((java.util.concurrent.ExecutorService) backgroundExecutor).shutdownNow()
                Thread.currentThread().interrupt()
            }
        }

        fileTracker?.shutdown()
        document?.shutdown()
        cancellationService?.cancelAll()
        ThreadSafeLruCache.shutdown()

        log.info("[GrailsService] Shutdown complete")
    }
}
