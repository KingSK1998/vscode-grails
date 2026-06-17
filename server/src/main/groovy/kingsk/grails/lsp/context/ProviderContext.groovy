package kingsk.grails.lsp.context

import kingsk.grails.lsp.model.config.GrailsLspConfig
import kingsk.grails.lsp.protocol.GrailsLanguageClient
import kingsk.grails.lsp.providers.ProviderRegistry
import kingsk.grails.lsp.services.CancellationService
import kingsk.grails.lsp.services.ErrorService
import kingsk.grails.lsp.services.ProviderHealthService
import kingsk.grails.lsp.services.GrailsDiagnosticService
import kingsk.grails.lsp.services.DiscoveryService

interface ProviderContext {
    DiscoveryService getDiscoveryService()
    ErrorService getErrorService()
    GrailsDiagnosticService getDiagnostics()
    CancellationService getCancellationService()
    ProviderHealthService getHealthService()
    GrailsLspConfig getConfig()
    ProviderRegistry getProviderRegistry()
    GrailsLanguageClient getClient()
    kingsk.grails.lsp.services.FileContentTracker getFileTracker()
}