package kingsk.grails.lsp.providersDocument.completions

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.providersDocument.CompletionRequest
import kingsk.grails.lsp.utils.GrailsUtils
import org.codehaus.groovy.ast.ASTNode

/**
 * Clean completion builder that manages strategies internally.
 */
@Slf4j
@CompileStatic
class CompletionBuilder {
	
	// Strategy classes - instantiated with request injection per completion
	private static final List<Class<? extends BaseCompletionStrategy>> STRATEGY_CLASSES = [
			ImportStrategy, // {OFFSET} {95 - Very High - import statements are specific}
			PropertyExpressionStrategy, // {BOTH} {90 - High - Very specific context}
			GrailsArtifactStrategy, // {OFFSET - 92 - High - Grails-specific} - Grails-specific completions
			AnnotationStrategy, // {OFFSET - 88 - High - annotation specific} - @Controller, @Service, etc.
			MethodCallExpressionStrategy, // {BOTH} {85 - High - Method calls and constructors}
			ArgumentListStrategy, // {BOTH - 82 - High - method argument} - method argument completion
			ClassNodeStrategy, // {OFFSET} {80 - High - type completions}
			DeclarationExpressionStrategy, // {BOTH - 75 - Medium - High} - variable declarations, assignments
			VariableExpressionStrategy, // {OFFSET} {70 - Medium - High}
			MethodNodeStrategy, // {OFFSET - 60 - Medium - method signatures, parameters}
			ScopeStrategy, // {BOTH - 30 - Low - fallback strategy}
	]
	
	/**
	 * Build completions using two clear phases:
	 *   1. OFFSET-targeted strategies against request.offsetNode
	 *   2. If none produced results, PARENT-targeted strategies against request.parentNode
	 */
	static void buildCompletions(CompletionRequest request) {
		if (!request?.offsetNode) return
		
		// Create strategy instances with request injection
		List<BaseCompletionStrategy> strategies = STRATEGY_CLASSES.collect { strategyClass ->
			strategyClass.newInstance(request)
		}
		
		// Phase 1: OFFSET
		boolean anyOffset = applyPhase(strategies, CompletionTarget.OFFSET, request.offsetNode, true)
		
		// Phase 2: PARENT fallback
		if (!anyOffset) {
			applyPhase(strategies, CompletionTarget.PARENT, request.parentNode, false)
		}
	}
	
	/**
	 * Runs all strategies whose target matches the given phase, against the given node.
	 * Returns true if any completions were generated.
	 */
	private static boolean applyPhase(List<BaseCompletionStrategy> strategies, CompletionTarget phase, ASTNode node, boolean skipDummyPrefix) {
		if (node == null) return false
		
		boolean produced = strategies.findAll { it.target().matches(phase) }
				.any { strategy ->
					try {
						boolean isDummy = skipDummyPrefix && GrailsUtils.isDummyPrefix(strategy.request.prefix)
						if (strategy.canHandle(node) && !isDummy) {
							strategy.provideCompletions(node)
							return true
						}
					} catch (Exception e) {
						log.error("[COMPLETION] {} failed on {}: {}",
								strategy.class.simpleName, phase, e.message, e)
					}
				} ?: false
		return produced
	}
}