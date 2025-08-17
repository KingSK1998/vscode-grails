package kingsk.grails.lsp.providersDocument.completions

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.providersDocument.CompletionRequest
import kingsk.grails.lsp.utils.ASTUtils
import kingsk.grails.lsp.utils.GrailsUtils
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.PropertyExpression

/**
 * Handles completions for property expressions (obj.prop|)
 * Provides smart, detailed completions for object member access
 */
@CompileStatic
class PropertyExpressionStrategy extends BaseCompletionStrategy {
	
	PropertyExpressionStrategy(CompletionRequest request) { super(request) }
	
	@Override
	int getPriority() { return 90 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.BOTH }
	
	@Override
	boolean canHandle(ASTNode node) { node instanceof PropertyExpression }
	
	/**
	 * Handles chained property access, e.g. <code>foo.bar</code>
	 * @param node The PropertyExpression node to handle
	 */
	@Override
	void provideCompletions(ASTNode node) { provideCompletions(node as PropertyExpression) }
	
	protected void provideCompletions(PropertyExpression propExpr) {
		// Cursor might land on a PropertyExpression, or the parent could be one if cursor is in .foo.
		logDebug("Providing completions for property expression: %s", propExpr.text)
		
		Expression objectExpression = propExpr.objectExpression
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
		
		// Add Grails-specific completions if in Grails project
		if (request.isGrailsProject) {
			addGrailsSpecificCompletions(objectType)
		}
	}
	
	/**
	 * Add Grails-specific completions based on object type using existing GrailsUtils detection
	 */
	private void addGrailsSpecificCompletions(ClassNode objectType) {
		// Automatic handling of Grails injected properties such as param, request, response, etc
		if (GrailsUtils.isGrailsArtefact(objectType, request.file.uri)) {
			getClassMembersAsASTNodes(objectType).each { node ->
				if (!ASTUtils.isInvalidDocumentSymbol(node)) request.addCompletion(node)
			}
		}
	}
}