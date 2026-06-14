package kingsk.grails.lsp.providers.document

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
import org.eclipse.lsp4j.*

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsSignatureHelpProvider extends BaseProvider {

    GrailsSignatureHelpProvider(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }

    CompletableFuture<SignatureHelp> provideSignatureHelp(TextDocumentIdentifier textDocument, Position position, SignatureHelpContext context) {
        def offset = getNodeAtPosition(textDocument, position)
        if (!offset) {
            log.debug("[SIGNATURE] Offset Node is null, returning empty Signature.")
            return emptyResult(new SignatureHelp([], -1, -1))
        }

        def parentNode = visitor.getParent(offset)
        int activeParamIndex = -1
        def methodCall = offset instanceof ArgumentListExpression ? parentNode as MethodCall : null
        
        if (offset instanceof ArgumentListExpression) {
            if (!methodCall) {
                log.debug("[SIGNATURE] method call is null, returning empty Signature.")
                return emptyResult(new SignatureHelp([], -1, -1))
            }

            activeParamIndex = getActiveParameter(position, offset.expressions)
        }

        def methods = GrailsASTHelper.getMethodOverloadsFromCallExpression(methodCall, visitor)
        if (!methods) {
            log.debug("[SIGNATURE] methods is empty, returning empty Signature.")
            return emptyResult(new SignatureHelp([], -1, -1))
        }

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
        CompletableFuture.completedFuture(new SignatureHelp(information, methods.indexOf(bestMethod), activeParamIndex))
    }

    private static int getActiveParameter(Position position, List<Expression> expressions) {
        return expressions.findIndexOf { expression ->
            Range range = ASTUtils.astNodeToRange(expression)
            // if range exist and line < end line return i OR ...
            range && ((position.line < range.end.line)
                ||
                (position.line == range.end.line
                    &&
                    position.character <= range.end.character))
        } ?: expressions.size()
    }
}
