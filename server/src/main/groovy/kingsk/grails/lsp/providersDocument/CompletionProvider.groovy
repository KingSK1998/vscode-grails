package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic

/**
 * Base trait for all completion providers
 * Keeps providers very small and focused
 */
@CompileStatic
trait CompletionProvider {
	
	/**
	 * Check if this provider can handle the given context
	 * @param request The completion request context
	 * @return true if this provider should be used
	 */
	abstract boolean canHandle(CompletionRequest request)
	
	/**
	 * Provide completions for the given context
	 * @param request The completion request context
	 */
	abstract void provideCompletions(CompletionRequest request)
	
	/**
	 * Priority for this provider (higher = earlier execution)
	 * Default is 0, override for specific ordering needs
	 */
	int getPriority() { 0 }
	
	/**
	 * Whether this provider should run for offset node
	 */
	boolean handlesOffsetNode() { true }
	
	/**
	 * Whether this provider should run for parent node
	 */
	boolean handlesParentNode() { false }
}