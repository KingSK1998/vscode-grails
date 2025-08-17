package kingsk.grails.lsp.providersDocument.completions

import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.providersDocument.CompletionRequest
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
