package kingsk.grails.lsp.providersDocument.completions

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.providersDocument.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ImportNode

/**
 * Handles completions for import statements
 */
@CompileStatic
class ImportStrategy extends BaseCompletionStrategy {
	
	ImportStrategy(CompletionRequest request) { super(request) }
	
	@Override
	int getPriority() { return 95 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.OFFSET }
	
	@Override
	boolean canHandle(ASTNode node) { return node instanceof ImportNode }
	
	/**
	 * Inside import statements like <code>import java.ut</code>
	 * @param node The ImportNode node to complete
	 */
	@Override
	void provideCompletions(ASTNode node) {
		// Directly over import keyword or target; no parent relevance.
		addPackageCompletions(request)
		addClassCompletions(request)
		if (request.isGrailsProject) {
			addGrailsImportCompletions(request)
		}
	}
	
	private void addPackageCompletions(CompletionRequest request) {
		List<String> commonPackages = [
				'java.lang', 'java.util', 'java.io', 'java.net',
				'groovy.lang', 'groovy.util', 'groovy.transform'
		]
		
		if (request.isGrailsProject) {
			commonPackages.addAll([
					'grails.', 'org.grails.', 'grails.web.', 'grails.gorm.'
			])
		}
		
		commonPackages.each { pkg ->
			//request.addCompletion(pkg, CompletionItemKind.Module, 'Package')
		}
	}
	
	private void addClassCompletions(CompletionRequest request) {
		// Add common Java/Groovy classes
		List<String> commonClasses = [
				'String', 'List', 'Map', 'Set', 'Date', 'BigDecimal', 'Pattern'
		]
		
		commonClasses.each { cls ->
			//request.addCompletion(cls, CompletionItemKind.Class, 'Common class')
		}
	}
	
	private void addGrailsImportCompletions(CompletionRequest request) {
		List<String> grailsImports = [
				'grails.validation.Validateable',
				'grails.artefact.Controller',
				'grails.gorm.transactions.Transactional',
				'grails.util.GrailsNameUtils'
		]
		
		grailsImports.each { cls ->
			//request.addCompletion(cls, CompletionItemKind.Class, 'Grails class')
		}
	}
}