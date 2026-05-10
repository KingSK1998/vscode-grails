package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import kingsk.grails.lsp.GrailsService
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeKind
import org.eclipse.lsp4j.TextDocumentIdentifier

import java.util.concurrent.CompletableFuture

@CompileStatic
class GrailsFoldingRangeProvider extends BaseProvider {

    GrailsFoldingRangeProvider(GrailsService service) {
        super(service)
    }

    CompletableFuture<List<FoldingRange>> provideFoldingRanges(TextDocumentIdentifier textDocument) {
        List<FoldingRange> ranges = []

        visitor.getClassNodes().each { ClassNode clazz ->
            if (visitor.getURI(clazz) != textDocument.uri) return

            if (clazz.lineNumber > 0 && clazz.lastLineNumber > clazz.lineNumber) {
                ranges << new FoldingRange(clazz.lineNumber - 1, clazz.lastLineNumber - 1)
            }

            clazz.methods.each { MethodNode method ->
                if (method.lineNumber > 0 && method.lastLineNumber > method.lineNumber) {
                    ranges << new FoldingRange(method.lineNumber - 1, method.lastLineNumber - 1)
                }
            }
        }

        def module = visitor.getModuleNode(textDocument.uri)
        if (module?.imports && module.imports.size() > 1) {
            def imports = module.imports
            int start = imports[0].lineNumber
            int end = imports[-1].lineNumber
            if (end > start) {
                ranges << new FoldingRange(start - 1, end - 1).with {
                    it.kind = FoldingRangeKind.Imports
                    it
                }
            }
        }

        CompletableFuture.completedFuture(ranges)
    }
}
