package kingsk.grails.lsp.providers.completions

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.strategies.special.ImportStrategy
import kingsk.grails.lsp.providers.completions.strategies.context.PropertyExpressionStrategy
import kingsk.grails.lsp.providers.completions.strategies.snippet.NamedParameterStrategy
import kingsk.grails.lsp.providers.completions.strategies.special.AnnotationStrategy
import kingsk.grails.lsp.providers.completions.strategies.context.MethodCallExpressionStrategy
import kingsk.grails.lsp.providers.completions.strategies.snippet.ArgumentListStrategy
import kingsk.grails.lsp.providers.completions.strategies.type.ClassNodeStrategy
import kingsk.grails.lsp.providers.completions.strategies.context.DeclarationExpressionStrategy
import kingsk.grails.lsp.providers.completions.strategies.context.ClosureDelegateStrategy
import kingsk.grails.lsp.providers.completions.strategies.context.VariableExpressionStrategy
import kingsk.grails.lsp.providers.completions.strategies.type.MethodNodeStrategy
import kingsk.grails.lsp.providers.completions.strategies.snippet.GrailsSnippetStrategy
import kingsk.grails.lsp.providers.completions.strategies.special.GspTagStrategy
import kingsk.grails.lsp.providers.completions.strategies.context.ScopeStrategy
import kingsk.grails.lsp.providers.completions.strategies.special.GrailsArtifactStrategy
import kingsk.grails.lsp.providers.completions.strategies.context.GrailsInjectedStrategy
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.codehaus.groovy.ast.ASTNode

/**
 * Clean completion builder that manages strategies internally.
 */
@Slf4j
@CompileStatic
class CompletionBuilder {

    // Strategy classes - instantiated with request injection per completion
    // Organized by category: special, context, snippet, type
    private static final List<Class<? extends BaseCompletionStrategy>> STRATEGY_CLASSES = [
        ImportStrategy, // {OFFSET} {95 - Very High - import statements are specific}
        PropertyExpressionStrategy, // {BOTH} {90 - High - Very specific context}
        GrailsArtifactStrategy, // {OFFSET} {92}
        GrailsInjectedStrategy, // {OFFSET} {70}
        NamedParameterStrategy, // {BOTH - 85}
        AnnotationStrategy, // {OFFSET - 88 - High - annotation specific} - @Controller, @Service, etc.
        MethodCallExpressionStrategy, // {BOTH} {85 - High - Method calls and constructors}
        ArgumentListStrategy, // {BOTH - 82 - High - method argument} - method argument completion
        ClassNodeStrategy, // {OFFSET} {80 - High - type completions}
        DeclarationExpressionStrategy, // {BOTH - 75 - Medium - High} - variable declarations, assignments
        ClosureDelegateStrategy, // {OFFSET} {65}
        VariableExpressionStrategy, // {OFFSET} {70 - Medium - High}
        MethodNodeStrategy, // {OFFSET - 60 - Medium - method signatures, parameters}
        GrailsSnippetStrategy, // {OFFSET - 55 - Medium - snippets}
        GspTagStrategy, // {OFFSET - 50 - Medium - GSP tags}
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

        boolean produced = false
        strategies.findAll { it.target().matches(phase) }
            .each { strategy ->
                try {
                    boolean isDummy = skipDummyPrefix && GrailsUtils.isDummyPrefix(strategy.request.prefix)
                    log.info("[COMPLETION] Checking strategy ${strategy.class.simpleName}: canHandle=${strategy.canHandle(node)}")
                    if (strategy.canHandle(node) && !isDummy) {
                        int sizeBefore = strategy.request.items.size()
                        log.info("[COMPLETION] Executing strategy ${strategy.class.simpleName}")
                        strategy.provideCompletions(node)
                        log.info("[COMPLETION] Strategy ${strategy.class.simpleName} added ${strategy.request.items.size() - sizeBefore} items")
                        if (strategy.request.items.size() > sizeBefore) {
                            produced = true
                        }
                    }
                } catch (Exception e) {
                    log.error("[COMPLETION] {} failed on {}: {}",
                        strategy.class.simpleName, phase, e.message, e)
                }
            }
        return produced
    }
}