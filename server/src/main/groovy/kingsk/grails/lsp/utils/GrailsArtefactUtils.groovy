package kingsk.grails.lsp.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.model.GrailsArtifactType
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression

@Slf4j
@CompileStatic
class GrailsArtefactUtils {
	
	/**
	 * Attempts to resolve Grails-specific type definitions.
	 * Handles domain class properties, dynamic finders, GORM methods, controller actions, etc.
	 *
	 * @param node The AST node to resolve
	 * @param visitor The AST visitor providing context
	 * @return The resolved type definition node, or null if not a Grails-specific type
	 */
	static ASTNode tryToResolveGrailsTypeDefinition(ASTNode node, GrailsASTVisitor visitor) {
		// Get enclosing class to determine context
		def enclosingClass = GrailsASTHelper.getEnclosingClassNode(node, visitor)
		if (!enclosingClass) return null
		
		// Check if in Grails artefact
		def artifactType = getGrailsArtifactType(enclosingClass, visitor)
		if (artifactType == GrailsArtifactType.UNKNOWN) return null
		
		log.debug("Resolving Grails type definition in ${artifactType} for node: $node")
		
		switch (artifactType) {
			case GrailsArtifactType.DOMAIN: return resolveDomainClassTypeDefinition(node, enclosingClass, visitor)
			case GrailsArtifactType.CONTROLLER: return resolveControllerTypeDefinition(node, enclosingClass, visitor)
			case GrailsArtifactType.SERVICE: return resolveServiceTypeDefinition(node, enclosingClass, visitor)
			case GrailsArtifactType.TAGLIB: return resolveTagLibTypeDefinition(node, enclosingClass, visitor)
			default: return null
		}
	}
	
	/**
	 * Determines if a class is a Grails artifact and returns its type.
	 * Checks class name, package, annotations, and file path to determine the artifact type.
	 *
	 * @param classNode The class to check
	 * @param visitor The AST visitor providing context
	 * @return The GrailsArtifactType, or UNKNOWN if not a Grails artifact
	 */
	static GrailsArtifactType getGrailsArtifactType(ClassNode classNode, GrailsASTVisitor visitor) {
		if (!visitor) return GrailsArtifactType.UNKNOWN
		// Check if we have a URI for this class
		return getGrailsArtifactType(classNode, visitor.getURI(classNode))
	}
	
	static GrailsArtifactType getGrailsArtifactType(ClassNode classNode, String uri = null) {
		if (!classNode) return GrailsArtifactType.UNKNOWN
		
		// Try to derive URI if not provided
		if (!uri) {
			log.debug("No URI provided for class: ${classNode.nameWithoutPackage}, attempting fallback resolution")
			
			uri = GrailsUtils.resolveFallbackUriFromClassNode(classNode)
			if (!uri) {
				log.debug("Failed to resolve URI for class: ${classNode.name}")
				return GrailsArtifactType.UNKNOWN
			}
		}
		
		// Full classification logic
		if (GrailsUtils.isControllerClass(classNode, uri)) return GrailsArtifactType.CONTROLLER
		if (GrailsUtils.isServiceClass(classNode, uri)) return GrailsArtifactType.SERVICE
		if (GrailsUtils.isTagLibClass(classNode, uri)) return GrailsArtifactType.TAGLIB
		if (GrailsUtils.isInterceptorClass(classNode, uri)) return GrailsArtifactType.INTERCEPTOR
		if (GrailsUtils.isApplicationClass(classNode, uri)) return GrailsArtifactType.APPLICATION
		if (GrailsUtils.isDomainClass(classNode, uri)) return GrailsArtifactType.DOMAIN
		
		// Check by URI-based location fallbacks
		GrailsArtifactType typeByDirectory = getArtifactTypeByDirectory(uri)
		if (typeByDirectory != GrailsArtifactType.UNKNOWN) return typeByDirectory
		
		// Fall back to naming conventions if they match AND directory looks okay
		GrailsArtifactType typeByName = getArtifactTypeByName(classNode.nameWithoutPackage)
		if (typeByName != GrailsArtifactType.UNKNOWN && isInCorrectDirectory(uri, typeByName)) {
			return typeByName
		}
		
		// Check annotation-based
		//		GrailsArtifactType typeByAnnotation = getArtifactTypeByAnnotation(classNode)
		//		if (typeByAnnotation != GrailsArtifactType.UNKNOWN) return typeByAnnotation
		
		// Handle domain vs POGO fallback logic
		if (uri.contains(GrailsUtils.DOMAINS_PATH)) {
			if (GrailsUtils.hasDomainAnnotation(classNode)) return GrailsArtifactType.DOMAIN
			else return GrailsArtifactType.POGO
		}
		
		// Not a recognized Grails artefact
		return GrailsArtifactType.POGO
	}
	
