package kingsk.grails.lsp.providers.document

import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.services.WorkspaceManager

import groovy.transform.CompileStatic
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Position

import java.util.concurrent.CompletableFuture

@CompileStatic
class GrailsFormattingProvider extends BaseProvider {

    GrailsFormattingProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        super(providerContext, workspaceManager)
    }

    CompletableFuture<List<? extends TextEdit>> provideFormatting(TextDocumentIdentifier textDocument, FormattingOptions options) {
        CompletableFuture.completedFuture([] as List<TextEdit>)
    }
}
