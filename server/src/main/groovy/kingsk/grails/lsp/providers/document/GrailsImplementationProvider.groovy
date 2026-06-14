package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.utils.ast.ASTUtils
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
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
    
    GrailsImplementationProvider(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }
    
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> provideImplementation(TextDocumentIdentifier textDocument, Position position) {
        def offsetNode = getNodeAtPosition(textDocument, position)
        if (!offsetNode) {
            log.debug("[IMPLEMENTATION] No offset node found")
            return emptyResult(Either.forLeft([]))
        }

        def defNode = GrailsASTHelper.getDefinition(offsetNode, false, visitor)
        List<Location> implementationLocations = []

        if (defNode instanceof ClassNode && defNode.isInterface()) {
            visitor.getClassNodes().each { potentialImpl ->
                if (potentialImpl.allInterfaces.any { it.name == defNode.name }) {
                    def uri = visitor.getURI(potentialImpl)
                    if (uri) implementationLocations << ASTUtils.astNodeToLocation(potentialImpl, uri)
                }
            }
        } else if (defNode instanceof MethodNode && (defNode.isAbstract() || defNode.declaringClass.isInterface())) {
            visitor.getClassNodes().each { potentialImpl ->
                if (potentialImpl.allInterfaces.any { it.name == defNode.declaringClass.name } || potentialImpl.isDerivedFrom(defNode.declaringClass)) {
                    def overridingMethod = potentialImpl.getMethod(defNode.name, defNode.parameters)
                    if (overridingMethod && overridingMethod != defNode) {
                        def uri = visitor.getURI(overridingMethod)
                        if (uri) implementationLocations << ASTUtils.astNodeToLocation(overridingMethod, uri)
                    }
                }
            }
        }

        if (implementationLocations.empty && defNode) {
            def uri = visitor.getURI(defNode) ?: textDocument.uri
            implementationLocations << ASTUtils.astNodeToLocation(defNode, uri)
        }

        CompletableFuture.completedFuture(Either.forLeft(implementationLocations))
    }
}