	/**
	 * Determines the artifact type based on class name conventions.
	 *
	 * @param className The name of the class without package
	 * @return The GrailsArtifactType, or UNKNOWN if not matching any convention
	 */
	static GrailsArtifactType getArtifactTypeByName(String className) {
		if (!className) return GrailsArtifactType.UNKNOWN
		
		// Special files
		if (className == "Application") return GrailsArtifactType.APPLICATION
		if (className == "BootStrap") return GrailsArtifactType.BOOTSTRAP
		if (className == "UrlMappings") return GrailsArtifactType.URL_MAPPINGS
		
		// Common artefact types
		if (className.endsWith("Controller")) return GrailsArtifactType.CONTROLLER
		if (className.endsWith("Service")) return GrailsArtifactType.SERVICE
		if (className.endsWith("TagLib")) return GrailsArtifactType.TAGLIB
		if (className.endsWith("Interceptor")) return GrailsArtifactType.INTERCEPTOR
		
		// Testing artifacts
		if (className.endsWith("Spec")) return GrailsArtifactType.SPOCK_TEST
		if (className.endsWith("Test")) return GrailsArtifactType.JUNIT_TEST
		
		// Other artifacts
		if (className.endsWith("Job")) return GrailsArtifactType.JOB
		
		// Domain classes don't have a standard suffix, so we can't detect them by name alone
		
		return GrailsArtifactType.UNKNOWN
	}
	
	/**
	 * Determines the artifact type based on class annotations.
	 *
	 * @param classNode The class to check
	 * @return The GrailsArtifactType, or UNKNOWN if not matching any annotations
	 */
	static GrailsArtifactType getArtifactTypeByAnnotation(ClassNode classNode) {
		if (!classNode?.annotations) return GrailsArtifactType.UNKNOWN
		
		for (annotation in classNode.annotations) {
			String annotationName = annotation.classNode?.name
			if (!annotationName) continue
			
			// Traditional Grails annotations
			if (GrailsUtils.isControllerAnnotation(annotationName)) return GrailsArtifactType.CONTROLLER
			if (GrailsUtils.isServiceAnnotation(annotationName)) return GrailsArtifactType.SERVICE
			if (GrailsUtils.isTagLibAnnotation(annotationName)) return GrailsArtifactType.TAGLIB
			if (GrailsUtils.isInterceptorAnnotation(annotationName)) return GrailsArtifactType.INTERCEPTOR
			if (GrailsUtils.isDomainAnnotation(annotationName)) return GrailsArtifactType.DOMAIN
			
			// Grails 7+ Spring annotations
			if (annotationName == GrailsUtils.ANNOTATION_SPRING_COMPONENT) return GrailsArtifactType.SPRING_COMPONENT
			if (annotationName == GrailsUtils.ANNOTATION_SPRING_SERVICE) return GrailsArtifactType.SERVICE
			if (annotationName == GrailsUtils.ANNOTATION_SPRING_CONTROLLER) return GrailsArtifactType.CONTROLLER
			if (annotationName == GrailsUtils.ANNOTATION_SPRING_REST_CONTROLLER) return GrailsArtifactType.SPRING_REST_CONTROLLER
			if (annotationName == GrailsUtils.ANNOTATION_SPRING_REPOSITORY) return GrailsArtifactType.SPRING_REPOSITORY
			if (annotationName == GrailsUtils.ANNOTATION_SPRING_CONFIGURATION) return GrailsArtifactType.SPRING_CONFIGURATION
			if (annotationName == GrailsUtils.ANNOTATION_SPRING_BOOT_APPLICATION) return GrailsArtifactType.SPRING_BOOT_APPLICATION
		}
		return GrailsArtifactType.UNKNOWN
	}
	
