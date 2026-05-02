package kingsk.grails.lsp.providersDocument

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
        // In a real implementation, we would use a Groovy formatter (like Npm 'groovy-formatter' or a Java-based one).
        // For now, we implement a basic indentation-based formatter logic placeholder or return empty.
        // Actually, let's provide a simple "stub" that would be replaced by a real engine.
        
        return CompletableFuture.completedFuture([])
    }
}
