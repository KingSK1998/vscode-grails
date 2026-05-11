package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import kingsk.grails.lsp.GrailsService
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Position

import java.util.concurrent.CompletableFuture

@CompileStatic
class GrailsFormattingProvider extends BaseProvider {

    GrailsFormattingProvider(GrailsService service) {
        super(service)
    }

    CompletableFuture<List<? extends TextEdit>> provideFormatting(TextDocumentIdentifier textDocument, FormattingOptions options) {
        CompletableFuture.completedFuture([] as List<TextEdit>)
    }
}