	/**
	 * Determines the artifact type based on the file's directory location.
	 *
	 * @param uri The URI of the file
	 * @return The GrailsArtifactType, or UNKNOWN if not in a recognized directory
	 */
	private static GrailsArtifactType getArtifactTypeByDirectory(String uri) {
		if (!uri) return GrailsArtifactType.UNKNOWN
		
		if (GrailsUtils.isTestSpec(uri)) return GrailsArtifactType.SPOCK_TEST
		if (GrailsUtils.isTestSpec(uri)) return GrailsArtifactType.JUNIT_TEST
		
		// Check if file is in the grails-app directory
		if (!GrailsUtils.isInGrailsAppDir(uri)) return GrailsArtifactType.POGO
		
		// Check specific directories
		if (GrailsUtils.isInControllerDir(uri)) return GrailsArtifactType.CONTROLLER
		if (GrailsUtils.isInServiceDir(uri)) return GrailsArtifactType.SERVICE
		if (GrailsUtils.isInDomainDir(uri)) return GrailsArtifactType.DOMAIN
		if (GrailsUtils.isInTagLibDir(uri)) return GrailsArtifactType.TAGLIB
		if (GrailsUtils.isInViewsDir(uri)) return GrailsArtifactType.VIEWS
		if (GrailsUtils.isInJobsDir(uri)) return GrailsArtifactType.JOB
		if (GrailsUtils.isInConfDir(uri)) return GrailsArtifactType.CONFIG
		if (GrailsUtils.isInInitDir(uri)) return GrailsArtifactType.BOOTSTRAP
		
		if (GrailsUtils.isInSrcDir(uri)) return GrailsArtifactType.POGO
		
		return GrailsArtifactType.UNKNOWN
	}
	
	/**
	 * Checks if a file is in the correct directory for its artifact type.
	 *
	 * @param uri The URI of the file
	 * @param artifactType The expected artifact type
	 * @return true if the file is in the correct directory, false otherwise
	 */
	private static boolean isInCorrectDirectory(String uri, GrailsArtifactType artifactType) {
		if (!uri || artifactType == GrailsArtifactType.UNKNOWN) return false
		
		switch (artifactType) {
			case GrailsArtifactType.CONTROLLER: return uri.contains(GrailsUtils.CONTROLLERS_PATH)
			case GrailsArtifactType.SERVICE: return uri.contains(GrailsUtils.SERVICES_PATH)
			case GrailsArtifactType.DOMAIN: return uri.contains(GrailsUtils.DOMAINS_PATH)
			case GrailsArtifactType.TAGLIB: return uri.contains(GrailsUtils.TAGLIBS_PATH)
			case GrailsArtifactType.VIEWS: return uri.contains(GrailsUtils.VIEWS_PATH)
			case GrailsArtifactType.JOB: return uri.contains(GrailsUtils.JOBS_PATH)
			case GrailsArtifactType.CONFIG: return uri.contains(GrailsUtils.CONF_PATH)
			case GrailsArtifactType.APPLICATION: // fallthrough
			case GrailsArtifactType.BOOTSTRAP:
				return uri.contains(GrailsUtils.INIT_PATH)
			case GrailsArtifactType.URL_MAPPINGS: // fallthrough
			case GrailsArtifactType.INTERCEPTOR:
				return uri.contains(GrailsUtils.CONTROLLERS_PATH)
			case GrailsArtifactType.SPOCK_TEST: // fallthrough
			case GrailsArtifactType.JUNIT_TEST:
				return uri.contains(GrailsUtils.TEST_PATH)
			default: false
		}
	}
	
