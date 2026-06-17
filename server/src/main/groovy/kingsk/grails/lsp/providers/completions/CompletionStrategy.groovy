package kingsk.grails.lsp.providers.completions

import kingsk.grails.lsp.context.RequestContext
import org.eclipse.lsp4j.CompletionItem

interface CompletionStrategy {
    /**
     * Determines if this strategy can handle the given completion request.
     */
    boolean canHandle(CompletionRequest request, RequestContext ctx)

    /**
     * Generates a list of completion items for the given request.
     * Strategies should be stateless and return a new list.
     */
    List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx)

    /**
     * Returns the priority of this strategy. Higher priority strategies
     * are evaluated first and their results may influence others.
     */
    int getPriority()
}
