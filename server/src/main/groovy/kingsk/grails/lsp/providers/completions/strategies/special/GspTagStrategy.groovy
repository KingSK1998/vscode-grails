package kingsk.grails.lsp.providers.completions.strategies.special

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.core.capability.TagLibCapabilityAdapter
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import org.codehaus.groovy.ast.ASTNode

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

/**
 * Handles completions for GSP tags in Groovy context (e.g. g.link)
 */
@CompileStatic
class GspTagStrategy extends BaseCompletionStrategy {

    @Override
    int getPriority() { return 50 }

    @Override
    CompletionTarget target() { return CompletionTarget.OFFSET }

    @Override
    boolean canHandle(CompletionRequest request, RequestContext ctx) {
        // Very basic heuristic for now
        return request.prefix?.startsWith('g.')
    }

    @Override
    List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
        List<CompletionItem> completions = []
        if (request.isGrailsProject) {
            ['link', 'message', 'form', 'submitButton', 'textField', 'select', 'each', 'if', 'else', 'elseif', 'render', 'include'].each { tag ->
                CompletionItem item = new CompletionItem(tag)
                item.kind = CompletionItemKind.Method
                item.detail = 'GSP Tag'
                completions.add(item)
            }
        }
        return completions
        if (!request.isGrailsProject) return []
        return TagLibCapabilityAdapter.INSTANCE.getTagCompletions("g", ctx)
    }
}