	/**
	 * Resolves type definitions specific to domain classes.
	 * Handles GORM dynamic methods, relationship properties, etc.
	 *
	 * @param node The AST node to resolve
	 * @param domainClass The enclosing domain class
	 * @param visitor The AST visitor
	 * @return The resolved type definition node, or null if not resolvable
	 */
	static ASTNode resolveDomainClassTypeDefinition(ASTNode node, ClassNode domainClass, GrailsASTVisitor visitor) {
		// Handle GORM dynamic methods
		if (node instanceof MethodCallExpression) {
			String methodName = node.method.text
			// Handle dynamic finders like findBy, findAllBy, countBy, existsBy
			if (GrailsUtils.GORM_DYNAMIC_FINDERS.any { methodName.startsWith(it) }) {
				// For findBy* and existsBy*, return the domain class itself
				if (node.methodAsString.startsWith("findBy") && !node.methodAsString.startsWith("findAllBy")) return domainClass
				
				// For findAllBy*, return List<DomainClass>
				if (node.methodAsString.startsWith("findAllBy")) {
					// In a real implementation, we would create a parameterized List type
					// For now, just return the domain class as an approximation
					return domainClass
				}
				
				// For countBy*, return Integer/Long type
				if (node.methodAsString.startsWith("countBy")) {
					return visitor.getClassNodes().find { it.name == GrailsUtils.TYPE_LONG }
				}
				
				// For existsBy*, return Boolean*, return Boolean type
				if (node.methodAsString.startsWith("countBy")) {
					return visitor.getClassNodes().find { it.name == GrailsUtils.TYPE_BOOLEAN }
				}
			}
			
			// Handle other GORM methods like save(), delete(), etc.
			switch (node.methodAsString) {
				case "save" || "merge" || "attach": return domainClass
				case "list" || "findAll": return domainClass // Should be List<DomainClass>
				case "get" || "read" || "load": return domainClass
				case "count": return visitor.getClassNodes().find { it.name == GrailsUtils.TYPE_LONG }
				case "delete": return visitor.getClassNodes().find { it.name == GrailsUtils.TYPE_VOID }
			}
		}
		
		// Handle domain class properties including relationships
		if (node instanceof PropertyExpression || node instanceof VariableExpression) {
			// For a complete implementation, we would check hasMany, belongsTo, etc.
			// and resolve the appropriate relationship type
			
			// For now, just use standard property resolution
			return null
		}
		
		return null
	}
	
	/**
	 * Resolves type definitions specific to controllers.
	 * Handles params, session, flash, render, redirect, etc.
	 *
	 * @param node The AST node to resolve
	 * @param controllerClass The enclosing controller class
	 * @param visitor The AST visitor
	 * @return The resolved type definition node, or null if not resolvable
	 */
	static ASTNode resolveControllerTypeDefinition(ASTNode node, ClassNode controllerClass, GrailsASTVisitor visitor) {
		// Handle controller special parameters
		if (node instanceof VariableExpression) {
			switch (node.name) {
				case "params" || "session" || "flash" || "chainModel" || "model":
					return visitor.getClassNodes().find { it.name == GrailsUtils.TYPE_MAP }
				case "request": return visitor.getClassNodes().find { it.name.contains("HttpServletRequest") }
				case "response": return visitor.getClassNodes().find { it.name.contains("HttpServletResponse") }
				case "view": return visitor.getClassNodes().find { it.name == GrailsUtils.TYPE_STRING }
			}
		}
		
		// Handle controller methods like redirect, render, etc.
		if (node instanceof MethodCallExpression) {
			String methodName = node.method.text
			
			switch (methodName) {
				case "redirect" || "forward" || "render" || "respond":
					return visitor.getClassNodes().find { it.name == GrailsUtils.TYPE_OBJECT }
					// These methods typically return null in Grails
					//				case "render":
					// render returns the rendered content or null
					//					return visitor.getClassNodes().find { it.name == GrailsEnvironment.TYPE_OBJECT }
			}
		}
		
		return null
	}
	
