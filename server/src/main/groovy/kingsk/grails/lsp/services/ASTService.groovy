package kingsk.grails.lsp.services

import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.enums.ErrorSource
import kingsk.grails.lsp.model.enums.ErrorSeverity
import kingsk.grails.lsp.model.types.TextFile
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.Position

import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized AST service for managing Groovy/Grails AST data.
 * Modular and plug-and-play.
 */
@Slf4j
@CompileStatic
class ASTService {
	private final GrailsService service

	ASTService(GrailsService service) {
		this.service = service
	}
	
	// Cache Grails artifacts by URI
	final Map<String, List<ClassNode>> grailsServices = new ConcurrentHashMap<>()
	final Map<String, List<ClassNode>> grailsControllers = new ConcurrentHashMap<>()
	final Map<String, List<ClassNode>> grailsTagLibs = new ConcurrentHashMap<>()
	
	/**
	 * Detect Grails artifacts during AST traversal
	 */
	void detectGrailsArtifacts(ClassNode classNode, String uri) {
        try {
            String normalizedUri = TextFile.normalizePath(uri)
            if (isGrailsService(classNode)) {
                grailsServices.computeIfAbsent(normalizedUri, { k -> new ArrayList<ClassNode>() }).add(classNode)
            } else if (isGrailsController(classNode)) {
                grailsControllers.computeIfAbsent(normalizedUri, { k -> new ArrayList<ClassNode>() }).add(classNode)
            } else if (isGrailsTagLib(classNode)) {
                grailsTagLibs.computeIfAbsent(normalizedUri, { k -> new ArrayList<ClassNode>() }).add(classNode)
            }
        } catch (Exception e) {
            service.errorService.handleError("Failed to detect Grails artifacts for ${uri}", e, ErrorSource.AST_SERVICE, ErrorSeverity.WARNING)
        }
	}
	
	private static boolean isGrailsService(ClassNode classNode) {
		classNode.annotations*.classNode.name.any { String it -> it.endsWith("Service") } ||
				(classNode.name.endsWith("Service") && (!classNode.packageName || classNode.packageName.contains("service")))
	}
	
	private static boolean isGrailsController(ClassNode classNode) {
		classNode.annotations*.classNode.name.any { String it -> it.endsWith("Controller") } ||
				(classNode.name.endsWith("Controller") && (!classNode.packageName || classNode.packageName.contains("controller")))
	}
	
	private static boolean isGrailsTagLib(ClassNode classNode) {
		classNode.name.endsWith("TagLib") && (!classNode.packageName || classNode.packageName.contains("taglib"))
	}
	
	/**
	 * Resolves an injected service by name
	 */
	ClassNode findInjectedService(String serviceName) {
		List<ClassNode> services = grailsServices.values().flatten() as List<ClassNode>
		return services.find { ClassNode it -> it.nameWithoutPackage == "${serviceName.capitalize()}Service" || it.name == serviceName }
	}

    /**
     * Resets all AST caches
     */
    void reset() {
        log.info("[AST_SERVICE] Resetting AST caches")
        grailsServices.clear()
        grailsControllers.clear()
        grailsTagLibs.clear()
    }

    /**
     * Evicts AST caches for a specific URI
     */
    void clearUri(String uri) {
        String normalizedUri = TextFile.normalizePath(uri)
        grailsServices.remove(normalizedUri)
        grailsControllers.remove(normalizedUri)
        grailsTagLibs.remove(normalizedUri)
    }

    /**
     * Utility to get node at position with error handling
     */
    ASTNode getNodeAtPosition(String uri, Position position) {
        try {
            return service.visitor.getNodeAtPosition(TextFile.normalizePath(uri), position)
        } catch (Exception e) {
            service.errorService.handleError("Error retrieving AST node at position", e, ErrorSource.AST_SERVICE)
            return null
        }
    }
}
