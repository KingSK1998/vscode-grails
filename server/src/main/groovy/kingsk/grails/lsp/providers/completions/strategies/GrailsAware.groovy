package kingsk.grails.lsp.providers.completions.strategies

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.enums.GrailsArtifactType
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.utils.grails.GrailsArtefactUtils

/**
 * Trait for Grails-aware completion strategies.
 */
@CompileStatic
trait GrailsAware {
	
	boolean isArtifactType(CompletionRequest request, RequestContext ctx, GrailsArtifactType type) {
		if (!request.isGrailsProject) return false
		def currentClass = ctx.compilationContext().visitor.allClassNodes.get(request.uri)?.find { it }
		if (!currentClass) return false
		return GrailsArtefactUtils.getGrailsArtifactType(currentClass, request.uri) == type
	}
	
	boolean isController(CompletionRequest request, RequestContext ctx) {
		return isArtifactType(request, ctx, GrailsArtifactType.CONTROLLER)
	}
	
	boolean isDomain(CompletionRequest request, RequestContext ctx) {
		return isArtifactType(request, ctx, GrailsArtifactType.DOMAIN)
	}
	
	boolean isService(CompletionRequest request, RequestContext ctx) {
		return isArtifactType(request, ctx, GrailsArtifactType.SERVICE)
	}
}
