package kingsk.grails.lsp.providersDocument.completions

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.providersDocument.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression

/**
 * Handles completions for method calls (obj.method(|))
 * Provides smart argument completions and method-specific suggestions
 */
@CompileStatic
class MethodCallExpressionStrategy extends BaseCompletionStrategy {
	
	MethodCallExpressionStrategy(CompletionRequest request) { super(request) }
	
	@Override
	int getPriority() { return 85 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.BOTH }
	
	@Override
	boolean canHandle(ASTNode node) { return node instanceof MethodCallExpression }
	
	/**
	 * Handles method or constructor calls like <code>service.save()</code>
	 * @param node The MethodCallExpression to process
	 */
	@Override
	void provideCompletions(ASTNode node) {
		provideCompletion(node as MethodCallExpression)
	}
	
	protected void provideCompletion(MethodCallExpression methodCall) {
		if (!methodCall) return
		
		logDebug("Providing completions for method call: %s", methodCall.methodAsString)
		
		Expression objectExpression = methodCall.objectExpression
		if (!objectExpression) {
			logDebug("No object expression found")
			return
		}
		
		// Get the type of the object being accessed
		ClassNode objectType = getTypeOf(objectExpression)
		if (!objectType) {
			logDebug("Could not determine object type for: %s", objectExpression.text)
			return
		}
		
		logDebug("Object type resolved to: %s", objectType.name)
		
		// Use MemberExtractor as primary source for all member completions, with superclasses, interfaces i.e. till Metaclass
		addMemberCompletions(objectExpression)
	}
}