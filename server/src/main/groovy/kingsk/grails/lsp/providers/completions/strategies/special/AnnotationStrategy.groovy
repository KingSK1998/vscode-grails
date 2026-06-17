package kingsk.grails.lsp.providers.completions.strategies.special

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

@CompileStatic
class AnnotationStrategy extends BaseCompletionStrategy {
	
	@Override
	int getPriority() { return 88 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.OFFSET }
	
	@Override
	boolean canHandle(CompletionRequest request, RequestContext ctx) { 
		request.offsetNode instanceof AnnotationNode 
	}
	
	@Override
	List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
		List<CompletionItem> completions = []
		List<String> annotations = ['Controller', 'Service', 'Transactional', 'CompileStatic', 'Slf4j']
		if (request.isGrailsProject) {
			annotations.addAll(['Artefact', 'Mock', 'TestFor'])
		}
		annotations.each { name ->
			CompletionItem item = new CompletionItem(name)
			item.kind = CompletionItemKind.Class
			item.detail = 'Annotation'
			completions.add(item)
		}
		return completions
	}
}
