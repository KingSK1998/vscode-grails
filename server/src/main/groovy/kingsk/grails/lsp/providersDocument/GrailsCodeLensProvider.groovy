package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.CodeLensMode
import kingsk.grails.lsp.model.GrailsArtifactType
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.utils.ASTUtils
import kingsk.grails.lsp.utils.GrailsArtefactUtils
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.TextDocumentIdentifier

import java.util.concurrent.CompletableFuture

/**
 * Provides code lens functionality for Grails artifacts.
 * Supports different modes of code lens display based on configuration:
 * - BASIC: Method references, test runners, service injections
 * - ADVANCED: URL mappings, controller routes, application runners
 * - FULL: Overridden methods, reference counts
 */
@Slf4j
@CompileStatic
class GrailsCodeLensProvider {
	private final GrailsService service
	
	GrailsCodeLensProvider(GrailsService service) {
		this.service = service
	}
	
	/**
	 * Provides code lenses for a document based on the configured mode.
	 *
	 * @param textDocument The document identifier
	 * @return A CompletableFuture containing a list of CodeLens objects
	 */
	CompletableFuture<List<? extends CodeLens>> provideCodeLens(TextDocumentIdentifier textDocument) {
		def mode = service.config.codeLensMode
		if (mode == CodeLensMode.OFF) {
			log.debug "[CODE_LENS] Code lens is disabled (OFF mode)"
			return CompletableFuture.completedFuture([])
		}
		
		def uri = TextFile.normalizePath(textDocument.uri)
		log.debug "[CODE_LENS] Providing code lens for document: $uri with mode: $mode"
		
		// Get the first class node in the file
		def classNodes = service.visitor.getClassNodes(uri)
		if (!classNodes || classNodes.empty) {
			log.debug "[CODE_LENS] No class nodes found in document: $uri"
			return CompletableFuture.completedFuture([])
		}
		
		List<CodeLens> codeLenses = []
		classNodes.each { classNode ->
			def artifactType = GrailsArtefactUtils.getGrailsArtifactType(classNode, uri)
			log.debug "[CODE_LENS] Found artifact type: $artifactType for class: ${classNode.name}"
			
			
			// Add code lenses based on the configured mode
			addBasicCodeLenses(codeLenses, classNode, artifactType, uri, mode)
			
			if (mode >= CodeLensMode.ADVANCED) {
				addAdvancedCodeLenses(codeLenses, classNode, artifactType, uri)
			}
			
			if (mode == CodeLensMode.FULL) {
				addFullCodeLenses(codeLenses, classNode, uri)
			}
		}
		
		log.debug "[CODE_LENS] Returning ${codeLenses.size()} code lenses"
		return CompletableFuture.completedFuture(codeLenses as List<? extends CodeLens>)
	}
	
	/**
	 * Resolves a code lens by adding command information based on the lens type.
	 *
	 * @param unresolved The unresolved code lens
	 * @return A CompletableFuture containing the resolved code lens
	 */
	static CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
		def data = unresolved.data as Map
		if (!data) return CompletableFuture.completedFuture(unresolved)
		
		def type = data.type as String ?: ""
		def label = ""
		Command command = null
		
