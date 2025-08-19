package kingsk.grails.lsp.test

import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.GrailsProject
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.utils.ServiceUtils
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

/**
 * Base specification class for LSP tests
 * Provides common setup and utilities for testing LSP functionality
 */
@Slf4j
abstract class BaseLspSpec extends Specification {

	// Shared service instance to avoid recreating for each test
	@Shared
	protected GrailsService grailsService

	// Mock client for capturing diagnostics and messages
	@Shared
	protected MockLanguageClient mockClient

	// Configuration
	protected static final int DEFAULT_TIMEOUT = 5000
	// ms

	/**
	 * Setup method that runs before each test
	 * Override in subclasses but call super.setup() first
	 */
	def setupProject() {
		mockClient = new MockLanguageClient()
		grailsService = new GrailsService()
		grailsService.connect(mockClient)
	}

	/**
	 * Initialize a project for testing
	 * @param projectType The type of project to initialize
	 * @param fullCompile Whether to perform a full compilation
	 * @return The initialized GrailsService
	 */
	protected GrailsService initializeProject(ProjectType projectType, boolean fullCompile = false) {
		setupProject()
		String projectDir = getProjectDir(projectType)
		if (fullCompile && projectDir) {
			grailsService.setupWorkspace(projectDir, false)
		} else if (projectDir) {
			grailsService.project = grailsService.gradle.getGrailsProject(projectDir)
		} else {
			grailsService.project = new GrailsProject()
		}
		return grailsService
	}

	/**
	 * Get the project directory URI for a given project type
	 * @param projectType The type of project
	 * @return The project directory URI as a string
	 */
	protected String getProjectDir(ProjectType projectType) {
		Path userDir = Paths.get(System.getProperty("user.dir"))
		switch (projectType) {
			case ProjectType.GRAILS: return getMockProjectPath(userDir, "grails-test-project")
			case ProjectType.GROOVY: return getMockProjectPath(userDir, "groovy-test-project")
			case ProjectType.DUMMY: return createDummyWorkspace(userDir)
			case ProjectType.EMPTY: return null
			default: return null
		}
	}

	/**
	 * Get the path to a mock project
	 * @param userDir The user directory
	 * @param projectName The name of the mock project
	 * @return The path to the mock project
	 */
	private static String getMockProjectPath(Path userDir, String projectName) {
		Path projectPath = userDir.parent.parent.resolve("Resources").resolve(projectName)
		if (!Files.exists(projectPath)) {
			throw new FileNotFoundException("Mock project directory not found: ${projectPath}")
		}
		return projectPath.toUri().toString()
	}

	/**
	 * Create a dummy workspace for testing
	 * @param userDir The user directory
	 * @return The path to the dummy workspace
	 */
	private static String createDummyWorkspace(Path userDir) {
		Path workspaceDir = userDir.resolve("build/test_workspace")
		Path srcDir = workspaceDir.resolve("src/main/groovy")
		if (!Files.exists(srcDir)) {
			Files.createDirectories(srcDir)
		}
		return workspaceDir.toUri().toString()
	}

	/**
	 * Open a text document for testing
	 * @param fileName The name of the file (can include path like "grails-app/controllers/BookController.groovy")
	 * @param content The content of the file
	 * @param version The version of the document
	 * @return The URI of the opened document
	 */
	protected String openTextDocument(String fileName, String content, int version = 0) {
		String uri = resolveFileUri(fileName)
		grailsService.document.didOpen(
				new DidOpenTextDocumentParams(
						new TextDocumentItem(uri, "groovy", version, content)
				)
		)
		return uri
	}

	/**
	 * Open an existing project file for testing
	 * @param fileName The name of the file to find in the project
	 * @param version The version of the document
	 * @return The URI of the opened document, or null if not found
	 */
	protected String openExistingProjectFile(String fileName, int version = 0) {
		if (!grailsService.project) {
			log.warn("No project initialized, cannot open existing file: ${fileName}")
			return null
		}

		// Find the file in project source directories
		File foundFile = findFileInProject(fileName)
		if (!foundFile) {
			log.warn("File not found in project: ${fileName}")
			return null
		}

		String content = foundFile.text
		String uri = foundFile.toURI().toString()

		grailsService.document.didOpen(
				new DidOpenTextDocumentParams(
						new TextDocumentItem(uri, "groovy", version, content)
				)
		)
		return uri
	}

	/**
	 * Close a text document
	 * @param uri The URI of the document to close
	 */
	protected void closeTextDocument(String uri) {
		grailsService.document.didClose(
				new DidCloseTextDocumentParams(
						new TextDocumentIdentifier(uri)
				)
		)
	}

	/**
	 * Change a text document
	 * @param uri The URI of the document to change
	 * @param range The range to change
	 * @param newText The new text
	 * @param version The version of the document
	 */
	protected void changeTextDocument(String uri, Range range, String newText, int version = 1) {
		grailsService.document.didChange(
				new DidChangeTextDocumentParams(
						new VersionedTextDocumentIdentifier(uri, version),
						[new TextDocumentContentChangeEvent(range, newText)]
				)
		)
	}

