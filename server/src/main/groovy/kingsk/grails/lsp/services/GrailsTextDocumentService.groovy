package kingsk.grails.lsp.services

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.providers.document.GrailsCompletionProvider
import kingsk.grails.lsp.providers.document.GrailsYamlIntelligenceProvider
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.TextDocumentService

import java.util.concurrent.ConcurrentHashMap

@Slf4j
@CompileStatic
class GrailsTextDocumentService implements TextDocumentService {
    private final GrailsService service
    private final Map<String, TextFile> pendingChanges = new ConcurrentHashMap<>()
    private final GrailsCompletionProvider completionProvider
    final GrailsYamlIntelligenceProvider yamlProvider

    GrailsTextDocumentService(GrailsService service) {
        this.service = service
        this.completionProvider = service.providerRegistry.getProvider(GrailsCompletionProvider)
        this.yamlProvider = service.providerRegistry.getProvider(GrailsYamlIntelligenceProvider)
    }

    @Override
    void didOpen(DidOpenTextDocumentParams params) {
        try {
            log.info("[DOCUMENT] - Opened: ${params.textDocument.uri}")
            
            def textFile = service.fileTracker.didOpenFile(params)
            if (textFile) {
                service.workspaceManager.getProjectForUri(textFile.uri)?.compiler?.markDirty(textFile.uri)
                service.workspaceManager.getProjectForUri(textFile.uri)?.compileAndVisitAST(textFile)
            }
        } catch (Exception e) {
            service.errorService.handleError("Failed to handle didOpen", e)
        }
    }

    @Override
    void didChange(DidChangeTextDocumentParams params) {
        try {
            def uri = params.textDocument.uri
            def textFile = service.fileTracker.didChangeFile(params)
            if (textFile) {
                pendingChanges.put(uri, textFile)
                debounceCompile(uri)
            }
        } catch (Exception e) {
            service.errorService.handleError("Failed to handle didChange", e)
        }
    }

    private void debounceCompile(String uriString) {
        def latestTextFile = pendingChanges.get(uriString)
        if (latestTextFile) {
            try {
                service.workspaceManager.getProjectForUri(latestTextFile.uri)?.compiler?.markDirty(latestTextFile.uri)
                service.workspaceManager.getProjectForUri(latestTextFile.uri)?.compileAndVisitAST(latestTextFile)
            } catch (Exception e) {
                service.errorService.handleError("Error in debounced compile", e)
            }
        }
    }

    @Override
    void didClose(DidCloseTextDocumentParams params) {
        try {
            log.info("[DOCUMENT] - Closed: ${params.textDocument.uri}")
            def textFile = service.fileTracker.didCloseFile(params)
            if (textFile) {
                service.workspaceManager.getProjectForUri(textFile.uri)?.visitor?.removeFileWithDependencies(textFile.uri)
                service.workspaceManager.getProjectForUri(textFile.uri)?.indexManager?.evictFile(textFile.uri)
                service.diagnostics.clearDiagnosticsForFile(textFile.uri)
                completionProvider.clearCaches(textFile.uri)
            }
        } catch (Exception e) {
            service.errorService.handleError("Failed to handle didClose", e)
        }
    }

    @Override
    void didSave(DidSaveTextDocumentParams params) {
    }

    void shutdown() {
        pendingChanges.clear()
    }
}
