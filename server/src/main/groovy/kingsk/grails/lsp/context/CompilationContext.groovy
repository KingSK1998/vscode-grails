package kingsk.grails.lsp.context

import kingsk.grails.lsp.core.compiler.GrailsCompiler
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.services.ASTService
import kingsk.grails.lsp.services.FileContentTracker

interface CompilationContext {
    GrailsCompiler getCompiler()
    GrailsASTVisitor getVisitor()
    FileContentTracker getFileTracker()
    ASTService getAstService()
    void compileAndVisitAST(TextFile textFile)
    void visitAST(TextFile textFile)
    <T> T withReadLock(groovy.lang.Closure<T> closure)
}