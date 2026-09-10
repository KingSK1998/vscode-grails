package kingsk.grails.lsp.context

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.eclipse.lsp4j.Position

import java.util.Collections
import java.util.Set

/**
 * Null-safe, detached ASTAccessor used when a project is hibernated or uncompiled.
 * Retains zero ASTNodes, zero ClassLoaders, and zero CompilationUnits in memory.
 */
@CompileStatic
class DetachedASTAccessor implements ASTAccessor {
    static final DetachedASTAccessor INSTANCE = new DetachedASTAccessor()

    private DetachedASTAccessor() {}

    @Override
    ASTNode getNodeAtPosition(String uri, Position position) {
        return null
    }

    @Override
    ASTNode getNodeAtLineAndColumn(String uri, int line, int column) {
        return null
    }

    @Override
    ASTNode getParent(ASTNode node) {
        return null
    }

    @Override
    String getURI(ASTNode node) {
        return null
    }

    @Override
    Set<ASTNode> getNodes(String uri) {
        return Collections.emptySet()
    }

    @Override
    Set<ASTNode> getNodes() {
        return Collections.emptySet()
    }

    @Override
    Set<ClassNode> getClassNodes(String uri) {
        return Collections.emptySet()
    }

    @Override
    Set<ClassNode> getClassNodes() {
        return Collections.emptySet()
    }
}
