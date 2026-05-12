package kingsk.grails.lsp.providers.completions.strategies.type

import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.MethodNode

class MethodNodeStrategy extends BaseCompletionStrategy {
	
	MethodNodeStrategy(CompletionRequest request) { super(request) }
	
	@Override
	int getPriority() { return 60 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.OFFSET }
	
	@Override
	boolean canHandle(ASTNode node) { return node instanceof MethodNode }
	
	@Override
	void provideCompletions(ASTNode node) {
	
	}
}
