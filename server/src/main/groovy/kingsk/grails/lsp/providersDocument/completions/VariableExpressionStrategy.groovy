package kingsk.grails.lsp.providersDocument.completions

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.providersDocument.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.VariableExpression

/**
 * Handles completions for variable expressions (var|)
 * Provides scope-aware variable and type completions
 */
@CompileStatic
class VariableExpressionStrategy extends BaseCompletionStrategy {
	
	VariableExpressionStrategy(CompletionRequest request) { super(request) }
	
	@Override
	int getPriority() { return 70 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.OFFSET }
	
	@Override
	boolean canHandle(ASTNode node) { return node instanceof VariableExpression }
	
	/**
	 * Local or field variable completions
	 * @param node The VariableExpression node to complete
	 */
	@Override
	void provideCompletions(ASTNode node) {
		provideCompletion(node as VariableExpression)
	}
	
	void provideCompletion(VariableExpression varExpr) {
		logDebug("Providing completions for variable expression: %s", varExpr.name)
		addScopeCompletions(varExpr)
		if (request.isGrailsProject) {
			// TODO: analyse and see if completion already present if yes just enhance
		}
	}
}