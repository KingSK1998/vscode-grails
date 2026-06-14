package kingsk.grails.lsp.index

import groovy.transform.CompileStatic
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import kingsk.grails.lsp.model.enums.GrailsArtifactType

@CompileStatic
record SymbolInfo(
    String descriptor,
    String containerDescriptor,
    String name,
    SymbolKind kind,
    Range range,
    Range selectionRange,
    String signature,
    String returnType,
    GrailsArtifactType artifact,
    int modifierFlags,
    String fileUri,
    String fieldType
) {}
