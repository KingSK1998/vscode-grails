package kingsk.grails.lsp.providers.completions.strategies.snippet

import kingsk.grails.lsp.context.RequestContext

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.enums.GrailsArtifactType
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat
import kingsk.grails.lsp.utils.grails.GrailsArtefactUtils

@CompileStatic
class GrailsSnippetStrategy extends BaseCompletionStrategy {

    @Override
    boolean canHandle(CompletionRequest request, RequestContext ctx) {
        return request.isGrailsProject && request.providerContext.config.includeSnippets
    }

    @Override
    List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
        List<CompletionItem> completions = []
        
        // Derive artifact type from snapshot metadata if possible
        def currentClass = ctx.ast()?.getClassNodes(request.uri)?.find { it }
        GrailsArtifactType type = currentClass ? GrailsArtefactUtils.getGrailsArtifactType(currentClass, request.uri) : GrailsArtifactType.UNKNOWN
        
        if (type == GrailsArtifactType.CONTROLLER) {
            completions.add(createSnippet("g-action", "Grails CRUD Action", 
                "def \${1:index}(\${2:Integer max}) {\n" +
                "    params.max = Math.min(max ?: 10, 100)\n" +
                "    respond \${3:Model}.list(params), model: [\${3:model}Count: \${3:Model}.count()]\n" +
                "}\n"
            ))
            
            completions.add(createSnippet("g-render", "Grails Render View", 
                "render(view: \"\${1:viewName}\", model: [\${2:key}: \${3:value}])"
            ))

            completions.add(createSnippet("g-redirect", "Grails Redirect", 
                "redirect(action: \"\${1:index}\", params: [\${2:key}: \${3:value}])"
            ))
        } else if (type == GrailsArtifactType.SERVICE) {
            completions.add(createSnippet("g-tx", "Grails Transactional Method", 
                "@Transactional\n" +
                "def \${1:serviceMethod}(\${2:args}) {\n" +
                "    \${0}\n" +
                "}\n"
            ))
        } else if (type == GrailsArtifactType.DOMAIN) {
            completions.add(createSnippet("g-hasMany", "GORM hasMany", 
                "static hasMany = [\${1:children}: \${2:ChildClass}]"
            ))
            completions.add(createSnippet("g-constraints", "GORM Constraints", 
                "static constraints = {\n" +
                "    \${1:propertyName} \${2:nullable: true}\n" +
                "}"
            ))
        }

        // Global Grails snippets
        completions.add(createSnippet("g-log", "Grails Log Debug", "log.debug \"\${1:message}: \${\${2:variable}}\""))
        
        return completions
    }

    private CompletionItem createSnippet(String label, String detail, String snippet) {
        CompletionItem item = new CompletionItem(label)
        item.kind = CompletionItemKind.Snippet
        item.detail = detail
        item.insertText = snippet
        item.insertTextFormat = InsertTextFormat.Snippet
        return item
    }
}
