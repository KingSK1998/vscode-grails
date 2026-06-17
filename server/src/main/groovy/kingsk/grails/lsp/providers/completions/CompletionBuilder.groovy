package kingsk.grails.lsp.providers.completions

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.RequestContext
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
import org.eclipse.lsp4j.CompletionItem

@Slf4j
@CompileStatic
class CompletionBuilder {

    private static final List<CompletionStrategy> STRATEGIES = [
        new ImportStrategy(),
        new PropertyExpressionStrategy(),
        new GrailsArtifactStrategy(),
        new GrailsInjectedStrategy(),
        new NamedParameterStrategy(),
        new AnnotationStrategy(),
        new MethodCallExpressionStrategy(),
        new ArgumentListStrategy(),
        new ClassNodeStrategy(),
        new DeclarationExpressionStrategy(),
        new ClosureDelegateStrategy(),
        new VariableExpressionStrategy(),
        new MethodNodeStrategy(),
        new GrailsSnippetStrategy(),
        new GspTagStrategy(),
        new ScopeStrategy()
    ]

    static List<CompletionItem> buildCompletions(CompletionRequest request, RequestContext ctx) {
        if (!request?.offsetNode) return []

        List<CompletionItem> allItems = []
        Set<String> seen = new HashSet<>()

        // Phase 1: OFFSET
        applyPhase(STRATEGIES, CompletionTarget.OFFSET, request, ctx, allItems, seen)

        // Phase 2: PARENT fallback if empty
        if (allItems.isEmpty() && request.parentNode) {
            applyPhase(STRATEGIES, CompletionTarget.PARENT, request, ctx, allItems, seen)
        }

        return allItems
    }

    private static void applyPhase(List<CompletionStrategy> strategies, CompletionTarget target, 
                                     CompletionRequest request, RequestContext ctx, 
                                     List<CompletionItem> allItems, Set<String> seen) {
        
        strategies.findAll { strategy ->
            if (strategy instanceof BaseCompletionStrategy) {
                return ((BaseCompletionStrategy) strategy).target() == target || ((BaseCompletionStrategy) strategy).target() == CompletionTarget.BOTH
            }
            return true
        }.sort { -it.priority }.each { strategy ->
            try {
                if (strategy.canHandle(request, ctx)) {
                    List<CompletionItem> items = strategy.provideCompletions(request, ctx)
                    if (items) {
                        items.each { item ->
                            if (!seen.contains(item.label)) {
                                allItems.add(item)
                                seen.add(item.label)
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[COMPLETION] Strategy ${strategy.class.simpleName} failed: ${e.message}")
            }
        }
    }
}
