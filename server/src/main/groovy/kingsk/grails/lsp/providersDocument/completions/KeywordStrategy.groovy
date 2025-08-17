package kingsk.grails.lsp.providersDocument.completions

import groovy.transform.CompileStatic
import kingsk.grails.lsp.providersDocument.CompletionRequest
import kingsk.grails.lsp.utils.DynamicDiscoveryUtil
import org.codehaus.groovy.ast.ASTNode

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
		List<String> keywords = DynamicDiscoveryUtil.getLanguageKeywords()
		
		keywords.each { keyword ->
			//request.addCompletion(keyword, CompletionItemKind.Keyword, 'Language keyword')
		}
	}
	
	private void addGroovyKeywords(CompletionRequest request) {
		List<String> groovyKeywords = [
				'trait', 'in', 'as'
		]
		
		groovyKeywords.each { keyword ->
			//request.addCompletion(keyword, CompletionItemKind.Keyword, 'Groovy keyword')
		}
	}
	
	private void addGrailsKeywords(CompletionRequest request) {
		List<String> grailsKeywords = [
				'constraints', 'mapping', 'belongsTo', 'hasMany', 'hasOne'
		]
		
		grailsKeywords.each { keyword ->
			//request.addCompletion(keyword, CompletionItemKind.Keyword, 'Grails keyword')
		}
	}
}