package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.utils.ASTUtils
import kingsk.grails.lsp.utils.GrailsASTHelper
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsImplementationProvider extends BaseProvider {
    
    GrailsImplementationProvider(GrailsService service) {
        super(service)
    }
    
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> provideImplementation(TextDocumentIdentifier textDocument, Position position) {
        ASTNode offsetNode = getNodeAtPosition(textDocument, position)
        if (!offsetNode) {
            log.debug("[IMPLEMENTATION] No offset node found")
            return emptyResult(Either.forLeft([]))
        }

        // Implementation resolution logic: 
        // 1. If it's an interface ClassNode, find implementing classes.
        // 2. If it's an abstract MethodNode, find overriding methods.
        // 3. Fallback: Definition.
        
        ASTNode defNode = GrailsASTHelper.getDefinition(offsetNode, false, visitor)
        List<Location> implementationLocations = []

        if (defNode instanceof ClassNode && defNode.isInterface()) {
            // Find implementing classes in workspace
            visitor.getClassNodes().each { potentialImpl ->
                if (potentialImpl.allInterfaces.any { it.name == defNode.name }) {
                    def uri = visitor.getURI(potentialImpl)
                    if (uri) implementationLocations << ASTUtils.astNodeToLocation(potentialImpl, uri)
                }
            }
        } else if (defNode instanceof MethodNode && (defNode.isAbstract() || defNode.declaringClass.isInterface())) {
            // Find overriding methods
            visitor.getClassNodes().each { potentialImpl ->
                if (potentialImpl.allInterfaces.any { it.name == defNode.declaringClass.name } || potentialImpl.isDerivedFrom(defNode.declaringClass)) {
                    MethodNode overridingMethod = potentialImpl.getMethod(defNode.name, defNode.parameters)
                    if (overridingMethod && overridingMethod != defNode) {
                        def uri = visitor.getURI(overridingMethod)
                        if (uri) implementationLocations << ASTUtils.astNodeToLocation(overridingMethod, uri)
                    }
                }
            }
        }

        // Fallback to definition if no specific implementations found
        if (implementationLocations.isEmpty() && defNode) {
            def uri = visitor.getURI(defNode) ?: textDocument.uri
            implementationLocations << ASTUtils.astNodeToLocation(defNode, uri)
        }
        
        return CompletableFuture.completedFuture(Either.forLeft(implementationLocations))
    }
}
