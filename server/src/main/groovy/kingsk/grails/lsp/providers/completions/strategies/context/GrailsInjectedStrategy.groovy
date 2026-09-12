package kingsk.grails.lsp.providers.completions.strategies.context

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

/**
 * Handles Grails injected properties (log, grailsApplication, etc.) only in appropriate contexts.
 */
@Slf4j
@CompileStatic
class GrailsInjectedStrategy extends BaseCompletionStrategy {
	
	@Override
	int getPriority() { return 70 }
	
	@Override
	boolean canHandle(CompletionRequest request, RequestContext ctx) {
		if (!request.isGrailsProject) return false
		if (request.offsetNode instanceof PropertyExpression) return false
		if (request.offsetNode instanceof MethodCallExpression) return false
		
		ClassNode currentClass = ctx.ast()?.getClassNodes(request.uri)?.find { it }
		if (!currentClass) return false
		
		return GrailsUtils.isGrailsArtefact(currentClass, request.uri)
	}
	
	@Override
	List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
        List<CompletionItem> completions = []
		
		// 1. Common properties
		['log', 'grailsApplication'].each { String propName ->
			CompletionItem item = new CompletionItem(propName)
			item.kind = CompletionItemKind.Property
			item.detail = "Grails Injected Property"
			completions.add(item)
		}
		
		// 2. Artifact-specific
		ClassNode currentClass = ctx.ast()?.getClassNodes(request.uri)?.find { it }
		if (currentClass) {
			if (GrailsUtils.isControllerClass(currentClass, request.uri)) {
				addArtifactCompletions(completions, kingsk.grails.lsp.utils.grails.GrailsHelperIntegration.getControllerProperties(), 'Controller')
			} else if (GrailsUtils.isServiceClass(currentClass, request.uri)) {
				addArtifactCompletions(completions, kingsk.grails.lsp.utils.grails.GrailsHelperIntegration.getServiceProperties(), 'Service')
			}
		}
		
		return completions
	}

	private void addArtifactCompletions(List<CompletionItem> completions, List<String> props, String type) {
		props.each { String name ->
			CompletionItem item = new CompletionItem(name)
			item.kind = CompletionItemKind.Property
			item.detail = "${type} Injected Property"
			completions.add(item)
		}
	}
}
