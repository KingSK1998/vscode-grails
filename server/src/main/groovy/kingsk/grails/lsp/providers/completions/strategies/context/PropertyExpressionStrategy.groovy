package kingsk.grails.lsp.providers.completions.strategies.context

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.utils.ast.ASTUtils
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.eclipse.lsp4j.CompletionItem

/**
 * Handles completions for property expressions (obj.prop|)
 */
@CompileStatic
class PropertyExpressionStrategy extends BaseCompletionStrategy {
	
	@Override
	int getPriority() { return 90 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.BOTH }
	
	@Override
	boolean canHandle(CompletionRequest request, RequestContext ctx) {
		request.offsetNode instanceof PropertyExpression || request.parentNode instanceof PropertyExpression
	}
	
	@Override
	List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
		List<CompletionItem> completions = []
		if (request.offsetNode instanceof PropertyExpression) {
			provideCompletionsInternal((PropertyExpression) request.offsetNode, request, ctx, completions)
		} else if (request.parentNode instanceof PropertyExpression) {
			provideCompletionsInternal((PropertyExpression) request.parentNode, request, ctx, completions)
		}
		return completions
	}
	
	private void provideCompletionsInternal(PropertyExpression propExpr, CompletionRequest request, RequestContext ctx, List<CompletionItem> completions) {
		Expression objectExpression = propExpr.objectExpression
		if (!objectExpression) return
		
		ClassNode objectType = getTypeOf(objectExpression, ctx)
		if (!objectType) return
		
		addMemberCompletions(objectExpression, ctx, completions)
		
		if (request.isGrailsProject) {
			addGrailsSpecificCompletions(objectType, request.uri, completions)
		}
	}
	
	private void addGrailsSpecificCompletions(ClassNode objectType, String uri, List<CompletionItem> completions) {
		if (GrailsUtils.isGrailsArtefact(objectType, uri)) {
			// Extract members using static helper rather than strategy method
			kingsk.grails.lsp.utils.ast.MemberExtractor.collectMembers(objectType, false, null).properties.each { node ->
				if (!ASTUtils.isInvalidDocumentSymbol(node)) {
					completions.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(node))
				}
			}
		}
	}
}
