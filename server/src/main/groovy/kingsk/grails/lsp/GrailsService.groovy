package kingsk.grails.lsp

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.CompilationContext
import kingsk.grails.lsp.context.ProjectContext
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
import kingsk.grails.lsp.index.*
import kingsk.grails.lsp.services.*
import kingsk.grails.lsp.utils.cache.ThreadSafeLruCache
import kingsk.grails.lsp.utils.project.ProjectDiffUtil
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantReadWriteLock

@Slf4j
@CompileStatic
class GrailsService implements LanguageClientAware, ProjectContext, ProviderContext, CompilationContext {

    GrailsLanguageClient client
    Map<String, GrailsProject> projects = new ConcurrentHashMap<>()
    String activeProjectUri

    private final Map<String, ProjectDTO> lastSent = new ConcurrentHashMap<>()

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

    final ProjectIndex projectIndex
    final IndexManager indexManager
    final MethodScopeCache methodScopeCache
    final GroovydocCache groovydocCache

    private final Executor backgroundExecutor = Executors.newFixedThreadPool(4)
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

    GrailsService() {
        this.errorService = new ErrorService(this)
        this.cancellationService = new CancellationService()
        this.healthService = new ProviderHealthService()
        this.providerRegistry = new ProviderRegistry(this, this, this)
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
        this.dependencyProvider = new GrailsDependencyProvider(this, this, this)
        this.gormSqlProvider = new GrailsGormSqlProvider(this, this, this)
        this.testDiscoveryProvider = new GrailsTestDiscoveryProvider(this, this, this)
        this.config = new GrailsLspConfig()
        this.projectIndex = new ProjectIndex("root")
        this.methodScopeCache = new MethodScopeCache()
        this.groovydocCache = new GroovydocCache(100)
        this.indexManager = new IndexManager(projectIndex, methodScopeCache, groovydocCache)
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

            Map<String, List<org.codehaus.groovy.ast.ClassNode>> allNodes = [:]
            withWriteLock {
                compiler.invalidateCompiler()
                visitor.invalidateVisitor()
                fileTracker.resetFQCNDependencies()
                diagnostics.clearAllDiagnostics()

                compiler.compileProject()

                visitor.visitCompilationUnit(compiler)

                visitor.getAllClassNodes().each { uri, nodes ->
                    allNodes[uri] = nodes as List<org.codehaus.groovy.ast.ClassNode>
                }
            }

            indexManager.rebuildAll(allNodes)

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

        List<org.codehaus.groovy.ast.ClassNode> classNodes = null
        withWriteLock {
            if (!compiler.isDirty(textFile.uri) && compiler.compilationExistsFor(textFile.uri)) {
                log.debug("[COMPILE] Skipping - no changes detected for: ${textFile.uri}")
                return
            }

            compiler.markDirty(textFile.uri)
            compiler.compileSourceFile(textFile)
            visitASTInternal(textFile)
            
            def sourceUnit = compiler.getSourceUnit(textFile)
            if (sourceUnit) {
                classNodes = sourceUnit.getAST()?.getClasses()
            }
            
            clearCrossFileCaches()
            diagnostics.publishDiagnosticsForFile(textFile.uri)
        }

        if (classNodes != null) {
            indexManager.rebuildFile(textFile.uri, classNodes)
        }

        if (log.isDebugEnabled()) {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)
            log.debug("[PERF] compileAndVisitAST ${textFile.uri} ${ms}ms")
        }
    }

    void visitAST(TextFile textFile) {
        List<org.codehaus.groovy.ast.ClassNode> classNodes = null
        withWriteLock {
            visitASTInternal(textFile)
            def sourceUnit = compiler.getSourceUnit(textFile)
            if (sourceUnit) {
                classNodes = sourceUnit.getAST()?.getClasses()
            }
        }
        if (classNodes != null) {
            indexManager.rebuildFile(textFile.uri, classNodes)
        }
    }

    /**
     * Clears all globally tracked symbol caches to ensure cross-file dependency
     * inferences accurately reflect updated type signatures across the workspace.
     */
    void clearCrossFileCaches() {
        log.debug("[GrailsService] Evicting cross-file symbol caches")
        providerRegistry.getProvider(kingsk.grails.lsp.providers.document.GrailsCompletionProvider).clearCaches() // Clear all file completions globally
        discoveryService.clearSymbolCaches()
        kingsk.grails.lsp.utils.grails.GroovyRuntimeIntegration.clearCaches()
        kingsk.grails.lsp.utils.grails.GroovyHelperIntegration.clearCaches()
        kingsk.grails.lsp.utils.grails.GrailsHelperIntegration.clearCaches()
    }

    private void visitASTInternal(TextFile textFile) {
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

    void addProject(GrailsProject project) {
        if (project?.rootDirectory) {
            projects[project.rootDirectory.toURI().toString()] = project
        }
    }

    void removeProject(String projectDir) {
        projects.remove(projectDir)
        if (activeProjectUri == projectDir) {
            activeProjectUri = projects.keySet().first()
        }
    }

    void updateProject(GrailsProject project, String projectDir) {
        if (project && projectDir) {
            projects[projectDir] = project
        }
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

    @Override
    ProjectIndex getProjectIndex() {
        return this.projectIndex
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