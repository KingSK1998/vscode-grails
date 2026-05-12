package kingsk.grails.lsp

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
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
import kingsk.grails.lsp.services.*
import kingsk.grails.lsp.utils.cache.ThreadSafeLruCache
import kingsk.grails.lsp.utils.project.ProjectDiffUtil
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Slf4j
@CompileStatic
class GrailsService implements LanguageClientAware {

    GrailsLanguageClient client
    Map<String, GrailsProject> projects = [:]
    String activeProjectUri

    private final Map<String, ProjectDTO> lastSent = [:]

    @Override
    void connect(LanguageClient client) {
        this.client = client as GrailsLanguageClient
//        progressService.connect(client)
    }

    final GrailsWorkspaceService workspace
    final GrailsTextDocumentService document

    final GradleService gradle
    final GrailsCompiler compiler

    final FileContentTracker fileTracker
    final ASTService astService
    final GrailsASTVisitor visitor
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

    private final Executor backgroundExecutor = Executors.newCachedThreadPool()

    GrailsService() {
        this.errorService = new ErrorService(this)
        this.cancellationService = new CancellationService()
        this.healthService = new ProviderHealthService()
        this.providerRegistry = new ProviderRegistry(this)
        this.discoveryService = new DiscoveryService()
        this.gradle = new GradleService(this)
        this.fileTracker = new FileContentTracker(this)
        this.compiler = new GrailsCompiler(this)
        this.astService = new ASTService(this)
        this.progressService = new ProgressService(this)
        this.diagnostics = new GrailsDiagnosticService(this)
        this.document = new GrailsTextDocumentService(this)
        this.workspace = new GrailsWorkspaceService(this)
        this.visitor = new GrailsASTVisitor(this)
        this.dependencyProvider = new GrailsDependencyProvider(this)
        this.gormSqlProvider = new GrailsGormSqlProvider(this)
        this.testDiscoveryProvider = new GrailsTestDiscoveryProvider(this)
        this.config = new GrailsLspConfig()
    }

    /**
     * Initial workspace setup: full compile + AST index.
     * Sets up the workspace by adding dependencies and source files.
     * This will execute at at the time of Server initialization i.e. no features available yet.
     * @param projectDir The root directory of the Grails project.
     */
    void setupWorkspace(String projectDir, boolean asyncCompile = true) {
        refreshAndReindexWorkspace(projectDir, "Workspace Setup", asyncCompile)
    }

    /**
     * Refresh and reindex workspace: load project, invalidate, compile, AST visit, diagnostics.
     */
    void refreshAndReindexWorkspace(String projectDir, String title = "Workspace Refresh", boolean async = true) {
        Runnable task = {
            progressService.begin(title, "Loading project...")

            GrailsProject project = gradle.getGrailsProjectAsync(projectDir).join()
            if (!project) {
                errorService.handleError(
                    "Invalid Grails project at $projectDir",
                    null,
                    ErrorSource.GRADLE_SERVICE,
                    ErrorSeverity.CRITICAL
                )
                return
            }

            projects[projectDir] = project
            if (!activeProjectUri) activeProjectUri = projectDir

            publishProject(project)

            progressService.update("Project loaded", 20)

            compiler.invalidateCompiler()
            visitor.invalidateVisitor()
            fileTracker.resetFQCNDependencies()
            diagnostics.clearAllDiagnostics()

            compiler.compileProject()

            progressService.update("Visiting AST...", 80)
            visitor.visitCompilationUnit(compiler)

            progressService.update("Publishing diagnostics...", 90)
            diagnostics.publishWorkspaceDiagnostics()

            progressService.end("$title complete")
            log.info("[GrailsService] $title finished")
        }

        if (async) {
            CompletableFuture.runAsync(task, backgroundExecutor)
        } else {
            task.run()
        }
    }

