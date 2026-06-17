package kingsk.grails.lsp.context

import kingsk.grails.lsp.model.types.TextFile
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.eclipse.lsp4j.Position

interface ASTAccessor {
    /**
     * Finds the AST node at the given position in the specified file.
     * Acquired and valid only within the current request scope.
     */
    ASTNode getNodeAtPosition(String uri, Position position)

    /**
     * Finds the AST node at the given line and column.
     */
    ASTNode getNodeAtLineAndColumn(String uri, int line, int column)

    /**
     * Returns the parent of the specified AST node.
     * Useful for climbing the tree to detect enclosing context.
     */
    ASTNode getParent(ASTNode node)

    /**
     * Returns the normalized URI of the file containing the given AST node.
     */
    String getURI(ASTNode node)
    
    /**
     * Returns all class nodes for the given URI or across all files if null.
     */
    Set<ASTNode> getNodes(String uri)
    Set<ASTNode> getNodes()

    /**
     * Returns all class nodes for the given URI or across all files if null.
     */
    Set<ClassNode> getClassNodes(String uri)
    Set<ClassNode> getClassNodes()
}
