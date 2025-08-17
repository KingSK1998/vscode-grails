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
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Slf4j
@CompileStatic
class GrailsService implements LanguageClientAware {
	LanguageClient client
	GrailsProject project
	
	final GrailsTextDocumentService document
	final GrailsWorkspaceService workspace
	
	final GradleService gradle
	final GrailsCompiler compiler
	
	final FileContentTracker fileTracker
	final GrailsASTVisitor visitor
	final GrailsDiagnosticService diagnostics
	final ProgressReportService reportingService
	
	final GrailsLspConfig config
	
	GrailsService() {
		this.gradle = new GradleService()
		this.fileTracker = new FileContentTracker(this)
		this.compiler = new GrailsCompiler(this)
		this.reportingService = new ProgressReportService()
		this.diagnostics = new GrailsDiagnosticService(this)
		this.document = new GrailsTextDocumentService(this)
		this.workspace = new GrailsWorkspaceService(this)
		this.visitor = new GrailsASTVisitor(this)
		this.config = new GrailsLspConfig()
	}
	
	@Override
	void connect(LanguageClient client) {
		this.client = client
		reportingService.connect(client)
	}
	
	final Executor backgroundExecutor = Executors.newFixedThreadPool(2)
	
	/**
	 * Sets up the workspace by adding dependencies and source files.
	 * This will execute at at the time of Server initialization i.e. no features available yet.
	 * @param projectDir The root directory of the Grails project.
	 */
	void setupWorkspace(String projectDir, boolean asyncCompile = true) {
		// Clear all previous state when setting up new workspace
		invalidateAll()
		
		// Attempt to get the Grails project (may be cached inside gradleService)
		this.project = gradle.getGrailsProject(projectDir)
		
		if (!project) {
			log.warn("[GrailsService] No valid Grails project found at: $projectDir")
			return
		}
		
		reportingService.sendProgressReport("Indexing - Gradle Sync Done", 20)
		
		if (asyncCompile) {
			CompletableFuture.runAsync({
				compiler.compileProject()
				// TODO: Workspace wide analysis
				// Optionally, post-compile visit or indexing can be done here
			}, backgroundExecutor)
		} else {
			// For sync setup or testing, compile synchronously
			compiler.compileProject()
		}
	}
	
	void onDocumentOpened(TextFile textFile) {
		compiler.compileSourceFile(textFile)
		visitAST(textFile)
		diagnostics.publishDiagnosticsForFile(textFile.uri)
	}
	
	void onDocumentChanged(TextFile textFile) {
		compiler.compileSourceFile(textFile)
		visitAST(textFile)
		diagnostics.publishDiagnosticsForFile(textFile.uri)
	}
	
	void onDocumentClosed(TextFile textFile) {
		// Remove file and its dependencies from AST (smart cleanup)
		visitor.removeFileWithDependencies(textFile.uri)
		
		// Only invalidate compiler if needed (not for every file close)
		// Let compiler handle incremental updates
		diagnostics.clearDiagnosticsForFile(textFile.uri)
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
	 * Invalidate both compiler and visitor state - use for major changes
	 */
	void invalidateAll() {
		log.info("[GRAILS_SERVICE] Invalidating all state (compiler + visitor)")
		compiler.invalidateCompiler()
		visitor.invalidateVisitor()
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
		if (!dependency || !project) return null
		
		DependencyNode dep = project.dependencies.find { it == dependency }
		if (dep?.javadocFileClasspath) return dep.javadocFileClasspath
		
		File downloaded = gradle?.downloadJavaDocJarFile(project.rootDirectory, dependency)
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
		if (!dependency || !project) return null
		
		DependencyNode dep = project.dependencies.find { it == dependency }
		if (dep?.sourceJarFileClasspath) return dep.sourceJarFileClasspath
		
		File download = gradle?.downloadSourcesJarFile(project.rootDirectory, dependency)
		if (download) {
			dep?.sourceJarFileClasspath = download
			return download
		}
		
		return null
	}
}