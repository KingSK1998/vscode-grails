package kingsk.grails.lsp.services

import org.codehaus.groovy.ast.ClassNode

class GrailsASTService {
	
	// Cache Grails artifacts by URI
	Map<String, List<ClassNode>> grailsServices = [:]
	Map<String, List<ClassNode>> grailsControllers = [:]
	Map<String, List<ClassNode>> grailsTagLibs = [:]
	
	// Detect Grails artifacts during AST traversal
	void detectGrailsArtifacts(ClassNode classNode, String uri) {
		if (isGrailsService(classNode)) {
			grailsServices.computeIfAbsent(uri, { [] }).add(classNode)
		} else if (isGrailsController(classNode)) {
			grailsControllers.computeIfAbsent(uri, { [] }).add(classNode)
		} else if (isGrailsTagLib(classNode)) {
			grailsTagLibs.computeIfAbsent(uri, { [] }).add(classNode)
		}
	}
	
	private static boolean isGrailsService(ClassNode classNode) {
		classNode.annotations*.classNode.name.any { it.endsWith("Service") } ||
				classNode.name.endsWith("Service") && classNode.packageName?.contains("service")
	}
	
	private static boolean isGrailsController(ClassNode classNode) {
		classNode.annotations*.classNode.name.any { it.endsWith("Controller") } ||
				classNode.name.endsWith("Controller") && classNode.packageName?.contains("controller")
	}
	
	private static boolean isGrailsTagLib(ClassNode classNode) {
		classNode.name.endsWith("TagLib") && classNode.packageName?.contains("taglib")
	}
	
	// Add methods to resolve injected dependencies
	ClassNode findInjectedService(ClassNode classNode, String serviceName) {
		List<ClassNode> services = grailsServices.values().flatten() as List<ClassNode>
		services.find { it.name == "${serviceName.capitalize()}Service" }
	}
}
