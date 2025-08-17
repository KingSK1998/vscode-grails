package kingsk.grails.lsp.providersDocument.completions

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.GrailsArtifactType
import kingsk.grails.lsp.providersDocument.CompletionRequest

/**
 * Trait for Grails-aware completion providers
 * Provides common Grails context checks
 */
@CompileStatic
trait GrailsAware {
	
	/**
	 * Check if request is in a Grails project
	 */
	boolean isGrailsProject(CompletionRequest request) {
		return request.isGrailsProject
	}
	
	/**
	 * Check if current class is a specific Grails artifact type
	 */
	boolean isArtifactType(CompletionRequest request, GrailsArtifactType type) {
		return request.isGrailsProject && request.artefactType == type
	}
	
	/**
	 * Check if current class is a Controller
	 */
	boolean isController(CompletionRequest request) {
		return isArtifactType(request, GrailsArtifactType.CONTROLLER)
	}
	
	/**
	 * Check if current class is a Domain
	 */
	boolean isDomain(CompletionRequest request) {
		return isArtifactType(request, GrailsArtifactType.DOMAIN)
	}
	
	/**
	 * Check if current class is a Service
	 */
	boolean isService(CompletionRequest request) {
		return isArtifactType(request, GrailsArtifactType.SERVICE)
	}
	
	/**
	 * Check if current class is a TagLib
	 */
	boolean isTagLib(CompletionRequest request) {
		return isArtifactType(request, GrailsArtifactType.TAGLIB)
	}
}