		switch (type) {
			case "test":
				def action = data.action as String
				label = action == "debug" ? "Debug" : "Run"
				command = new Command(label, "grailsLsp.runTest", [data.path, data.method, data.action])
				break
			case "main":
				def action = data.action as String
				label = action == "debug" ? "Debug" : "Run"
				command = new Command(label, "grailsLsp.runApp", [data.path, data.action])
				break
			case "controller":
				label = "Show Route"
				command = new Command(label, "grailsLsp.showRoute", [data.path, data.method])
				break
			case "mapping":
				label = "URL Mapping"
				command = new Command(label, "grailsLsp.previewMapping", [data.path, data.method])
				break
			case "domain":
				label = "CURD Operations"
				command = new Command(label, "grailsLsp.showCurdOperations", [data.path, data.className])
			case "injection":
				label = "Injected: ${data.service}"
				command = new Command(label, "grailsLsp.openService", [data.service])
				break
			case "override":
				label = "@Overridden"
				command = new Command(label, "grailsLsp.openParentMethod", [data.path, data.method])
				break
			case "reference":
				label = "References"
				command = new Command(label, "grailsLsp.findReferences", [data.path, data.method])
				break
			case "super":
				label = "Go to Super"
				command = new Command(label, "grailsLsp.goToSuperMethod", [data.path, data.method])
				break
			case "refCount":
				// This would typically be resolved by counting references
				// For now, just use a placeholder
				int refCount = 0
				label = "$refCount References"
				command = new Command(label, "grailsLsp.showReferences", [data.path, data.method])
				break
			default:
				command = new Command("Details", "grailsLsp.defaultAction", [data.path, data.method])
		}
		unresolved.command = command
		return CompletableFuture.completedFuture(unresolved)
	}
	
	//==== PRIVATE METHODS ====
	
	/**
	 * Adds basic code lenses for method references, tests, and service injections.
	 *
	 * @param codeLenses The list to add code lenses to
	 * @param classNode The class node to analyze
	 * @param artifactType The type of Grails artifact
	 * @param uri The document URI
	 * @param mode The current code lens mode
	 */
	private static void addBasicCodeLenses(List<CodeLens> codeLenses, ClassNode classNode,
	                                       GrailsArtifactType artifactType, String uri, CodeLensMode mode) {
		// Only add basic lenses if mode is at least BASIC
		if (mode < CodeLensMode.BASIC) return
		
		// Add test runner lenses for test classes
		if (artifactType == GrailsArtifactType.SPOCK_TEST || artifactType == GrailsArtifactType.JUNIT_TEST) {
			addTestRunnerLenses(codeLenses, classNode, uri)
		}
		// Add method reference lenses for non-test classes
		else {
			addMethodReferenceLenses(codeLenses, classNode, uri)
		}
		
		// Add service injection lenses
		addServiceInjectionLenses(codeLenses, classNode, uri)
	}
	
	/**
	 * Adds test runner lenses for test methods.
	 *
	 * @param codeLenses The list to add code lenses to
	 * @param classNode The test class node
	 * @param uri The document URI
	 */
	private static void addTestRunnerLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
		classNode.methods.each { method ->
			// Skip synthetic methods and non-test methods
			if (method.synthetic || !isTestMethod(method)) return
			
			def runLens = ASTUtils.astNodeToCodeLens(method, [
					type     : "test",
					action   : "run",
					className: classNode.nameWithoutPackage,
					method   : method.name,
					path     : uri
			], "Run")
			
			def debugLens = ASTUtils.astNodeToCodeLens(method, [
					type     : "test",
					action   : "debug",
					className: classNode.nameWithoutPackage,
					method   : method.name,
					path     : uri
			], "Debug")
			
			if (runLens) codeLenses << runLens
			if (debugLens) codeLenses << debugLens
		}
	}
	
	/**
	 * Determines if a method is a test method based on naming conventions.
	 *
	 * @param method The method to check
	 * @return true if the method appears to be a test method
	 */
	private static boolean isTestMethod(MethodNode method) {
		// Spock feature methods or JUnit test methods
		return !method.synthetic &&
				(method.name.startsWith("test") ||
						method.name.contains("should") ||
						method.name.contains("when") ||
						method.name.contains("given"))
	}
	
	/**
	 * Adds method reference lenses for regular methods.
	 *
	 * @param codeLenses The list to add code lenses to
	 * @param classNode The class node
	 * @param uri The document URI
	 */
	private static void addMethodReferenceLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
		classNode.methods.each { method ->
			// Skip synthetic, private, or constructor methods
			if (method.synthetic || method.private || method.name == "<init>") return
			
			def lens = ASTUtils.astNodeToCodeLens(method, [
					type  : "reference",
					method: method.name,
					path  : uri
			])
			if (lens) codeLenses << lens
		}
	}
	
	/**
	 * Adds service injection lenses for fields that appear to be injected services.
	 *
	 * @param codeLenses The list to add code lenses to
	 * @param classNode The class node
	 * @param uri The document URI
	 */
	private static void addServiceInjectionLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
		classNode.fields.findAll {
			!it.synthetic && (it.name.endsWith("Service") ||
					it.type.name.endsWith("Service"))
		}.each { field ->
			def lens = ASTUtils.astNodeToCodeLens(field, [
					type   : "injection",
					service: field.type.name,
					path   : uri
			], "Injection")
			if (lens) codeLenses << lens
		}
	}
	
	/**
	 * Adds advanced code lenses for URL mappings, controller routes, and application runners.
	 *
	 * @param codeLenses The list to add code lenses to
	 * @param classNode The class node to analyze
	 * @param artifactType The type of Grails artifact
	 * @param uri The document URI
	 */
	private static void addAdvancedCodeLenses(List<CodeLens> codeLenses, ClassNode classNode, GrailsArtifactType artifactType, String uri) {
		// URL Mappings preview
		if (artifactType == GrailsArtifactType.URL_MAPPINGS) addUrlMappingLenses(codeLenses, classNode, uri)
		// Controller route visualization
		if (artifactType == GrailsArtifactType.CONTROLLER) addControllerRouteLenses(codeLenses, classNode, uri)
		// Domain CRUD operations
		if (artifactType == GrailsArtifactType.DOMAIN) addDomainClassLenses(codeLenses, classNode, uri)
		// Application main method runner
		if (artifactType == GrailsArtifactType.APPLICATION && ASTUtils.hasMainMethod(classNode)) addApplicationRunnerLenses(codeLenses, classNode, uri)
	}
	
	/**
	 * Adds URL mapping preview lenses for URL mapping methods.
	 *
	 * @param codeLenses The list to add code lenses to
	 * @param classNode The URL mappings class node
	 * @param uri The document URI
	 */
	private static void addUrlMappingLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
		classNode.methods.each { method ->
			// Skip synthetic methods
			if (method.synthetic) return
			
			def lens = ASTUtils.astNodeToCodeLens(method, [
					type  : "mapping",
					action: "preview",
					method: method.name,
					path  : uri
			], "URL Mapping")
			if (lens) codeLenses << lens
		}
	}
	
	/**
	 * Adds controller route visualization lenses for controller action methods.
	 *
	 * @param codeLenses The list to add code lenses to
	 * @param classNode The controller class node
	 * @param uri The document URI
	 */
	private static void addControllerRouteLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
		classNode.methods.findAll {
			!it.synthetic && !it.isPrivate() && it.name != "<init>" &&
					!it.name.startsWith("get") && !it.name.startsWith("set")
		}.each { method ->
			def lens = ASTUtils.astNodeToCodeLens(method, [
					type  : "controller",
					action: "showRoute",
					method: method.name,
					path  : uri
			], "Show Route")
			if (lens) codeLenses << lens
		}
	}
	
	/**
	 * Adds domain class CRUD operation lenses.
	 *
	 * @param codeLenses The list to add code lenses to
	 * @param classNode The domain class node
	 * @param uri The document URI
	 */
	private static void addDomainClassLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
		// Add a lens at the class level for CRUD operations
		def lens = ASTUtils.astNodeToCodeLens(classNode, [
				type     : "domain",
				action   : "showCrud",
				className: classNode.name,
				path     : uri
		], "CRUD Operations")
		if (lens) codeLenses << lens
	}
	
	/**
	 * Adds application runner lenses for the main method.
	 *
	 * @param codeLenses The list to add code lenses to
	 * @param classNode The application class node
	 * @param uri The document URI
	 */
	private static void addApplicationRunnerLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
		def runLens = ASTUtils.astNodeToCodeLens(classNode, [
				type     : "main",
				action   : "run",
				className: classNode.name,
				path     : uri
		], "Run")
		
		def debugLens = ASTUtils.astNodeToCodeLens(classNode, [
				type     : "main",
				action   : "debug",
				className: classNode.name,
				path     : uri
		], "Debug")
		
		if (runLens) codeLenses << runLens
		if (debugLens) codeLenses << debugLens
	}
	
	/**
	 * Adds full mode code lenses for overridden methods and reference counts.
	 *
	 * @param codeLenses The list to add code lenses to
	 * @param classNode The class node to analyze
	 * @param uri The document URI
	 */
	private static void addFullCodeLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
		// Overridden method indicators
		classNode.methods
				.findAll { !it.synthetic && ASTUtils.isOverriddenMethod(it, classNode) }
				.each {
					def lens = ASTUtils.astNodeToCodeLens(it, [
							type  : "override",
							method: it.name,
							path  : uri
					], "@Overridden")
					if (lens) codeLenses << lens
				}
		
		// Reference/implementation count placeholder (resolved later)
		classNode.methods
				.findAll { !it.synthetic && !it.private && it.name != "<init>" }
				.each {
					def lens = ASTUtils.astNodeToCodeLens(it, [
							type  : "refCount",
							method: it.name,
							path  : uri
					])
					if (lens) codeLenses << lens
				}
	}
}
