package kingsk.grails.lsp.providers.completions.strategies.type

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.MethodNode
import org.eclipse.lsp4j.CompletionItem

@CompileStatic
class MethodNodeStrategy extends BaseCompletionStrategy {
	
	@Override
	int getPriority() { return 60 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.OFFSET }
	
	@Override
	boolean canHandle(CompletionRequest request, RequestContext ctx) { 
		request.offsetNode instanceof MethodNode 
	}
	
	@Override
	List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
        return [] as List<CompletionItem>
    }
}
