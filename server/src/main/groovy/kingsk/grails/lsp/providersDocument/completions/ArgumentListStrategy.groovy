package kingsk.grails.lsp.providersDocument.completions

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.providersDocument.CompletionRequest
import kingsk.grails.lsp.utils.GrailsASTHelper
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression

@CompileStatic
class ArgumentListStrategy extends BaseCompletionStrategy {
	
	ArgumentListStrategy(CompletionRequest request) { super(request) }
	
	@Override
	int getPriority() { return 82 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.BOTH }
	
	@Override
	boolean canHandle(ASTNode node) {
		return node instanceof ArgumentListExpression && getParentOf(node) instanceof MethodCallExpression
	}
	
	@Override
	void provideCompletions(ASTNode node) { provideCompletion(node as ArgumentListExpression) }
	
	private void provideCompletion(ArgumentListExpression argumentList) {
		def methodCall = getParentOf(argumentList)
		if (methodCall instanceof MethodCallExpression) {
			addMethodParameterCompletions(methodCall)
		}
		addScopeCompletions(argumentList)
	}
	
	/**
	 * Add completions for method parameters based on the method signature.
	 */
	private void addMethodParameterCompletions(MethodCallExpression methodCall) {
		def method = GrailsASTHelper.getMethodFromCallExpression(methodCall, request.visitor)
		if (method instanceof MethodNode && method.parameters) {
			logDebug("Adding parameter completions for method: %s", method.name)
			method.parameters.each { Parameter param ->
				request.addCompletion(param)
			}
		}
	}
}
