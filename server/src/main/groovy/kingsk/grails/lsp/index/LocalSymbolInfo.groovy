package kingsk.grails.lsp.index

import groovy.transform.CompileStatic
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind

@CompileStatic
record LocalSymbolInfo(
    String name,
    SymbolKind kind,
    Range range,
    String signature,
    String returnType,
    String fileUri
) {}
