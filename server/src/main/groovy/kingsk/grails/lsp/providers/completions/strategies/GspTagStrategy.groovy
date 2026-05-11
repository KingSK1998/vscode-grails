package kingsk.grails.lsp.providers.completions.strategies

import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat

class GspTagStrategy extends BaseCompletionStrategy {

    GspTagStrategy(CompletionRequest request) {
        super(request)
    }

    @Override
    boolean canHandle(ASTNode node) {
        return request.textFile.uri.endsWith(".gsp")
    }

    @Override
    void provideCompletions(ASTNode node) {
        String prefix = request.prefix
        if (!prefix) return

        // Grails default tags (g:)
        addTag('g:if', 'Checks conditions', 'g:if test="${${1:condition}}">\n\t${0}\n</g:if>')
        addTag('g:else', 'Alternative to g:if', 'g:else>\n\t${0}\n</g:else>')
        addTag('g:elseif', 'Alternative check', 'g:elseif test="${${1:condition}}">\n\t${0}\n</g:elseif>')
        addTag('g:each', 'Iterates over a collection', 'g:each in="${${1:items}}" var="${2:it}">\n\t${0}\n</g:each>')
        addTag('g:set', 'Sets a variable', 'g:set var="${1:varName}" value="${${2:value}}" />')
        addTag('g:render', 'Renders a template or view', 'g:render template="${1:templateName}" model="[${2:key}: ${3:value}]" />')
        addTag('g:link', 'Creates an HTML link', 'g:link controller="${1:controllerName}" action="${2:actionName}" id="${${3:id}}">${4:Text}</g:link>')
        addTag('g:message', 'Resolves i18n messages', 'g:message code="${1:message.code}" />')
        addTag('g:javascript', 'Includes javascript', 'g:javascript src="${1:script}.js" />')
        addTag('g:img', 'Renders an image', 'g:img dir="images" file="${1:logo}.png" />')
        addTag('g:form', 'Creates an HTML form', 'g:form controller="${1:controller}" action="${2:save}" method="POST">\n\t${0}\n</g:form>')
        addTag('g:textField', 'Renders a text input', 'g:textField name="${1:fieldName}" value="${${2:value}}" />')
        addTag('g:submitButton', 'Renders a submit button', 'g:submitButton name="${1:btnName}" value="${message(code: \'${2:default.button.label}\')}" />')
        addTag('g:applyLayout', 'Applies Sitemesh layout', 'g:applyLayout name="${1:main}">\n\t${0}\n</g:applyLayout>')
        addTag('g:layoutTitle', 'Gets layout title', '<title><g:layoutTitle default="${1:My Title}" /></title>')
        addTag('g:layoutHead', 'Gets layout head', '<g:layoutHead />')
        addTag('g:layoutBody', 'Gets layout body', '<g:layoutBody />')
    }

    private void addTag(String label, String detail, String snippet) {
        CompletionItem item = new CompletionItem(label)
        item.kind = CompletionItemKind.Property
        item.detail = "GSP Tag: ${detail}"
        item.insertText = snippet
        item.insertTextFormat = InsertTextFormat.Snippet
        request.addCompletion(item)
    }
}
