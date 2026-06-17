package kingsk.grails.lsp.model.state

import groovy.transform.CompileStatic
import kingsk.grails.lsp.index.IndexSnapshot
import kingsk.grails.lsp.model.dto.GradleModel
import kingsk.grails.lsp.context.ASTAccessor

/**
 * Immutable record capturing the entire state of a project at a specific point in time.
 * ENSURES: Consistency between Symbol Index and AST Generation.
 */
@CompileStatic
record VersionedSnapshot(
    long version,
    IndexSnapshot index,
    GradleModel gradleModel,
    ASTAccessor ast, // Bound to the specific compilation generation
    Map<String, String> fileHashes,
    long timestamp
) {}
