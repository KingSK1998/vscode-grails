package kingsk.grails.lsp.providers.completions.strategies.type

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.eclipse.lsp4j.CompletionItem

/**
 * Handles completions for constructor calls
 */
@CompileStatic
class ConstructorStrategy extends BaseCompletionStrategy {
	
	@Override
	CompletionTarget target() { return CompletionTarget.PARENT }
	
	@Override
	int getPriority() { return 75 }
	
	@Override
	boolean canHandle(CompletionRequest request, RequestContext ctx) {
		request.offsetNode instanceof ConstructorCallExpression
	}
	
	@Override
	List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
        List<CompletionItem> completions = []
		ConstructorCallExpression constructorCall = (ConstructorCallExpression) request.offsetNode
		ClassNode constructorType = constructorCall.type
		
		if (!constructorType) return completions
		
		addConstructorParameterCompletions(constructorType, completions)
		addNamedParameterCompletions(constructorType, completions)
		
		return completions
	}
	
	private void addConstructorParameterCompletions(ClassNode constructorType, List<CompletionItem> completions) {
		constructorType.declaredConstructors?.each { ConstructorNode constructor ->
			constructor.parameters?.each { param ->
				completions.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(param))
			}
		}
	}
	
	private void addNamedParameterCompletions(ClassNode constructorType, List<CompletionItem> completions) {
		constructorType.properties?.each { property ->
			completions.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(property))
		}
	}
}
