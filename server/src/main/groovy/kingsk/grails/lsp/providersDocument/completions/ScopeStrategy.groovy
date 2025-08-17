package kingsk.grails.lsp.providersDocument.completions

import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.providersDocument.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.stmt.Statement

class ScopeStrategy extends BaseCompletionStrategy {
	
	ScopeStrategy(CompletionRequest request) { super(request) }
	
	@Override
	int getPriority() { return 30 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.BOTH }
	
	@Override
	boolean canHandle(ASTNode node) { return node instanceof Statement }
	
	@Override
	void provideCompletions(ASTNode node) {
		addScopeCompletions(node)
	}
}
