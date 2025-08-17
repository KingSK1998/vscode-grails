package kingsk.grails.lsp.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ArgumentListExpression

@Slf4j
@CompileStatic
class GrailsASTHelperUtils {
	
	/**
	 * Calculates the score for matching parameters and argument expressions.
	 * A higher score indicates a better match.
	 *
	 * @param parameters The array of method parameters
	 * @param arguments The argument list expression
	 * @param argIndex The index of the argument to consider, defaults to -1
	 * @return The calculated score for the arguments and parameters match
	 */
	static int calculateArgumentsScore(Parameter[] parameters, ArgumentListExpression arguments, int argIndex) {
		int score = 0
		int paramCount = parameters.size()
		int expressionsCount = arguments.expressions.size()
		int argsCount = (argIndex >= expressionsCount) ? argIndex + 1 : expressionsCount
		int minCount = Math.min(paramCount, argsCount)
		if (minCount == 0 && paramCount == argsCount) score++
		
		for (i in 0..<minCount) {
			ClassNode argType = (i < expressionsCount) ? arguments.expressions[i].type : null
			ClassNode paramType = (i < paramCount) ? parameters[i].type : null
			if (argType && paramType) {
				// equal types are preferred
				if (argType == paramType) score += 1000
				// subtypes are nice, but less important
				else if (argType.isDerivedFrom(paramType)) score += 100
				// if a type doesn't match at all, it's not worth much
				else score++
			} else if (paramType) {
				score++ // extra parameters are like a type not matching
			}
		}
		return score
	}
	
	/**
	 * Find the best matching method node based on the arguments and index
	 * @param methodNodes The list of method nodes
	 * @param arguments The argument list expression
	 * @param argIndex The argument index, defaults to -1
	 * @return {@link org.codehaus.groovy.ast.MethodNode} - The best matching method node or {@code null}
	 */
	static MethodNode findBestMatchingMethodNode(List<MethodNode> methodNodes, ArgumentListExpression arguments, int argIndex = -1) {
		if (!(methodNodes || arguments)) {
			log.info "methodNodes and arguments should not be null"
			return null
		}
		
		return methodNodes.max { m1, m2 ->
			int m1Value = calculateArgumentsScore(m1.parameters, arguments, argIndex)
			int m2Value = calculateArgumentsScore(m2.parameters, arguments, argIndex)
			m1Value <=> m2Value // Spaceship Operator: Used <=> for comparing argument scores.
		}
	}
	
	// ======================= Compare Line And Columns =============================== //
	
	static boolean compareMethodNodeNameLineAndColumn(MethodNode node1, MethodNode node2) {
		if (!node1) return false
		if (!node2) return false
		if (node1.name != node2.name) return false
		if (node1.declaringClass != node2.declaringClass) return false
		return compareLineNumbersAndColumnNumbers(node1, node2)
	}
	
	static boolean compareFieldNodeNameLineAndColumn(FieldNode node1, FieldNode node2) {
		if (!node1) return false
		if (!node2) return false
		if (node1.name != node2.name) return false
		if (node1.originType != node2.originType) return false
		if (node1.owner != node2.owner) return false
		return compareLineNumbersAndColumnNumbers(node1, node2)
	}
	
	static boolean compareLineNumbersAndColumnNumbers(ASTNode node1, ASTNode node2) {
		if (node1.lineNumber != node2.lineNumber) return false
		if (node1.columnNumber != node2.columnNumber) return false
		if (node1.lastLineNumber != node2.lastLineNumber) return false
		if (node1.lastColumnNumber != node2.lastColumnNumber) return false
		return true
	}
}
