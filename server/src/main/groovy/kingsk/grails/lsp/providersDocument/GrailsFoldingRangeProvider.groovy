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
        
        // Use visitor.getClassNodes() to handle multi-root
        visitor.getClassNodes().each { ClassNode clazz ->
            if (visitor.getURI(clazz) != textDocument.uri) return

            // Fold class body
            if (clazz.lineNumber > 0 && clazz.lastLineNumber > clazz.lineNumber) {
                ranges << new FoldingRange(clazz.lineNumber - 1, clazz.lastLineNumber - 1)
            }
            
            clazz.methods.each { MethodNode method ->
                if (method.lineNumber > 0 && method.lastLineNumber > method.lineNumber) {
                    ranges << new FoldingRange(method.lineNumber - 1, method.lastLineNumber - 1)
                }
            }
        }
        
        // Fold Import statements as a block
        // We can get them from the compilation unit's module
        org.codehaus.groovy.ast.ModuleNode module = visitor.getModuleNode(textDocument.uri)
        if (module != null && module.imports != null && module.imports.size() > 1) {
            List<org.codehaus.groovy.ast.ImportNode> imports = module.imports
            int start = imports.get(0).lineNumber
            int end = imports.get(imports.size() - 1).lineNumber
            if (end > start) {
                FoldingRange range = new FoldingRange(start - 1, end - 1)
                range.kind = FoldingRangeKind.Imports
                ranges.add(range)
            }
        }
        
        return CompletableFuture.completedFuture(ranges)
    }
}
