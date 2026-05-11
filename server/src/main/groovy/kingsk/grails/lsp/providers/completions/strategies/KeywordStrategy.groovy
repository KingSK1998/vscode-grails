package kingsk.grails.lsp.providers.completions.strategies

import groovy.transform.CompileStatic
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

/**
 * Handles keyword completions
 */
@CompileStatic
class KeywordStrategy extends BaseCompletionStrategy {
	
	KeywordStrategy(CompletionRequest request) {
		super(request)
	}
	
	@Override
	boolean canHandle(ASTNode node) {
		// Keywords can be suggested in most contexts
		return true
	}
	
	@Override
	int getPriority() {
		return 30 // Lower priority, fallback strategy
	}
	
	@Override
	void provideCompletions(ASTNode node) {
		addLanguageKeywords(request)
		addGroovyKeywords(request)
		
		if (request.isGrailsProject) {
			addGrailsKeywords(request)
		}
	}
	
	private void addLanguageKeywords(CompletionRequest request) {
		// Use dynamic discovery instead of hardcoded list
		List<String> keywords = request.service.discoveryService.getLanguageKeywords()
		
		keywords.each { keyword ->
			CompletionItem item = new CompletionItem(keyword)
			item.kind = CompletionItemKind.Keyword
			item.detail = 'Language keyword'
			request.addCompletion(item)
		}
	}
	
	private void addGroovyKeywords(CompletionRequest request) {
		List<String> groovyKeywords = [
				'trait', 'in', 'as'
		]
		
		groovyKeywords.each { keyword ->
			CompletionItem item = new CompletionItem(keyword)
			item.kind = CompletionItemKind.Keyword
			item.detail = 'Groovy keyword'
			request.addCompletion(item)
		}
	}
	
	private void addGrailsKeywords(CompletionRequest request) {
		List<String> grailsKeywords = [
				'constraints', 'mapping', 'belongsTo', 'hasMany', 'hasOne'
		]
		
		grailsKeywords.each { keyword ->
			CompletionItem item = new CompletionItem(keyword)
			item.kind = CompletionItemKind.Keyword
			item.detail = 'Grails keyword'
			request.addCompletion(item)
		}
	}
}