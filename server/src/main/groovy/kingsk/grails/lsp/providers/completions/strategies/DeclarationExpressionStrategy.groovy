package kingsk.grails.lsp.providers.completions.strategies

import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression

class DeclarationExpressionStrategy extends BaseCompletionStrategy {
	
	DeclarationExpressionStrategy(CompletionRequest request) { super(request) }
	
	@Override
	int getPriority() { return 75 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.BOTH }
	
	@Override
	boolean canHandle(ASTNode node) { return node instanceof ArgumentListExpression }
	
	@Override
	void provideCompletions(ASTNode node) {
	
	}
}
