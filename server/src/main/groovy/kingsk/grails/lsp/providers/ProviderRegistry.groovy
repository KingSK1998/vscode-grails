package kingsk.grails.lsp.providers

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.providers.document.*
import kingsk.grails.lsp.providers.workspace.*

import java.util.concurrent.ConcurrentHashMap

@Slf4j
@CompileStatic
class ProviderRegistry {
    private final GrailsService service
    private final Map<Class<?>, Object> providers = new ConcurrentHashMap<>()

    ProviderRegistry(GrailsService service) {
        this.service = service
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

        if (type == GrailsCompletionProvider) return new GrailsCompletionProvider(service)
        if (type == GrailsHoverProvider) return new GrailsHoverProvider(service)
        if (type == GrailsDefinitionProvider) return new GrailsDefinitionProvider(service)
        if (type == GrailsTypeDefinitionProvider) return new GrailsTypeDefinitionProvider(service)
        if (type == GrailsImplementationProvider) return new GrailsImplementationProvider(service)
        if (type == GrailsFormattingProvider) return new GrailsFormattingProvider(service)
        if (type == GrailsFoldingRangeProvider) return new GrailsFoldingRangeProvider(service)
        if (type == GrailsCodeActionProvider) return new GrailsCodeActionProvider(service)
        if (type == GrailsReferenceProvider) return new GrailsReferenceProvider(service)
        if (type == GrailsSignatureHelpProvider) return new GrailsSignatureHelpProvider(service)
        if (type == GrailsDocumentSymbolProvider) return new GrailsDocumentSymbolProvider(service)
        if (type == GrailsCodeLensProvider) return new GrailsCodeLensProvider(service)
        if (type == GrailsInlayHintProvider) return new GrailsInlayHintProvider(service)
        if (type == GrailsRenameProvider) return new GrailsRenameProvider(service)
        if (type == GrailsSemanticTokensProvider) return new GrailsSemanticTokensProvider(service)
        if (type == GrailsYamlIntelligenceProvider) return new GrailsYamlIntelligenceProvider(service)
        if (type == GrailsGormSqlProvider) return service.gormSqlProvider
        if (type == GrailsDependencyProvider) return service.dependencyProvider
        if (type == GrailsTestDiscoveryProvider) return service.testDiscoveryProvider
        if (type == GrailsWorkspaceSymbolProvider) return new GrailsWorkspaceSymbolProvider(service)

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