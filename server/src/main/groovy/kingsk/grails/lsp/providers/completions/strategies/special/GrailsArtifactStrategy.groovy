package kingsk.grails.lsp.providers.completions.strategies.special

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.core.capability.GormCapabilityAdapter
import kingsk.grails.lsp.core.capability.GrailsInjectionCapabilityAdapter
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.model.enums.GrailsArtifactType
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.utils.grails.GrailsArtefactUtils

import org.codehaus.groovy.ast.ClassNode

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
        ClassNode currentClass = ctx.ast()?.getClassNodes(request.uri)?.find { it }
        return currentClass != null
    }

    @Override
    List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
        List<CompletionItem> completions = []
        
        ClassNode currentClass = ctx.ast()?.getClassNodes(request.uri)?.find { it }
        if (!currentClass) return completions

        GrailsArtifactType artifactType = GrailsArtefactUtils.getGrailsArtifactType(currentClass, request.uri)
        if (!artifactType || !artifactType.valid) return completions

        switch (artifactType) {
            case GrailsArtifactType.CONTROLLER:
                addControllerCompletions(completions)
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

    private void addControllerCompletions(List<CompletionItem> completions) {
        completions.addAll(GrailsInjectionCapabilityAdapter.INSTANCE.getControllerMethods())
        for (String webParam : ['view', 'model', 'text', 'status', 'template', 'collection', 'contentType']) {
            CompletionItem item = new CompletionItem(webParam)
            item.kind = CompletionItemKind.Keyword
            item.detail = 'Grails controller parameter / Grails controller method'
            completions.add(item)
        }
    }

    private void addDomainCompletions(RequestContext ctx, ClassNode currentClass, List<CompletionItem> completions) {
        if (GormCapabilityAdapter.INSTANCE.isApplicable(ctx, ctx?.uri())) {
            completions.addAll(GormCapabilityAdapter.INSTANCE.getInstanceCompletions(currentClass, ctx))
            completions.addAll(GormCapabilityAdapter.INSTANCE.getStaticCompletions(currentClass, ctx))
        }
    }

    private void addServiceCompletions(List<CompletionItem> completions) {
        for (String prop : ['transactional']) {
            CompletionItem item = new CompletionItem(prop)
            item.kind = CompletionItemKind.Property
            item.detail = 'Service property'
            completions.add(item)
        }
    }

    private void addTagLibCompletions(List<CompletionItem> completions) {
        for (String prop : ['namespace', 'out', 'request', 'response', 'session', 'params', 'flash', 'grailsApplication']) {
            CompletionItem item = new CompletionItem(prop)
            item.kind = CompletionItemKind.Property
            item.detail = 'TagLib property'
            completions.add(item)
        }
    }

    private void addCommonGrailsCompletions(List<CompletionItem> completions) {
        for (String obj : ['grailsApplication', 'applicationContext', 'log']) {
            CompletionItem item = new CompletionItem(obj)
            item.kind = CompletionItemKind.Variable
            item.detail = 'Grails object'
            completions.add(item)
        }
    }
}
