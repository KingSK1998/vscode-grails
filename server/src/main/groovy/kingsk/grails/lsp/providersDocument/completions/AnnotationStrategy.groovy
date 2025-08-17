package kingsk.grails.lsp.providersDocument.completions

import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.providersDocument.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode

class AnnotationStrategy extends BaseCompletionStrategy {
	
	AnnotationStrategy(CompletionRequest request) { super(request) }
	
	@Override
	int getPriority() { return 88 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.OFFSET }
	
	@Override
	boolean canHandle(ASTNode node) { return node instanceof AnnotationNode }
	
	@Override
	void provideCompletions(ASTNode node) {}
}
