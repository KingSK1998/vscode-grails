package kingsk.grails.lsp.providers.completions.strategies.snippet

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.eclipse.lsp4j.CompletionItem
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor

@CompileStatic
class ArgumentListStrategy extends BaseCompletionStrategy {
	
	@Override
	int getPriority() { return 82 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.BOTH }
	
	@Override
	boolean canHandle(CompletionRequest request, RequestContext ctx) {
		request.offsetNode instanceof ArgumentListExpression && ctx.ast().getParent(request.offsetNode) instanceof MethodCallExpression
	}
	
	@Override
	List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
        List<CompletionItem> completions = []
		ArgumentListExpression argumentList = (ArgumentListExpression) request.offsetNode
		
		def methodCall = ctx.ast().getParent(argumentList)
		if (methodCall instanceof MethodCallExpression) {
			addMethodParameterCompletions((MethodCallExpression) methodCall, ctx, completions)
		}
		addScopeCompletions(argumentList, ctx, completions)
		
		return completions
	}
	
	private void addMethodParameterCompletions(MethodCallExpression methodCall, RequestContext ctx, List<CompletionItem> completions) {
		def visitor = ctx.ast() as GrailsASTVisitor
		def method = GrailsASTHelper.getMethodFromCallExpression(methodCall, visitor)
		if (method instanceof MethodNode && method.parameters) {
			method.parameters.each { Parameter param ->
				completions.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(param))
			}
		}
    }
}
