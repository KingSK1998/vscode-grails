package kingsk.grails.lsp.providers.completions.strategies.context

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.ASTAccessor
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.eclipse.lsp4j.CompletionItem

/**
 * Handles completions for Closure Delegates inside DSLs, e.g., obj.with { | }
 */
@Slf4j
@CompileStatic
class ClosureDelegateStrategy extends BaseCompletionStrategy {

    @Override
    int getPriority() { return 65 }

    @Override
    boolean canHandle(CompletionRequest request, RequestContext ctx) {
        return getEnclosingClosureMethodCall(request.offsetNode, ctx.ast()) != null || 
               getEnclosingClosureField(request.offsetNode, ctx.ast()) != null
    }

    @Override
    List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
        List<CompletionItem> completions = []
        MethodCallExpression methodCall = getEnclosingClosureMethodCall(request.offsetNode, ctx.ast())
        if (methodCall) {
            ClassNode delegateType = resolveDelegateType(methodCall, ctx)
            if (delegateType) {
                log.debug("Found closure delegate type from method call: ${delegateType.name}")
                org.codehaus.groovy.ast.expr.VariableExpression dummyObj = new org.codehaus.groovy.ast.expr.VariableExpression("it", delegateType)
                addMemberCompletions(dummyObj, ctx, completions)
            }
            return completions
        }

        def fieldClosure = getEnclosingClosureField(request.offsetNode, ctx.ast())
        if (fieldClosure) {
            String fieldName = fieldClosure.name
            // Domain class can be derived from visitor
            ClassNode domainClass = ctx.compilationContext().visitor.allClassNodes.values().flatten().find { it instanceof ClassNode && ((ClassNode)it).name == ctx.uri() } as ClassNode
            
            if (domainClass) {
                if (fieldName == 'constraints') {
                    domainClass.properties.each { org.codehaus.groovy.ast.PropertyNode p ->
                        CompletionItem item = new CompletionItem(p.name)
                        item.kind = org.eclipse.lsp4j.CompletionItemKind.Property
                        item.detail = "Domain property constraint"
                        item.insertText = (p.name + "(nullable: false)").toString()
                        completions.add(item)
                    }
                }
            }
        }
        return completions
    }

    private org.codehaus.groovy.ast.FieldNode getEnclosingClosureField(ASTNode node, ASTAccessor ast) {
        ASTNode current = node
        while (current != null) {
            if (current instanceof ClosureExpression) {
                ASTNode parent = ast.getParent(current)
                if (parent instanceof org.codehaus.groovy.ast.FieldNode) {
                    return parent as org.codehaus.groovy.ast.FieldNode
                }
            }
            current = ast.getParent(current)
        }
        return null
    }

    private MethodCallExpression getEnclosingClosureMethodCall(ASTNode node, ASTAccessor ast) {
        ASTNode current = node
        while (current != null) {
            if (current instanceof ClosureExpression) {
                ASTNode parent = ast.getParent(current)
                if (parent instanceof org.codehaus.groovy.ast.expr.ArgumentListExpression) {
                    ASTNode grandParent = ast.getParent(parent)
                    if (grandParent instanceof MethodCallExpression) {
                        return grandParent
                    }
                }
            }
            current = ast.getParent(current)
        }
        return null
    }

    private ClassNode resolveDelegateType(MethodCallExpression methodCall, RequestContext ctx) {
        String methodName = methodCall.methodAsString
        if (methodName in ['with', 'tap']) {
            return getTypeOf(methodCall.objectExpression, ctx)
        }
        if (methodName in ['createCriteria', 'where', 'withCriteria']) {
            return getTypeOf(methodCall.objectExpression, ctx)
        }
        return null
    }
}
