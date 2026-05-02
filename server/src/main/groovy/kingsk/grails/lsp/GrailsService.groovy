package kingsk.grails.lsp

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.core.compiler.GrailsCompiler
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.model.DependencyNode
import kingsk.grails.lsp.model.GrailsLspConfig
import kingsk.grails.lsp.model.GrailsProject
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.services.*
import kingsk.grails.lsp.providersDocument.*
import kingsk.grails.lsp.providersWorkspace.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Slf4j
@CompileStatic
class GrailsService implements LanguageClientAware {
    LanguageClient client
    Map<String, GrailsProject> projects = [:]
    String activeProjectUri

    GrailsProject getProject() {
        return projects[activeProjectUri]
    }

    GrailsProject getProjectForUri(String uri) {
        GrailsProject project = projects.values().find { GrailsProject p -> uri.startsWith(p.rootDirectory.toURI().toString()) }
        return project ?: getProject()
    }
    final GrailsWorkspaceService workspace
    final GrailsTextDocumentService document

    final GradleService gradle
    final GrailsCompiler compiler

    final FileContentTracker fileTracker
    final GrailsASTVisitor visitor
    final GrailsDiagnosticService diagnostics
    final ProgressService progressService
    final GrailsDependencyProvider dependencyProvider
    final GrailsGormSqlProvider gormSqlProvider
    final GrailsTestDiscoveryProvider testDiscoveryProvider

    final GrailsLspConfig config

    private final Executor backgroundExecutor = Executors.newCachedThreadPool()

    GrailsService() {
        this.gradle = new GradleService()
        this.fileTracker = new FileContentTracker(this)
        this.compiler = new GrailsCompiler(this)
        this.progressService = new ProgressService()
        this.diagnostics = new GrailsDiagnosticService(this)
        this.document = new GrailsTextDocumentService(this)
        this.workspace = new GrailsWorkspaceService(this)
        this.visitor = new GrailsASTVisitor(this)
        this.dependencyProvider = new GrailsDependencyProvider(this)
        this.gormSqlProvider = new GrailsGormSqlProvider(this)
        this.testDiscoveryProvider = new GrailsTestDiscoveryProvider(this)
        this.config = new GrailsLspConfig()
    }

    @Override
    void connect(LanguageClient client) {
        this.client = client
        progressService.connect(client)
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
            GrailsProject newProject = gradle.getGrailsProject(projectDir)
            if (!newProject) {
                progressService.error("Invalid Grails project at $projectDir")
                log.warn("[GrailsService] Invalid project: $projectDir")
                return
            }
            
            projects[projectDir] = newProject
            if (!activeProjectUri) activeProjectUri = projectDir

            progressService.update("Project loaded", 20)

            compiler.invalidateCompiler()
            visitor.invalidateVisitor()
            fileTracker.resetFQCNDependencies()
            diagnostics.clearAllDiagnostics()

            // compileProject publish progress from 30 to 60
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
}