	/**
	 * Resolves type definitions specific to services.
	 * Handles transactional methods, injected services, etc.
	 *
	 * @param node The AST node to resolve
	 * @param serviceClass The enclosing service class
	 * @param visitor The AST visitor
	 * @return The resolved type definition node, or null if not resolvable
	 */
	static ASTNode resolveServiceTypeDefinition(ASTNode node, ClassNode serviceClass, GrailsASTVisitor visitor) {
		// For a complete implementation, we would handle service-specific features
		// For now, just use standard type resolution
		return null
	}
	
	/**
	 * Resolves type definitions specific to tag libraries.
	 * Handles tag methods, out, attrs, etc.
	 *
	 * @param node The AST node to resolve
	 * @param tagLibClass The enclosing tag library class
	 * @param visitor The AST visitor
	 * @return The resolved type definition node, or null if not resolvable
	 */
	static ASTNode resolveTagLibTypeDefinition(ASTNode node, ClassNode tagLibClass, GrailsASTVisitor visitor) {
		// Handle taglib implicit variables
		if (node instanceof VariableExpression) {
			switch (node.name) {
				case "out": return visitor.getClassNodes().find { it.name.contains("Writer") }
				case "attrs": return visitor.getClassNodes().find { it.name == GrailsUtils.TYPE_MAP }
				case "tag": return visitor.getClassNodes().find { it.name.contains("TagLibraryLookup") }
			}
		}
		
		return null
	}
	
	// Check if class extends from a Grails domain class
	static boolean isGrailsDomainClass(ClassNode classNode) {
		return classNode.superClass?.name == GrailsUtils.ANNOTATION_DOMAIN ||
				classNode.interfaces.any { it.name == GrailsUtils.ANNOTATION_DOMAIN } ||
				classNode.annotations.any { it.classNode.name == GrailsUtils.ANNOTATION_DOMAIN }
	}
	
	// Check if class is a Grails controller
	static boolean isGrailsControllerClass(ClassNode classNode) {
		return classNode.name.endsWith(GrailsUtils.SUFFIX_CONTROLLER) ||
				classNode.superClass?.name == GrailsUtils.ANNOTATION_CONTROLLER ||
				classNode.interfaces.any { it.name == GrailsUtils.ANNOTATION_WEB_CONTROLLER } ||
				classNode.annotations.any { it.classNode.name == GrailsUtils.ANNOTATION_WEB_CONTROLLER }
	}
	
	// Check if method is a controller action
	static boolean isControllerAction(MethodNode methodNode) {
		return !methodNode.static &&
				!methodNode.private &&
				methodNode.public &&
				methodNode.parameters.size() <= 1 // TODO: This statement is wrong
	}
	
	// Check if method call is a GORM dynamic finder
	static boolean isGormDynamicFinder(MethodCallExpression expression) {
		String name = expression.method.text
		return name.startsWith("findBy") ||
				name.startsWith("findAllBy") ||
				name.startsWith("countBy") ||
				name.startsWith("listOrderBy")
	}
	
	static String getControllerName(ClassNode classNode) {
		if (!isGrailsControllerClass(classNode)) return null
		
		return classNode.nameWithoutPackage
				.replace(GrailsUtils.SUFFIX_CONTROLLER, "")
				.toLowerCase()
	}
}
