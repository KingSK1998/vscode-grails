package kingsk.grails.lsp.providers.completions

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.ProviderContext
import org.codehaus.groovy.ast.ASTNode

@CompileStatic
record CompletionRequest(
    String uri,
    ASTNode offsetNode,
    ASTNode parentNode,
    String prefix,
    boolean isGrailsProject,
    ProviderContext providerContext
) {}
