package kingsk.grails.lsp.providers.completions.strategies.context

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.VariableExpression
import org.eclipse.lsp4j.CompletionItem

/**
 * Handles completions for variable expressions (var|)
 */
@CompileStatic
class VariableExpressionStrategy extends BaseCompletionStrategy {
	
	@Override
	int getPriority() { return 70 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.OFFSET }
	
	@Override
	boolean canHandle(CompletionRequest request, RequestContext ctx) { 
		request.offsetNode instanceof VariableExpression 
	}
	
	@Override
	List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
        List<CompletionItem> completions = []
		VariableExpression varExpr = (VariableExpression) request.offsetNode
		
		addScopeCompletions(varExpr, ctx, completions)
		
		return completions
	}
}
