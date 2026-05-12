package kingsk.grails.lsp.providers.completions.strategies.snippet

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

/**
 * Provides named parameter completions for Grails DSL methods like render, redirect, etc.
 */
@Slf4j
@CompileStatic
class NamedParameterStrategy extends BaseCompletionStrategy {

    NamedParameterStrategy(CompletionRequest request) { super(request) }

    @Override
    int getPriority() { return 87 }

    @Override
    CompletionTarget target() { return CompletionTarget.OFFSET }

    @Override
    boolean canHandle(ASTNode node) {
        return getEnclosingMethodCall(node) != null
    }

    @Override
    void provideCompletions(ASTNode node) {
        MethodCallExpression methodCall = getEnclosingMethodCall(node)
        if (!methodCall) return

        List<MethodNode> methods = GrailsASTHelper.getMethodOverloadsFromCallExpression(methodCall, request.visitor)
        if (!methods && request.isGrailsProject) {
             // Fallback for grails named params DSL
             String name = methodCall.methodAsString
             if (name in ['render', 'redirect', 'respond', 'forward']) {
                 def params = getGrailsDslParams(name)
                 params.each { p ->
                     CompletionItem item = new CompletionItem(p)
                     item.kind = CompletionItemKind.Property
                     item.detail = "Named parameter"
                     item.insertText = "${p}: "
                     request.addCompletion(item)
                 }
                 return
             }
        }

        methods.each { MethodNode method ->
            if (method.parameters && method.parameters.length > 0) {
                // If the first parameter is a Map, it accepts named parameters!
                Parameter firstParam = method.parameters[0]
                if (firstParam.type.name == 'java.util.Map' || firstParam.type.name == 'java.util.LinkedHashMap') {
                    // It accepts named arguments. We don't necessarily know which ones unless it's statically typed.
                    // But we can at least suggest standard ones if it's a known Grails method
                }
                
                // Also suggest normal parameters as named parameters for Groovy named arg methods
                method.parameters.each { Parameter param ->
                    CompletionItem item = new CompletionItem(param.name)
                    item.kind = CompletionItemKind.Property
                    item.detail = "Named parameter (${param.type.nameWithoutPackage})"
                    item.insertText = "${param.name}: "
                    request.addCompletion(item)
                }
            }
        }
    }

    private MethodCallExpression getEnclosingMethodCall(ASTNode node) {
        ASTNode current = node
        while (current != null) {
            if (current instanceof ArgumentListExpression || current instanceof org.codehaus.groovy.ast.expr.TupleExpression) {
                ASTNode parent = getParentOf(current)
                if (parent instanceof MethodCallExpression) {
                    return parent
                }
            }
            if (current instanceof MethodCallExpression) {
                // if cursor is inside the parenthesis, usually node parses as MethodCallExpression but inside its arguments
                return current
            }
            current = getParentOf(current)
        }
        return null
    }

    private List<String> getGrailsDslParams(String methodName) {
        switch (methodName) {
            case 'render': return ['view', 'template', 'model', 'text', 'status', 'contentType', 'encoding']
            case 'redirect': return ['action', 'controller', 'id', 'params', 'url', 'uri', 'mapping']
            case 'forward': return ['action', 'controller', 'id', 'params']
            case 'respond': return ['view', 'model', 'status']
            default: return []
        }
    }
}
