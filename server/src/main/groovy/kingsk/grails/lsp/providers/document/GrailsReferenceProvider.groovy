package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.utils.ast.ASTUtils
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.TextDocumentIdentifier
import kingsk.grails.lsp.index.ReferenceInfo
import kingsk.grails.lsp.model.types.TextFile

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsReferenceProvider extends BaseProvider {
	
	GrailsReferenceProvider(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }
	
    CompletableFuture<List<? extends Location>> provideReferences(TextDocumentIdentifier textDocument, Position position, ReferenceContext context) {
        def token = createCancellationToken(textDocument.uri)
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync {
            try {
                checkCancellation(token)
                def offsetNode = getNodeAtPosition(textDocument, position)
                if (!offsetNode) {
                    log.debug("[REFERENCES] No offset node found")
                    return [] as List<Location>
                }

                if (getConfig().referencesUsesIndex) {
                    String targetName = null
                    if (offsetNode instanceof VariableExpression) {
                        targetName = ((VariableExpression) offsetNode).name
                    } else if (offsetNode instanceof MethodCallExpression) {
                        targetName = ((MethodCallExpression) offsetNode).methodAsString
                    } else if (offsetNode instanceof PropertyExpression) {
                        targetName = ((PropertyExpression) offsetNode).propertyAsString
                    } else if (offsetNode instanceof FieldNode) {
                        targetName = ((FieldNode) offsetNode).name
                    } else if (offsetNode instanceof MethodNode) {
                        targetName = ((MethodNode) offsetNode).name
                    } else if (offsetNode instanceof PropertyNode) {
                        targetName = ((PropertyNode) offsetNode).name
                    }

                    if (targetName) {
                        log.info("[REFERENCES] path=index tier=2 target=$targetName")
                        def refs = compilationContext.projectIndex.snapshot.getReferencesFor(targetName)
                        if (refs) {
                            def locations = refs.collect { ReferenceInfo ref ->
                                new Location(ref.fileUri, ref.range)
                            }
                            log.debug("[REFERENCES] Found ${locations.size()} locations via Index")
                            return locations as List<Location>
                        }
                    }
                }

                checkCancellation(token)
                log.info("[REFERENCES] path=ast tier=3 kind=fallback")
                log.debug("[REFERENCES] offsetNode: $offsetNode")

                def references = GrailsASTHelper.getReferences(offsetNode, visitor, position) ?: []
                checkCancellation(token)

                def locations = references.collect { ASTNode refNode ->
                    String fileUri = visitor.getURI(refNode) ?: TextFile.normalizePath(textDocument.uri)
                    ASTUtils.astNodeToLocation(refNode, fileUri)
                }.findAll { it != null }

                log.debug("[REFERENCES] found ${references.size()} references")
                log.debug("[REFERENCES] converted to ${locations.size()} locations")

                return locations as List<Location>
            } finally {
                recordHealth("references", System.currentTimeMillis() - startTime, true)
            }
        }
    }
}