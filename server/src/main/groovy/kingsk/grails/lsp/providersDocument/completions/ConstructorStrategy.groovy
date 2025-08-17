package kingsk.grails.lsp.providersDocument.completions

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.providersDocument.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.expr.ConstructorCallExpression

/**
 * Handles completions for constructor calls
 */
@CompileStatic
class ConstructorStrategy extends BaseCompletionStrategy {
	
	ConstructorStrategy(CompletionRequest request) { super(request) }
	
	@Override
	CompletionTarget target() { return CompletionTarget.PARENT }
	
	@Override
	int getPriority() { return 75 }
	
	@Override
	boolean canHandle(ASTNode node) {
		if (node instanceof ConstructorCallExpression) return true
		return node instanceof ClassNode && node.declaredConstructors.any {
			// Additional logic to determine if cursor is in constructor context
			!it.synthetic
		}
	}
	
	@Override
	void provideCompletions(ASTNode node) {
		// Offset is often ClassNode, but context matters (in constructor).
		ConstructorCallExpression constructorCall = (ConstructorCallExpression) node
		ClassNode constructorType = constructorCall.type
		
		if (!constructorType) return
		
		// Add constructor parameter completions
		addConstructorParameterCompletions(constructorType, request)
		
		// Add named parameter completions for Groovy-style constructors
		addNamedParameterCompletions(constructorType, request)
		
		// Add Grails-specific constructor completions
		if (request.isGrailsProject) {
			addGrailsConstructorCompletions(constructorType, request)
		}
	}
	
	private void addConstructorParameterCompletions(ClassNode constructorType, CompletionRequest request) {
		constructorType.declaredConstructors?.each { ConstructorNode constructor ->
			constructor.parameters?.each { param ->
				request.addCompletion(param)
			}
		}
	}
	
	private void addNamedParameterCompletions(ClassNode constructorType, CompletionRequest request) {
		// Add property-based named parameters
		constructorType.properties?.each { property ->
			request.addCompletion(property)
		}
	}
	
	private void addGrailsConstructorCompletions(ClassNode constructorType, CompletionRequest request) {
		// Add domain-specific constructor completions
		if (constructorType.name.endsWith('Domain')) {
			List<String> domainParams = ['id', 'version']
			domainParams.each { param ->
				//request.addCompletion(param)
			}
		}
	}
}