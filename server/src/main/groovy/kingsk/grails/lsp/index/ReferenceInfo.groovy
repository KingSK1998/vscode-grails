package kingsk.grails.lsp.index

import groovy.transform.CompileStatic
import org.eclipse.lsp4j.Range

@CompileStatic
record ReferenceInfo(
    String targetName,
    String fileUri,
    Range range
) {}