	/**
	 * Replace the entire content of a text document.
	 * @param uri The URI of the document
	 * @param newText The new content to replace the entire document with
	 * @param version The version of the document (default = 1)
	 */
	protected void replaceTextDocument(String uri, String newText, int version = 1) {
		grailsService.document.didChange(
				new DidChangeTextDocumentParams(
						new VersionedTextDocumentIdentifier(uri, version),
						[new TextDocumentContentChangeEvent(null, newText)]
				)
		)
	}


	/**
	 * Wait for a CompletableFuture to complete with a timeout
	 * @param future The future to wait for
	 * @param timeout The timeout in milliseconds
	 * @return The result of the future
	 */
	protected <T> T waitForFuture(CompletableFuture<T> future, long timeout = DEFAULT_TIMEOUT) {
		//		return future.get(timeout, TimeUnit.MILLISECONDS)
		return future.get()
	}

	/**
	 * Resolve file URI for testing, handling both new files and existing project files
	 * @param fileName The file name or path
	 * @return The resolved URI
	 */
	protected String resolveFileUri(String fileName) {
		fileName = ensureGroovyExtension(fileName)

		// If it's a simple filename, check if it exists in project first
		if (!fileName.contains("/") && !fileName.contains("\\")) {
			File existingFile = findFileInProject(fileName)
			if (existingFile) {
				log.debug("Found existing project file: ${existingFile.absolutePath}")
				return existingFile.toURI().toString()
			}
		}

		// If it contains path separators, resolve relative to project root
		if (grailsService.project?.rootDirectory && (fileName.contains("/") || fileName.contains("\\"))) {
			File projectFile = new File(grailsService.project.rootDirectory, fileName)
			return projectFile.toURI().toString()
		}

		// Fallback: create in project root or current directory
		if (grailsService.project?.rootDirectory) {
			File projectFile = new File(grailsService.project.rootDirectory, fileName)
			return projectFile.toURI().toString()
		} else {
			// No project, use current directory
			File currentDirFile = new File(fileName)
			return currentDirFile.toURI().toString()
		}
	}

	/**
	 * Find a file in the project using ServiceUtils
	 * @param fileName The file name to find
	 * @return The found file, or null if not found
	 */
	protected File findFileInProject(String fileName) {
		if (!grailsService.project) return null

		// Use ServiceUtils to get all project files
		List<File> allProjectFiles = ServiceUtils.getAllGroovySourceFilesFromProject(grailsService.project)

		// Find file by name
		return allProjectFiles.find { file ->
			file.name == fileName || file.name == ensureGroovyExtension(fileName)
		}
	}

	/**
	 * Find files by pattern in the project using ServiceUtils
	 * @param pattern The pattern to match (e.g., "*Controller.groovy")
	 * @return List of matching files
	 */
	protected List<File> findFilesByPattern(String pattern) {
		if (!grailsService.project) return []

		List<File> allProjectFiles = ServiceUtils.getAllGroovySourceFilesFromProject(grailsService.project)

		// Convert pattern to regex
		String regex = pattern.replace("*", ".*").replace("?", ".")

		return allProjectFiles.findAll { file ->
			file.name.matches(regex)
		}
	}

	/**
	 * Get FQCN to TextFile mapping using ServiceUtils
	 * @return Map of FQCN to TextFile
	 */
	protected Map<String, TextFile> getProjectFQCNMap() {
		if (!grailsService.project) return [:]

		List<File> allProjectFiles = ServiceUtils.getAllGroovySourceFilesFromProject(grailsService.project)
		return ServiceUtils.generateFQCNFromSourceFiles(allProjectFiles)
	}

	/**
	 * Get all related files for a given file using ServiceUtils
	 * @param sourceFile The source file
	 * @return List of related files
	 */
	protected List<TextFile> getAllRelatedFiles(TextFile sourceFile) {
		if (!grailsService.project) return [sourceFile]

		return ServiceUtils.getAllRelatedFilesFromProject(sourceFile, grailsService.project)
	}

	/**
	 * Ensure a file name has a .groovy extension
	 * @param fileName The file name
	 * @return The file name with a .groovy extension
	 */
	protected String ensureGroovyExtension(String fileName) {
		return fileName.toLowerCase().endsWith(".groovy") ? fileName : "${fileName}.groovy"
	}

	/**
	 * Create a position in a document
	 * @param line The line number (0-based)
	 * @param character The character number (0-based)
	 * @return The position
	 */
	protected Position pos(int line, int character) {
		return new Position(line, character)
	}

	/**
	 * Create a range in a document
	 * @param startLine The start line number (0-based)
	 * @param startChar The start character number (0-based)
	 * @param endLine The end line number (0-based)
	 * @param endChar The end character number (0-based)
	 * @return The range
	 */
	protected Range range(int startLine, int startChar, int endLine, int endChar) {
		return new Range(pos(startLine, startChar), pos(endLine, endChar))
	}

