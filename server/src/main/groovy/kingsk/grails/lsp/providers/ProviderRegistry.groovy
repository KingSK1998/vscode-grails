package kingsk.grails.lsp.providers

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.providers.document.*
import kingsk.grails.lsp.providers.workspace.*

import java.util.concurrent.ConcurrentHashMap

import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.context.CompilationContext
import kingsk.grails.lsp.context.ProjectContext

@Slf4j
@CompileStatic
class ProviderRegistry {
    private final ProviderContext providerContext
    private final CompilationContext compilationContext
    private final ProjectContext projectContext
    private final Map<Class<?>, Object> providers = new ConcurrentHashMap<>()

    ProviderRegistry(ProviderContext providerContext, CompilationContext compilationContext, ProjectContext projectContext) {
        this.providerContext = providerContext
        this.compilationContext = compilationContext
        this.projectContext = projectContext
    }

    @SuppressWarnings("unchecked")
    def <T> T getProvider(Class<T> type) {
        Object provider = providers.get(type)
        if (provider == null) {
            provider = createProvider(type)
            if (provider != null) {
                providers.put(type, provider)
            }
        }
        return (T) provider
    }

    private Object createProvider(Class<?> type) {
        log.debug("[REGISTRY] Creating provider: ${type.simpleName}")

        if (type == GrailsCompletionProvider) return new GrailsCompletionProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsHoverProvider) return new GrailsHoverProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsDefinitionProvider) return new GrailsDefinitionProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsTypeDefinitionProvider) return new GrailsTypeDefinitionProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsImplementationProvider) return new GrailsImplementationProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsFormattingProvider) return new GrailsFormattingProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsFoldingRangeProvider) return new GrailsFoldingRangeProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsCodeActionProvider) return new GrailsCodeActionProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsReferenceProvider) return new GrailsReferenceProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsSignatureHelpProvider) return new GrailsSignatureHelpProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsDocumentSymbolProvider) return new GrailsDocumentSymbolProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsCodeLensProvider) return new GrailsCodeLensProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsInlayHintProvider) return new GrailsInlayHintProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsRenameProvider) return new GrailsRenameProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsSemanticTokensProvider) return new GrailsSemanticTokensProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsYamlIntelligenceProvider) return new GrailsYamlIntelligenceProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsGormSqlProvider) return new GrailsGormSqlProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsDependencyProvider) return new GrailsDependencyProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsTestDiscoveryProvider) return new GrailsTestDiscoveryProvider(providerContext, compilationContext, projectContext)
        if (type == GrailsWorkspaceSymbolProvider) return new GrailsWorkspaceSymbolProvider(providerContext, compilationContext, projectContext)

        log.warn("[REGISTRY] Unknown provider type: ${type.name}")
        null
    }

    Map<String, Object> getStats() {
        [
            registeredProviders: providers.size(),
            providerTypes: providers.keySet()*.simpleName
        ]
    }
}