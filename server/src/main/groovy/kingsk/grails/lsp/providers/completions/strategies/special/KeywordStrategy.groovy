package kingsk.grails.lsp.providers.completions.strategies.special

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

/**
 * Handles basic Groovy keyword completions
 */
@CompileStatic
class KeywordStrategy extends BaseCompletionStrategy {

    @Override
    int getPriority() { return 10 }

    @Override
    CompletionTarget target() { return CompletionTarget.OFFSET }

    @Override
    boolean canHandle(CompletionRequest request, RequestContext ctx) {
        return true // Fallback
    }

    @Override
    List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
        List<CompletionItem> completions = []
        GrailsUtils.GROOVY_KEYWORDS.each { String kw ->
            CompletionItem item = new CompletionItem(kw)
            item.kind = CompletionItemKind.Keyword
            completions.add(item)
        }
        return completions
    }
}