	// === Grails-specific helper methods ===

	/**
	 * Open a controller file in the proper Grails location
	 * @param controllerName The controller name (without "Controller" suffix)
	 * @param content The content of the controller
	 * @param version The version of the document
	 * @return The URI of the opened document
	 */
	protected String openGrailsController(String controllerName, String content, int version = 0) {
		String fileName = "grails-app/controllers/${controllerName}Controller.groovy"
		return openTextDocument(fileName, content, version)
	}

	/**
	 * Open a service file in the proper Grails location
	 * @param serviceName The service name (without "Service" suffix)
	 * @param content The content of the service
	 * @param version The version of the document
	 * @return The URI of the opened document
	 */
	protected String openGrailsService(String serviceName, String content, int version = 0) {
		String fileName = "grails-app/services/${serviceName}Service.groovy"
		return openTextDocument(fileName, content, version)
	}

	/**
	 * Open a domain file in the proper Grails location
	 * @param domainName The domain name
	 * @param content The content of the domain
	 * @param version The version of the document
	 * @return The URI of the opened document
	 */
	protected String openGrailsDomain(String domainName, String content, int version = 0) {
		String fileName = "grails-app/domain/${domainName}.groovy"
		return openTextDocument(fileName, content, version)
	}

	/**
	 * Open an existing project file by searching for it
	 * @param fileName The file name to search for
	 * @param version The version of the document
	 * @return The URI of the opened document, or null if not found
	 */
	protected String openExistingFile(String fileName, int version = 0) {
		return openExistingProjectFile(fileName, version)
	}

	/**
	 * List all Groovy files in the project using ServiceUtils
	 * @return List of all Groovy files
	 */
	protected List<File> getAllProjectGroovyFiles() {
		if (!grailsService.project) return []
		return ServiceUtils.getAllGroovySourceFilesFromProject(grailsService.project)
	}

	/**
	 * Find controllers in the project
	 * @return List of controller files
	 */
	protected List<File> getAllControllers() {
		return findFilesByPattern("*Controller.groovy")
	}

	/**
	 * Find services in the project
	 * @return List of service files
	 */
	protected List<File> getAllServices() {
		return findFilesByPattern("*Service.groovy")
	}

	/**
	 * Find domain classes in the project
	 * @return List of domain files
	 */
	protected List<File> getAllDomains() {
		return findFilesByPattern("*.groovy").findAll { file ->
			file.absolutePath.contains("grails-app${File.separator}domain")
		}
	}

	/**
	 * Find a specific controller by name
	 * @param controllerName The controller name (with or without "Controller" suffix)
	 * @return The controller file, or null if not found
	 */
	protected File findController(String controllerName) {
		String fileName = controllerName.endsWith("Controller") ?
				"${controllerName}.groovy" : "${controllerName}Controller.groovy"
		return findFileInProject(fileName)
	}

	/**
	 * Find a specific service by name
	 * @param serviceName The service name (with or without "Service" suffix)
	 * @return The service file, or null if not found
	 */
	protected File findService(String serviceName) {
		String fileName = serviceName.endsWith("Service") ?
				"${serviceName}.groovy" : "${serviceName}Service.groovy"
		return findFileInProject(fileName)
	}

	/**
	 * Get file content using ServiceUtils validation
	 * @param file The file to read
	 * @return The file content, or null if file is invalid
	 */
	protected String getValidFileContent(File file) {
		URL validUrl = ServiceUtils.validateClasspathEntry(file)
		return validUrl ? file.text : null
	}
}

/**
 * Enum for project types
 */
enum ProjectType {
	GRAILS, // live grails project
	GROOVY, // live groovy project
	DUMMY, // temp single groovy files under build folder, not for integration test
	EMPTY // nothing, returns null
}

/**
 * Mock language client for testing
 */
class MockLanguageClient implements LanguageClient {
	List<PublishDiagnosticsParams> diagnostics = []
	List<MessageParams> messages = []
	List<ProgressParams> progressUpdates = []

	@Override
	void telemetryEvent(Object object) {}

	@Override
	void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
		this.diagnostics.add(diagnostics)
	}

	@Override
	void showMessage(MessageParams messageParams) {
		this.messages.add(messageParams)
	}

	@Override
	CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
		return CompletableFuture.completedFuture(null)
	}

	@Override
	void logMessage(MessageParams message) {
		this.messages.add(message)
	}

	@Override
	void notifyProgress(ProgressParams params) {
		this.progressUpdates.add(params)
	}

	/**
	 * Clear all captured data
	 */
	void clear() {
		diagnostics.clear()
		messages.clear()
		progressUpdates.clear()
	}

	/**
	 * Get the latest diagnostics for a specific URI
	 * @param uri The URI to get diagnostics for
	 * @return The diagnostics for the URI
	 */
	List<Diagnostic> getDiagnosticsForUri(String uri) {
		return diagnostics.findAll { it.uri == TextFile.normalizePath(uri) }*.diagnostics.flatten() as List<Diagnostic>
	}
}
