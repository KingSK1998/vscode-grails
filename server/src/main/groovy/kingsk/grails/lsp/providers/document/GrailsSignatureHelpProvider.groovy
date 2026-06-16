package kingsk.grails.lsp.providers.document

import kingsk.grails.lsp.context.CompilationContext
import kingsk.grails.lsp.context.ProjectContext
import kingsk.grails.lsp.context.ProviderContext

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.enums.DocumentationType
import kingsk.grails.lsp.utils.ast.ASTUtils
import kingsk.grails.lsp.utils.diagnostics.DocumentationHelper
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCall
import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpContext
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.TextDocumentIdentifier

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsSignatureHelpProvider extends BaseProvider {

    GrailsSignatureHelpProvider(ProviderContext providerContext, CompilationContext compilationContext, ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }

    CompletableFuture<SignatureHelp> provideSignatureHelp(TextDocumentIdentifier textDocument, Position position, SignatureHelpContext context) {
        def token = createCancellationToken(textDocument.uri)
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync {
            try {
                checkCancellation(token)
                def offset = getNodeAtPosition(textDocument, position)
                if (!offset) {
                    log.debug("[SIGNATURE] Offset Node is null, returning empty Signature.")
                    return new SignatureHelp([], -1, -1)
                }

                // Climb up to find the closest enclosing MethodCall
                ASTNode current = offset
                MethodCall methodCall = null
                int depth = 0
                while (current != null && depth < 100) {
                    checkCancellation(token)
                    if (current instanceof MethodCall) {
                        methodCall = (MethodCall) current
                        break
                    }
                    current = visitor.getParent(current)
                    depth++
                }

                if (!methodCall) {
                    log.debug("[SIGNATURE] No enclosing method call found, returning empty Signature.")
                    return new SignatureHelp([], -1, -1)
                }

                int activeParamIndex = -1
                Expression args = methodCall.arguments
                if (args instanceof ArgumentListExpression) {
                    activeParamIndex = getActiveParameter(position, ((ArgumentListExpression) args).expressions)
                }

                checkCancellation(token)
                def methods = GrailsASTHelper.getMethodOverloadsFromCallExpression(methodCall, visitor)
                if (!methods) {
                    log.debug("[SIGNATURE] methods is empty, returning empty Signature.")
                    return new SignatureHelp([], -1, -1)
                }

                checkCancellation(token)
                def information = methods.collect { method ->
                    def label = ASTUtils.astNodeToName(method)
                    def documentation = DocumentationHelper.getDocumentation(method, project.isGrailsProject, visitor, DocumentationType.SIGNATURE_HELP)
                    def parameters = method.parameters.collect { param ->
                        def paramName = ASTUtils.astNodeToName(param)
                        def paramDocs = DocumentationHelper.getDocumentation(method, project.isGrailsProject, visitor, DocumentationType.SIGNATURE_HELP)
                        new ParameterInformation(paramName, paramDocs)
                    }

                    new SignatureInformation(label, documentation, parameters)
                }

                def bestMethod = GrailsASTHelper.getMethodFromCallExpression(methodCall, visitor, activeParamIndex)
                log.info("[SIGNATURE] Provided workspace symbols for document")
                return new SignatureHelp(information, methods.indexOf(bestMethod), activeParamIndex)
            } catch (Exception e) {
                recordHealth("SignatureHelpProvider", System.currentTimeMillis() - startTime, false)
                throw e
            } finally {
                recordHealth("SignatureHelpProvider", System.currentTimeMillis() - startTime, true)
            }
        }
    }

    private static int getActiveParameter(Position position, List<Expression> expressions) {
        int index = expressions.findIndexOf { expression ->
            Range range = ASTUtils.astNodeToRange(expression)
            // if range exist and line < end line return i OR ...
            range && ((position.line < range.end.line)
                ||
                (position.line == range.end.line
                    &&
                    position.character <= range.end.character))
        }
        return index >= 0 ? index : expressions.size()
    }
}
