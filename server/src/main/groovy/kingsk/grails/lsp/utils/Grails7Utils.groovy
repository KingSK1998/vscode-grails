package kingsk.grails.lsp.utils

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode

/**
 * Utility class for Grails 7+ specific functionality
 */
@CompileStatic
class Grails7Utils {
	
	// === Grails 7 Features ===
	static final List<String> GRAILS_7_SPRING_ANNOTATIONS = [
			GrailsUtils.ANNOTATION_SPRING_COMPONENT,
			GrailsUtils.ANNOTATION_SPRING_SERVICE,
			GrailsUtils.ANNOTATION_SPRING_CONTROLLER,
			GrailsUtils.ANNOTATION_SPRING_REST_CONTROLLER,
			GrailsUtils.ANNOTATION_SPRING_REPOSITORY,
			GrailsUtils.ANNOTATION_SPRING_CONFIGURATION,
			GrailsUtils.ANNOTATION_SPRING_BOOT_APPLICATION
	]
	
	// === Grails 7 New Artifact Types ===
	static final String SUFFIX_COMPONENT = "Component"
	static final String SUFFIX_CONFIG = "Config"
	static final String SUFFIX_REPOSITORY = "Repository"
	
	// === Grails 7 Dependency Injection Patterns ===
	static final List<String> GRAILS_7_DI_ANNOTATIONS = [
			"org.springframework.beans.factory.annotation.Autowired",
			"org.springframework.beans.factory.annotation.Value",
			"org.springframework.beans.factory.annotation.Qualifier",
			"jakarta.inject.Inject",
			"jakarta.inject.Named"
	]
	
	/**
	 * Check if a class uses Grails 7+ Spring annotations
	 */
	static boolean hasGrails7SpringAnnotations(ClassNode classNode) {
		if (!classNode?.annotations) return false
		
		return classNode.annotations.any { annotation ->
			String annotationName = annotation.classNode.name
			return GRAILS_7_SPRING_ANNOTATIONS.contains(annotationName)
		}
	}
	
	/**
	 * Check if a class is a Spring Boot configuration class
	 */
	static boolean isSpringBootConfiguration(ClassNode classNode) {
		if (!classNode?.annotations) return false
		
		return classNode.annotations.any { annotation ->
			String annotationName = annotation.classNode.name
			return annotationName == GrailsUtils.ANNOTATION_SPRING_CONFIGURATION ||
					annotationName == GrailsUtils.ANNOTATION_SPRING_BOOT_APPLICATION
		}
	}
	
	/**
	 * Check if a class is a Spring REST controller
	 */
	static boolean isSpringRestController(ClassNode classNode) {
		if (!classNode?.annotations) return false
		
		return classNode.annotations.any { annotation ->
			String annotationName = annotation.classNode.name
			return annotationName == GrailsUtils.ANNOTATION_SPRING_REST_CONTROLLER
		}
	}
	
	/**
	 * Check if a class uses dependency injection annotations
	 */
	static boolean usesDependencyInjection(ClassNode classNode) {
		if (!classNode) return false
		
		// Check class-level annotations
		boolean hasClassDI = classNode.annotations?.any { annotation ->
			String annotationName = annotation.classNode.name
			return GRAILS_7_DI_ANNOTATIONS.contains(annotationName)
		}
		
		if (hasClassDI) return true
		
		// Check field-level annotations
		boolean hasFieldDI = classNode.fields?.any { field ->
			field.annotations?.any { annotation ->
				String annotationName = annotation.classNode.name
				return GRAILS_7_DI_ANNOTATIONS.contains(annotationName)
			}
		}
		
		if (hasFieldDI) return true
		
		// Check method-level annotations
		return classNode.methods?.any { method ->
			method.annotations?.any { annotation ->
				String annotationName = annotation.classNode.name
				return GRAILS_7_DI_ANNOTATIONS.contains(annotationName)
			}
		}
	}
	
}