package kingsk.grails.lsp.providersDocument.completions

import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.providersDocument.CompletionRequest
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
