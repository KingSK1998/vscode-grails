package kingsk.grails.lsp.context

import kingsk.grails.lsp.model.config.GrailsLspConfig
import kingsk.grails.lsp.protocol.GrailsLanguageClient
import kingsk.grails.lsp.providers.ProviderRegistry
import kingsk.grails.lsp.services.CancellationService
import kingsk.grails.lsp.services.ErrorService
import kingsk.grails.lsp.services.ProviderHealthService

interface ProviderContext {
    ErrorService getErrorService()
    CancellationService getCancellationService()
    ProviderHealthService getHealthService()
    GrailsLspConfig getConfig()
    ProviderRegistry getProviderRegistry()
    GrailsLanguageClient getClient()
}