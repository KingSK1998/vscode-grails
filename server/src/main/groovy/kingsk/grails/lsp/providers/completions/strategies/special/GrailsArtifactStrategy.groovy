package kingsk.grails.lsp.providers.completions.strategies.special

import kingsk.grails.lsp.context.RequestContext
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.model.enums.GrailsArtifactType
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.utils.grails.GrailsHelperIntegration
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

/**
 * Handles Grails artifact-specific completions.
 */
@Slf4j
@CompileStatic
class GrailsArtifactStrategy extends BaseCompletionStrategy {

    @Override
    int getPriority() { return 92 }

    @Override
    CompletionTarget target() { return CompletionTarget.OFFSET }

    @Override
    boolean canHandle(CompletionRequest request, RequestContext ctx) {
        if (!request.isGrailsProject) return false
        def currentClass = ctx.ast()?.getClassNodes(request.uri)?.find { it }
        return currentClass != null
    }

    @Override
    List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
        List<CompletionItem> completions = []
        
        ClassNode currentClass = ctx.ast()?.getClassNodes(request.uri)?.find { it }
        if (!currentClass) return completions

        GrailsArtifactType artifactType = kingsk.grails.lsp.utils.grails.GrailsArtefactUtils.getGrailsArtifactType(currentClass, request.uri)
        if (!artifactType || !artifactType.valid) return completions

        switch (artifactType) {
            case GrailsArtifactType.CONTROLLER:
                addControllerCompletions(ctx, completions)
                break
            case GrailsArtifactType.DOMAIN:
                addDomainCompletions(ctx, currentClass, completions)
                break
            case GrailsArtifactType.SERVICE:
                addServiceCompletions(completions)
                break
            case GrailsArtifactType.TAGLIB:
                addTagLibCompletions(completions)
                break
        }

        addCommonGrailsCompletions(completions)
        return completions
    }

    private void addControllerCompletions(RequestContext ctx, List<CompletionItem> completions) {
        ClassLoader loader = ctx.classLoader()
        GrailsHelperIntegration.getControllerMethods(loader).each { method ->
            CompletionItem item = new CompletionItem(method)
            item.kind = CompletionItemKind.Method
            item.detail = 'Grails controller method'
            completions.add(item)
        }
        ['view', 'model', 'template', 'collection'].each { webParam ->
            CompletionItem item = new CompletionItem(webParam)
            item.kind = CompletionItemKind.Keyword
            item.detail = 'Grails parameter'
            item.detail = 'Grails controller parameter'
            completions.add(item)
        }
    }

    private void addDomainCompletions(RequestContext ctx, ClassNode currentClass, List<CompletionItem> completions) {
        ClassLoader loader = ctx.classLoader()
        GrailsHelperIntegration.getGormInstanceMethods(loader).each { method ->
            CompletionItem item = new CompletionItem(method)
            item.kind = CompletionItemKind.Method
            item.detail = 'GORM Instance Method'
            completions.add(item)
        }
        GrailsHelperIntegration.getGormStaticMethods(loader).each { method ->
            CompletionItem item = new CompletionItem(method)
            item.kind = CompletionItemKind.Method
            item.detail = 'GORM Static Method'
            completions.add(item)
        }
        // Dynamic finders
        currentClass.properties.each { prop ->
            if (prop.name != 'class') {
                String cap = prop.name.capitalize()
                ['findBy', 'findAllBy', 'countBy'].each { prefix ->
                    CompletionItem item = new CompletionItem(prefix + cap)
                    item.kind = CompletionItemKind.Method
                    item.detail = 'GORM dynamic finder'
                    completions.add(item)
                }
            }
        }
    }

    private void addServiceCompletions(List<CompletionItem> completions) {
        ['transactional'].each { prop ->
            CompletionItem item = new CompletionItem(prop)
            item.kind = CompletionItemKind.Property
            item.detail = 'Service property'
            completions.add(item)
        }
    }

    private void addTagLibCompletions(List<CompletionItem> completions) {
        ['namespace', 'out', 'request', 'response', 'session', 'params'].each { prop ->
            CompletionItem item = new CompletionItem(prop)
            item.kind = CompletionItemKind.Property
            item.detail = 'TagLib property'
            completions.add(item)
        }
    }

    private void addCommonGrailsCompletions(List<CompletionItem> completions) {
        ['grailsApplication', 'applicationContext', 'log'].each { obj ->
            CompletionItem item = new CompletionItem(obj)
            item.kind = CompletionItemKind.Variable
            item.detail = 'Grails object'
            completions.add(item)
        }
    }
}
