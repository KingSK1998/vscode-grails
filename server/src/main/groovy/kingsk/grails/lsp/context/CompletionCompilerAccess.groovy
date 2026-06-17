package kingsk.grails.lsp.context

import kingsk.grails.lsp.model.types.TextFile

interface CompletionCompilerAccess {
    /**
     * Returns the ClassLoader used by the compiler.
     * Useful for reflectively loading trait methods.
     */
    ClassLoader getClassLoader()

    /**
     * Returns the source text after applying completion-specific patches.
     */
    TextFile getPatchedSourceUnitTextFile(TextFile file)
}
