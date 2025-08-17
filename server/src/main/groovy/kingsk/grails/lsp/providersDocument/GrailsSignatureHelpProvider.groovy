package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.DocumentationType
import kingsk.grails.lsp.utils.ASTUtils
import kingsk.grails.lsp.utils.DocumentationHelper
import kingsk.grails.lsp.utils.GrailsASTHelper
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
	
	GrailsSignatureHelpProvider(GrailsService service) {
		super(service)
	}
	
	CompletableFuture<SignatureHelp> provideSignatureHelp(TextDocumentIdentifier textDocument, Position position, SignatureHelpContext context) {
		ASTNode offset = getNodeAtPosition(textDocument, position)
		if (!offset) {
			log.debug("[SIGNATURE] Offset Node is null, returning empty Signature.")
			return emptyResult(new SignatureHelp([], -1, -1))
		}
		
		ASTNode parentNode = visitor.getParent(offset)
		int activeParamIndex = -1
		MethodCall methodCall = null
		if (offset instanceof ArgumentListExpression) {
			methodCall = parentNode as MethodCall
			if (!methodCall) {
				log.debug("[SIGNATURE] method call is null, returning empty Signature.")
				return emptyResult(new SignatureHelp([], -1, -1))
			}
			
			List<Expression> expressions = offset.getExpressions()
			activeParamIndex = getActiveParameter(position, expressions)
		}
		
		List<MethodNode> methods = GrailsASTHelper.getMethodOverloadsFromCallExpression(methodCall, visitor)
		if (!methods) {
			log.debug("[SIGNATURE] methods is empty, returning empty Signature.")
			return emptyResult(new SignatureHelp([], -1, -1))
		}
		
		List<SignatureInformation> informations = methods.collect { method ->
			String label = ASTUtils.astNodeToName(method)
			MarkupContent documentation = DocumentationHelper.getDocumentation(method, service, DocumentationType.SIGNATURE_HELP)
			List<ParameterInformation> parameters = method.parameters.collect { param ->
				String paramName = ASTUtils.astNodeToName(param)
				MarkupContent paramDocs = DocumentationHelper.getDocumentation(method, service, DocumentationType.SIGNATURE_HELP)
				return new ParameterInformation(paramName, paramDocs)
			}
			
			return new SignatureInformation(label, documentation, parameters)
		}
		
		MethodNode bestMethod = GrailsASTHelper.getMethodFromCallExpression(methodCall, visitor, activeParamIndex)
		log.info("[SIGNATURE] Provided workspace symbols for document")
		return CompletableFuture.completedFuture(new SignatureHelp(informations, methods.indexOf(bestMethod), activeParamIndex))
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
