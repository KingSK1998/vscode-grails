package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Position

import java.util.concurrent.CompletableFuture

@CompileStatic
class GrailsFormattingProvider extends BaseProvider {

    GrailsFormattingProvider(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }

    CompletableFuture<List<? extends TextEdit>> provideFormatting(TextDocumentIdentifier textDocument, FormattingOptions options) {
        CompletableFuture.completedFuture([] as List<TextEdit>)
    }
}
