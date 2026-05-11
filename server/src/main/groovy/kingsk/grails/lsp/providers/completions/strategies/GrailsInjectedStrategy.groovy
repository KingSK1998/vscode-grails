package kingsk.grails.lsp.providers.completions.strategies

import groovy.transform.CompileStatic
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression

/**
 * Handles Grails injected properties (log, grailsApplication, etc.) only in appropriate contexts.
 *
 * This strategy provides Grails injected completions ONLY when:
 * - We're in a Grails artifact (Controller, Service, etc.)
 * - We're NOT in a property expression context (obj.prop|)
 * - We're at the start of a statement or after 'this.'
 */
@CompileStatic
class GrailsInjectedStrategy extends BaseCompletionStrategy {
	
	GrailsInjectedStrategy(CompletionRequest request) {
		super(request)
	}
	
	@Override
	int getPriority() {
		return 70 // High priority for Grails injected properties
	}
	
	// GrailsInjectedStrategy - handles Grails service injection and dependency injection
	@Override
	boolean canHandle(ASTNode node) {
		//		if (!(node instanceof VariableExpression || node instanceof PropertyExpression)) {
		//			return false
		//		}
		
		// Only in Grails projects
		if (!request.isGrailsProject) {
			return false
		}
		
		// Don't provide injected properties in property expression contexts
		// (they should come from the object type, not as global completions)
		if (node instanceof PropertyExpression) {
			return false
		}
		
		// Don't provide in method call contexts
		if (node instanceof MethodCallExpression) {
			return false
		}
		
		// Only provide in Grails artifacts
		if (!isInGrailsArtifact()) {
			return false
		}
		
		// Provide when we're at statement level or after 'this.'
		return isAtStatementLevel() || isAfterThis()
	}
	
	@Override
	void provideCompletions(ASTNode node) {
		logDebug("Providing Grails injected property completions")
		
		ClassNode currentClass = request.getCurrentClass()
		if (!currentClass) return
		
		// Add common Grails injected properties
		addCommonGrailsInjectedProperties()
		
		// Add artifact-specific injected properties
		addArtifactSpecificInjectedProperties(currentClass)
	}
	
	/**
	 * Check if we're in a Grails artifact class
	 */
	private boolean isInGrailsArtifact() {
		ClassNode currentClass = request.getCurrentClass()
		if (!currentClass) return false
		
		// Use existing GrailsUtils method
		return GrailsUtils.isGrailsArtefact(currentClass)
	}
	
	/**
	 * Check if we're at statement level (not inside an expression)
	 */
	private boolean isAtStatementLevel() {
		String lineText = request.file?.textAtLine(request.position?.line ?: 0) ?: ""
		String beforeCursor = lineText.substring(0, Math.min(request.position?.character ?: 0, lineText.length()))
		
		// At start of line or after common statement patterns
		return beforeCursor.trim().isEmpty() ||
				beforeCursor.matches(".*[;{}]\\s*\$") ||
				beforeCursor.matches(".*\\b(if|while|for|return|def|var)\\s*\\(?\\s*\$")
	}
	
	/**
	 * Check if we're after 'this.'
	 */
	private boolean isAfterThis() {
		String lineText = request.file?.textAtLine(request.position?.line ?: 0) ?: ""
		String beforeCursor = lineText.substring(0, Math.min(request.position?.character ?: 0, lineText.length()))
		
		return beforeCursor.endsWith("this.")
	}
	
	/**
	 * Add common Grails injected properties available in all artifacts
	 */
	private void addCommonGrailsInjectedProperties() {
		// Common injected properties from helper
		kingsk.grails.lsp.utils.grails.GrailsHelperIntegration.getGrailsConfigurationKeys().take(5).each { String propName ->
			request.addGrailsCompletion(propName, 'Grails Configuration Property')
		}
		// Core injected properties
		['log', 'grailsApplication'].each { String propName ->
			request.addGrailsCompletion(propName, "Grails Injected Property")
		}
	}
	
	/**
	 * Add artifact-specific injected properties
	 */
	private void addArtifactSpecificInjectedProperties(ClassNode currentClass) {
		// Use existing GrailsUtils methods for artifact detection
		if (GrailsUtils.isControllerClass(currentClass)) {
			addControllerInjectedProperties()
		} else if (GrailsUtils.isServiceClass(currentClass)) {
			addServiceInjectedProperties()
		} else if (GrailsUtils.isDomainClass(currentClass)) {
			addDomainInjectedProperties()
		} else if (GrailsUtils.isTagLibClass(currentClass)) {
			addTagLibInjectedProperties()
		} else if (GrailsUtils.isJobClass(currentClass)) {
			addJobInjectedProperties()
		}
	}
	
	/**
	 * Add Job-specific injected properties
	 */
	private void addJobInjectedProperties() {
		// Jobs have access to basic Grails services
		// Most are covered by common injected properties
	}
	
	/**
	 * Add controller-specific injected properties
	 */
	private void addControllerInjectedProperties() {
		kingsk.grails.lsp.utils.grails.GrailsHelperIntegration.getControllerProperties().each { String propName ->
			request.addGrailsCompletion(propName, 'Controller Injected Property')
		}
	}
	
	/**
	 * Add service-specific injected properties
	 */
	private void addServiceInjectedProperties() {
		kingsk.grails.lsp.utils.grails.GrailsHelperIntegration.getServiceProperties().each { String propName ->
			request.addGrailsCompletion(propName, 'Service Injected Property')
		}
	}
	
	/**
	 * Add domain-specific injected properties
	 */
	private void addDomainInjectedProperties() {
		kingsk.grails.lsp.utils.grails.GrailsHelperIntegration.getPropertiesForArtifactType('domain').each { String propName ->
			request.addGrailsCompletion(propName, 'Domain Injected Property')
		}
	}
	
	/**
	 * Add TagLib-specific injected properties
	 */
	private void addTagLibInjectedProperties() {
		kingsk.grails.lsp.utils.grails.GrailsHelperIntegration.getTagLibProperties().each { String propName ->
			request.addGrailsCompletion(propName, 'TagLib Injected Property')
		}
	}
}