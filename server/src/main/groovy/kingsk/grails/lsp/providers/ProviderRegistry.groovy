package kingsk.grails.lsp.providers

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.providers.document.GrailsCodeActionProvider
import kingsk.grails.lsp.providers.document.GrailsCodeLensProvider
import kingsk.grails.lsp.providers.document.GrailsCompletionProvider
import kingsk.grails.lsp.providers.document.GrailsDefinitionProvider
import kingsk.grails.lsp.providers.document.GrailsDocumentSymbolProvider
import kingsk.grails.lsp.providers.document.GrailsFoldingRangeProvider
import kingsk.grails.lsp.providers.document.GrailsFormattingProvider
import kingsk.grails.lsp.providers.document.GrailsGormSqlProvider
import kingsk.grails.lsp.providers.document.GrailsHoverProvider
import kingsk.grails.lsp.providers.document.GrailsImplementationProvider
import kingsk.grails.lsp.providers.document.GrailsInlayHintProvider
import kingsk.grails.lsp.providers.document.GrailsReferenceProvider
import kingsk.grails.lsp.providers.document.GrailsRenameProvider
import kingsk.grails.lsp.providers.document.GrailsSemanticTokensProvider
import kingsk.grails.lsp.providers.document.GrailsSignatureHelpProvider
import kingsk.grails.lsp.providers.document.GrailsTypeDefinitionProvider
import kingsk.grails.lsp.providers.document.GrailsYamlIntelligenceProvider
import kingsk.grails.lsp.providers.workspace.GrailsDependencyProvider
import kingsk.grails.lsp.providers.workspace.GrailsTestDiscoveryProvider
import kingsk.grails.lsp.providers.workspace.GrailsWorkspaceSymbolProvider
import kingsk.grails.lsp.services.WorkspaceManager

import java.util.concurrent.ConcurrentHashMap

@Slf4j
@CompileStatic
class ProviderRegistry {
    private final ProviderContext providerContext
    private final WorkspaceManager workspaceManager
    private final Map<Class<?>, Object> providers = new ConcurrentHashMap<>()

    ProviderRegistry(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        this.providerContext = providerContext
        this.workspaceManager = workspaceManager
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

        if (type == GrailsCompletionProvider) return new GrailsCompletionProvider(providerContext, workspaceManager)
        if (type == GrailsHoverProvider) return new GrailsHoverProvider(providerContext, workspaceManager)
        if (type == GrailsDefinitionProvider) return new GrailsDefinitionProvider(providerContext, workspaceManager)
        if (type == GrailsTypeDefinitionProvider) return new GrailsTypeDefinitionProvider(providerContext, workspaceManager)
        if (type == GrailsImplementationProvider) return new GrailsImplementationProvider(providerContext, workspaceManager)
        if (type == GrailsFormattingProvider) return new GrailsFormattingProvider(providerContext, workspaceManager)
        if (type == GrailsFoldingRangeProvider) return new GrailsFoldingRangeProvider(providerContext, workspaceManager)
        if (type == GrailsCodeActionProvider) return new GrailsCodeActionProvider(providerContext, workspaceManager)
        if (type == GrailsReferenceProvider) return new GrailsReferenceProvider(providerContext, workspaceManager)
        if (type == GrailsSignatureHelpProvider) return new GrailsSignatureHelpProvider(providerContext, workspaceManager)
        if (type == GrailsDocumentSymbolProvider) return new GrailsDocumentSymbolProvider(providerContext, workspaceManager)
        if (type == GrailsCodeLensProvider) return new GrailsCodeLensProvider(providerContext, workspaceManager)
        if (type == GrailsInlayHintProvider) return new GrailsInlayHintProvider(providerContext, workspaceManager)
        if (type == GrailsRenameProvider) return new GrailsRenameProvider(providerContext, workspaceManager)
        if (type == GrailsSemanticTokensProvider) return new GrailsSemanticTokensProvider(providerContext, workspaceManager)
        if (type == GrailsYamlIntelligenceProvider) return new GrailsYamlIntelligenceProvider(providerContext, workspaceManager)
        if (type == GrailsGormSqlProvider) return new GrailsGormSqlProvider(providerContext, workspaceManager)
        if (type == GrailsDependencyProvider) return new GrailsDependencyProvider(providerContext, workspaceManager)
        if (type == GrailsTestDiscoveryProvider) return new GrailsTestDiscoveryProvider(providerContext, workspaceManager)
        if (type == GrailsWorkspaceSymbolProvider) return new GrailsWorkspaceSymbolProvider(providerContext, workspaceManager)

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