    void compileAndVisitAST(TextFile textFile) {
        if (!textFile) return
        long t0 = System.nanoTime()

        if (textFile.uri.endsWith(".yml") || textFile.uri.endsWith(".yaml")) {
            document.yamlProvider.analyzeSensitiveInfo(textFile)
            return
        }

        if (!compiler.isDirty(textFile.uri) && compiler.compilationExistsFor(textFile.uri)) {
            log.debug("[COMPILE] Skipping - no changes detected for: ${textFile.uri}")
            return
        }

        compiler.markDirty(textFile.uri)
        compiler.compileSourceFile(textFile)
        visitAST(textFile)
        diagnostics.publishDiagnosticsForFile(textFile.uri)
        if (log.isDebugEnabled()) {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)
            log.debug("[PERF] compileAndVisitAST ${textFile.uri} ${ms}ms")
        }
    }

    void visitAST(TextFile textFile) {
        if (!textFile) return
        def sourceUnit = compiler.getSourceUnit(textFile)
        if (!sourceUnit) {
            log.warn "[GrailsService] No source unit found for file: ${textFile.uri}"
            return
        }
        visitor.visitSourceUnit(sourceUnit)
    }

    ProjectDTO getProjectInfo(String projectDir) {
        GrailsProject project = projects[projectDir]

        if (project == null) {
            project = gradle.getGrailsProject(projectDir)
            if (project == null) return null
            projects[projectDir] = project
        }

        return ProjectMapper.toDTO(project)
    }

    void notifyAllProjects() {
        List<ProjectDTO> dtos = projects.values()
            .collect { ProjectMapper.toDTO(it) }

        client.notifyAllProjects(dtos)
    }

    private void publishProject(GrailsProject project) {

        ProjectDTO newDto = ProjectMapper.toDTO(project)
        ProjectDTO oldDto = lastSent.get(newDto.id)

        if (!oldDto) {
            client.projectUpdated(newDto)   // first time → full
        } else {
            def patch = ProjectDiffUtil.diff(oldDto, newDto)
            if (patch) {
                client.projectPatched(patch)
            }
        }

        lastSent.put(newDto.id, newDto)
    }

    GrailsProject getProject() {
        return projects[activeProjectUri]
    }

    GrailsProject getProjectForUri(String uri) {
        GrailsProject project = projects.values().find { GrailsProject p -> uri.startsWith(p.rootDirectory.toURI().toString()) }
        return project ?: getProject()
    }

    /**
     * Retrieve or download the Javadoc JAR for a dependency.
     * Usage:
     * def dependency = new DependencyNode(
     *              group: "org.springframework.boot",
     *              name: "spring-boot-starter",
     *              version: "2.5.4",
     *              scope: "compile" // (optional)
     * )
     * dependency.javadocFileClasspath = getJavaDocJarFile(dependency)
     * String content = DocumentationHelper.getContentFromJavadocJar(dependency, "className")
     */
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

    /**
     * Retrieve or download the Sources JAR for a dependency.
     */
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
     * Shuts down all executors and resources.
     * Called during server shutdown to ensure clean termination.
     */
    void shutdown() {
        log.info("[GrailsService] Shutting down executors...")

        if (backgroundExecutor instanceof java.util.concurrent.ExecutorService) {
            ((java.util.concurrent.ExecutorService) backgroundExecutor).shutdown()
            try {
                if (!((java.util.concurrent.ExecutorService) backgroundExecutor).awaitTermination(5, TimeUnit.SECONDS)) {
                    ((java.util.concurrent.ExecutorService) backgroundExecutor).shutdownNow()
                    log.warn("[GrailsService] Background executor did not terminate gracefully")
                } else {
                    log.info("[GrailsService] Background executor terminated")
                }
            } catch (InterruptedException e) {
                ((java.util.concurrent.ExecutorService) backgroundExecutor).shutdownNow()
                Thread.currentThread().interrupt()
                log.warn("[GrailsService] Background executor shutdown interrupted")
            }
        }

        fileTracker?.shutdown()
        document?.shutdown()
        cancellationService?.cancelAll()
        ThreadSafeLruCache.shutdown()

        log.info("[GrailsService] Shutdown complete")
    }
}