package kingsk.grails.lsp.providers.completions.strategies.context

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.eclipse.lsp4j.CompletionItem

/**
 * Handles completions for method calls (obj.method(|))
 */
@CompileStatic
class MethodCallExpressionStrategy extends BaseCompletionStrategy {
	
	@Override
	int getPriority() { return 85 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.BOTH }
	
	@Override
	boolean canHandle(CompletionRequest request, RequestContext ctx) { 
		request.offsetNode instanceof MethodCallExpression 
	}
	
	@Override
	List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
        List<CompletionItem> completions = []
		MethodCallExpression methodCall = (MethodCallExpression) request.offsetNode
		
		Expression objectExpression = methodCall.objectExpression
		if (!objectExpression) return completions
		
		ClassNode objectType = getTypeOf(objectExpression, ctx)
		if (!objectType) return completions
		
		addMemberCompletions(objectExpression, ctx, completions)
		
		return completions
	}
}
