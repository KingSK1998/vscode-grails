package kingsk.grails.lsp.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.MethodCallExpression

import java.lang.reflect.Type

@Slf4j
@CompileStatic
class TypeInferenceService {
	
	//===== Inlay Hints ========
	
	static ClassNode inferParameterType(Parameter param) {
		// This would require more complex analysis of method body
		// For now, return null to indicate we can't infer the type
		return null
	}
	
	static ClassNode inferDomainPropertyType(def property) {
		// This would require analyzing constraints block
		// For now, return null to indicate we can't infer the type
		return null
	}
	
	static ClassNode inferControllerActionReturnType(MethodNode method) {
		// This would require analyzing render/respond calls
		// For now, return null to indicate we can't infer the type
		return null
	}
	
	static ClassNode inferGormDynamicFinderReturnType(MethodCallExpression methodCall, GrailsASTVisitor visitor) {
		// This would require parsing the method name to determine domain class
		// For now, return null to indicate we can't infer the type
		return null
	}
	
	//TODO: ============ REFACTORING ====================
	
	/**
	 * Infers the type of the given AST node.</br>
	 * Resolve types of variables, method calls, properties, and other expressions.
	 * @param node The AST node for which the type should inferred
	 * @return the inferred ClassNode representing the type, or null if unknown
	 */
	static ClassNode inferType(ASTNode node, GrailsASTVisitor visitor) {
		// TODO: Implement actual inference logic, e.g.
		// - if node is VariableExpression, look up variable declaration type
		// - if node is MethodCallExpression, resolve method return type
		// - if node is PropertyExpression, resolve property type
		// - else return node.getType()
		return GrailsASTHelper.getTypeOfNode(node, visitor)
	}
	
	static List<MethodNode> findMethodsByName(Type type, String methodName) {
	
	}
	
	static List<PropertyNode> findProperties(Type type) {
	
	}
	
	static boolean isSubType(Type subType, Type superType) {
	
	}
}
